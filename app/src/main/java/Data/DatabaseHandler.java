package Data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import Model.Task;
import Utils.Util;

public class DatabaseHandler extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHandler";

    public DatabaseHandler(Context context) {
        super(context, Util.DATABASE_NAME, null, Util.DATABASE_VERSION);
        Log.d(TAG, "DatabaseHandler создан");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "onCreate вызван - создаём таблицу");
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
        Log.d(TAG, "Таблица создана");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade вызван: oldVersion = " + oldVersion + ", newVersion = " + newVersion);
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminderEnabled INTEGER DEFAULT 0;");
            Log.d(TAG, "Добавлен столбец reminderEnabled");
        }
    }

    public void updateTask(Task task) {
        Log.d(TAG, "updateTask вызван для задачи с id: " + task.getId());
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", task.getTitle());
        values.put("description", task.getDescription());
        values.put("date", task.getDate());
        values.put("time", task.getTime());
        values.put("priority", task.getPriority());
        values.put("category", task.getCategory());

        int rowsAffected = db.update("tasks", values, "id = ?", new String[]{String.valueOf(task.getId())});
        Log.d(TAG, "Обновлено строк: " + rowsAffected);
        db.close();
    }

    public long addTask(Task task) {
        SQLiteDatabase db = null;
        long taskId = -1;
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
            values.put(Util.KEY_PREVIOUS_REMINDER_ENABLED, task.getReminderEnabled());

            taskId = db.insertOrThrow(Util.TABLE_NAME, null, values);

            Log.d(TAG, "Задача добавлена: " + task.getTitle() + ", id: " + taskId);
            Log.d(TAG, "Дата при сохранении: " + task.getDate() + ", reminderEnabled: " + task.getReminderEnabled());

        } catch (Exception e) {
            Log.e(TAG, "Ошибка при добавлении задачи: " + e.getMessage(), e);
        } finally {
            if (db != null) db.close();
        }
        return taskId;
    }

    public Task getTask(long taskId) {
        Log.d(TAG, "getTask вызван для id: " + taskId);
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null,
                Util.KEY_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndex(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndex(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndex(Util.KEY_CATEGORY)));
                task.setDate(cursor.getString(cursor.getColumnIndex(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndex(Util.KEY_TIME)));
                task.setStatus(cursor.getInt(cursor.getColumnIndex(Util.KEY_STATUS)));
                task.setDescription(cursor.getString(cursor.getColumnIndex(Util.KEY_DESCRIPTION)));
                task.setPriority(cursor.getInt(cursor.getColumnIndex(Util.KEY_PRIORITY)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndex(Util.KEY_REMINDER_ENABLED)));
                task.setCompletedAt(cursor.getString(cursor.getColumnIndex(Util.KEY_COMPLETED_AT)));

                int prevReminderIndex = cursor.getColumnIndex(Util.KEY_PREVIOUS_REMINDER_ENABLED);
                if (prevReminderIndex != -1) {
                    task.setPreviousReminderEnabled(cursor.getInt(prevReminderIndex));
                }

                cursor.close();
                db.close();
                Log.d(TAG, "Задача успешно получена: " + task.getTitle());
                return task;
            }
            cursor.close();
        }

        db.close();
        Log.w(TAG, "Задача с id " + taskId + " не найдена");
        return null;
    }

    public List<Task> getAllTasks() {
        Log.d(TAG, "getAllTasks вызван");
        List<Task> taskList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null,
                Util.KEY_STATUS + " = ?",
                new String[]{"1"},
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));
                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));
                task.setStatus(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_STATUS)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));
                task.setCompletedAt(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_COMPLETED_AT)));
                task.setPreviousReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PREVIOUS_REMINDER_ENABLED)));

                taskList.add(task);
            } while (cursor.moveToNext());

            cursor.close();
        } else {
            Log.w(TAG, "getAllTasks: курсор пуст или null");
        }

        db.close();
        Log.d(TAG, "getAllTasks возвращает " + taskList.size() + " задач");
        return taskList;
    }

    public List<Task> getTasksByDate(String date) {
        Log.d(TAG, "getTasksByDate вызван для даты: " + date);
        List<Task> taskList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null,
                Util.KEY_DATE + " = ? AND " + Util.KEY_STATUS + " = ?",
                new String[]{date, "1"},
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));
                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));
                task.setStatus(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_STATUS)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));
                task.setCompletedAt(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_COMPLETED_AT)));
                task.setPreviousReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PREVIOUS_REMINDER_ENABLED)));

                taskList.add(task);
            } while (cursor.moveToNext());

            cursor.close();
        } else {
            Log.w(TAG, "getTasksByDate: курсор пуст или null для даты " + date);
        }

        db.close();
        Log.d(TAG, "getTasksByDate возвращает " + taskList.size() + " задач");
        return taskList;
    }

    public List<Task> getCompletedTasks() {
        Log.d(TAG, "getCompletedTasks вызван");
        List<Task> taskList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                Util.TABLE_NAME,
                null,
                Util.KEY_STATUS + " = ?",
                new String[]{"0"},
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));
                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));
                task.setStatus(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_STATUS)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));
                task.setCompletedAt(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_COMPLETED_AT)));
                task.setPreviousReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PREVIOUS_REMINDER_ENABLED)));

                taskList.add(task);
            } while (cursor.moveToNext());

            cursor.close();
        } else {
            Log.w(TAG, "getCompletedTasks: курсор пуст или null");
        }

        db.close();
        Log.d(TAG, "getCompletedTasks возвращает " + taskList.size() + " задач");
        return taskList;
    }
}