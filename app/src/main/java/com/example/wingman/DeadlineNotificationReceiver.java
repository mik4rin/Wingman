package com.example.wingman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.wingman.util.NotificationScheduler;

public class DeadlineNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "DeadlineNotifReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent");
            return;
        }

        try {
            String taskId = intent.getStringExtra("taskId");
            String taskTitle = intent.getStringExtra("taskTitle");
            String notificationType = intent.getStringExtra("notificationType");
            String userId = intent.getStringExtra("userId");

            if (taskId == null) {
                Log.w(TAG, "Task ID is null, cannot show notification");
                return;
            }

            // Use default values if not provided
            if (taskTitle == null) taskTitle = "Task";
            if (notificationType == null) notificationType = NotificationScheduler.TYPE_AT_DEADLINE;

            Log.d(TAG, "Deadline alarm triggered - Task: " + taskTitle + ", Type: " + notificationType);

            // Generate notification title and message
            String notifTitle = NotificationScheduler.getNotificationTitle(notificationType);
            String notifMessage = NotificationScheduler.getNotificationMessage(notificationType, taskTitle);

            // Send notification using NotificationCenter
            NotificationCenter.notifyAndLog(
                    context,
                    userId,
                    taskId,
                    notificationType,
                    notifTitle,
                    notifMessage,
                    "tasks"
            );

            Log.d(TAG, "Successfully sent notification for task: " + taskTitle);

        } catch (Exception e) {
            Log.e(TAG, "Error processing deadline notification", e);
        }
    }
}