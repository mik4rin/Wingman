package com.example.wingman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.wingman.data.Notification;
import com.example.wingman.data.NotificationRepository;
import com.example.wingman.data.OnFirestoreObjectListener;
import com.example.wingman.data.Task;
import com.example.wingman.data.TaskRepository;
import com.example.wingman.util.NotificationScheduler;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class DeadlineNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "DeadlineNotifReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent != null ? intent.getStringExtra("taskId") : null;
        if (taskId == null) {
            Log.w(TAG, "onReceive: missing taskId");
            return;
        }

        TaskRepository taskRepo = new TaskRepository();
        taskRepo.getTaskById(taskId, new OnFirestoreObjectListener<Task>() {
            @Override
            public void onSuccess(Task task) {
                if (task == null) return;

                if (task.isCompleted()) {
                    NotificationScheduler.cancelDeadline(context, task.getId());
                    return;
                }

                String title = "Task Due";
                String message = "Your task '" + (task.getTitle() == null ? "Untitled" : task.getTitle()) + "' is due now.";
                String type = "deadline";

                String userId = SessionManager.getUserId(context);

                try {
                    NotificationUtils.showAndLogTaskNotification(context, userId, task.getId(), type, title, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing/upserting notification via NotificationUtils", e);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error fetching task for deadline", e);
            }
        });
    }
}