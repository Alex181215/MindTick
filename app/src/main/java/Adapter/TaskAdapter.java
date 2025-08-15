package Adapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mindtick.EditTask;
import com.example.mindtick.R;
import com.example.mindtick.StickyHeaderInterface;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import Data.DatabaseHandler;
import Model.Task;
import Utils.FilterType;
import Utils.ReminderHelper;
import Utils.Util;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements StickyHeaderInterface {
    private Fragment parentFragment;
    private Context context;
    private List<Object> itemList;
    private boolean isTodayScreen;
    private boolean isCompletedScreen;
    private boolean isAllTasksScreen;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK = 1;
    private DatabaseHandler db;

    public static final int getHeaderType() {
        return TYPE_HEADER;
    }

    public List<Object> getItemList() {
        return itemList;
    }

    public interface OnTaskUpdatedListener {
        void onTaskUpdated();
    }

    private OnTaskUpdatedListener onTaskUpdatedListener;

    public void setOnTaskUpdatedListener(OnTaskUpdatedListener listener) {
        this.onTaskUpdatedListener = listener;
    }

    public TaskAdapter(Context context, List<Object> itemList, boolean isTodayScreen, DatabaseHandler db, boolean isCompletedScreen, boolean isAllTasksScreen, Fragment parentFragment) {
        this.context = context;
        this.itemList = itemList;
        this.isTodayScreen = isTodayScreen;
        this.db = db;
        this.isCompletedScreen = isCompletedScreen;
        this.isAllTasksScreen = isAllTasksScreen;
        this.parentFragment = parentFragment;
    }

    public void updateList(List<Task> tasks, FilterType filterType, boolean sortAscending) {
        itemList.clear();
        if (isCompletedScreen) {
            // Сортировка по времени завершения
            tasks.sort((t1, t2) -> {
                if (t1.getCompletedAt() == null || t1.getCompletedAt().isEmpty()) return sortAscending ? -1 : 1;
                if (t2.getCompletedAt() == null || t2.getCompletedAt().isEmpty()) return sortAscending ? 1 : -1;
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                try {
                    return sortAscending ?
                            sdf.parse(t1.getCompletedAt()).compareTo(sdf.parse(t2.getCompletedAt())) :
                            sdf.parse(t2.getCompletedAt()).compareTo(sdf.parse(t1.getCompletedAt()));
                } catch (ParseException e) {
                    Log.e("TaskAdapter", "Ошибка парсинга времени завершения: " + e.getMessage());
                    return 0;
                }
            });

            Map<String, List<Task>> groupedTasks = new LinkedHashMap<>();
            for (Task task : tasks) {
                String category = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Без категории";
                groupedTasks.computeIfAbsent(category, k -> new ArrayList<>()).add(task);
            }

            List<String> sortedCategories = new ArrayList<>(groupedTasks.keySet());
            Collections.sort(sortedCategories, sortAscending ? String.CASE_INSENSITIVE_ORDER : (o1, o2) -> o2.compareToIgnoreCase(o1));
            if (sortedCategories.contains("Без категории")) {
                sortedCategories.remove("Без категории");
                sortedCategories.add(0, "Без категории");
            }

            for (String category : sortedCategories) {
                List<Task> categoryTasks = groupedTasks.get(category);
                if (!categoryTasks.isEmpty()) {
                    itemList.add(category);
                    itemList.addAll(categoryTasks);
                }
            }
        } else {
            // Для "Сегодня" и "Все задачи"
            switch (filterType) {
                case TIME:
                    Map<String, List<Task>> timeGroups = new LinkedHashMap<>();
                    timeGroups.put("Без времени", new ArrayList<>());
                    timeGroups.put("Ночь (00:00–06:00)", new ArrayList<>());
                    timeGroups.put("Утро (06:00–12:00)", new ArrayList<>());
                    timeGroups.put("День (12:00–18:00)", new ArrayList<>());
                    timeGroups.put("Вечер (18:00–24:00)", new ArrayList<>());

                    for (Task task : tasks) {
                        String header = getTimeHeader(task.getTime());
                        timeGroups.get(header).add(task);
                    }

                    for (Map.Entry<String, List<Task>> entry : timeGroups.entrySet()) {
                        List<Task> groupTasks = entry.getValue();
                        if (!groupTasks.isEmpty()) {
                            groupTasks.sort((t1, t2) -> {
                                boolean t1Empty = t1.getTime() == null || t1.getTime().isEmpty();
                                boolean t2Empty = t2.getTime() == null || t2.getTime().isEmpty();
                                if (t1Empty && t2Empty) return 0;
                                if (t1Empty) return sortAscending ? -1 : 1;
                                if (t2Empty) return sortAscending ? 1 : -1;
                                return sortAscending ? t1.getTime().compareTo(t2.getTime()) : t2.getTime().compareTo(t1.getTime());
                            });
                            itemList.add(entry.getKey());
                            itemList.addAll(groupTasks);
                        }
                    }
                    break;

                case CATEGORY:
                    Map<String, List<Task>> categoryGroups = new LinkedHashMap<>();
                    for (Task task : tasks) {
                        String category = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Без категории";
                        categoryGroups.computeIfAbsent(category, k -> new ArrayList<>()).add(task);
                    }

                    List<String> sortedCategories = new ArrayList<>(categoryGroups.keySet());
                    Collections.sort(sortedCategories, sortAscending ? String.CASE_INSENSITIVE_ORDER : (o1, o2) -> o2.compareToIgnoreCase(o1));
                    if (sortedCategories.contains("Без категории")) {
                        sortedCategories.remove("Без категории");
                        sortedCategories.add(0, "Без категории");
                    }

                    for (String category : sortedCategories) {
                        List<Task> categoryTasks = categoryGroups.get(category);
                        if (!categoryTasks.isEmpty()) {
                            categoryTasks.sort((t1, t2) -> {
                                String c1 = t1.getCategory() != null && !t1.getCategory().isEmpty() ? t1.getCategory() : "Без категории";
                                String c2 = t2.getCategory() != null && !t2.getCategory().isEmpty() ? t2.getCategory() : "Без категории";
                                return sortAscending ? c1.compareTo(c2) : c2.compareTo(c1);
                            });
                            itemList.add(category);
                            itemList.addAll(categoryTasks);
                        }
                    }
                    break;

                case PRIORITY:
                    tasks.sort((t1, t2) -> sortAscending ?
                            Integer.compare(t1.getPriority(), t2.getPriority()) :
                            Integer.compare(t2.getPriority(), t1.getPriority()));

                    Map<String, List<Task>> priorityGroups = new LinkedHashMap<>();
                    priorityGroups.put("Высокий", new ArrayList<>());
                    priorityGroups.put("Средний", new ArrayList<>());
                    priorityGroups.put("Низкий", new ArrayList<>());

                    for (Task task : tasks) {
                        String priority = getPriorityHeader(task.getPriority());
                        priorityGroups.get(priority).add(task);
                    }

                    List<String> orderedPriorities = sortAscending ?
                            Arrays.asList("Низкий", "Средний", "Высокий") :
                            Arrays.asList("Высокий", "Средний", "Низкий");
                    for (String priority : orderedPriorities) {
                        List<Task> groupTasks = priorityGroups.get(priority);
                        if (!groupTasks.isEmpty()) {
                            itemList.add(priority);
                            itemList.addAll(groupTasks);
                        }
                    }
                    break;
            }
        }
        notifyDataSetChanged();
        Log.d("TaskAdapter", "Updated itemList with filter " + filterType + ", sortAscending: " + sortAscending + ", size: " + itemList.size());
    }

    private String getTimeHeader(String time) {
        if (time == null || time.isEmpty()) return "Без времени";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(sdf.parse(time));
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (hour >= 0 && hour < 6) return "Ночь (00:00–06:00)";
            if (hour < 12) return "Утро (06:00–12:00)";
            if (hour < 18) return "День (12:00–18:00)";
            return "Вечер (18:00–24:00)";
        } catch (ParseException e) {
            Log.e("TaskAdapter", "Ошибка парсинга времени: " + e.getMessage());
            return "Без времени";
        }
    }

    private String getPriorityHeader(int priority) {
        switch (priority) {
            case 1: return "Высокий";
            case 2: return "Средний";
            default: return "Низкий";
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (itemList.get(position) instanceof String) {
            return TYPE_HEADER;
        } else {
            return TYPE_TASK;
        }
    }

    private void showDateTimeDialog(Task task, TaskViewHolder taskHolder) {
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.CustomDialog)
                .setMessage("Чтобы включить напоминание, нужно установить дату и время. Добавить их сейчас?")
                .setPositiveButton("Да", (dialog1, which) -> {
                    String returnFragmentTag = isTodayScreen ? "TodayFragment" : isAllTasksScreen ? "AllTasksFragment" : "DoneFragment";
                    Intent intent = new Intent(context, EditTask.class);
                    intent.putExtra("task_id", task.getId());
                    intent.putExtra("returnFragment", returnFragmentTag);
                    ((Activity) context).startActivityForResult(intent, EditTask.REQUEST_EDIT_TASK);
                    ((Activity) context).overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
                    taskHolder.switchReminder.setChecked(false);
                })
                .setNegativeButton("Отмена", (dialog12, which) -> {
                    taskHolder.switchReminder.setChecked(false);
                })
                .create();

        dialog.setOnShowListener(dialog1 -> {
            int positiveColor = ContextCompat.getColor(context, R.color.all_text);
            int negativeColor = ContextCompat.getColor(context, R.color.all_text);
            int messageColor = ContextCompat.getColor(context, R.color.title_text);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(positiveColor);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(negativeColor);
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(messageColor);
            }
        });

        dialog.show();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_category, parent, false);
            return new HeaderViewHolder(view);
        } else {
            if (isCompletedScreen) {
                View view = inflater.inflate(R.layout.item_task_completed, parent, false);
                return new TaskViewHolder(view);
            } else {
                View view = inflater.inflate(R.layout.item_task, parent, false);
                return new TaskViewHolder(view);
            }
        }
    }

    private boolean isTaskOverdue(String date, String time) {
        if (date == null || date.isEmpty() || time == null || time.isEmpty()) {
            return false;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        try {
            Date taskDateTime = sdf.parse(date + " " + time);
            return taskDateTime != null && taskDateTime.before(new Date());
        } catch (ParseException e) {
            Log.e("TaskAdapter", "Ошибка парсинга даты/времени: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            String category = (String) itemList.get(position);
            ((HeaderViewHolder) holder).tvCategory.setText(category);
            Log.d("TaskAdapter", "Binding header at position " + position + ": [" + category + "]");
        } else {
            Task task = (Task) itemList.get(position);
            holder.itemView.setOnClickListener(v -> {
                if (isCompletedScreen) {
                    Toast.makeText(context, "Редактирование запрещено. Восстановите задачу для изменений.", Toast.LENGTH_SHORT).show();
                } else {
                    String returnFragmentTag = isTodayScreen ? "TodayFragment" : isAllTasksScreen ? "AllTasksFragment" : "DoneFragment";
                    Intent intent = new Intent(context, EditTask.class);
                    intent.putExtra("task_id", task.getId());
                    intent.putExtra("returnFragment", returnFragmentTag);
                    ((Activity) context).startActivityForResult(intent, EditTask.REQUEST_EDIT_TASK);
                    ((Activity) context).overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity);
                }
            });

            TaskViewHolder taskHolder = (TaskViewHolder) holder;
            taskHolder.tvTitle.setText(task.getTitle());
            taskHolder.tvDescription.setText(task.getDescription().isEmpty() ? "Описание не установлено" : task.getDescription());

            if (!isCompletedScreen) {
                if (isTaskOverdue(task.getDate(), task.getTime())) {
                    taskHolder.tvNextExecutionTime.setTextColor(ContextCompat.getColor(context, R.color.red));
                    if (task.getReminderEnabled() == 1) {
                        task.setReminderEnabled(0);
                        taskHolder.switchReminder.setChecked(false);
                        SQLiteDatabase database = db.getWritableDatabase();
                        ContentValues values = new ContentValues();
                        values.put(Util.KEY_REMINDER_ENABLED, 0);
                        database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
                        database.close();
                    }
                } else {
                    taskHolder.tvNextExecutionTime.setTextColor(ContextCompat.getColor(context, R.color.title_text));
                }
            }

            if (isCompletedScreen) {
                if (task.getCompletedAt() != null && !task.getCompletedAt().isEmpty()) {
                    taskHolder.tvNextExecutionTime.setText("Завершено: " + task.getCompletedAt());
                } else {
                    taskHolder.tvNextExecutionTime.setText("Завершено: Не указано");
                }
            } else if (isTodayScreen) {
                if (task.getTime() != null && !task.getTime().isEmpty()) {
                    taskHolder.tvNextExecutionTime.setText(task.getTime());
                } else {
                    taskHolder.tvNextExecutionTime.setText("Время не установлено");
                }
            } else if (isAllTasksScreen) {
                if (task.getDate() != null && !task.getDate().isEmpty()) {
                    if (task.getTime() != null && !task.getTime().isEmpty()) {
                        taskHolder.tvNextExecutionTime.setText(task.getDate() + " " + task.getTime());
                    } else {
                        taskHolder.tvNextExecutionTime.setText(task.getDate());
                    }
                } else {
                    taskHolder.tvNextExecutionTime.setText("Дата не установлена");
                }
            } else {
                taskHolder.tvNextExecutionTime.setText("");
            }

            taskHolder.ivPriorityR.setVisibility(View.GONE);
            taskHolder.ivPriorityO.setVisibility(View.GONE);
            taskHolder.ivPriorityB.setVisibility(View.GONE);

            if (!isCompletedScreen) {
                int priority = task.getPriority();
                if (priority == 1) {
                    taskHolder.ivPriorityR.setVisibility(View.VISIBLE);
                } else if (priority == 2) {
                    taskHolder.ivPriorityO.setVisibility(View.VISIBLE);
                } else {
                    taskHolder.ivPriorityB.setVisibility(View.VISIBLE);
                }
            }

            taskHolder.switchReminder.setOnCheckedChangeListener(null);
            taskHolder.switchReminder.setChecked(task.getReminderEnabled() == 1);

            taskHolder.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Task freshTask = db.getTask(task.getId());
                if (freshTask == null) {
                    Log.e("TaskAdapter", "Ошибка: не удалось получить задачу из базы при переключении напоминания");
                    Toast.makeText(context, "Ошибка при установке напоминания", Toast.LENGTH_SHORT).show();
                    taskHolder.switchReminder.setChecked(false);
                    return;
                }

                Log.d("TaskAdapter", "switchReminder toggled: " + isChecked +
                        " for task id: " + freshTask.getId() +
                        ", title: " + freshTask.getTitle() +
                        ", reminder time: " + freshTask.getDate() + " " + freshTask.getTime());

                if (isTaskOverdue(freshTask.getDate(), freshTask.getTime())) {
                    Toast.makeText(context, "Напоминание для просроченной задачи невозможно!", Toast.LENGTH_SHORT).show();
                    Log.d("TaskAdapter", "Cannot enable reminder - task is overdue");
                    taskHolder.switchReminder.setChecked(false);
                    return;
                }

                if (isChecked) {
                    if (freshTask.getDate() == null || freshTask.getDate().isEmpty() ||
                            freshTask.getTime() == null || freshTask.getTime().isEmpty()) {
                        Log.d("TaskAdapter", "Reminder enable requested, but date/time not set - showing dialog");
                        showDateTimeDialog(freshTask, taskHolder);
                        return;
                    }
                    freshTask.setReminderEnabled(1);
                    Log.d("TaskAdapter", "Setting alarm for task id " + freshTask.getId());
                    ReminderHelper.setAlarm(context, freshTask);
                } else {
                    Log.d("TaskAdapter", "Cancelling alarm for task id " + freshTask.getId());
                    ReminderHelper.cancelAlarm(context, freshTask);
                    freshTask.setReminderEnabled(0);
                }

                SQLiteDatabase database = db.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(Util.KEY_REMINDER_ENABLED, isChecked ? 1 : 0);
                int rowsUpdated = database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(freshTask.getId())});
                Log.d("TaskAdapter", "Database updated rows: " + rowsUpdated + " for task id " + freshTask.getId());
                database.close();
            });
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }

    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvNextExecutionTime, tvDescription;
        ImageView ivPriorityR, ivPriorityO, ivPriorityB;
        Switch switchReminder;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvNextExecutionTime = itemView.findViewById(R.id.tvDate);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            ivPriorityR = itemView.findViewById(R.id.ivPriorityR);
            ivPriorityO = itemView.findViewById(R.id.ivPriorityO);
            ivPriorityB = itemView.findViewById(R.id.ivPriorityB);
            switchReminder = itemView.findViewById(R.id.switchReminder);
        }
    }

    @Override
    public int getHeaderPositionForItem(int itemPosition) {
        for (int pos = itemPosition; pos >= 0; pos--) {
            if (getItemViewType(pos) == TYPE_HEADER) {
                return pos;
            }
        }
        return -1;
    }

    @Override
    public void bindHeaderData(View header, int headerPosition) {
        TextView tvCategory = header.findViewById(R.id.tvCategory);
        String category = (String) itemList.get(headerPosition);
        tvCategory.setText(category);
        Log.d("TaskAdapter", "Binding header at position " + headerPosition + ": [" + category + "]");
    }

    @Override
    public boolean isHeader(int itemPosition) {
        return getItemViewType(itemPosition) == TYPE_HEADER;
    }
}