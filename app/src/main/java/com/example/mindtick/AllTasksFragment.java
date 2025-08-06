package com.example.mindtick;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import Adapter.TaskAdapter;
import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;
import Utils.Util;

public class AllTasksFragment extends Fragment implements OnTaskUpdatedListener{
    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Object> itemList = new ArrayList<>();
    private DatabaseHandler db;
    private TextView emptyTextView;
    private TaskAdapter adapter;


    private static final String[] NO_TASK_MESSAGES = {
            "Вы пока не создали ни одной задачи. Самое время начать! ✍️",
            "Пусто... но ведь это ненадолго, верно? 🤔",
            "Задач пока нет. Но вы всегда можете добавить! ➕",
            "Все под контролем! Или просто еще ничего не запланировано? 😏",
            "Нет задач — нет забот? Давайте добавим первую! 🎯",
            "Ваш список пока пуст. Может, начнем с чего-то простого? ☕",
            "Сегодня идеальный день, чтобы спланировать дела! 📅",
            "Свобода — это круто! Но хоть одну задачу стоит записать. 😉",
            "Задач нет... Или это уже выполненный список мечты? ✨",
            "Ваш список дел ждет первых записей. Сделаем это! 🚀",
            "Пока пусто, но ведь великие дела начинаются с малого! 🔥",
            "Не откладывайте на потом. Создайте первую задачу прямо сейчас! ⏳",
            "Организованность начинается с первого шага. Вперед! 🏁",
            "Здесь пока тихо... Давайте заполним этот список! 📖",
            "Ты можешь начать с чего угодно. Главное — начать! 🎬",
            "Время творить продуктивность! Добавь первую задачу. ⚡",
            "Пусто? Значит, есть место для великих идей! 💡",
            "Одна задача – это уже начало! Попробуй! 🚀",
            "Как насчет небольшого челленджа? Добавь задачу! 💪",
            "Твой список пока пуст, но впереди столько всего интересного! 🔥",
            "Каждое большое дело начинается с одного маленького шага. Сделай его! 🏆",
            "Планирование — ключ к успеху! Давай создадим первую задачу. 🔑",
            "Сегодня твой шанс стать продуктивнее! Начнем? ✅",
            "Ты хозяин своего времени. Наполни список важными делами! ⏳",
            "Новый день – новые цели! Пора добавить первую задачу. 🌞",
            "Давай сделаем этот день продуктивным! Начни с первой задачи. 🚀"
    };


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_tasks, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        emptyTextView = view.findViewById(R.id.emptyTextView);

        db = new DatabaseHandler(getContext());
        adapter = new TaskAdapter(getContext(), itemList, false, db, false, true, this); // для Все задачи
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
                        // 🔥 **Свайп вправо – подтверждение выполнения**
                        showConfirmationDialog("Отметить задачу как выполненную?", () -> {
                            markTaskAsCompleted(task, position);
                        }, () -> {
                            recyclerView.getAdapter().notifyItemChanged(position);
                        });

                    } else if (direction == ItemTouchHelper.LEFT) {
                        // ❌ **Свайп влево – подтверждение удаления**
                        showConfirmationDialog("Удалить задачу?", () -> {
                            deleteTask(task, position);
                        }, () -> {
                            recyclerView.getAdapter().notifyItemChanged(position);
                        });
                    }

                } else {
                    // Если это категория, отменяем свайп
                    recyclerView.getAdapter().notifyItemChanged(position);
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

        String completedAt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
        values.put(Util.KEY_STATUS, 0); // 0 = выполнена
        values.put(Util.KEY_COMPLETED_AT, completedAt);

        // Логирование перед сохранением времени
        Log.d("CompleteDebug", "Time before update: " + task.getTime());


        // Сохраняем время задачи, если оно задано
        if (!task.getTime().isEmpty()) {
            values.put(Util.KEY_TIME, task.getTime()); // сохраняем время, если оно есть
        }

        // Логирование перед сохранением времени
        Log.d("CompleteDebug", "Time before update: " + task.getTime());

        database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
        database.close();

        // Используем getContext() или requireContext(), если адаптер привязан к Fragment
        if (getContext() != null) {
            ReminderHelper.cancelAlarm(getContext(), task);
        } else {
            Log.e("TaskAdapter", "Context is null. Cannot cancel alarm.");
        }

        // Удаляем задачу из списка
        itemList.remove(position);
        recyclerView.getAdapter().notifyItemRemoved(position);

        // Проверяем и удаляем пустую категорию
        checkAndRemoveEmptyCategory(task);

        // Проверяем, пуст ли экран
        checkTasks();
    }

    private void deleteTask(Task task, int position) {
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

        // Выбираем все активные задачи
        Cursor cursor = database.query(Util.TABLE_NAME, null, Util.KEY_STATUS + " = ?",
                new String[]{"1"}, null, null, Util.KEY_CATEGORY);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TITLE)));
                task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_CATEGORY)));
                task.setDate(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DATE)));
                task.setTime(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_TIME)));
                task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(Util.KEY_DESCRIPTION)));

                // ЗАГРУЖАЕМ ПРИОРИТЕТ
                task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_PRIORITY)));

                task.setReminderEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(Util.KEY_REMINDER_ENABLED)));  // Прямое присваивание

                // Группируем задачи по категориям
                String category = task.getCategory();
                if (!categoryMap.containsKey(category)) {
                    categoryMap.put(category, new ArrayList<>());
                }
                categoryMap.get(category).add(task);
            } while (cursor.moveToNext());
            cursor.close();
        }
        database.close();

        // Сортируем задачи внутри каждой категории по дате и времени
        for (String category : categoryMap.keySet()) {
            List<Task> tasks = categoryMap.get(category);

            // Сортировка по дате и времени
            tasks.sort((task1, task2) -> {
                if (task1.getDate() != null && task2.getDate() != null) {
                    int dateComparison = task1.getDate().compareTo(task2.getDate());
                    if (dateComparison == 0) {
                        // Если даты одинаковые, сортируем по времени
                        if (task1.getTime() != null && task2.getTime() != null) {
                            return task1.getTime().compareTo(task2.getTime());
                        } else if (task1.getTime() == null || task1.getTime().isEmpty()) {
                            return 1; // Задачи без времени идут ниже
                        } else {
                            return -1; // Задачи с временем идут выше
                        }
                    }
                    return dateComparison;
                } else if (task1.getDate() == null || task1.getDate().isEmpty()) {
                    return 1; // Задачи без даты идут вниз
                } else {
                    return -1; // Задачи с датой идут выше
                }
            });

            // Добавляем заголовок категории и задачи
            itemList.add(category); // Заголовок
            itemList.addAll(tasks); // Все задачи из этой категории
        }
        checkTasks();
    }

    private void checkTasks() {
        if (itemList.isEmpty()) {
            Random random = new Random();
            String message = NO_TASK_MESSAGES[random.nextInt(NO_TASK_MESSAGES.length)];
            emptyTextView.setText(message);
            emptyTextView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyTextView.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fade_in));
        } else {
            emptyTextView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
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
      //  adapter.notifyDataSetChanged(); // Уведомляем адаптер, что данные обновились
    }
}
