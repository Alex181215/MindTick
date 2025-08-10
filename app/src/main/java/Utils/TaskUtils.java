package Utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Model.Task;

public class TaskUtils {

    private static final String TAG = "TaskUtils";

    public static void markTaskAsCompleted(Context context, Task task) {
        if (task == null || context == null) {
            Log.e(TAG, "markTaskAsCompleted: task or context is null");
            return;
        }

        SQLiteDatabase database = null;
        try {
            database = new DatabaseHandler(context).getWritableDatabase();

            ContentValues values = new ContentValues();

            String completedAt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());

            values.put(Util.KEY_STATUS, 0); // 0 = выполнена
            values.put(Util.KEY_COMPLETED_AT, completedAt);
            values.put(Util.KEY_PREVIOUS_REMINDER_ENABLED, task.getReminderEnabled());
            values.put(Util.KEY_REMINDER_ENABLED, 0); // отключаем напоминание

            int updatedRows = database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});

            Log.d(TAG, "markTaskAsCompleted: updated rows = " + updatedRows);

            // Отменяем будильник
            ReminderHelper.cancelAlarm(context, task);

        } catch (Exception e) {
            Log.e(TAG, "markTaskAsCompleted: error updating task", e);
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
        }
    }
}
