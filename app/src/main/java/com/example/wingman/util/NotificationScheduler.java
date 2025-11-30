package com.example.wingman.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.wingman.DeadlineNotificationReceiver;
import com.example.wingman.data.Task;

import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    private static final String TAG = "NotificationScheduler";
    private static final String EXTRA_TASK_ID = "taskId";
    private static final String EXTRA_TASK_TITLE = "taskTitle";
    private static final String EXTRA_NOTIFICATION_TYPE = "notificationType";
    private static final String EXTRA_USER_ID = "userId";

    // Threshold-based notification intervals (in milliseconds)
    private static final long THRESHOLD_24_HOURS = TimeUnit.HOURS.toMillis(24);
    private static final long THRESHOLD_12_HOURS = TimeUnit.HOURS.toMillis(12);
    private static final long THRESHOLD_6_HOURS = TimeUnit.HOURS.toMillis(6);
    private static final long THRESHOLD_1_HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long THRESHOLD_15_MIN = TimeUnit.MINUTES.toMillis(15);

    // Notification type constants
    public static final String TYPE_24H = "24_hours_before";
    public static final String TYPE_12H = "12_hours_before";
    public static final String TYPE_6H = "6_hours_before";
    public static final String TYPE_1H = "1_hour_before";
    public static final String TYPE_15M = "15_min_before";
    public static final String TYPE_AT_DEADLINE = "at_deadline";

    /**
     * Generate unique request code for each task + notification type combination
     */
    private static int requestCodeForTask(String taskId, String notificationType) {
        String combined = taskId + "_" + (notificationType != null ? notificationType : "default");
        return Math.abs(combined.hashCode() % 1000000);
    }

    /**
     * Schedule all threshold-based deadline alarms for a task
     * This implements the threshold-based triggering algorithm
     */
    public static void scheduleDeadlineAlarm(Context context, Task task) {
        if (context == null || task == null) return;

        long deadline = task.getDeadline();
        if (deadline <= 0 || task.isCompleted()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Check exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not allowed. Consider requesting SCHEDULE_EXACT_ALARM permission.");
                return;
            }
        }

        // Cancel any existing alarms for this task
        cancelDeadline(context, task.getId());

        long now = System.currentTimeMillis();

        // Schedule threshold-based notifications
        scheduleThresholdNotification(context, task, am, now, deadline, THRESHOLD_24_HOURS, TYPE_24H);
        scheduleThresholdNotification(context, task, am, now, deadline, THRESHOLD_12_HOURS, TYPE_12H);
        scheduleThresholdNotification(context, task, am, now, deadline, THRESHOLD_6_HOURS, TYPE_6H);
        scheduleThresholdNotification(context, task, am, now, deadline, THRESHOLD_1_HOUR, TYPE_1H);
        scheduleThresholdNotification(context, task, am, now, deadline, THRESHOLD_15_MIN, TYPE_15M);

        // Schedule notification at exact deadline time
        scheduleThresholdNotification(context, task, am, now, deadline, 0, TYPE_AT_DEADLINE);

        Log.d(TAG, "Scheduled all threshold-based alarms for task: " + task.getTitle());
    }

    /**
     * Schedule a single threshold-based notification
     */
    private static void scheduleThresholdNotification(Context context, Task task,
                                                      AlarmManager am, long now,
                                                      long deadline, long thresholdMillis,
                                                      String notificationType) {
        long triggerTime = deadline - thresholdMillis;

        // Only schedule if trigger time is in the future
        if (triggerTime <= now) {
            Log.d(TAG, "Skipping " + notificationType + " notification (time has passed)");
            return;
        }

        Intent intent = new Intent(context, DeadlineNotificationReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.getId());
        intent.putExtra(EXTRA_TASK_TITLE, task.getTitle());
        intent.putExtra(EXTRA_NOTIFICATION_TYPE, notificationType);
        intent.putExtra(EXTRA_USER_ID, task.getUserId());

        int requestCode = requestCodeForTask(task.getId(), notificationType);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
            Log.d(TAG, "Scheduled " + notificationType + " alarm for task " + task.getId() +
                    " at " + new java.util.Date(triggerTime));
        } catch (SecurityException se) {
            Log.e(TAG, "Cannot schedule exact alarm for " + notificationType, se);
        }
    }

    /**
     * Cancel all deadline alarms for a task (all thresholds)
     */
    public static void cancelDeadline(Context context, String taskId) {
        if (context == null || taskId == null) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Cancel all threshold-based alarms
        String[] allTypes = {TYPE_24H, TYPE_12H, TYPE_6H, TYPE_1H, TYPE_15M, TYPE_AT_DEADLINE};

        for (String type : allTypes) {
            Intent intent = new Intent(context, DeadlineNotificationReceiver.class);
            intent.putExtra(EXTRA_TASK_ID, taskId);
            intent.putExtra(EXTRA_NOTIFICATION_TYPE, type);

            int requestCode = requestCodeForTask(taskId, type);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );

            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
                Log.d(TAG, "Cancelled " + type + " alarm for task " + taskId);
            }
        }
    }

    /**
     * Get user-friendly notification message based on type
     */
    public static String getNotificationMessage(String notificationType, String taskTitle) {
        if (taskTitle == null) taskTitle = "Your task";

        switch (notificationType) {
            case TYPE_24H:
                return "Task \"" + taskTitle + "\" is due in 24 hours!";
            case TYPE_12H:
                return "Task \"" + taskTitle + "\" is due in 12 hours!";
            case TYPE_6H:
                return "Task \"" + taskTitle + "\" is due in 6 hours!";
            case TYPE_1H:
                return "Task \"" + taskTitle + "\" is due in 1 hour!";
            case TYPE_15M:
                return "Task \"" + taskTitle + "\" is due in 15 minutes!";
            case TYPE_AT_DEADLINE:
                return "Task \"" + taskTitle + "\" is due now!";
            default:
                return "Task \"" + taskTitle + "\" deadline is approaching!";
        }
    }

    /**
     * Get notification title based on type
     */
    public static String getNotificationTitle(String notificationType) {
        switch (notificationType) {
            case TYPE_AT_DEADLINE:
                return "Task Deadline!";
            case TYPE_15M:
                return "Urgent: Task Due Soon!";
            default:
                return "Task Reminder";
        }
    }
}