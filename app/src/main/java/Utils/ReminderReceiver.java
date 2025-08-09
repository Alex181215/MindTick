package Utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.mindtick.MainActivity;
import com.example.mindtick.R;

import Data.DatabaseHandler;
import Model.Task;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";  // Для логирования

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive вызван с intent: " + intent);

        // Извлекаем ID задачи как long
        long taskId = intent.getLongExtra("task_id", -1);  // Используем getLongExtra для получения long значения
        String taskTitle = intent.getStringExtra("task_title");

        // Логируем получение данных
        Log.d(TAG, "Получены данные: task_id = " + taskId + ", task_title = " + taskTitle);

        if (taskId == -1) {
            Log.w(TAG, "Получен некорректный task_id: -1, отмена обработки.");
            return;
        }

        // Получаем задачу из базы данных
        DatabaseHandler db = new DatabaseHandler(context);
        Task task = db.getTask(taskId);  // Получаем задачу по ID
        if (task == null) {
            Log.w(TAG, "Задача с id " + taskId + " не найдена в базе.");
            return;
        }

        Log.d(TAG, "Задача получена из базы: " + task.getTitle());

        // Создаем намерение для открытия приложения при нажатии на уведомление
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) taskId,  // Преобразуем taskId в int для PendingIntent
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Создаем уведомление с использованием NotificationCompat
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "reminder_channel")
                .setSmallIcon(R.drawable.baseline_notifications_active_24w)  // Иконка уведомления
                .setContentTitle("Напоминание: " + taskTitle)  // Заголовок уведомления (например, название задачи)
                .setContentText("Не забудьте выполнить задачу!")  // Текст уведомления
                .setAutoCancel(true)  // Уведомление автоматически исчезает после нажатия
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // Высокий приоритет — для экстренных уведомлений
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Показывает уведомление на экране блокировки
                .setCategory(NotificationCompat.CATEGORY_ALARM)  // Уведомление категории "будильник"
                .setContentIntent(pendingIntent)  // PendingIntent, чтобы открыть активность при нажатии
                .setFullScreenIntent(pendingIntent, true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);  // Включает звук, вибрацию и свет для уведомления

        // Получаем NotificationManager для управления уведомлениями
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Проверяем, если версия Android 8.0 (O) или выше — необходимо создать канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Создание/проверка notification channel для Android O и выше");

            // Создаем канал для уведомлений с высоким приоритетом
            NotificationChannel channel = new NotificationChannel(
                    "reminder_channel",  // Идентификатор канала
                    "Напоминания",  // Название канала, которое будет отображаться пользователю
                    NotificationManager.IMPORTANCE_HIGH  // Важность канала — уведомления с этим каналом будут отображаться с высоким приоритетом
            );

            // Описание канала, которое можно увидеть в настройках
            channel.setDescription("Канал для напоминаний");

            // Включаем вибрацию и возможность отображать свет
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);  // Устанавливаем цвет света (например, красный)

            // Устанавливаем видимость канала для экрана блокировки
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            // Устанавливаем звук уведомлений с использованием системного звука будильника
            channel.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)  // Используем звук как будильник
                            .build());

            // Регистрируем канал в системе
            notificationManager.createNotificationChannel(channel);

            Log.d(TAG, "Notification channel 'reminder_channel' создан или обновлен");
        }

        // Логируем перед отправкой уведомления
        Log.d(TAG, "Отправка уведомления для задачи: " + task.getTitle());

        // Отправляем уведомление
        if (notificationManager != null) {
            notificationManager.notify((int) taskId, builder.build());  // Используем (int) taskId для идентификатора уведомления
            Log.d(TAG, "Уведомление успешно отправлено для задачи с id: " + taskId);

            // Удаляем запись из DirectBoot prefs после успешного уведомления
            ReminderHelper.removeReminderFromDirectBoot(context, taskId);
            Log.d(TAG, "Удалил запись из DirectBoot после срабатывания для задачи id=" + taskId);
        } else {
            Log.e(TAG, "Ошибка: NotificationManager не доступен, уведомление не отправлено");
        }
    }
}
