package Data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Model.Task;
import Utils.Util;

public class DatabaseHandler extends SQLiteOpenHelper {
    public DatabaseHandler(Context context) {
        super(context, Util.DATABASE_NAME, null, Util.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TASKS_TABLE = "CREATE TABLE " + Util.TABLE_NAME + "(" +
                Util.KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Util.KEY_TITLE + " TEXT," +
                Util.KEY_CATEGORY + " TEXT," +
                Util.KEY_DATE + " TEXT," +
                Util.KEY_TIME + " TEXT," +
                Util.KEY_STATUS + " INTEGER," +
                Util.KEY_DESCRIPTION + " TEXT," +
                Util.KEY_PRIORITY + " INTEGER," +
                Util.KEY_REMINDER_ENABLED + " INTEGER DEFAULT 0," +
                Util.KEY_COMPLETED_AT + " TEXT," +
                Util.KEY_PREVIOUS_REMINDER_ENABLED + " INTEGER DEFAULT 0" +
                ")";
        db.execSQL(CREATE_TASKS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminderEnabled INTEGER DEFAULT 0;");
        }
    }

    private static final int DATABASE_VERSION = 2;  // Увеличиваем версию базы данных


    public void updateTask(Task task) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", task.getTitle());
        values.put("description", task.getDescription());
       // values.put("status", task.getStatus());
        values.put("date", task.getDate());
        values.put("time", task.getTime());
       // values.put("reminderEnabled", task.getReminderEnabled());  // Здесь обновляем reminderEnabled
        values.put("priority", task.getPriority());
        values.put("category", task.getCategory());

        db.update("tasks", values, "id = ?", new String[]{String.valueOf(task.getId())});
    }


    public long addTask(Task task) {
        SQLiteDatabase db = null;
        long taskId = -1;  // Переменная для хранения ID задачи
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Util.KEY_TITLE, task.getTitle());
            values.put(Util.KEY_CATEGORY, task.getCategory());
            values.put(Util.KEY_DATE, task.getDate());
            values.put(Util.KEY_TIME, task.getTime());
            values.put(Util.KEY_STATUS, task.getStatus());
            values.put(Util.KEY_DESCRIPTION, task.getDescription());
            values.put(Util.KEY_PRIORITY, task.getPriority());
            values.put(Util.KEY_REMINDER_ENABLED, task.getReminderEnabled());
            values.put(Util.KEY_PREVIOUS_REMINDER_ENABLED, task.getReminderEnabled()); // сохраняем и как "предыдущее"

            // Вставляем задачу в таблицу и получаем ID вставленной строки
            taskId = db.insertOrThrow(Util.TABLE_NAME, null, values);

            // Логируем
            Log.d("DatabaseHandler", "Задача добавлена: " + task.getTitle());
            Log.d("DatabaseHandler", "Добавляем задачу: " + task.getTitle());
            Log.d("DatabaseHandler", "Дата при сохранении: " + task.getDate());
            Log.d("DatabaseHandler", "SQL-вставка: " + task.getTitle() + ", Напоминание: " + task.getReminderEnabled());

        } catch (Exception e) {
            Log.e("DatabaseHandler", "Ошибка при добавлении задачи: " + e.getMessage());
        } finally {
            if (db != null) db.close();
        }
        return taskId;  // Возвращаем ID вставленной задачи
    }


    public Task getTask(long taskId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null, // все поля
                Util.KEY_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndex(Util.KEY_ID)));  // Используем getLong
                task.setTitle(cursor.getString(cursor.getColumnIndex(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndex(Util.KEY_CATEGORY)));
                task.setDate(cursor.getString(cursor.getColumnIndex(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndex(Util.KEY_TIME)));
                task.setStatus(cursor.getInt(cursor.getColumnIndex(Util.KEY_STATUS)));
                task.setDescription(cursor.getString(cursor.getColumnIndex(Util.KEY_DESCRIPTION)));
                task.setPriority(cursor.getInt(cursor.getColumnIndex(Util.KEY_PRIORITY)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndex(Util.KEY_REMINDER_ENABLED)));
                task.setCompletedAt(cursor.getString(cursor.getColumnIndex(Util.KEY_COMPLETED_AT)));

                // Аккуратная проверка — если поле есть, то устанавливаем
                int prevReminderIndex = cursor.getColumnIndex(Util.KEY_PREVIOUS_REMINDER_ENABLED);
                if (prevReminderIndex != -1) {
                    task.setPreviousReminderEnabled(cursor.getInt(prevReminderIndex));
                }

                cursor.close();
                return task;
            }
            cursor.close();
        }

        return null; // Если задача не найдена
    }

    public List<Task> getAllTasks() {
        List<Task> taskList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Util.KEY_ID)));  // Используем getLong
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));
                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));
                task.setStatus(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_STATUS)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));

                // Добавляем недостающее поле
                task.setPreviousReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PREVIOUS_REMINDER_ENABLED)));

                taskList.add(task);
            } while (cursor.moveToNext());

            cursor.close();
        }

        db.close();
        return taskList;
    }
}

