package Model;

import java.io.Serializable;

public class Task implements Serializable {
    // создаем поля
    private long id;
    private String title;
    private String category;
    private String date;
    private String time;
    private int status;
    private String description;
    private int priority; // 1 - Высокий, 2 - Средний, 3 - Низкий
    private int reminderEnabled; // 1 - включено, 0 - выключено
    private boolean switchChecked; // Состояние переключателя для UI
    private String completedAt;
    private int previousReminderEnabled;

    public Task() {
    }

    public Task(long id, String title, String category,
                String date, String time , int status,
                String description, int priority,
                int reminderEnabled, Boolean switchChecked,
                String completedAt, int previousReminderEnabled) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.date = date;
        this.time = time;
        this.status = status;
        this.description = description;
        this.priority = priority;
        this.reminderEnabled = reminderEnabled;
        this.switchChecked = switchChecked;
        this.completedAt = completedAt;
        this.previousReminderEnabled = previousReminderEnabled;
    }

    public Task(String title, String category, String date, String time, int status, String description, int priority, int reminderEnabled, Boolean switchChecked, String completedAt, int previousReminderEnabled) {
        this.title = title;
        this.category = category;
        this.date = date;
        this.time = time;
        this.status = status;
        this.description = description;
        this.priority = priority;
        this.reminderEnabled = reminderEnabled;
        this.switchChecked = switchChecked;
        this.completedAt = completedAt;
        this.previousReminderEnabled = previousReminderEnabled;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(int reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isSwitchChecked() {
        return switchChecked;
    }

    public void setSwitchChecked(boolean switchChecked) {
        this.switchChecked = switchChecked;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public int getPreviousReminderEnabled() {
        return previousReminderEnabled;
    }

    public void setPreviousReminderEnabled(int previousReminderEnabled) {
        this.previousReminderEnabled = previousReminderEnabled;
    }
}
