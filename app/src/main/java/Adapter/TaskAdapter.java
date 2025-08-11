package Adapter;

import static java.security.AccessController.getContext;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mindtick.NewTask;
import com.example.mindtick.R;
import com.example.mindtick.TaskBottomSheetDialogFragment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import Data.DatabaseHandler;
import Model.Task;
import Utils.ReminderHelper;
import Utils.Util;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Fragment parentFragment;
    private Context context;
    private List<Object> itemList; // Мы работаем с List<Object>, чтобы поддерживать заголовки и задачи
    private boolean isTodayScreen;
    private boolean isCompletedScreen;
    private boolean isAllTasksScreen;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK = 1;
    private DatabaseHandler db;  // Объявляем переменную для работы с базой данных

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

    @Override
    public int getItemViewType(int position) {
        if (itemList.get(position) instanceof String) {
            return TYPE_HEADER; // Заголовок (категория)
        } else {
            return TYPE_TASK; // Обычная задача
        }
    }

    private void showDateTimeDialog(Task task, TaskViewHolder taskHolder) {
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.CustomDialog)
                .setMessage("Чтобы включить напоминание, нужно установить дату и время. Добавить их сейчас?")
                .setPositiveButton("Добавить", (dialog1, which) -> {
                    // Открываем BottomSheet редактирования задачи с флагом для автозапуска пикеров
                    TaskBottomSheetDialogFragment bottomSheet = TaskBottomSheetDialogFragment.newInstance(task);

                    Bundle args = bottomSheet.getArguments();
                    if (args == null) args = new Bundle();
                    args.putBoolean("openDateTimePicker", true); // наш флаг
                    bottomSheet.setArguments(args);

                    bottomSheet.setOnTaskUpdatedListener(() -> {
                        if (onTaskUpdatedListener != null) {
                            onTaskUpdatedListener.onTaskUpdated();
                        }
                    });

                    // Открываем фрагмент
                    FragmentManager fragmentManager = parentFragment.getParentFragmentManager();
                    bottomSheet.show(fragmentManager, bottomSheet.getTag());
                })
                .setNegativeButton("Отменить", (dialog12, which) -> {
                    // Сбрасываем переключатель
                    taskHolder.switchReminder.setChecked(false);
                })
                .create();

        dialog.setOnShowListener(dialog1 -> {
            // Цвета
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
            // Для категорий
            View view = inflater.inflate(R.layout.item_category, parent, false);
            return new HeaderViewHolder(view);
        } else {
            if (isCompletedScreen) {
                // Для экрана "Выполненные" используем другой XML
                View view = inflater.inflate(R.layout.item_task_completed, parent, false);
                return new TaskViewHolder(view);
            } else {
                // Для экранов "Сегодня" и "Все задачи" — стандартный XML
                View view = inflater.inflate(R.layout.item_task, parent, false);
                return new TaskViewHolder(view);
            }
        }
    }

    // ✅ ВОТ СЮДА добавляем метод для проверки просроченности
    private boolean isTaskOverdue(String date, String time) {
        if (date == null || date.isEmpty() || time == null || time.isEmpty()) {
            return false; // Если даты/времени нет – не просрочена
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        try {
            Date taskDateTime = sdf.parse(date + " " + time);
            return taskDateTime != null && taskDateTime.before(new Date()); // Если в прошлом – просрочена
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            String category = (String) itemList.get(position);
            ((HeaderViewHolder) holder).tvCategory.setText(category);
        } else {
            Task task = (Task) itemList.get(position);

            holder.itemView.setOnClickListener(v -> {
                TaskBottomSheetDialogFragment bottomSheet = TaskBottomSheetDialogFragment.newInstance(task);

                bottomSheet.setOnTaskUpdatedListener(() -> {
                    if (onTaskUpdatedListener != null) {
                        onTaskUpdatedListener.onTaskUpdated();
                    }
                });


                FragmentManager fragmentManager = parentFragment.getParentFragmentManager();
                bottomSheet.show(fragmentManager, bottomSheet.getTag());
            });


            TaskViewHolder taskHolder = (TaskViewHolder) holder;
            taskHolder.tvTitle.setText(task.getTitle());

            // Проверка на пустое описание
            taskHolder.tvDescription.setText(task.getDescription().isEmpty() ? "Описание не установлено" : task.getDescription());

            // Обновление цвета текста для просроченной задачи
            if (!isCompletedScreen) { // Только для экранов "Сегодня" и "Все задачи"
                if (isTaskOverdue(task.getDate(), task.getTime())) {
                    taskHolder.tvNextExecutionTime.setTextColor(ContextCompat.getColor(context, R.color.red));

                    // Отключение напоминания для просроченных задач
                    if (task.getReminderEnabled() == 1) {
                        task.setReminderEnabled(0); // Обновляем модель
                        taskHolder.switchReminder.setChecked(false); // Обновляем UI

                        // Обновляем в БД
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
                // Экран "Выполнено"
                if (task.getCompletedAt() != null && !task.getCompletedAt().isEmpty()) {
                    taskHolder.tvNextExecutionTime.setText("Завершено в: " + task.getCompletedAt());
                } else {
                    taskHolder.tvNextExecutionTime.setText("Завершено в: " + task.getCompletedAt());
                }
            } else if (isTodayScreen) {
                // Экран "Сегодня"
                if (task.getTime() != null && !task.getTime().isEmpty()) {
                    taskHolder.tvNextExecutionTime.setText(task.getTime());
                } else {
                    taskHolder.tvNextExecutionTime.setText("Время не установлено");
                }
            } else if (isAllTasksScreen) {
                // Экран "Все задачи"
                if (task.getDate() != null && !task.getDate().isEmpty()) {
                    if (task.getTime() != null && !task.getTime().isEmpty()) {
                        taskHolder.tvNextExecutionTime.setText(task.getTime() + "  " + task.getDate());
                    } else {
                        taskHolder.tvNextExecutionTime.setText(task.getDate());
                    }
                } else {
                    taskHolder.tvNextExecutionTime.setText("Дата не установлена");
                }
            } else {
                // На случай других экранов
                taskHolder.tvNextExecutionTime.setText("");
            }

            taskHolder.ivPriorityR.setVisibility(View.GONE);
            taskHolder.ivPriorityO.setVisibility(View.GONE);
            taskHolder.ivPriorityB.setVisibility(View.GONE);

            // Скрытие/отображение приоритетов для экрана "Выполненные"
            if (isCompletedScreen) {
                taskHolder.ivPriorityR.setVisibility(View.GONE);
                taskHolder.ivPriorityO.setVisibility(View.GONE);
                taskHolder.ivPriorityB.setVisibility(View.GONE);
            } else {
                int priority = task.getPriority();
                if (priority == 1) {
                    taskHolder.ivPriorityR.setVisibility(View.VISIBLE);
                } else if (priority == 2) {
                    taskHolder.ivPriorityO.setVisibility(View.VISIBLE);
                } else {
                    taskHolder.ivPriorityB.setVisibility(View.VISIBLE);
                }
            }

            taskHolder.switchReminder.setOnCheckedChangeListener(null); // Отключаем слушатель временно
            taskHolder.switchReminder.setChecked(task.getReminderEnabled() == 1); // Устанавливаем текущее значение

            taskHolder.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Получить свежую задачу из базы
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

                // Проверка на просрочку
                if (isTaskOverdue(freshTask.getDate(), freshTask.getTime())) {
                    Toast.makeText(context, "Напоминание для просроченной задачи невозможно!", Toast.LENGTH_SHORT).show();
                    Log.d("TaskAdapter", "Cannot enable reminder - task is overdue");
                    taskHolder.switchReminder.setChecked(false);
                    return;
                }

                if (isChecked) {
                    // Если нет даты или времени — показываем диалог
                    if (freshTask.getDate() == null || freshTask.getDate().isEmpty() ||
                            freshTask.getTime() == null || freshTask.getTime().isEmpty()) {
                        Log.d("TaskAdapter", "Reminder enable requested, but date/time not set - showing dialog");
                        showDateTimeDialog(freshTask, taskHolder);
                        return;
                    }

                    // Обновляем флаг
                    freshTask.setReminderEnabled(1);

                    // Ставим будильник
                    Log.d("TaskAdapter", "Setting alarm for task id " + freshTask.getId());
                    ReminderHelper.setAlarm(context, freshTask);
                } else {
                    // Отключаем напоминание
                    Log.d("TaskAdapter", "Cancelling alarm for task id " + freshTask.getId());
                    ReminderHelper.cancelAlarm(context, freshTask);
                    freshTask.setReminderEnabled(0);
                }

                // Сохраняем изменения в базе
                SQLiteDatabase database = db.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(Util.KEY_REMINDER_ENABLED, isChecked ? 1 : 0);
                int rowsUpdated = database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(freshTask.getId())});
                Log.d("TaskAdapter", "Database updated rows: " + rowsUpdated + " for task id " + freshTask.getId());
                database.close();
            });


        }
    }

    // Метод для обновления данных

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    // ViewHolder для заголовка (категории)
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }

    // ViewHolder для задачи
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
}
