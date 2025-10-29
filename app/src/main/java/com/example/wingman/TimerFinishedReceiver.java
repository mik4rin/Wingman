package com.example.wingman;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;

public class TimerFinishedReceiver extends BroadcastReceiver {

    private static final String TAG = "TimerFinishedReceiver";
    private static final String CHANNEL_ID = "pomodoro_channel";
    private static final int NOTIF_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive fired for timer finished broadcast");

        String title = "Time's Up!";
        String message = "Your timer has finished.";

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.done_sound);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
                NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
                if (existing != null) {
                    Log.d(TAG, "Deleting existing channel to enforce custom sound");
                    manager.deleteNotificationChannel(CHANNEL_ID);
                }

                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Pomodoro Timer Notifications",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications when Pomodoro timer ends");
                channel.setSound(soundUri, attrs);
                channel.enableVibration(true);
                manager.createNotificationChannel(channel);
            }

            Intent openAppIntent = new Intent(context, MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.wingman_logo)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(soundUri)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 400, 200, 400})
                    .setContentIntent(pendingIntent);

            if (manager != null) {
                manager.notify(NOTIF_ID, builder.build());
                Log.d(TAG, "Notification posted");
            }

            String userId = FirebaseAuth.getInstance().getUid();
            if (userId != null) {
                NotificationCenter.notifyAndLog(
                        context,
                        userId,
                        null,
                        "timer_finished",
                        "Pomodoro Completed",
                        "Your timer has finished!",
                        "timer"
                );
            }

            try {
                Ringtone ringtone = RingtoneManager.getRingtone(context, soundUri);
                if (ringtone != null && !ringtone.isPlaying()) {
                    ringtone.play();
                    Log.d(TAG, "Ringtone play requested");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Ringtone fallback failed", t);
            }

            try {
                MediaPlayer mp = MediaPlayer.create(context.getApplicationContext(), soundUri);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                    Log.d(TAG, "MediaPlayer fallback play requested");
                }
            } catch (Throwable t) {
                Log.w(TAG, "MediaPlayer fallback failed", t);
            }

        } catch (Exception ex) {
            Log.e(TAG, "Error in TimerFinishedReceiver", ex);
        }
    }
}