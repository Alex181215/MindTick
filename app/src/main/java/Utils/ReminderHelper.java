package Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.mindtick.R;
import com.example.mindtick.MainActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import Model.Task;

public class ReminderHelper {

    private static final String TAG = "ReminderHelper";
    private static final String PREFS_NAME = "reminders_prefs";
    private static final String PREFS_KEY = "reminders";

    // --- существующий метод, теперь сохраняет в DirectBoot prefs
    public static void setAlarm(Context context, Task task) {
        Log.d(TAG, "setAlarm вызван: " + task.getTitle() + ", дата: " + task.getDate() + ", время: " + task.getTime());

        if (task.getReminderEnabled() != 1) {
            Log.d(TAG, "Напоминание не включено для задачи: " + task.getTitle());
            return;
        }

        long reminderTime = getReminderTimeMillis(task);
        Log.d(TAG, "Вычисленное время напоминания (ms): " + reminderTime);

        if (reminderTime == -1) {
            Log.d(TAG, "Некорректные дата/время для задачи: " + task.getTitle());
            return;
        }

        if (reminderTime < System.currentTimeMillis()) {
            Log.d(TAG, "Напоминание в прошлом. Пропускаем для задачи: " + task.getTitle());
            return;
        }

        // ставим будильник
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
            Log.d(TAG, "Будильник установлен на: " + new Date(reminderTime).toString());

            // сохраняем в Direct Boot prefs (чтобы восстановить до разблокировки)
            saveReminderToDirectBoot(context, task.getId(), task.getTitle(), reminderTime);

        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка установки будильника — нет разрешения", e);
        }
    }

    // --- перегруженная версия для восстановления из Direct Boot (если нужно)
    public static void setAlarm(Context context, long taskId, String title, long reminderTime) {
        Log.d(TAG, "setAlarm(restore) вызван для id=" + taskId + ", title=" + title + ", time=" + reminderTime);

        if (reminderTime < System.currentTimeMillis()) {
            Log.d(TAG, "restore: время в прошлом, пропускаем id=" + taskId);
            return;
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("task_id", taskId);
        intent.putExtra("task_title", title);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) taskId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
            Log.d(TAG, "restore: Будильник восстановлен на: " + new Date(reminderTime).toString());

            // сохраняем (или обновляем) в direct-boot prefs
            saveReminderToDirectBoot(context, taskId, title, reminderTime);

        } catch (SecurityException e) {
            Log.e(TAG, "restore: ошибка установки будильника — нет разрешения", e);
        }
    }

    public static void cancelAlarm(Context context, Task task) {
        Log.d(TAG, "Попытка отменить будильник для задачи с ID: " + task.getId());

        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        // удаляем из Direct Boot prefs
        removeReminderFromDirectBoot(context, task.getId());

        Log.d(TAG, "Будильник для задачи с id " + task.getId() + " отменен и удалён из DirectBoot prefs.");
    }

    // Метод для получения времени напоминания в миллисекундах
    public static long getReminderTimeMillis(Task task) {
        String dateStr = task.getDate();
        String timeStr = task.getTime();

        Log.d(TAG, "getReminderTimeMillis вызван с date: " + dateStr + ", time: " + timeStr);

        if (dateStr == null || timeStr == null || dateStr.isEmpty() || timeStr.isEmpty()) {
            Log.w(TAG, "Пустая дата или время для задачи: " + task.getTitle());
            return -1;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try {
            Date taskDate = dateFormat.parse(dateStr);
            Date taskTime = timeFormat.parse(timeStr);

            if (taskDate != null && taskTime != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(taskDate);
                calendar.set(Calendar.HOUR_OF_DAY, taskTime.getHours());
                calendar.set(Calendar.MINUTE, taskTime.getMinutes());
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                long result = calendar.getTimeInMillis();
                Log.d(TAG, "Время напоминания вычислено: " + new Date(result).toString());
                return result;
            }
        } catch (ParseException e) {
            Log.e(TAG, "Ошибка парсинга даты/времени: " + e.getMessage());
        }
        return -1;
    }

    // Метод для показа уведомления о пропущенном напоминании (оставляем как есть)
    public static void showMissedReminder(Context context, long taskId, String taskTitle) {
        Log.d(TAG, "showMissedReminder вызван для задачи ID: " + taskId + ", title: " + taskTitle);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Нет разрешения на показ уведомлений, пропускаем showMissedReminder");
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "reminder_channel")
                .setSmallIcon(R.drawable.baseline_notifications_active_24w)
                .setContentTitle("Пропущенное напоминание")
                .setContentText("Задача \"" + taskTitle + "\" была запланирована, но пропущена.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) taskId,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        builder.setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) taskId, builder.build());
            Log.d(TAG, "Уведомление о пропущенном напоминании показано для задачи ID: " + taskId);
        } catch (SecurityException e) {
            Log.e(TAG, "Ошибка показа уведомления: нет разрешения POST_NOTIFICATIONS", e);
        }
    }

    // -----------------------
    // Direct Boot helpers
    // -----------------------
    private static Context getDeviceProtectedContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return context.createDeviceProtectedStorageContext();
            } catch (Exception e) {
                Log.w(TAG, "Не удалось создать deviceProtectedStorageContext, буду использовать обычный context", e);
            }
        }
        return context;
    }

    private static void saveReminderToDirectBoot(Context context, long id, String title, long timeMillis) {
        try {
            Context dp = getDeviceProtectedContext(context);
            SharedPreferences prefs = dp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> set = prefs.getStringSet(PREFS_KEY, new HashSet<>());
            Set<String> newSet = new HashSet<>(set);

            // strip vertical bar '|' from title to keep format consistent
            String safeTitle = title != null ? title.replace("|", " ") : "";

            // удаляем старую запись с тем же id (если есть)
            newSet.removeIf(s -> s.startsWith(id + "|"));

            String entry = id + "|" + safeTitle + "|" + timeMillis;
            newSet.add(entry);

            prefs.edit().putStringSet(PREFS_KEY, newSet).apply();
            Log.d(TAG, "Saved reminder to DirectBoot prefs: " + entry);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка сохранения в DirectBoot prefs", e);
        }
    }

    public static void removeReminderFromDirectBoot(Context context, long id) {
        try {
            Context dp = getDeviceProtectedContext(context);
            SharedPreferences prefs = dp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> set = prefs.getStringSet(PREFS_KEY, new HashSet<>());
            Set<String> newSet = new HashSet<>(set);

            boolean removed = newSet.removeIf(s -> s.startsWith(id + "|"));
            if (removed) {
                prefs.edit().putStringSet(PREFS_KEY, newSet).apply();
                Log.d(TAG, "Removed reminder id=" + id + " from DirectBoot prefs");
            } else {
                Log.d(TAG, "No DirectBoot entry found for id=" + id);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка удаления из DirectBoot prefs", e);
        }
    }
}
