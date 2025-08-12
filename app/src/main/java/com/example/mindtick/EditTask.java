package com.example.mindtick;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mindtick.databinding.ActivityEditTaskBinding;
import com.example.mindtick.databinding.ActivityNewTaskBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;
import Utils.Util;

public class EditTask extends AppCompatActivity {
    private boolean restoreMode = false;

    private static final String TAG = "EditTask";
    public static final int RESULT_UPDATED = RESULT_OK;
    public static final int REQUEST_EDIT_TASK = 1001;

    private ActivityEditTaskBinding b;
    private DatabaseHandler db;
    private Task task;
    private long taskId = -1;
    private int selectedHour = -1, selectedMinute = -1;
    private boolean isReminderEnabled = false;
    private String returnFragment = null; // опционально: откуда пришли

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditTaskBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = new DatabaseHandler(this);

        // Получаем task_id (long) и optional returnFragment
        Intent intent = getIntent();
        taskId = intent.getLongExtra("task_id", -1);
        returnFragment = intent.getStringExtra("returnFragment");
        restoreMode = getIntent().getBooleanExtra("restoreMode", false);

        initSpinners();
        setupClickers();

        if (taskId != -1) {
            loadTask(taskId);
        } else {
            // пустая форма (если захотим использовать EditTask для создания)
            b.btnSelectDate.setText("00.00.00г.");
            b.btnSelectTime.setText("00:00");
        }

