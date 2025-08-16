package com.example.mindtick;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import Adapter.TaskAdapter;
import Data.DatabaseHandler;
import Model.Task;
import Utils.FilterType;
import Utils.ReminderHelper;
import Utils.Util;

public class AllTasksFragment extends Fragment implements TaskAdapter.OnTaskUpdatedListener {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private List<Object> itemList = new ArrayList<>();
    private DatabaseHandler db;
    private TaskAdapter adapter;
    private FilterType currentFilter = FilterType.CATEGORY;
    private boolean sortAscending = true;

    private static final String[] NO_TASK_MESSAGES = {
            "Пока нет задач... Добавь новые планы! 📅",
            "Список пуст! Пора запланировать что-то крутое! ✍️",
            "Без задач? Время строить большие планы! 🚀",
            "Добавь задачу и начни двигаться к цели! 🎯",
            "Чистый лист – идеально для новых идей! 💡"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_tasks, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        emptyTextView = view.findViewById(R.id.emptyTextView);

        db = new DatabaseHandler(getContext());
        adapter = new TaskAdapter(getContext(), itemList, false, db, false, true, this);
        adapter.setOnTaskUpdatedListener(() -> {
            Log.d("AllTasksFragment", "Обновляем задачи после изменения");
            loadItems();
        });

        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new StickyHeaderItemDecoration(adapter));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

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
                final int finalPosition = viewHolder.getAdapterPosition();
                Object item = itemList.get(finalPosition);

                if (item instanceof Task) {
                    Task task = (Task) item;

                    if (direction == ItemTouchHelper.RIGHT) {
                        showConfirmationDialog(
                                "Отметить задачу \"" + task.getTitle() + "\" как выполненную?",
                                () -> markTaskAsCompleted(task, finalPosition),
                                () -> recyclerView.getAdapter().notifyItemChanged(finalPosition)
                        );
                    } else if (direction == ItemTouchHelper.LEFT) {
                        showConfirmationDialog(
                                "Удалить задачу \"" + task.getTitle() + "\"?",
                                () -> deleteTask(task, finalPosition),
                                () -> recyclerView.getAdapter().notifyItemChanged(finalPosition)
                        );
                    }
                } else {
                    recyclerView.getAdapter().notifyItemChanged(finalPosition);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    float maxSwipe = viewHolder.itemView.getWidth() / 2f;
                    float newDX = Math.min(Math.max(dX, -maxSwipe), maxSwipe);

                    if (isCurrentlyActive) {
                        super.onChildDraw(c, recyclerView, viewHolder, newDX, dY, actionState, true);
                    } else {
                        viewHolder.itemView.animate()
                                .translationX(0)
                                .setInterpolator(new OvershootInterpolator(2f))
                                .setDuration(400)
                                .start();
                    }
                } else {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        loadItems();
        return view;
    }

    private void showConfirmationDialog(String message, Runnable onConfirm, Runnable onCancel) {
        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.CustomDialog)
                .setMessage(message)
                .setPositiveButton("Да", (dialogInterface, which) -> onConfirm.run())
                .setNegativeButton("Отмена", (dialogInterface, which) -> onCancel.run())
                .setCancelable(false)
                .create();

        dialog.show();

