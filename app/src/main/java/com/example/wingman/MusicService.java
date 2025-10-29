package com.example.wingman;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class MusicService extends Service {

    private static MediaPlayer player;
    private static int pausePosition = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        if ("PLAY".equals(action)) {
            playMusic();
        } else if ("PAUSE".equals(action)) {
            pauseMusic();
        } else if ("RESUME".equals(action)) {
            resumeMusic();
        } else if ("STOP".equals(action)) {
            stopMusic();
        } else {
            playMusic();
        }

        return START_NOT_STICKY;
    }

    private void playMusic() {
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.bg_pomodoromusic);
            player.setLooping(true);
        }
        player.seekTo(pausePosition);
        player.start();
    }

    private void pauseMusic() {
        if (player != null && player.isPlaying()) {
            pausePosition = player.getCurrentPosition();
            player.pause();
        }
    }

    private void resumeMusic() {
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.bg_pomodoromusic);
            player.setLooping(true);
            player.seekTo(pausePosition);
        } else {
            player.seekTo(pausePosition);
        }
        player.start();
    }

    private void stopMusic() {
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
            pausePosition = 0;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}