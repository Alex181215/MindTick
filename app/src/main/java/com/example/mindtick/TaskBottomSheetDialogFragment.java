package com.example.mindtick;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;
import Utils.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

public class TaskBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private OnTaskUpdatedListener updateListener;
    private TextView taskTitle, taskDescription, taskDate, taskTime;
    private CardView saveButton;
    private Task task;
    private Switch switchReminder;
    private boolean isReminderEnabled = false;

    private Spinner spCategory, spPriority;


    public void setOnTaskUpdatedListener(OnTaskUpdatedListener listener) {
        this.updateListener = listener;
    }

    private int selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute;

    // Этот метод будет вызываться при создании фрагмента
    public static TaskBottomSheetDialogFragment newInstance(Task task) {
        TaskBottomSheetDialogFragment fragment = new TaskBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable("TASK", task);  // Сохраняем весь объект Task
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_bottom_sheet_dialog, container, false);

        // Достаём task сразу
        if (getArguments() != null) {
            task = (Task) getArguments().getSerializable("TASK");
        }

        taskTitle = view.findViewById(R.id.etTitle);
        taskDescription = view.findViewById(R.id.etDescription);
        taskDate = view.findViewById(R.id.btnSelectDate);
        taskTime = view.findViewById(R.id.btnSelectTime);
        saveButton = view.findViewById(R.id.saveButton);
        switchReminder = view.findViewById(R.id.switchReminder);
        spCategory = view.findViewById(R.id.spCategory);
        spPriority = view.findViewById(R.id.spPriority);

        // Настроим адаптер для категорий и приоритетов
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.category_array, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.priorities_array, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPriority.setAdapter(priorityAdapter);

        // Инициализируем данные в UI
        if (task != null) {
            taskTitle.setText(task.getTitle());
            taskDescription.setText(task.getDescription());

            // если врея в задаче пустое то подставляем сами, если нет то ставим как есть
            if(task.getTime() == null || task.getTime().isEmpty()){
                taskTime.setText("00:00");
            }else{
                taskTime.setText(task.getTime());
            }

            // Проверка, есть ли напоминание
            switchReminder.setChecked(task.getReminderEnabled() == 1);  // Устанавливаем состояние переключателя

            // Проверяем, есть ли дата в task
            if (task.getDate() == null || task.getDate().isEmpty()) {
                taskDate.setText("00.00.00");  // Если дата не задана, устанавливаем значение по умолчанию
            } else {
                taskDate.setText(task.getDate());  // Если дата задана, отображаем её
            }

            if (task.getCategory() != null) {
                int categoryPosition = categoryAdapter.getPosition(task.getCategory());
                spCategory.setSelection(categoryPosition);
            }

            if (task.getPriority() != -1) {
                String[] priorities = getResources().getStringArray(R.array.priorities_array);
                String priorityString = priorities[task.getPriority()];
                int position = priorityAdapter.getPosition(priorityString);
                spPriority.setSelection(position);
            }
        }

        // Обработчик нажатия на кнопку выбора даты
        taskDate.setOnClickListener(v -> showDatePickerDialog());

        // Обработчик нажатия на кнопку выбора времени
        taskTime.setOnClickListener(v -> showTimePickerDialog());

        // Обработчик сохранения
        saveButton.setOnClickListener(v -> saveTaskData());

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            task.setReminderEnabled(isChecked ? 1 : 0);

            // Сохраняем в базу данных
            DatabaseHandler db = new DatabaseHandler(getContext());
            SQLiteDatabase database = db.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Util.KEY_REMINDER_ENABLED, isChecked ? 1 : 0);
            database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
            database.close();

            // Устанавливаем или отменяем напоминание
            if (isChecked) {
                ReminderHelper.setAlarm(getContext(), task);
            } else {
                ReminderHelper.cancelAlarm(getContext(), task);
            }
        });


        return view;
    }


    private void showDatePickerDialog() {
        // Получаем текущую дату
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

        // Создаём DatePickerDialog
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(), (view1, year1, month1, dayOfMonth1) -> {
            // Форматируем выбранную дату
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year1, month1, dayOfMonth1);
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            String formattedDate = dateFormat.format(selectedDate.getTime());

            // Устанавливаем форматированную дату в TextView
            taskDate.setText(formattedDate);
        }, year, month, dayOfMonth);
        datePickerDialog.show();
    }

    private void showTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    taskTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute));
                },
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
        timePickerDialog.show();
    }

    private void saveTaskData() {
        if (task == null) {
            Toast.makeText(getContext(), "Ошибка: задача не найдена", Toast.LENGTH_SHORT).show();
            return;
        }

        // Получаем данные из формы
        String title = taskTitle.getText().toString().trim();
        String description = taskDescription.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();
        String date = taskDate.getText().toString();
        String time = taskTime.getText().toString();

        // Проверка на пустое название задачи
        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Введите название задачи!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Если напоминание включено, но не выбраны дата или время, выводим ошибку
        if (switchReminder.isChecked() && (date.isEmpty() || time.isEmpty())) {
            Toast.makeText(getContext(), "Выберите дату и время для напоминания!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Если категория "Не выбрано", присваиваем "Без категории"
        if (category.equals("Не выбрано")) {
            category = "Без категории";
        }

        // Если дата и время пустые — оставляем их пустыми
        if (date.isEmpty() && time.isEmpty()) {
            date = "";
            time = "";
        }
// Если только дата пустая, но время выбрано — подставляем сегодняшнюю дату
        else if (date.isEmpty() && !time.isEmpty()) {
            date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        }

// А теперь дополнительно: если в полях явно стоит "00.00.00" или "00:00" — тоже чистим
        if ("00.00.00".equals(date)) {
            date = "";
        }
        if ("00:00".equals(time)) {
            time = "";
        }

// Если только дата пустая, но время выбрано — подставляем сегодняшнюю дату
        else if (date.isEmpty() && !time.isEmpty()) {
            date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        }

        // Если и дата, и время указаны, проверяем, не прошло ли время для заданной даты
        // Новый код
        if (!date.isEmpty() && !date.equals("00.00.00") && !time.isEmpty()) {
            if (isDateTimeInPast(date, time)) {
                Toast.makeText(getContext(), "Указанное время уже прошло!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Обновляем поля задачи
        task.setTitle(title);
        task.setDescription(description);
        task.setCategory(category);
        task.setDate(date);
        task.setTime(time);
        task.setReminderEnabled(switchReminder.isChecked() ? 1 : 0);  // Используем switch напрямую
        task.setPriority(spPriority.getSelectedItemPosition());

        try {
            DatabaseHandler db = new DatabaseHandler(getContext());  // Инициализация базы данных

            if (task.getId() == 0) {  // Новая задача, добавляем
                long taskId = db.addTask(task);  // Добавляем задачу в базу
                task.setId(taskId);  // Устанавливаем ID
                Log.d("DatabaseHandler", "Задача успешно добавлена: " + task.getTitle() + ", ID: " + task.getId());
            } else {  // Задача существует, обновляем
                db.updateTask(task);
                Log.d("DatabaseHandler", "Задача обновлена: " + task.getTitle() + ", ID: " + task.getId());
            }

            // Показываем сообщение пользователю
            Toast.makeText(getContext(), "Задача сохранена!", Toast.LENGTH_SHORT).show();
            dismiss();

            if (updateListener != null) {
                Log.d("TaskUpdated", "onTaskUpdated запущен");  // Лог, чтобы понять, что метод был вызван
                updateListener.onTaskUpdated();
            }


        } catch (Exception e) {
            Log.e("SaveTaskData", "Ошибка при сохранении задачи", e);
            Toast.makeText(getContext(), "Произошла ошибка при сохранении задачи.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDateTimeInPast(String date, String time) {
        try {
            // Формируем строку для даты и времени
            String dateTimeString = date + " " + time;

            // Определяем формат, соответствующий дате и времени
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

            // Преобразуем строку в объект Date
            Date selectedDateTime = dateTimeFormat.parse(dateTimeString);

            // Получаем текущее время
            Date currentDateTime = new Date();

            // Сравниваем даты и возвращаем результат
            return selectedDateTime.before(currentDateTime);

        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }
}
