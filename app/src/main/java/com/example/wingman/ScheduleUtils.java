package com.example.wingman;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.wingman.data.ClassSched;

import java.util.Calendar;

public class ScheduleUtils {
    public static void scheduleAlarm(Context context, ClassSched sched, boolean exactAlarmAllowed) {
        if (context == null || sched == null || sched.getId() == null) return;
        context = context.getApplicationContext();

        try {
            cancelAlarm(context, sched);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, sched.getStartHour());
            cal.set(Calendar.MINUTE, sched.getStartMinute());
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            int dayIndex = Math.max(0, Math.min(4, sched.getDayIndex()));
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY + dayIndex);

            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            }

            PendingIntent pendingIntent = createPendingIntent(context, sched);

            boolean canScheduleExact = exactAlarmAllowed &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            alarmManager.canScheduleExactAlarms());

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            pendingIntent
                    );
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            pendingIntent
                    );
                }
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(),
                        pendingIntent
                );
            }

            Log.d("ScheduleUtils", "Alarm scheduled for " + sched.getTitle() + " at " + cal.getTime());

        } catch (SecurityException se) {
            Log.e("ScheduleUtils", "Exact alarm not allowed: " + se.getMessage());
        } catch (Exception e) {
            Log.e("ScheduleUtils", "Failed to schedule alarm: " + e.getMessage());
        }
    }

    public static void cancelAlarm(Context context, ClassSched sched) {
        if (context == null || sched == null || sched.getId() == null) return;
        context = context.getApplicationContext();

        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            PendingIntent pendingIntent = createPendingIntent(context, sched);
            alarmManager.cancel(pendingIntent);
            Log.d("ScheduleUtils", "Alarm canceled for " + sched.getTitle());
        } catch (Exception e) {
            Log.e("ScheduleUtils", "Failed to cancel alarm: " + e.getMessage());
        }
    }

    private static PendingIntent createPendingIntent(Context context, ClassSched sched) {
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.putExtra("title", sched.getTitle());
        intent.putExtra("schedId", sched.getId());
        intent.putExtra("hour", sched.getStartHour());
        intent.putExtra("minute", sched.getStartMinute());
        intent.putExtra("userId", sched.getUserId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getBroadcast(
                context,
                Math.abs(sched.getId().hashCode()),
                intent,
                flags
        );
    }
}