        int positiveColor = ContextCompat.getColor(getContext(), R.color.all_text);
        int negativeColor = ContextCompat.getColor(getContext(), R.color.all_text);
        int messageColor = ContextCompat.getColor(getContext(), R.color.title_text);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(positiveColor);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(negativeColor);
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(messageColor);
        }
    }

    private void markTaskAsCompleted(Task task, int position) {
        SQLiteDatabase database = db.getWritableDatabase();
        ContentValues values = new ContentValues();

        String completedAt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
        values.put(Util.KEY_STATUS, 0);
        values.put(Util.KEY_COMPLETED_AT, completedAt);
        values.put(Util.KEY_PREVIOUS_REMINDER_ENABLED, task.getReminderEnabled());
        values.put(Util.KEY_REMINDER_ENABLED, 0);

        database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
        database.close();

        if (getContext() != null) {
            ReminderHelper.cancelAlarm(getContext(), task);
        } else {
            Log.e("TaskAdapter", "Context is null. Cannot cancel alarm.");
        }

        itemList.remove(position);
        recyclerView.getAdapter().notifyItemRemoved(position);
        checkAndRemoveEmptyHeader(task);
        checkTasks();
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

        itemList.remove(position);
        recyclerView.getAdapter().notifyItemRemoved(position);
        checkAndRemoveEmptyHeader(task);
        checkTasks();
    }

    private void checkAndRemoveEmptyHeader(Task task) {
        final String header;
        if (currentFilter == FilterType.CATEGORY) {
            header = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Без категории";
        } else if (currentFilter == FilterType.TIME) {
            header = getTimeCategory(task.getTime());
        } else if (currentFilter == FilterType.DATE) {
            header = task.getDate() != null && !task.getDate().isEmpty() ? task.getDate() : "Без даты";
        } else {
            return;
        }

        boolean isHeaderEmpty = true;
        for (Object obj : itemList) {
            if (obj instanceof Task) {
                Task currentTask = (Task) obj;
                String taskHeader = null;
                if (currentFilter == FilterType.CATEGORY) {
                    taskHeader = currentTask.getCategory() != null && !currentTask.getCategory().isEmpty() ? currentTask.getCategory() : "Без категории";
                } else if (currentFilter == FilterType.TIME) {
                    taskHeader = getTimeCategory(currentTask.getTime());
                } else if (currentFilter == FilterType.DATE) {
                    taskHeader = currentTask.getDate() != null && !currentTask.getDate().isEmpty() ? currentTask.getDate() : "Без даты";
                }
                if (header.equals(taskHeader)) {
                    isHeaderEmpty = false;
                    break;
                }
            }
        }

        if (isHeaderEmpty) {
            itemList.removeIf(obj -> obj instanceof String && obj.equals(header));
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private String getTimeCategory(String time) {
        if (time == null || time.isEmpty()) {
            return "Без времени";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = sdf.parse(time);
            if (date == null) return "Без времени";

            int hour = date.getHours();
            if (hour >= 6 && hour < 12) return "Утро";
            if (hour >= 12 && hour < 18) return "День";
            if (hour >= 18 && hour < 24) return "Вечер";
            return "Ночь";
        } catch (ParseException e) {
            Log.e("AllTasksFragment", "Error parsing time: " + time, e);
            return "Без времени";
        }
    }

    private int getTimeCategoryOrder(String category, boolean ascending) {
        String[] orderAsc = {"Без времени", "Утро", "День", "Вечер", "Ночь"};
        String[] orderDesc = {"Без времени", "Ночь", "Вечер", "День", "Утро"};
        String[] order = ascending ? orderAsc : orderDesc;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(category)) return i;
        }
        return 0;
    }

    private void loadItems() {
        itemList.clear();
        String currentDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        List<Task> tasks = db.getUpcomingTasks(currentDate);

        Log.d("AllTasksFragment", "Applying filter: " + currentFilter + ", sortAscending: " + sortAscending + ", tasks fetched: " + tasks.size());
        for (Task task : tasks) {
            Log.d("AllTasksFragment", "Task: " + task.getTitle() + ", Date: " + task.getDate() + ", Time: " + task.getTime() + ", Status: " + task.getStatus());
        }

        if (tasks.isEmpty()) {
            checkTasks();
            adapter.notifyDataSetChanged();
            return;
        }

        switch (currentFilter) {
            case CATEGORY:
                List<String> categories = new ArrayList<>();
                for (Task task : tasks) {
                    String category = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Без категории";
                    if (!categories.contains(category)) {
                        categories.add(category);
                    }
                }
                Collections.sort(categories, (c1, c2) -> {
                    if (c1.equals("Без категории")) return -1;
                    if (c2.equals("Без категории")) return 1;
                    return sortAscending ? c1.compareTo(c2) : c2.compareTo(c1);
                });

                for (String category : categories) {
                    itemList.add(category);
                    List<Task> categoryTasks = new ArrayList<>();
                    for (Task task : tasks) {
                        String taskCategory = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Без категории";
                        if (taskCategory.equals(category)) {
                            categoryTasks.add(task);
                        }
                    }
                    Collections.sort(categoryTasks, (t1, t2) -> {
                        String date1 = t1.getDate() != null ? t1.getDate() : "";
                        String date2 = t2.getDate() != null ? t2.getDate() : "";
                        if (date1.isEmpty() && date2.isEmpty()) {
                            String time1 = t1.getTime() != null ? t1.getTime() : "";
                            String time2 = t2.getTime() != null ? t2.getTime() : "";
                            if (time1.isEmpty() && time2.isEmpty()) return 0;
                            if (time1.isEmpty()) return -1;
                            if (time2.isEmpty()) return 1;
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                Date timeDate1 = sdf.parse(time1);
                                Date timeDate2 = sdf.parse(time2);
                                return timeDate1.compareTo(timeDate2);
                            } catch (ParseException e) {
                                Log.e("AllTasksFragment", "Error parsing time in CATEGORY: " + time1 + " vs " + time2, e);
                                return time1.compareTo(time2);
                            }
                        }
                        if (date1.isEmpty()) return -1;
                        if (date2.isEmpty()) return 1;
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                            Date dateDate1 = sdf.parse(date1);
                            Date dateDate2 = sdf.parse(date2);
                            int dateComparison = dateDate1.compareTo(dateDate2);
                            if (dateComparison != 0) return dateComparison;
                            String time1 = t1.getTime() != null ? t1.getTime() : "";
                            String time2 = t2.getTime() != null ? t2.getTime() : "";
                            if (time1.isEmpty() && time2.isEmpty()) return 0;
                            if (time1.isEmpty()) return -1;
                            if (time2.isEmpty()) return 1;
                            try {
                                SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                Date timeDate1 = timeSdf.parse(time1);
                                Date timeDate2 = timeSdf.parse(time2);
                                return timeDate1.compareTo(timeDate2);
                            } catch (ParseException e) {
                                Log.e("AllTasksFragment", "Error parsing time in CATEGORY: " + time1 + " vs " + time2, e);
                                return time1.compareTo(time2);
                            }
                        } catch (ParseException e) {
                            Log.e("AllTasksFragment", "Error parsing date in CATEGORY: " + date1 + " vs " + date2, e);
                            return date1.compareTo(date2);
                        }
                    });
                    itemList.addAll(categoryTasks);
                }
                break;

            case TIME:
                List<String> timeCategories = new ArrayList<>();
                for (Task task : tasks) {
                    String timeCategory = getTimeCategory(task.getTime());
                    if (!timeCategories.contains(timeCategory)) {
                        timeCategories.add(timeCategory);
                    }
                }
                Collections.sort(timeCategories, (c1, c2) -> {
                    int order1 = getTimeCategoryOrder(c1, sortAscending);
                    int order2 = getTimeCategoryOrder(c2, sortAscending);
                    return Integer.compare(order1, order2);
                });

                for (String timeCategory : timeCategories) {
                    itemList.add(timeCategory);
                    List<Task> timeCategoryTasks = new ArrayList<>();
                    for (Task task : tasks) {
                        if (timeCategory.equals(getTimeCategory(task.getTime()))) {
                            timeCategoryTasks.add(task);
                        }
                    }
                    Collections.sort(timeCategoryTasks, (t1, t2) -> {
                        String date1 = t1.getDate() != null ? t1.getDate() : "";
                        String date2 = t2.getDate() != null ? t2.getDate() : "";
                        if (date1.isEmpty() && date2.isEmpty()) {
                            String time1 = t1.getTime() != null ? t1.getTime() : "";
                            String time2 = t2.getTime() != null ? t2.getTime() : "";
                            if (time1.isEmpty() && time2.isEmpty()) return 0;
                            if (time1.isEmpty()) return -1;
                            if (time2.isEmpty()) return 1;
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                Date timeDate1 = sdf.parse(time1);
                                Date timeDate2 = sdf.parse(time2);
                                return timeDate1.compareTo(timeDate2);
                            } catch (ParseException e) {
                                Log.e("AllTasksFragment", "Error parsing time in TIME: " + time1 + " vs " + time2, e);
                                return time1.compareTo(time2);
                            }
                        }
                        if (date1.isEmpty()) return -1;
                        if (date2.isEmpty()) return 1;
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                            Date dateDate1 = sdf.parse(date1);
                            Date dateDate2 = sdf.parse(date2);
                            int dateComparison = dateDate1.compareTo(dateDate2);
                            if (dateComparison != 0) return dateComparison;
                            String time1 = t1.getTime() != null ? t1.getTime() : "";
                            String time2 = t2.getTime() != null ? t2.getTime() : "";
                            if (time1.isEmpty() && time2.isEmpty()) return 0;
                            if (time1.isEmpty()) return -1;
                            if (time2.isEmpty()) return 1;
                            try {
                                SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                Date timeDate1 = timeSdf.parse(time1);
                                Date timeDate2 = timeSdf.parse(time2);
                                return timeDate1.compareTo(timeDate2);
                            } catch (ParseException e) {
                                Log.e("AllTasksFragment", "Error parsing time in TIME: " + time1 + " vs " + time2, e);
                                return time1.compareTo(time2);
                            }
                        } catch (ParseException e) {
                            Log.e("AllTasksFragment", "Error parsing date in TIME: " + date1 + " vs " + date2, e);
                            return date1.compareTo(date2);
                        }
                    });
                    itemList.addAll(timeCategoryTasks);
                }
                break;

            case DATE:
                List<String> dates = new ArrayList<>();
                for (Task task : tasks) {
                    String date = task.getDate() != null && !task.getDate().isEmpty() ? task.getDate() : "Без даты";
                    if (!dates.contains(date)) {
                        dates.add(date);
                    }
                }
                Collections.sort(dates, (d1, d2) -> {
                    if (d1.equals("Без даты")) return -1;
                    if (d2.equals("Без даты")) return 1;
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                        Date date1 = sdf.parse(d1);
                        Date date2 = sdf.parse(d2);
                        return sortAscending ? date1.compareTo(date2) : date2.compareTo(date1);
                    } catch (ParseException e) {
                        Log.e("AllTasksFragment", "Error parsing date in DATE: " + d1 + " vs " + d2, e);
                        return sortAscending ? d1.compareTo(d2) : d2.compareTo(d1);
                    }
                });

                for (String date : dates) {
                    itemList.add(date);
                    List<Task> dateTasks = new ArrayList<>();
                    for (Task task : tasks) {
                        String taskDate = task.getDate() != null && !task.getDate().isEmpty() ? task.getDate() : "Без даты";
                        if (taskDate.equals(date)) {
                            dateTasks.add(task);
                        }
                    }
                    Collections.sort(dateTasks, (t1, t2) -> {
                        String time1 = t1.getTime() != null ? t1.getTime() : "";
                        String time2 = t2.getTime() != null ? t2.getTime() : "";
                        if (time1.isEmpty() && time2.isEmpty()) return 0;
                        if (time1.isEmpty()) return -1;
                        if (time2.isEmpty()) return 1;
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            Date timeDate1 = sdf.parse(time1);
                            Date timeDate2 = sdf.parse(time2);
                            return timeDate1.compareTo(timeDate2);
                        } catch (ParseException e) {
                            Log.e("AllTasksFragment", "Error parsing time in DATE: " + time1 + " vs " + time2, e);
                            return time1.compareTo(time2);
                        }
                    });
                    itemList.addAll(dateTasks);
                }
                break;

            case PRIORITY:
                Collections.sort(tasks, (t1, t2) -> {
                    int priority1 = t1.getPriority();
                    int priority2 = t2.getPriority();
                    int priorityComparison = sortAscending ? Integer.compare(priority2, priority1) : Integer.compare(priority1, priority2);
                    if (priorityComparison != 0) return priorityComparison;
                    String date1 = t1.getDate() != null ? t1.getDate() : "";
                    String date2 = t2.getDate() != null ? t2.getDate() : "";
                    if (date1.isEmpty() && date2.isEmpty()) {
                        String time1 = t1.getTime() != null ? t1.getTime() : "";
                        String time2 = t2.getTime() != null ? t2.getTime() : "";
                        if (time1.isEmpty() && time2.isEmpty()) return 0;
                        if (time1.isEmpty()) return -1;
                        if (time2.isEmpty()) return 1;
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            Date timeDate1 = sdf.parse(time1);
                            Date timeDate2 = sdf.parse(time2);
                            return timeDate1.compareTo(timeDate2);
                        } catch (ParseException e) {
                            Log.e("AllTasksFragment", "Error parsing time in PRIORITY: " + time1 + " vs " + time2, e);
                            return time1.compareTo(time2);
                        }
                    }
                    if (date1.isEmpty()) return -1;
                    if (date2.isEmpty()) return 1;
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                        Date dateDate1 = sdf.parse(date1);
                        Date dateDate2 = sdf.parse(date2);
                        int dateComparison = dateDate1.compareTo(dateDate2);
                        if (dateComparison != 0) return dateComparison;
                        String time1 = t1.getTime() != null ? t1.getTime() : "";
                        String time2 = t2.getTime() != null ? t2.getTime() : "";
                        if (time1.isEmpty() && time2.isEmpty()) return 0;
                        if (time1.isEmpty()) return -1;
                        if (time2.isEmpty()) return 1;
                        try {
                            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            Date timeDate1 = timeSdf.parse(time1);
                            Date timeDate2 = timeSdf.parse(time2);
                            return timeDate1.compareTo(timeDate2);
                        } catch (ParseException e) {
                            Log.e("AllTasksFragment", "Error parsing time in PRIORITY: " + time1 + " vs " + time2, e);
                            return time1.compareTo(time2);
                        }
                    } catch (ParseException e) {
                        Log.e("AllTasksFragment", "Error parsing date in PRIORITY: " + date1 + " vs " + date2, e);
                        return date1.compareTo(date2);
                    }
                });
                itemList.addAll(tasks);
                break;
        }

        adapter.notifyDataSetChanged();
        recyclerView.smoothScrollToPosition(0);
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
        Log.d("AllTasksFragment", "onTaskUpdated вызван");
        loadItems();
    }

    public void setFilter(FilterType filterType, boolean sortAscending) {
        this.currentFilter = filterType;
        this.sortAscending = sortAscending;
        Log.d("AllTasksFragment", "setFilter called with filter: " + filterType + ", sortAscending: " + sortAscending);
        loadItems();
    }

    public FilterType getCurrentFilter() {
        return currentFilter;
    }

    public boolean getSortAscending() {
        return sortAscending;
    }
}