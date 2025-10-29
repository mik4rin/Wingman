package com.example.wingman.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.wingman.DeadlineNotificationReceiver;
import com.example.wingman.data.Task;

public class NotificationScheduler {

    private static final String TAG = "NotificationScheduler";
    private static final String EXTRA_TASK_ID = "taskId";

    private static int requestCodeForTask(String taskId) {
        return taskId == null ? 0 : Math.abs(taskId.hashCode() % 1000000);
    }

    public static void scheduleDeadlineAlarm(Context context, Task task) {
        if (context == null || task == null) return;
        long when = task.getDeadline();
        if (when <= 0 || task.isCompleted()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not allowed. Consider requesting SCHEDULE_EXACT_ALARM permission.");
                return;
            }
        }

        Intent intent = new Intent(context, DeadlineNotificationReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.getId());

        int req = requestCodeForTask(task.getId());
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
            Log.d(TAG, "Scheduled exact alarm for task " + task.getId());
        } catch (SecurityException se) {
            Log.e(TAG, "Cannot schedule exact alarm, missing permission", se);
        }
    }

    public static void cancelDeadline(Context context, String taskId) {
        if (context == null || taskId == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, DeadlineNotificationReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, taskId);

        int req = requestCodeForTask(taskId);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
            Log.d(TAG, "Cancelled alarm for task " + taskId);
        }
    }
}