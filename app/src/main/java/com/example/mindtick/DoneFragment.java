package com.example.mindtick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import Adapter.TaskAdapter;
import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;
import Utils.Util;

public class DoneFragment extends Fragment implements OnTaskUpdatedListener{
    private RecyclerView recyclerView;
    private List<Object> itemList = new ArrayList<>();
    private DatabaseHandler db;
    private TextView emptyTextView;
    private TaskAdapter adapter;

    private static final String[] COMPLETED_TASK_MESSAGES = {
            "Все задачи выполнены! Отличная работа! 🎉",
            "Пусто... Потому что ты уже все сделал! 🔥",
            "Задач нет, но ведь ты не остановишься на этом? 😉",
            "Ничего не осталось – ты просто машина продуктивности! 🤖",
            "Ты победил все задачи! Время для новых свершений! 🏆",
            "Вот это продуктивность! Можно и передохнуть. 😌",
            "Все под контролем! Ты настоящий мастер планирования. 🧠",
            "Ты разобрался со всем. Теперь можно насладиться моментом! 🌿",
            "Не осталось ни одной задачи... Так что же ты будешь делать дальше? 🤔",
            "Ты сделал это! Теперь можно позволить себе награду. 🍩",
            "Успешно выполнено! Время поставить новые цели. 🎯",
            "Твой список дел пуст, а энергия на максимуме! 💡",
            "Отличная работа! Теперь можно переключиться на что-то приятное. 🎮",
            "Ты закрыл все вопросы! Время немного расслабиться. 🏖️",
            "Чистый список – как чистый разум. Наслаждайся моментом! 🌿",
            "Ты прокачал свою продуктивность до 100%! 🚀",
            "Всё сделано! Может, пора добавить что-то новое? 📝",
            "Нет задач – нет стресса! Но ты ведь не привык сидеть без дела? 😉",
            "Ты в зоне победителя! Готов к новым свершениям? 💪",
            "Задач нет, но уверены ли вы, что ничего не забыли? 😉",
            "Может, пора выпить чаю и насладиться моментом? 🍵",
            "Ты разобрался со всеми делами. Чем займешься дальше? 🚀"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_done, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        emptyTextView = view.findViewById(R.id.emptyTextView);

        db = new DatabaseHandler(getContext());
        adapter = new TaskAdapter(getContext(), itemList, false, db, true, false, this); // для Выполненные
        adapter.setOnTaskUpdatedListener(() -> {
            Log.d("TodayFragment", "Обновляем задачи после изменения");
            loadItems(); // твой метод, фильтрующий задачи по дате
            adapter.notifyDataSetChanged();
        });
        loadItems();
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Object item = itemList.get(position);

                if (item instanceof Task) {
                    Task task = (Task) item;

                    if (direction == ItemTouchHelper.RIGHT) {
                        showConfirmationDialog(
                                "Восстановить задачу \"" + task.getTitle() + "\" как активную?",
                                () -> {
                                    Intent intent = new Intent(getActivity(), EditTask.class);
                                    intent.putExtra("task_id", task.getId());
                                    intent.putExtra("returnFragment", "DoneFragment");
                                    intent.putExtra("restoreMode", true);
                                    ((Activity) getActivity()).startActivityForResult(intent, EditTask.REQUEST_EDIT_TASK);
                                    getActivity().overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
                                },
                                () -> {
                                    recyclerView.getAdapter().notifyItemChanged(position);
                                }
                        );

                    } else if (direction == ItemTouchHelper.LEFT) {
                        showConfirmationDialog(
                                "Удалить задачу \"" + task.getTitle() + "\"?",
                                () -> {
                                    deleteTask(task, position);
                                },
                                () -> {
                                    recyclerView.getAdapter().notifyItemChanged(position);
                                }
                        );
                    }
                } else {
                    recyclerView.getAdapter().notifyItemChanged(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    float maxSwipe = viewHolder.itemView.getWidth() / 2f; // максимум 50% ширины
                    float newDX = Math.min(Math.max(dX, -maxSwipe), maxSwipe);

                    // Если свайп активен (рука на экране) — двигаем карточку
                    if (isCurrentlyActive) {
                        super.onChildDraw(c, recyclerView, viewHolder, newDX, dY, actionState, true);
                    } else {
                        // Возврат карточки после отмены свайпа
                        viewHolder.itemView.animate()
                                .translationX(0)
                                .setInterpolator(new OvershootInterpolator(2f)) // "пружина"
                                .setDuration(400)
                                .start();
                    }
                } else {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }
            }

        };

// Привязываем свайп к RecyclerView
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        return view;
    }

    private void showConfirmationDialog(String message, Runnable onConfirm, Runnable onCancel) {
        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.CustomDialog)
                .setMessage(message)
                .setPositiveButton("Да", (dialogInterface, which) -> onConfirm.run())
                .setNegativeButton("Отмена", (dialogInterface, which) -> onCancel.run())
                .setCancelable(false)
                .create(); // Создаем диалог, но пока не показываем

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background); // Устанавливаем закругленный фон
        dialog.show(); // Показываем диалог

        // Получаем цвета из ресурсов
        int positiveColor = ContextCompat.getColor(getContext(), R.color.all_text);
        int negativeColor = ContextCompat.getColor(getContext(), R.color.all_text);
        int messageColor = ContextCompat.getColor(getContext(), R.color.title_text);

        // После показа диалога меняем цвет кнопок
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(positiveColor);  // "Да"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(negativeColor); // "Отмена"

        // **Меняем цвет текста сообщения**
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(messageColor);
        }
    }

    private void markTaskAsCompleted(Task task, int position) {
        SQLiteDatabase database = db.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Восстанавливаем задачу как активную
        values.put(Util.KEY_STATUS, 1); // 1 = активная
        values.put(Util.KEY_COMPLETED_AT, " "); // Убираем дату выполнения

        Log.d("RestoreDebug", "prevReminder: " + task.getPreviousReminderEnabled());
        Log.d("RestoreDebug", "Restoring Task ID: " + task.getId());
        Log.d("RestoreDebug", "Restored Task Date: " + task.getDate());
        Log.d("RestoreDebug", "Restored Task Time: " + task.getTime());

        // Восстанавливаем напоминание, если оно было включено
        boolean reminderEnabled = false;

        if (task.getPreviousReminderEnabled() == 1) {
            String date = task.getDate();
            String time = task.getTime();
            if (!date.isEmpty() && !time.isEmpty()) {
                String dateTimeString = date + " " + time;
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                    Date dateTime = dateFormat.parse(dateTimeString);

                    Log.d("RestoreDebug", "Parsed DateTime: " + dateTime);

                    // Добавляем лог для проверки времени
                    Log.d("RestoreDebug", "Current time: " + new Date());

                    // Увеличиваем погрешность до 1 минуты
                    long diffInMilliseconds = dateTime.getTime() - new Date().getTime();
                    Log.d("RestoreDebug", "Time difference (ms): " + diffInMilliseconds);

                    if (dateTime != null && diffInMilliseconds > -60000) { // 1 минута допуска
                        reminderEnabled = true;
                    } else {
                        Log.d("RestoreDebug", "Task is too old or not yet due: " + task.getTitle());
                    }
                } catch (Exception e) {
                    Log.e("RestoreDebug", "Error parsing date/time", e);
                }
            }
        }

