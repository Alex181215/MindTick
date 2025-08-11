package com.example.mindtick;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.mindtick.databinding.ActivityNewTaskBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;

public class NewTask extends AppCompatActivity {
    ActivityNewTaskBinding  b;

    private Date date;
    private String selectedDate = "", selectedTime = "";
    private boolean isReminderEnabled = false;
    private DatabaseHandler db;
    private int priority = 3;  // Значение по умолчанию - низкий приоритет
    private static final String STUB_CHECK = "Не выбрано";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityNewTaskBinding.inflate(getLayoutInflater());
        View v = b.getRoot();
        setContentView(v);

        initClicker();
    }

    private void initClicker(){
        // Выбор даты
        b.btnSelectDate.setOnClickListener(view -> showDatePicker());

        // Выбор времени
        b.btnSelectTime.setOnClickListener(view -> showTimePicker());

        // Обработчик переключателя напоминания
        b.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReminderEnabled = isChecked;
        });

        // Обработчик нажатия на кнопку "Создать"
        b.btnCreate.setOnClickListener(view -> saveTask());

        b.spPriority.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    priority = 3; // Если выбрано "Не выбрано" — ставим низкий
                } else {
                    priority = position; // 1 - высокий, 2 - средний, 3 - низкий
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                priority = 3; // Если ничего не выбрали, остаётся низкий
            }
        });

        b.etDescription.setMaxLines(2);  // Ограничивает количество строк до 2

        // В методе onCreate или onViewCreated
        setFocusListener(b.rootLayout);

        b.getRoot().setOnClickListener(view -> {
            b.etTitle.clearFocus();
            b.etDescription.clearFocus();
            hideKeyboard(view);
        });

        // Инициализируем базу данных
        db = new DatabaseHandler(this);

        String[] categories = getResources().getStringArray(R.array.category_array);
        int textColorCategories = ContextCompat.getColor(this, R.color.all_text);
        CustomSpinnerAdapter adapterCategories = new CustomSpinnerAdapter(this, android.R.layout.simple_spinner_item, categories, textColorCategories);
        adapterCategories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spCategory.setAdapter(adapterCategories);

        String[] priorities = getResources().getStringArray(R.array.priorities_array);
        int textColorPriorities = ContextCompat.getColor(this, R.color.all_text);
        CustomSpinnerAdapter adapterPriorities = new CustomSpinnerAdapter(this, android.R.layout.simple_spinner_item, priorities, textColorPriorities);
        adapterPriorities.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spPriority.setAdapter(adapterPriorities);
    }

    private void saveTask() {
        String title = b.etTitle.getText().toString().trim();
        String category = b.spCategory.getSelectedItem().toString();
        String description = b.etDescription.getText().toString().trim(); // Добавляем описание

        // Проверка на название
        if (title.isEmpty()) {
            Toast.makeText(this, "Введите название задачи!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Если напоминание включено, но не выбраны дата или время, выводим ошибку
        if (isReminderEnabled && (selectedDate.isEmpty() || selectedTime.isEmpty())) {
            Toast.makeText(this, "Выберите дату и время для напоминания!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Если категория "Не выбрано", присваиваем "Без категории"
        if (category.equals(STUB_CHECK)) {
            category = "Без категории";
        }

        // Если пользователь указал только время, подставляем дату
        if (selectedDate.isEmpty() && (selectedTime != null && !selectedTime.isEmpty())) {
            try {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date taskTime = timeFormat.parse(selectedTime);
                Calendar now = Calendar.getInstance();
                Calendar taskCalendar = Calendar.getInstance();
                taskCalendar.set(Calendar.HOUR_OF_DAY, taskTime.getHours());
                taskCalendar.set(Calendar.MINUTE, taskTime.getMinutes());
                taskCalendar.set(Calendar.SECOND, 0);
                taskCalendar.set(Calendar.MILLISECOND, 0);

                if (taskCalendar.after(now)) {
                    selectedDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now.getTime());
                } else {
                    now.add(Calendar.DAY_OF_MONTH, 1);
                    selectedDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now.getTime());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Если и дата, и время указаны, проверяем, не прошло ли время для заданной даты
        if (!selectedDate.isEmpty() && !selectedTime.isEmpty()) {
            if (isDateTimeInPast(selectedDate, selectedTime)) {
                Toast.makeText(this, "Указанное время уже прошло!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Логируем параметры перед сохранением
        Log.d("NewTask_saveTask", "Задача: " + title + ", Категория: " + category + ", Описание: " + description);
        Log.d("NewTask_saveTask", "Дата: " + selectedDate + ", Время: " + selectedTime + ", Напоминание включено: " + isReminderEnabled);

        // Логируем текущую дату и время
        Log.d("DateTimeCheck", "Текущее время системы: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date()));
        Log.d("TimeCheck", "selectedTime перед сохранением: " + selectedTime);

        // Прежде чем сохранить дату и время, проверяем на пустоту или ненадежные значения
        if ("00.00.00".equals(selectedDate)) {
            selectedDate = null;  // Или ""
        }

        // Если время равно "00:00", заменяем на null
        if ("00:00".equals(selectedTime)) {
            selectedTime = null;  // Или можно использовать "" (пустую строку)
        }

        // Создаём объект задачи
        Task task = new Task();
        task.setTitle(title);
        task.setCategory(category);
        task.setDate(selectedDate.isEmpty() ? "" : selectedDate);
        task.setTime(selectedTime);  // Теперь время может быть null или пустым
        task.setStatus(1);
        task.setDescription(description);
        task.setPriority(priority);  // Устанавливаем приоритет перед сохранением
        task.setReminderEnabled(isReminderEnabled ? 1 : 0);  // Это правильное присваивание состояния напоминания

        // Логируем данные
        Log.d("CreateTask", "Date: " + task.getDate());
        Log.d("CreateTask", "Time: " + task.getTime());

        // Логируем перед добавлением в базу
        Log.d("DatabaseHandler", "Сохранение задачи в базу: " + title + ", Категория: " + category + ", Статус: " + task.getStatus() + ", Приоритет: " + task.getPriority());

        // Добавляем задачу в базу данных
        long taskId = db.addTask(task);  // Получаем уникальный ID задачи
        task.setId(taskId);  // Устанавливаем ID в объект задачи

        // Логируем успешное сохранение
        Log.d("DatabaseHandler", "Задача успешно сохранена: " + task.getTitle() + ", ID: " + task.getId());

        Log.d("StepCheck", "Сохраняется задача: " + task.getTitle() + ", дата: " + task.getDate() + ", время: " + task.getTime());

        // Логируем время, которое будет установлено для напоминания
        Log.d("DateTimeCheck", "Дата и время для напоминания: " + task.getDate() + " " + task.getTime());

        // Ставим напоминание, если включено
        ReminderHelper.setAlarm(this, task);

        // Логируем установку напоминания
        Log.d("StepCheck", "Напоминание установлено для задачи: " + task.getTitle());

        // Показываем сообщение и закрываем активити
        Toast.makeText(this, "Задача создана!", Toast.LENGTH_SHORT).show();

        // Проверяем, задача на сегодня или нет
        boolean isTaskForToday = checkIfTaskIsForToday(selectedDate);

        // Создаём Intent для перехода в MainActivity
        Intent intent = new Intent(NewTask.this, MainActivity.class);
        // Передаём ID задачи как long
        intent.putExtra("task_id", task.getId());  // Здесь передаем ID задачи
        intent.putExtra("isTaskForToday", isTaskForToday);

        // Запускаем MainActivity
        startActivity(intent);

        // очищаем все
        clearFields();
        // закрываем текущую активность
        finish();
    }




    private boolean isDateTimeInPast(String dateStr, String timeStr) {
        try {
            // Предположим, что дата в формате "dd.MM.yyyy" и время в формате "HH:mm"
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            Date taskDateTime = sdf.parse(dateStr + " " + timeStr);
            // Текущее время
            Date now = new Date();
            // Если время задачи меньше текущего – оно в прошлом
            return taskDateTime.before(now);
        } catch (ParseException e) {
            e.printStackTrace();
            return false; // Если не удалось распарсить, можно считать, что проверка не пройдена
        }
    }


    private boolean checkIfTaskIsForToday(String taskDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        String todayDate = dateFormat.format(Calendar.getInstance().getTime());
        return todayDate.equals(taskDate);
    }


    // Метод для выбора даты
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            // Создаём календарь с выбранной датой
            Calendar selectedCalendar = Calendar.getInstance();
            selectedCalendar.set(year1, month1, dayOfMonth);

            // Форматируем дату в нужный формат
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            selectedDate = dateFormat.format(selectedCalendar.getTime());

            // Устанавливаем дату в кнопку
            b.btnSelectDate.setText(selectedDate);
        }, year, month, day).show();
    }



    // Метод для выбора времени
    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            selectedTime = hourOfDay + ":" + (minute1 < 10 ? "0" + minute1 : minute1);
            b.btnSelectTime.setText(selectedTime);
        }, hour, minute, true).show();
    }

    private void clearFields() {
        b.etTitle.setText("");
        b.spCategory.setSelection(0);
        b.btnSelectDate.setText("00.00.00г.");
        b.btnSelectTime.setText("00:00");
        selectedDate = "";
        selectedTime = "";
    }

    public void setFocusListener(View view) {
        view.setOnTouchListener((v, event) -> {
            // Если фокус на EditText
            if (getCurrentFocus() != null && getCurrentFocus() instanceof EditText) {
                EditText et = (EditText) getCurrentFocus();
                // Скрываем клавиатуру
                hideKeyboard(et);
                // Убираем фокус с EditText
                et.clearFocus();
            }
            // Для других элементов, скрываем клавиатуру
            if (getCurrentFocus() != null && !(getCurrentFocus() instanceof EditText)) {
                hideKeyboard(getCurrentFocus());
            }
            return false;
        });
    }


    // скрыть клавиатуру
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public class CustomSpinnerAdapter extends ArrayAdapter<String> {

        private Context context;
        private String[] items;
        private int textColor;

        public CustomSpinnerAdapter(Context context, int resource, String[] objects, int textColor) {
            super(context, resource, objects);
            this.context = context;
            this.items = objects;
            this.textColor = textColor;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            TextView textView = (TextView) view.findViewById(android.R.id.text1);
            textView.setTextColor(textColor); // Устанавливаем цвет текста
            return view;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView textView = (TextView) view.findViewById(android.R.id.text1);
            textView.setTextColor(textColor); // Устанавливаем цвет текста
            return view;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
    }
}