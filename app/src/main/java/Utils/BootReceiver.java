package Utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.mindtick.MainActivity;
import com.example.mindtick.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Data.DatabaseHandler;
import Model.Task;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final long BOOT_DELAY_MS = 8000; // задержка перед работой после загрузки

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        // Обрабатываем только нормальный BOOT_COMPLETED (не делать DB-операции на LOCKED_BOOT_COMPLETED)
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            // Нам не нужна логика Direct Boot — пропускаем (пользователь попросил не делать direct-boot)
            Log.d(TAG, "Получен LOCKED_BOOT_COMPLETED — пропускаем восстановление (ожидаем BOOT_COMPLETED после разблокировки).");
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Не BOOT_COMPLETED — игнорируем.");
            return;
        }

        // Немного подождём — чтобы система и провайдеры успели стартануть
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "Начинаю обработку задач после перезагрузки (через delay).");

                DatabaseHandler db = new DatabaseHandler(context);
                List<Task> allTasks = db.getAllTasks();

                if (allTasks == null || allTasks.isEmpty()) {
                    Log.d(TAG, "В БД задач не найдено (или null).");
                    return;
                }

                Log.d(TAG, "Найдено задач в БД: " + allTasks.size());

                long now = System.currentTimeMillis();
                int restored = 0;
                int missed = 0;

                for (Task task : allTasks) {
                    try {
                        if (task == null) continue;

                        // проверяем флаг напоминания и поля даты/времени
                        if (task.getReminderEnabled() != 1) {
                            Log.d(TAG, "Пропускаю задачу (напоминание отключено): id=" + task.getId() + " title=\"" + task.getTitle() + "\"");
                            continue;
                        }

                        String date = task.getDate();
                        String time = task.getTime();

                        if (date == null || date.isEmpty() || time == null || time.isEmpty()) {
                            Log.w(TAG, "Пропускаю задачу (нет date/time): id=" + task.getId() + " title=\"" + task.getTitle() + "\"");
                            continue;
                        }

                        long taskTimeMillis = getTimeInMillis(date, time);
                        if (taskTimeMillis <= 0) {
                            Log.w(TAG, "Не могу распарсить дату/время для задачи: id=" + task.getId() + " title=\"" + task.getTitle() + "\"");
                            continue;
                        }

                        if (taskTimeMillis <= now) {
                            // просрочено — показываем "пропущенное напоминание"
                            Log.d(TAG, "Пропущенное напоминание: id=" + task.getId() + " title=\"" + task.getTitle() + "\" scheduled=" + new Date(taskTimeMillis));
                            showMissedNotification(context, task);
                            missed++;
                        } else {
                            // будущее — пересоздаём будильник
                            Log.d(TAG, "Восстанавливаю будильник: id=" + task.getId() + " title=\"" + task.getTitle() + "\" scheduled=" + new Date(taskTimeMillis));
                            ReminderHelper.setAlarm(context, task);
                            restored++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка при обработке одной задачи в цикле", e);
                    }
                }

                Log.d(TAG, "Обработка задач после перезагрузки завершена. Восстановлено: " + restored + ", Пропущено показано: " + missed);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка в BootReceiver.onReceive (после delay)", e);
            }
        }, BOOT_DELAY_MS);
    }

    // Вспомогательный парсер даты+времени -> ms
    private long getTimeInMillis(String date, String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            Date dateTime = sdf.parse(date + " " + time);
            return dateTime == null ? 0L : dateTime.getTime();
        } catch (Exception e) {
            Log.w(TAG, "getTimeInMillis: parse error for \"" + date + " " + time + "\" -> " + e.getMessage());
            return 0L;
        }
    }

    // Отдельный канал + уведомление для пропущенных задач
// Внутри класса BootReceiver

    private void showMissedNotification(Context context, Task task) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Log.e(TAG, "NotificationManager == null, не могу показать пропущенное уведомление для id=" + task.getId());
                return;
            }

            String channelId = "missed_tasks";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "Пропущенные напоминания",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Уведомления о напоминаниях, которые не сработали до перезагрузки");
                channel.enableVibration(true);
                channel.setLightColor(0xFFFF0000);
                nm.createNotificationChannel(channel);
            }

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.putExtra("task_id", task.getId());
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    (int) task.getId(),
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Intent для кнопки "Отложить"
            Intent snoozeIntent = new Intent(context, ReminderActionReceiver.class);
            snoozeIntent.setAction("ACTION_SNOOZE");
            snoozeIntent.putExtra("task_id", task.getId());
            PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) task.getId(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Intent для кнопки "Выполнено"
            Intent doneIntent = new Intent(context, ReminderActionReceiver.class);
            doneIntent.setAction("ACTION_DONE");
            doneIntent.putExtra("task_id", task.getId());
            PendingIntent donePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) task.getId() + 100000, // уникальный requestCode
                    doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.baseline_notifications_active_24w)
                    .setContentTitle("Пропущенное напоминание")
                    .setContentText(task.getTitle())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    // Добавляем кнопки с действиями
                    .addAction(R.drawable.baseline_calendar_today_24, "Отложить", snoozePendingIntent)
                    .addAction(R.drawable.baseline_notifications_active_24w, "Выполнено", donePendingIntent);

            nm.notify((int) task.getId(), builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при показе пропущенного уведомления для id=" + (task == null ? "null" : task.getId()), e);
        }
    }
}
