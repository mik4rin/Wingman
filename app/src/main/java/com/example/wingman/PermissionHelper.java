package com.example.wingman;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class PermissionHelper {

    public static final int REQUEST_READ_MEDIA = 100;
    public static final int REQUEST_EXACT_ALARM = 200;

    public static void checkAndRequestStoragePermission(Fragment fragment, Runnable onGranted) {
        Context ctx = fragment.requireContext();

        if (hasStoragePermission(ctx)) {
            if (onGranted != null) onGranted.run();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fragment.requestPermissions(
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    REQUEST_READ_MEDIA
            );
        } else {
            fragment.requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_MEDIA
            );
        }
    }

    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void handleStoragePermissionResult(
            int requestCode,
            @NonNull int[] grantResults,
            Runnable onGranted,
            Runnable onDenied
    ) {
        if (requestCode != REQUEST_READ_MEDIA) return;

        boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (granted) {
            if (onGranted != null) onGranted.run();
        } else {
            if (onDenied != null) onDenied.run();
        }
    }

    public static void checkExactAlarmPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            boolean exactAllowed = (am != null && am.canScheduleExactAlarms());

            SharedPreferences prefs = activity.getSharedPreferences("alarmPrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("exactAlarmAllowed", exactAllowed).apply();

            if (!exactAllowed) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    activity.startActivityForResult(intent, REQUEST_EXACT_ALARM);
                } catch (Exception e) {
                    Toast.makeText(activity, "Unable to open system permission screen.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            SharedPreferences prefs = activity.getSharedPreferences("alarmPrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("exactAlarmAllowed", true).apply();
        }
    }

    public static void handleExactAlarmResult(Activity activity, int requestCode) {
        if (requestCode != REQUEST_EXACT_ALARM) return;

        boolean exactAllowed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            exactAllowed = (am != null && am.canScheduleExactAlarms());
        } else {
            exactAllowed = true;
        }

        SharedPreferences prefs = activity.getSharedPreferences("alarmPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("exactAlarmAllowed", exactAllowed).apply();

        if (!exactAllowed) {
            Toast.makeText(activity, "Alarm permission denied. Some reminders may not work.", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean canUseAlarm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("alarmPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("exactAlarmAllowed", false);
    }
}