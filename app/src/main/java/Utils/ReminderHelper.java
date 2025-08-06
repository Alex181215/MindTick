package Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import Model.Task;

public class ReminderHelper {

    public static void setAlarm(Context context, Task task) {
        Log.d("StepCheck", "setAlarm вызван: " + task.getTitle() + ", дата: " + task.getDate() + ", время: " + task.getTime());

        if (task.getReminderEnabled() != 1) {
            Log.d("StepCheck", "Напоминание не включено для задачи: " + task.getTitle());
            return;
        }

        String dateStr = task.getDate();
        String timeStr = task.getTime();

        if (dateStr == null || timeStr == null || dateStr.isEmpty() || timeStr.isEmpty()) {
            Log.d("StepCheck", "Дата или время отсутствуют для задачи: " + task.getTitle());
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try {
            Date taskDate = dateFormat.parse(dateStr);
            Date taskTime = timeFormat.parse(timeStr);

            Calendar calendar = Calendar.getInstance();
            if (taskDate != null && taskTime != null) {
                calendar.setTime(taskDate);
                calendar.set(Calendar.HOUR_OF_DAY, taskTime.getHours());
                calendar.set(Calendar.MINUTE, taskTime.getMinutes());
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                // Логируем время для напоминания
                Log.d("StepCheck", "Время для напоминания в Calendar: " + calendar.getTime().toString());

                // Проверяем, что напоминание не в прошлом
                if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                    Log.d("StepCheck", "Напоминание в прошлом. Пропускаем.");
                    return;
                }

                Log.d("ReminderHelper", "Установка напоминания на: " + calendar.getTime().toString());

                Intent intent = new Intent(context, ReminderReceiver.class);
                intent.putExtra("task_id", task.getId());
                intent.putExtra("task_title", task.getTitle());

                Log.d("ReminderHelper", "ID задачи при установке напоминания: " + task.getId());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        (int) task.getId(),  // Преобразуем в int, так как PendingIntent требует int для идентификатора
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );


                // Лог перед вызовом AlarmManager
                Log.d("ReminderHelper", "Будильник будет установлен на: " + calendar.getTimeInMillis());

                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                    Log.d("ReminderHelper", "Будильник установлен (setExactAndAllowWhileIdle).");
                } catch (SecurityException e) {
                    Log.e("ReminderHelper", "Ошибка установки точного будильника — нет разрешения", e);
                }


                // Лог после вызова AlarmManager
                Log.d("ReminderHelper", "Будильник установлен.");

                // Дополнительный лог для отладки
                Log.d("ReminderHelper", "Напоминание для задачи " + task.getTitle() + " будет сработать в: " + calendar.getTime().toString());
            }

        } catch (ParseException e) {
            Log.e("ReminderHelper", "Ошибка при парсинге даты или времени: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void cancelAlarm(Context context, Task task) {
        Log.d("ReminderHelper", "Попытка отменить будильник для задачи с ID: " + task.getId());
        Intent intent = new Intent(context, ReminderReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int)  task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        Log.d("ReminderHelper", "Будильник для задачи с id " + task.getId() + " отменен.");
    }
}
