package com.example.wingman;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.wingman.data.Notification;
import com.example.wingman.data.NotificationRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NotificationUtils {
    private static final String TAG = "NotificationUtils";

    public static final String CHANNEL_ID = "timer_channel";
    public static final String CHANNEL_NAME = "Timer Notifications";
    public static final String TASK_CHANNEL_ID = "task_channel";
    public static final String TASK_CHANNEL_NAME = "Task Notifications";

    public static final String NOTIFICATION_TYPE_1_HOUR = "1_hour_before";
    public static final String NOTIFICATION_TYPE_15_MIN = "15_min_before";
    public static final String NOTIFICATION_TYPE_OVERDUE = "overdue";
    public static final String NOTIFICATION_TYPE_TIMER = "timer_finished";
    public static final String NOTIFICATION_TYPE_ALARM = "alarm_fired";

    public static final String EXTRA_NAVIGATE_TO = "navigate_to";
    public static final String EXTRA_ITEM_ID = "item_id";
    public static final String EXTRA_NOTIFICATION_TYPE = "notification_type";
    public static final String EXTRA_NOTIF_ID = "notif_id";

    private static void createTimerNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for timer events");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static void createTaskNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    TASK_CHANNEL_ID,
                    TASK_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for task reminders and schedules");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static void upsertNotificationToFirestore(final Context context,
                                                      final String userId,
                                                      final String taskId,
                                                      final String type,
                                                      final Notification notif,
                                                      final UpsertCallback callback) {

        if (notif.getTimestamp() <= 0L) notif.setTimestamp(System.currentTimeMillis());
        if (notif.getTime() == null || notif.getTime().trim().isEmpty()) {
            notif.setTime(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(notif.getTimestamp())));
        }

        final String resolvedUser = userId != null ? userId : SessionManager.getUserId(context);
        if (resolvedUser == null) {
            new NotificationRepository().insert(notif, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Inserted notification (no userId)");
                    if (callback != null) callback.onResult(null);
                } else {
                    Log.e(TAG, "Insert failed (no userId)", task.getException());
                    if (callback != null) callback.onResult(null);
                }
            });
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (taskId == null || taskId.trim().isEmpty()) {
            notif.setUserId(resolvedUser);
            db.collection("notifications")
                    .add(notif)
                    .addOnSuccessListener(docRef -> {
                        Log.d(TAG, "Added notification doc: " + docRef.getId());
                        if (callback != null) callback.onResult(docRef.getId());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed adding notification doc", e);
                        if (callback != null) callback.onResult(null);
                    });
            return;
        }

        db.collection("notifications")
                .whereEqualTo("userId", resolvedUser)
                .whereEqualTo("taskId", taskId)
                .whereEqualTo("type", type)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot existing = querySnapshot.getDocuments().get(0);
                        String docId = existing.getId();

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("title", notif.getTitle());
                        updates.put("message", notif.getMessage());
                        updates.put("time", notif.getTime());
                        updates.put("timestamp", notif.getTimestamp());
                        updates.put("isRead", notif.isRead());

                        existing.getReference().update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Updated existing notification doc: " + docId);
                                    if (callback != null) callback.onResult(docId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed updating existing notification doc", e);
                                    notif.setUserId(resolvedUser);
                                    db.collection("notifications").document(docId)
                                            .set(notif)
                                            .addOnSuccessListener(unused -> {
                                                Log.d(TAG, "Set existing doc after failure: " + docId);
                                                if (callback != null) callback.onResult(docId);
                                            })
                                            .addOnFailureListener(e2 -> {
                                                Log.e(TAG, "Fallback set failed", e2);
                                                if (callback != null) callback.onResult(null);
                                            });
                                });
                    } else {
                        notif.setUserId(resolvedUser);
                        db.collection("notifications")
                                .add(notif)
                                .addOnSuccessListener(docRef -> {
                                    Log.d(TAG, "Added new notification doc: " + docRef.getId());
                                    if (callback != null) callback.onResult(docRef.getId());
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed adding new notification doc", e);
                                    if (callback != null) callback.onResult(null);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query failed when attempting upsert; falling back to insert", e);
                    notif.setUserId(resolvedUser);
                    db.collection("notifications")
                            .add(notif)
                            .addOnSuccessListener(docRef -> {
                                Log.d(TAG, "Fallback added new notification doc: " + docRef.getId());
                                if (callback != null) callback.onResult(docRef.getId());
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Fallback insert failed", e2);
                                if (callback != null) callback.onResult(null);
                            });
                });
    }

    private interface UpsertCallback {
        void onResult(String docId);
    }

    private static Intent baseMainIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private static void showSystemNotification(final Context context,
                                               final String channelId,
                                               final String title,
                                               final String message,
                                               final String navigateTo,
                                               final String itemId,
                                               final String notifDocId,
                                               final String notifType,
                                               final int notificationId,
                                               final int requestCode) {
        try {
            if (CHANNEL_ID.equals(channelId)) createTimerNotificationChannel(context);
            else createTaskNotificationChannel(context);

            Intent intent = baseMainIntent(context);
            if (navigateTo != null) intent.putExtra(EXTRA_NAVIGATE_TO, navigateTo);
            if (itemId != null) intent.putExtra(EXTRA_ITEM_ID, itemId);
            if (notifType != null) intent.putExtra(EXTRA_NOTIFICATION_TYPE, notifType);
            if (notifDocId != null) intent.putExtra(EXTRA_NOTIF_ID, notifDocId);

            PendingIntent pendingIntent = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(intent)
                    .getPendingIntent(
                            requestCode,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.wingman_logo)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManager nm = (NotificationManager) context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(notificationId, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "showSystemNotification failed", e);
        }
    }

    public static void showTimerFinishedNotification(Context context) {
        createTimerNotificationChannel(context);
        final String type = NOTIFICATION_TYPE_TIMER;
        String userId = SessionManager.getUserId(context);
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        Notification inApp = new Notification(userId, null, type, "Time's Up!", "Your timer has finished.", formattedTime, false);
        inApp.setTimestamp(System.currentTimeMillis());

        upsertNotificationToFirestore(context, userId, null, type, inApp, docId -> {
            Intent intent = baseMainIntent(context);
            intent.putExtra(EXTRA_NAVIGATE_TO, "timer");
            intent.putExtra(EXTRA_NOTIFICATION_TYPE, type);
            if (docId != null) intent.putExtra(EXTRA_NOTIF_ID, docId);

            int notificationId = deterministicNotificationId(userId, null, type);
            int requestCode = notificationId;

            showSystemNotification(context, CHANNEL_ID, "Time's Up!", "Your timer has finished.", "timer", null, docId, type, notificationId, requestCode);
        });
    }

    public static void notifyNearlyDue(final Context context, final String taskTitle, final String userId, final String taskId) {
        createTaskNotificationChannel(context);

        final String type = NOTIFICATION_TYPE_1_HOUR;
        final String title = "Task Due Soon";
        final String message = "Task \"" + taskTitle + "\" is due in less than 1 hour!";
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        final Notification inApp = new Notification(userId, taskId, type, title, message, formattedTime, false);
        inApp.setTimestamp(System.currentTimeMillis());

        upsertNotificationToFirestore(context, userId, taskId, type, inApp, docId -> {
            String navigateTo = "task";
            String notifType = type;
            int notificationId = deterministicNotificationId(userId, taskId, type);
            int requestCode = notificationId;

            showSystemNotification(context, TASK_CHANNEL_ID, title, message, navigateTo, taskId, docId, notifType, notificationId, requestCode);
        });
    }

    public static void showTaskNotification(final Context context, final String title, final String message, final String taskId, final String type) {
        createTaskNotificationChannel(context);

        final String resolvedType = type == null ? "generic_task" : type;
        final String user = SessionManager.getUserId(context);
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        final Notification inApp = new Notification(user, taskId, resolvedType, title, message, formattedTime, false);
        inApp.setTimestamp(System.currentTimeMillis());

        upsertNotificationToFirestore(context, user, taskId, resolvedType, inApp, docId -> {
            String navigateTo = "task";
            String notifType = resolvedType;
            int notificationId = taskId != null ? deterministicNotificationId(user, taskId, notifType)
                    : deterministicNotificationIdForContent(title, message, notifType);
            int requestCode = notificationId;

            showSystemNotification(context, TASK_CHANNEL_ID, title, message, navigateTo, taskId, docId, notifType, notificationId, requestCode);
        });
    }

    public static void showScheduleNotification(final Context context, final String title, final String message, final String schedId, final String userId) {
        createTaskNotificationChannel(context);

        final String type = NOTIFICATION_TYPE_ALARM;
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        final Notification inApp = new Notification(userId, schedId, type, title, message, formattedTime, false);
        inApp.setTimestamp(System.currentTimeMillis());

        upsertNotificationToFirestore(context, userId, schedId, type, inApp, docId -> {
            String navigateTo = "schedule";
            String notifType = "schedule_reminder";
            int notificationId = schedId != null ? deterministicNotificationId(userId, schedId, type) : deterministicNotificationIdForContent(title, message, type);
            int requestCode = notificationId;

            showSystemNotification(context, TASK_CHANNEL_ID, title, message, navigateTo, schedId, docId, notifType, notificationId, requestCode);
        });
    }

    public static void showAndLogTaskNotification(Context context, String userId, String taskId, String type, String title, String message) {
        showTaskNotification(context, title, message, taskId, type);
    }

    public static void showAndLogTimerNotification(Context context, String userId) {
        showTimerFinishedNotification(context);
    }

    private static int deterministicNotificationId(String userId, String itemId, String type) {
        String key = (userId == null ? "anon" : userId) + "|" + (itemId == null ? "null" : itemId) + "|" + (type == null ? "generic" : type);
        return Math.abs(key.hashCode());
    }

    private static int deterministicNotificationIdForContent(String title, String message, String type) {
        String key = (title == null ? "" : title) + "|" + (message == null ? "" : message) + "|" + (type == null ? "generic" : type);
        return Math.abs(key.hashCode());
    }
}