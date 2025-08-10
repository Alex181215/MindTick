package Utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.mindtick.MainActivity;
import com.example.mindtick.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import Data.DatabaseHandler;
import Model.Task;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootReceiver", "Запуск после перезагрузки: " + intent.getAction());

        DatabaseHandler db = new DatabaseHandler(context);
        List<Task> allTasks = db.getAllTasks();

        long now = System.currentTimeMillis();

        for (Task task : allTasks) {
            if (task.getReminderEnabled() == 1
                    && !task.getDate().isEmpty()
                    && !task.getTime().isEmpty()) {

                long taskTimeMillis = getTimeInMillis(task.getDate(), task.getTime());

                if (taskTimeMillis <= now) {
                    // ПРОСРОЧЕННОЕ НАПОМИНАНИЕ
                    Log.d("BootReceiver", "Пропущенное напоминание: " + task.getTitle());
                    showMissedNotification(context, task);
                    // ❗ Здесь НЕ удаляем и не помечаем выполненным!
                } else {
                    // Будущее напоминание — пересоздаём
                    ReminderHelper.setAlarm(context, task);
                    Log.d("BootReceiver", "Будильник пересоздан: " + task.getTitle());
                }
            }
        }

        Log.d("BootReceiver", "Обработка задач завершена");
    }

    private long getTimeInMillis(String date, String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            Date dateTime = sdf.parse(date + " " + time);
            return dateTime != null ? dateTime.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showMissedNotification(Context context, Task task) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "missed_tasks";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Пропущенные напоминания",
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.baseline_notifications_active_24w)
                .setContentTitle("Пропущенное напоминание")
                .setContentText(task.getTitle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        nm.notify((int) task.getId(), builder.build());
    }
}
