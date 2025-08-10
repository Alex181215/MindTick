package Utils;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Model.Task;
import android.util.Log;


public class ReminderActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long taskId = intent.getLongExtra("task_id", -1);

        if (taskId == -1) return;

        DatabaseHandler db = new DatabaseHandler(context);
        Task task = db.getTask(taskId);
        if (task == null) return;

        switch (action) {
            case "ACTION_SNOOZE":
                long newReminderTime = System.currentTimeMillis() + 10 * 60 * 1000;

                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

                String newDate = dateFormat.format(new Date(newReminderTime));
                String newTime = timeFormat.format(new Date(newReminderTime));

                task.setDate(newDate);
                task.setTime(newTime);
                db.updateTask(task);

                ReminderHelper.setAlarm(context, task);

                Log.d("ReminderActionReceiver", "Отложка: новый будильник установлен на " + new Date(newReminderTime));

                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancel((int) taskId);
                }
                break;


            case "ACTION_DONE":
                // Помечаем задачу выполненной через TaskUtils
                TaskUtils.markTaskAsCompleted(context, task);
                
                // Удаляем уведомление
                NotificationManager nm2 = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm2 != null) {
                    nm2.cancel((int) taskId);
                }
                break;
        }
    }
}
