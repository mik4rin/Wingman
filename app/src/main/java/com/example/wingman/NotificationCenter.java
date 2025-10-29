package com.example.wingman;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationCenter {
    private static final String TAG = "NotificationCenter";
    public static void notifyAndLog(Context context,
                                    String userId,
                                    String itemId,
                                    String type,
                                    String title,
                                    String message,
                                    String navigateTo) {

        if (context == null) {
            Log.w(TAG, "notifyAndLog aborted: null context");
            return;
        }

        final String resolvedUser = !TextUtils.isEmpty(userId) ? userId : SessionManager.getUserId(context);
        final String safeType = TextUtils.isEmpty(type) ? "generic" : type;
        final String safeTitle = TextUtils.isEmpty(title) ? "Wingman" : title;
        final String safeMessage = TextUtils.isEmpty(message) ? "" : message;
        final String safeNav = TextUtils.isEmpty(navigateTo) ? inferDestinationFromType(safeType) : navigateTo;

        long now = System.currentTimeMillis();
        String formattedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(now));

        com.example.wingman.data.Notification notif = new com.example.wingman.data.Notification(resolvedUser, itemId, safeType, safeTitle, safeMessage, formattedTime, false);
        notif.setTimestamp(now);

        try {
            if ("timer".equalsIgnoreCase(safeNav) || NotificationUtils.NOTIFICATION_TYPE_TIMER.equalsIgnoreCase(safeType)) {
                NotificationUtils.showAndLogTimerNotification(context, resolvedUser);
                return;
            }

            if ("task".equalsIgnoreCase(safeNav) || safeType.toLowerCase(Locale.ROOT).contains("task")) {
                NotificationUtils.showAndLogTaskNotification(context, resolvedUser, itemId, safeType, safeTitle, safeMessage);
                return;
            }

            if ("schedule".equalsIgnoreCase(safeNav) || safeType.toLowerCase(Locale.ROOT).contains("schedule")) {
                NotificationUtils.showScheduleNotification(context, safeTitle, safeMessage, itemId, resolvedUser);
                return;
            }

            NotificationUtils.showAndLogTaskNotification(context, resolvedUser, itemId, safeType, safeTitle, safeMessage);

        } catch (Exception e) {
            Log.e(TAG, "Failed to show/log notification via NotificationUtils", e);
        }
    }

    private static String inferDestinationFromType(String type) {
        if (TextUtils.isEmpty(type)) return "home";
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("task") || lower.contains("due")) return "task";
        if (lower.contains("schedule") || lower.contains("alarm")) return "schedule";
        if (lower.contains("timer")) return "timer";
        return "home";
    }
}