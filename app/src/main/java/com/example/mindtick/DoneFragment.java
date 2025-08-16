package com.example.mindtick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
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
import Utils.ReminderHelper;
import Utils.Util;

public class DoneFragment extends Fragment implements OnTaskUpdatedListener {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private List<Object> itemList = new ArrayList<>();
    private DatabaseHandler db;
    private TaskAdapter adapter;
    private boolean sortAscending = false;

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
        adapter = new TaskAdapter(getContext(), itemList, false, db, true, false, this);
        adapter.setOnTaskUpdatedListener(() -> {
            Log.d("DoneFragment", "Обновляем задачи после изменения");
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
                                "Восстановить задачу \"" + task.getTitle() + "\" как активную?",
                                () -> {
                                    Intent intent = new Intent(getActivity(), EditTask.class);
                                    intent.putExtra("task_id", task.getId());
                                    intent.putExtra("returnFragment", "DoneFragment");
                                    intent.putExtra("restoreMode", true);
                                    ((Activity) getActivity()).startActivityForResult(intent, EditTask.REQUEST_EDIT_TASK);
                                    getActivity().overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
                                },
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

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
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
        checkAndRemoveEmptyDate(task);
        checkTasks();
    }

    private void checkAndRemoveEmptyDate(Task task) {
        final String date = task.getCompletedAt() != null && !task.getCompletedAt().isEmpty() ? task.getCompletedAt().split(" ")[0] : "Без даты завершения";
        boolean isDateEmpty = true;
        for (Object obj : itemList) {
            if (obj instanceof Task) {
                Task currentTask = (Task) obj;
                String taskDate = currentTask.getCompletedAt() != null && !currentTask.getCompletedAt().isEmpty() ? currentTask.getCompletedAt().split(" ")[0] : "Без даты завершения";
                if (date.equals(taskDate)) {
                    isDateEmpty = false;
                    break;
                }
            }
        }

        if (isDateEmpty) {
            itemList.removeIf(obj -> obj instanceof String && obj.equals(date));
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private void loadItems() {
        itemList.clear();
        List<Task> tasks = db.getCompletedTasks();

        Log.d("DoneFragment", "Applying sort: sortAscending=" + sortAscending);

        if (tasks.isEmpty()) {
            checkTasks();
            adapter.notifyDataSetChanged();
            return;
        }

        List<String> dates = new ArrayList<>();
        for (Task task : tasks) {
            String date = task.getCompletedAt() != null && !task.getCompletedAt().isEmpty() ? task.getCompletedAt().split(" ")[0] : "Без даты завершения";
            if (!dates.contains(date)) {
                dates.add(date);
            }
        }
        Collections.sort(dates, (d1, d2) -> {
            if (d1.equals("Без даты завершения")) return -1;
            if (d2.equals("Без даты завершения")) return 1;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                Date date1 = sdf.parse(d1);
                Date date2 = sdf.parse(d2);
                return sortAscending ? date1.compareTo(date2) : date2.compareTo(date1);
            } catch (ParseException e) {
                return sortAscending ? d1.compareTo(d2) : d2.compareTo(d1);
            }
        });

        for (String date : dates) {
            itemList.add(date);
            List<Task> dateTasks = new ArrayList<>();
            for (Task task : tasks) {
                String taskDate = task.getCompletedAt() != null && !task.getCompletedAt().isEmpty() ? task.getCompletedAt().split(" ")[0] : "Без даты завершения";
                if (taskDate.equals(date)) {
                    dateTasks.add(task);
                }
            }
            Collections.sort(dateTasks, (t1, t2) -> {
                String completedAt1 = t1.getCompletedAt() != null ? t1.getCompletedAt() : "";
                String completedAt2 = t2.getCompletedAt() != null ? t2.getCompletedAt() : "";
                if (completedAt1.isEmpty() && completedAt2.isEmpty()) return 0;
                if (completedAt1.isEmpty()) return sortAscending ? -1 : 1;
                if (completedAt2.isEmpty()) return sortAscending ? 1 : -1;
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                    Date date1 = sdf.parse(completedAt1);
                    Date date2 = sdf.parse(completedAt2);
                    return sortAscending ? date1.compareTo(date2) : date2.compareTo(date1);
                } catch (ParseException e) {
                    return sortAscending ? completedAt1.compareTo(completedAt2) : completedAt2.compareTo(completedAt1);
                }
            });
            itemList.addAll(dateTasks);
        }

        adapter.notifyDataSetChanged();
        recyclerView.smoothScrollToPosition(0);
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
            Log.e("DoneFragment", "Ошибка в checkTasks: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItems();
    }

    @Override
    public void onTaskUpdated() {
        Log.d("DoneFragment", "onTaskUpdated вызван");
        loadItems();
    }

    public void setSort(boolean sortAscending) {
        this.sortAscending = sortAscending;
        Log.d("DoneFragment", "setSort called with sortAscending: " + sortAscending);
        loadItems();
    }

    public boolean getSortAscending() {
        return sortAscending;
    }
}