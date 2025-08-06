package Adapter;

import static java.security.AccessController.getContext;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
                    // Открыть экран редактирования с активированными пикерами
                    Intent intent = new Intent(context, NewTask.class); // Пример для перехода
                    intent.putExtra("taskId", task.getId());  // Передаем ID задачи
                    context.startActivity(intent);
                })
                .setNegativeButton("Отменить", (dialog12, which) -> {
                    // Не делаем ничего, просто сбрасываем переключатель
                    taskHolder.switchReminder.setChecked(false);
                })
                .create();

        dialog.setOnShowListener(dialog1 -> {
            // Получаем цвета из ресурсов
            int positiveColor = ContextCompat.getColor(context, R.color.all_text);
            int negativeColor = ContextCompat.getColor(context, R.color.all_text);
            int messageColor = ContextCompat.getColor(context, R.color.title_text);

            // После показа диалога меняем цвет кнопок
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(positiveColor);  // "Добавить"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(negativeColor); // "Отменить"

            // Меняем цвет текста сообщения
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

            taskHolder.switchReminder.setChecked(task.getReminderEnabled() == 1);

            taskHolder.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isTaskOverdue(task.getDate(), task.getTime())) {
                    // Если задача просрочена, показываем сообщение и не разрешаем включить напоминание
                    Toast.makeText(context, "Напоминание для просроченной задачи невозможно!", Toast.LENGTH_SHORT).show();
                    taskHolder.switchReminder.setChecked(false); // Снимаем переключатель
                    return; // Не выполняем дальнейшие действия
                }

                if (isChecked) {
                    if (task.getDate() == null || task.getDate().isEmpty() ||
                            task.getTime() == null || task.getTime().isEmpty()) {
                        // Если дата/время не установлены, показываем диалог
                        showDateTimeDialog(task, taskHolder);
                        return;
                    }
                    // Устанавливаем напоминание
                    ReminderHelper.setAlarm(context, task);
                } else {
                    // Отменяем напоминание
                    ReminderHelper.cancelAlarm(context, task);
                }

                SQLiteDatabase database = db.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(Util.KEY_REMINDER_ENABLED, isChecked ? 1 : 0);
                database.update(Util.TABLE_NAME, values, Util.KEY_ID + " = ?", new String[]{String.valueOf(task.getId())});
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
