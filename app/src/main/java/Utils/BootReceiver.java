package Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

import Data.DatabaseHandler;
import Model.Task;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            DatabaseHandler db = new DatabaseHandler(context);
            List<Task> tasks = db.getAllTasks();

            for (Task task : tasks) {
                if (task.getReminderEnabled() == 1) {
                    ReminderHelper.setAlarm(context, task);
                }
            }
        }
    }
}
