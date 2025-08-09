package Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Data.DatabaseHandler;
import Model.Task;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            Context directBootContext = context.createDeviceProtectedStorageContext();

            // Переносим prefs, если вдруг ещё не перенесли
            if (!directBootContext.isDeviceProtectedStorage()) {
                directBootContext.moveSharedPreferencesFrom(context, "reminders_prefs");
            }

            // 1️⃣ Сначала восстанавливаем быстрые напоминания из Direct Boot хранилища (работает ДО разблокировки)
            restoreFromDirectBootStorage(directBootContext);

            // 2️⃣ Затем (с задержкой) пробуем восстановить из основной БД, если телефон уже разблокирован
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    DatabaseHandler dbHelper = new DatabaseHandler(context);
                    List<Task> tasks = dbHelper.getAllTasks();

                    long now = System.currentTimeMillis();

                    for (Task task : tasks) {
                        if (task.getReminderEnabled() == 1) {
                            long reminderTime = ReminderHelper.getReminderTimeMillis(task);

                            if (reminderTime == -1) {
                                Log.d(TAG, "Некорректное время напоминания для задачи: " + task.getTitle());
                                continue;
                            }

                            if (reminderTime > now) {
                                Log.d(TAG, "Восстанавливаю напоминание: " + task.getTitle());
                                ReminderHelper.setAlarm(context, task);
                            } else {
                                Log.d(TAG, "Пропущенное напоминание: " + task.getTitle());
                                ReminderHelper.showMissedReminder(context, task.getId(), task.getTitle());
                            }
                        }
                    }

                    Log.d(TAG, "Все напоминания из БД обработаны после перезагрузки.");

                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при восстановлении из БД", e);
                }
            }, 8000);
        }
    }

    private void restoreFromDirectBootStorage(Context directBootContext) {
        try {
            SharedPreferences prefs = directBootContext.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE);
            Set<String> reminders = prefs.getStringSet("reminders", new HashSet<>());

            DatabaseHandler db = new DatabaseHandler(directBootContext);
            long now = System.currentTimeMillis();

            for (String reminder : reminders) {
                String[] parts = reminder.split("\\|");
                if (parts.length != 3) continue;

                long taskId;
                long timeMillis;
                try {
                    taskId = Long.parseLong(parts[0]);
                    timeMillis = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "DirectBoot: Некорректный формат в записи: " + reminder);
                    continue;
                }
                String title = parts[1];

                // Проверяем есть ли задача и активна ли напоминалка
                Task task = db.getTask(taskId);
                if (task == null || task.getReminderEnabled() != 1) {
                    // Удаляем устаревшую запись из prefs
                    ReminderHelper.removeReminderFromDirectBoot(directBootContext, taskId);
                    Log.d(TAG, "DirectBoot: Удалена устаревшая или неактивная запись для задачи id=" + taskId);
                    continue;
                }

                if (timeMillis > now) {
                    ReminderHelper.setAlarm(directBootContext, taskId, title, timeMillis);
                    Log.d(TAG, "DirectBoot: Восстановлено напоминание: " + title);
                } else {
                    ReminderHelper.showMissedReminder(directBootContext, taskId, title);
                    Log.d(TAG, "DirectBoot: Пропущенное напоминание: " + title);
                }
            }

            Log.d(TAG, "DirectBoot: Все напоминания обработаны.");

        } catch (Exception e) {
            Log.e(TAG, "DirectBoot: Ошибка при восстановлении", e);
        }
    }
}