        if (restoreMode) {
            b.tvTitle.setText("Восстановление задачи");
            b.btnSaveText.setText("Восстановить");
        }
    }

    private void initSpinners() {
        // Категории
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this, R.array.category_array, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spCategory.setAdapter(categoryAdapter);

        // Приоритеты
        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(
                this, R.array.priorities_array, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spPriority.setAdapter(priorityAdapter);
    }

    private void setupClickers() {
        b.btnSelectDate.setOnClickListener(v -> showDatePicker());
        b.btnSelectTime.setOnClickListener(v -> showTimePicker());

        b.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReminderEnabled = isChecked;
            // если включили, но нет даты/времени — покажем диалог (или можно сразу открыть редактирование)
            if (isChecked) {
                String date = b.btnSelectDate.getText().toString();
                String time = b.btnSelectTime.getText().toString();
                if (date == null || date.isEmpty() || "00.00.00".equals(date) || time == null || time.isEmpty() || "00:00".equals(time)) {
                    Toast.makeText(this, "Выберите дату и время для напоминания", Toast.LENGTH_SHORT).show();
                    b.switchReminder.setChecked(false);
                    isReminderEnabled = false;
                }
            }
        });

        b.btnSave.setOnClickListener(v -> saveTask());
    }

    private void loadTask(long id) {
        task = db.getTask(id);
        if (task == null) {
            Toast.makeText(this, "Задача не найдена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Заполняем UI
        b.etTitle.setText(task.getTitle());
        b.etDescription.setText(task.getDescription() == null ? "" : task.getDescription());

        String date = task.getDate();
        String time = task.getTime();

        if (date == null || date.isEmpty()) b.btnSelectDate.setText("00.00.00г.");
        else b.btnSelectDate.setText(date);

        if (time == null || time.isEmpty()) b.btnSelectTime.setText("00:00");
        else b.btnSelectTime.setText(time);

        b.switchReminder.setChecked(task.getReminderEnabled() == 1);
        isReminderEnabled = task.getReminderEnabled() == 1;

        // category selection
        if (task.getCategory() != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) b.spCategory.getAdapter();
            int pos = adapter.getPosition(task.getCategory());
            if (pos >= 0) b.spCategory.setSelection(pos);
        }

        // priority selection — соответствие с логикой NewTask:
        // в NewTask: position==0 => priority = 3, else priority = position
        int storedPriority = task.getPriority();
        if (storedPriority == 3) {
            b.spPriority.setSelection(0);
        } else {
            // если приоритет был 1 или 2 — устанавливаем соответствующую позицию (если есть)
            if (storedPriority >= 0 && storedPriority < b.spPriority.getAdapter().getCount()) {
                b.spPriority.setSelection(storedPriority);
            }
        }
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(y, m, d);
            String formatted = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(sel.getTime());
            b.btnSelectDate.setText(formatted);
        }, year, month, day).show();
    }

    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedHour = hourOfDay;
            selectedMinute = minute;
            b.btnSelectTime.setText(String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private boolean isDateTimeInPast(String dateStr, String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            Date d = sdf.parse(dateStr + " " + timeStr);
            return d.before(new Date());
        } catch (ParseException e) {
            Log.e(TAG, "isDateTimeInPast parse error", e);
            return false;
        }
    }

    private void saveTask() {
        String title = b.etTitle.getText().toString().trim();
        String description = b.etDescription.getText().toString().trim();
        String category = b.spCategory.getSelectedItem() != null ? b.spCategory.getSelectedItem().toString() : "Без категории";
        String date = b.btnSelectDate.getText().toString();
        String time = b.btnSelectTime.getText().toString();

        if (title.isEmpty()) {
            Toast.makeText(this, "Введите название задачи!", Toast.LENGTH_SHORT).show();
            return;
        }

        // normalize placeholders
        if ("00.00.00г.".equals(date) || "00.00.00".equals(date)) date = "";
        if ("00:00".equals(time)) time = "";

        // Особая логика для режима восстановления
        if (restoreMode) {
            // Восстановление требует дату и время в будущем
            if (date.isEmpty() || time.isEmpty()) {
                Toast.makeText(this, "Выберите дату и время для восстановления задачи", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isDateTimeInPast(date, time)) {
                Toast.makeText(this, "Дата и время должны быть в будущем", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // If reminder is on, require date/time
            if (b.switchReminder.isChecked() && (date.isEmpty() || time.isEmpty())) {
                Toast.makeText(this, "Выберите дату и время для напоминания!", Toast.LENGTH_SHORT).show();
                return;
            } }

        // If both provided, check not in past
        if (!date.isEmpty() && !time.isEmpty()) {
            if (isDateTimeInPast(date, time)) {
                Toast.makeText(this, "Указанное время уже прошло!", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (date.isEmpty() && !time.isEmpty()) {
            // if time provided only -> set date to today (как в NewTask)
            date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        }

        // priority mapping: NewTask uses pos==0 -> priority=3 else priority=pos
        int spinnerPos = b.spPriority.getSelectedItemPosition();
        int priority = (spinnerPos == 0) ? 3 : spinnerPos;

        // если task == null => создаём новую задачу (не обязательно, но на всякий случай)
        if (task == null) {
            task = new Task();
        }

        task.setTitle(title);
        task.setDescription(description);
        task.setCategory("Не выбрано".equals(category) ? "Без категории" : category);
        task.setDate(date);
        task.setTime(time);
        task.setPriority(priority);
        task.setReminderEnabled(b.switchReminder.isChecked() ? 1 : 0);
        // Статус
        if (restoreMode) {
            task.setStatus(1); // При восстановлении делаем задачу активной
        } else {
            task.setStatus(1); // Обычное сохранение — тоже активная задача, можно поменять если надо
        }

        try {
            // обновим запись в БД (или добавим, если новая)
            if (task.getId() == 0) {
                long newId = db.addTask(task);
                task.setId(newId);
                Log.d(TAG, "Добавлена новая задача id=" + newId);
            } else {
                // используем прямой update, чтобы точно сохранить все поля (включая reminderEnabled)
                SQLiteDatabase database = db.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(Util.KEY_TITLE, task.getTitle());
                values.put(Util.KEY_DESCRIPTION, task.getDescription());
                values.put(Util.KEY_CATEGORY, task.getCategory());
                values.put(Util.KEY_DATE, task.getDate());
                values.put(Util.KEY_TIME, task.getTime());
                values.put(Util.KEY_PRIORITY, task.getPriority());
                values.put(Util.KEY_STATUS, task.getStatus());
                values.put(Util.KEY_REMINDER_ENABLED, task.getReminderEnabled());
                // можно обновить KEY_PREVIOUS_REMINDER_ENABLED при логике, если нужно
                int rows = database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
                database.close();
                Log.d(TAG, "Обновлено строк: " + rows + " для id=" + task.getId());
            }

            // Обновляем / отменяем будильник
            ReminderHelper.cancelAlarm(this, task); // отменяем старое
            if (task.getReminderEnabled() == 1 && !task.getDate().isEmpty() && !task.getTime().isEmpty()) {
                // только если время в будущем
                SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                Date dt = df.parse(task.getDate() + " " + task.getTime());
                if (dt != null && dt.after(new Date())) {
                    ReminderHelper.setAlarm(this, task);
                }
            }

            // вернём результат вызывающему
            Intent result = new Intent();
            result.putExtra("task_id", task.getId());
            if (returnFragment != null) result.putExtra("returnFragment", returnFragment);
            setResult(RESULT_OK, result);

            if (restoreMode) {
                Toast.makeText(this, "Задача восстановлена!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Задача сохранена!", Toast.LENGTH_SHORT).show();
            }
            finish();
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);

        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении задачи", e);
            Toast.makeText(this, "Ошибка сохранения задачи", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
    }
}
