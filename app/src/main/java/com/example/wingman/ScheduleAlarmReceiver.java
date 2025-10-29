package com.example.wingman;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.wingman.data.ClassSched;
import com.example.wingman.data.NotificationRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScheduleAlarmReceiver extends BroadcastReceiver {
    private static MediaPlayer mp;

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String schedId = intent.getStringExtra("schedId");
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        String userId = intent.getStringExtra("userId");

        if (hour == -1 || minute == -1) {
            java.util.Calendar now = java.util.Calendar.getInstance();
            hour = now.get(java.util.Calendar.HOUR_OF_DAY);
            minute = now.get(java.util.Calendar.MINUTE);
        }

        int notificationId = schedId != null ? Math.abs(schedId.hashCode()) : (int) System.currentTimeMillis();

        String timeStr = String.format("%02d:%02d %s",
                (hour % 12 == 0 ? 12 : hour % 12),
                minute,
                (hour < 12 ? "AM" : "PM")
        );

        if (mp == null) {
            try {
                mp = MediaPlayer.create(context, R.raw.alarm_test);
            } catch (Exception ex) {
                mp = null;
            }
        }
        if (mp != null) {
            mp.setLooping(true);
            try { mp.start(); } catch (IllegalStateException ignore) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "SCHEDULE_ALARM",
                    "Schedule Alarms",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent stopIntent = new Intent(context, StopAlarmReceiver.class);
        stopIntent.putExtra("id", notificationId);
        PendingIntent stopPi = PendingIntent.getBroadcast(
                context,
                notificationId,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "SCHEDULE_ALARM")
                .setSmallIcon(R.drawable.wingman_logo)
                .setContentTitle("Schedule Reminder")
                .setContentText(title + " is starting now at " + timeStr + "!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "Turn Off", stopPi);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(notificationId, builder.build());

        try {
            String now = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
            com.example.wingman.data.Notification notif = new com.example.wingman.data.Notification(
                    userId,
                    schedId,
                    "alarm_fired",
                    "Schedule Reminder",
                    title + " is starting now at " + timeStr + "!",
                    now,
                    false
            );
            NotificationRepository nr = new NotificationRepository();
            nr.insert(notif, task -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Calendar nextWeek = Calendar.getInstance();
            nextWeek.add(Calendar.WEEK_OF_YEAR, 1);

            ClassSched sched = new ClassSched();
            sched.setId(schedId);
            sched.setTitle(title);
            sched.setStartHour(hour);
            sched.setStartMinute(minute);
            sched.setDayIndex(nextWeek.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY);

            ScheduleUtils.scheduleAlarm(context, sched, true);
            Log.d("ScheduleAlarmReceiver", "Rescheduled " + title + " for next week");
        } catch (Exception e) {
            Log.e("ScheduleAlarmReceiver", "Failed to reschedule next week's alarm", e);
        }
    }

    public static void stopAlarm() {
        if (mp != null && mp.isPlaying()) {
            mp.stop();
            mp.release();
            mp = null;
        }
    }
}