// Логируем значение напоминания перед обновлением
        Log.d("RestoreDebug", "ReminderEnabled before update: " + reminderEnabled);

// Обновляем статус напоминания в объекте и базе данных
        task.setReminderEnabled(reminderEnabled ? 1 : 0);
        values.put(Util.KEY_REMINDER_ENABLED, reminderEnabled ? 1 : 0);

// Обновляем в базе данных
        database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});

// Логируем значение напоминания после обновления
        Log.d("RestoreDebug", "ReminderEnabled after update: " + task.getReminderEnabled());

// Удаляем задачу из списка выполненных
        itemList.remove(position);
        recyclerView.getAdapter().notifyItemRemoved(position);

        checkAndRemoveEmptyCategory(task);
        checkTasks();


        // Логируем, что напоминание должно быть установлено
        Log.d("ReminderCheck", "ReminderEnabled: " + reminderEnabled);

        // Если напоминание должно быть установлено, запускаем его
        if (reminderEnabled && !task.getDate().isEmpty() && !task.getTime().isEmpty()) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                Date dateTime = dateFormat.parse(task.getDate() + " " + task.getTime());

                Log.d("ReminderCheck", "Parsed Date: " + dateTime);

                if (dateTime != null && dateTime.after(new Date())) {
                    Log.d("ReminderCheck", "Setting reminder for task: " + task.getTitle());
                    ReminderHelper.setAlarm(getContext(), task);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void deleteTask(Task task, int position) {
        if (getContext() != null) {
            ReminderHelper.cancelAlarm(getContext(), task);
        } else {
            Log.e("TaskAdapter", "Context is null. Cannot cancel alarm.");
        }

        SQLiteDatabase database = db.getWritableDatabase();
        database.delete(Util.TABLE_NAME, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
        database.close();

        // Удаляем из списка
        itemList.remove(position);
        recyclerView.getAdapter().notifyItemRemoved(position);

        // Проверяем и удаляем пустую категорию
        checkAndRemoveEmptyCategory(task);

        // Проверяем, пуст ли экран
        checkTasks();
    }

    // Метод для проверки и удаления пустой категории
    private void checkAndRemoveEmptyCategory(Task task) {
        String category = task.getCategory(); // Получаем категорию задачи
        if (category == null || category.isEmpty()) return; // Если категории нет — выходим

        // Проверяем, остались ли задачи с этой категорией
        boolean isCategoryEmpty = true;
        for (Object obj : itemList) {
            if (obj instanceof Task) {
                Task currentTask = (Task) obj;
                if (category.equals(currentTask.getCategory())) {
                    isCategoryEmpty = false;
                    break;
                }
            }
        }

        // Если в категории больше нет задач — удаляем её из списка
        if (isCategoryEmpty) {
            itemList.removeIf(obj -> obj instanceof String && obj.equals(category));

            // Обновляем адаптер
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private void loadItems() {
        itemList.clear();
        LinkedHashMap<String, List<Task>> categoryMap = new LinkedHashMap<>();
        SQLiteDatabase database = db.getReadableDatabase();

        // Выбираем только неактивные задачи
        Cursor cursor = database.query(Util.TABLE_NAME, null, Util.KEY_STATUS + " = ?",
                new String[]{"0"}, null, null, Util.KEY_CATEGORY);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY));
                if (category == null || category.isEmpty()) {
                    category = "Без категории";
                }
                task.setCategory(category);

                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));

                // Дата выполнения (всегда есть)
                task.setCompletedAt(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_COMPLETED_AT)));

                // Сохраняем предыдущие настройки напоминаний
                task.setPreviousReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PREVIOUS_REMINDER_ENABLED)));

                // Запланированная дата (может быть нужна)
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));

                // Время (если было)
                String time = cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME));
                task.setTime(time);

                // Лог для проверки
                Log.d("LoadDebug", "Loaded Task: " + task.getTitle() + " completedAt: " + task.getCompletedAt());

                // Группируем задачи по категориям
                if (!categoryMap.containsKey(category)) {
                    categoryMap.put(category, new ArrayList<>());
                }
                categoryMap.get(category).add(task);
            } while (cursor.moveToNext());
            cursor.close();
        }
        database.close();

        // Сортируем категории: "Без категории" первой, остальные по алфавиту
        List<String> sortedCategories = new ArrayList<>(categoryMap.keySet());
        Collections.sort(sortedCategories, String.CASE_INSENSITIVE_ORDER);
        final String noCategory = "Без категории";
        if (sortedCategories.contains(noCategory)) {
            sortedCategories.remove(noCategory);
            sortedCategories.add(0, noCategory);
        }

        // Перебираем отсортированные категории
        for (String category : sortedCategories) {
            List<Task> tasks = categoryMap.get(category);

            // Сортируем задачи внутри категории по completedAt — новые сверху
            tasks.sort((t1, t2) -> {
                if (t1.getCompletedAt() == null) return 1;
                if (t2.getCompletedAt() == null) return -1;
                return t2.getCompletedAt().compareTo(t1.getCompletedAt());
            });

            itemList.add(category); // Заголовок категории
            itemList.addAll(tasks); // Задачи категории
        }

        checkTasks();
        removeEmptyCategories();
    }


    private void loadItems2() {
        itemList.clear();
        LinkedHashMap<String, List<Task>> categoryMap = new LinkedHashMap<>();

        String today = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        SQLiteDatabase database = db.getReadableDatabase();

        Cursor cursor = database.query(Util.TABLE_NAME, null, Util.KEY_DATE + " = ? AND " + Util.KEY_STATUS + " = ?",
                new String[]{today, "1"}, null, null, Util.KEY_CATEGORY);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));  // Убедись, что время загружается
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));

                // Логируем извлеченные данные
                Log.d("LoadDebug", "Loaded Task: " + task.getTitle() + " with time: " + task.getTime());

                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));
                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));

                String category = task.getCategory();
                if (!categoryMap.containsKey(category)) {
                    categoryMap.put(category, new ArrayList<>());
                }
                categoryMap.get(category).add(task);
            } while (cursor.moveToNext());
            cursor.close();
        }
        database.close();

        // Логируем, сколько задач загружено
        Log.d("LoadDebug", "Loaded " + itemList.size() + " tasks for today");

        // Сортировка по времени и добавление в список
        for (String category : categoryMap.keySet()) {
            List<Task> tasks = categoryMap.get(category);
            tasks.sort((task1, task2) -> {
                if (task1.getTime() != null && !task1.getTime().isEmpty() && task2.getTime() != null && !task2.getTime().isEmpty()) {
                    return task1.getTime().compareTo(task2.getTime());
                } else if (task1.getTime() == null || task1.getTime().isEmpty()) {
                    return 1;
                } else {
                    return -1;
                }
            });

            itemList.add(category); // Заголовок категории
            itemList.addAll(tasks);  // Все задачи из этой категории
        }
        checkTasks();
    }


    private void checkTasks() {
        try {
            if (itemList.isEmpty()) {
                Random random = new Random();
                String message = COMPLETED_TASK_MESSAGES[random.nextInt(COMPLETED_TASK_MESSAGES.length)];
                emptyTextView.setText(message);
                emptyTextView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                emptyTextView.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fade_in));
            } else {
                emptyTextView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e("checkTasks", "Ошибка: " + e.getMessage(), e);
        }
    }

    private void removeEmptyCategories() {
        try {
            for (int i = itemList.size() - 1; i >= 0; i--) {
                if (itemList.get(i) instanceof String) { // Это заголовок категории
                    String category = (String) itemList.get(i);

                    boolean hasTasks = false;
                    for (int j = i + 1; j < itemList.size(); j++) {
                        Object nextItem = itemList.get(j);
                        if (nextItem instanceof String) break; // дошли до следующей категории
                        if (nextItem instanceof Task) {
                            Task task = (Task) nextItem;
                            if (category.equals(task.getCategory())) {
                                hasTasks = true;
                                break;
                            }
                        }
                    }

                    if (!hasTasks) {
                        itemList.remove(i);
                    }
                }
            }
            recyclerView.getAdapter().notifyDataSetChanged();
        } catch (Exception e) {
            Log.e("removeEmptyCategories", "Ошибка: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItems();
    }

    @Override
    public void onTaskUpdated() {
        Log.d("TaskUpdated", "onTaskUpdated вызван");  // Лог, чтобы понять, что метод был вызван

      loadItems();
   //   adapter.notifyDataSetChanged(); // Уведомляем адаптер, что данные обновились
    }
}
