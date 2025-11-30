package com.example.wingman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wingman.data.NotificationRepository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class TimerFragment extends Fragment {

    private TextView timerDisplay, btnPomodoro, btnShortBreak, btnLongBreak, tvModeLabel;
    private ProgressBar circularProgressBar;
    private Button startPauseBtn, resetBtn;
    private Switch btnMusicToggle;
    private TimerViewModel timerViewModel;
    private boolean isVisibleToUser = false;
    private boolean hasBeenStarted = false;
    private boolean isMusicEnabled = true;
    private NotificationRepository notificationRepository;
    private SharedPreferences prefs;
    public static boolean isTimerFragmentVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timer, container, false);
        notificationRepository = new NotificationRepository();

        prefs = requireActivity().getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
        isMusicEnabled = prefs.getBoolean("music_enabled", true);

        circularProgressBar = view.findViewById(R.id.circularProgressBar);
        timerDisplay = view.findViewById(R.id.timerDisplay);
        startPauseBtn = view.findViewById(R.id.startPauseBtn);
        resetBtn = view.findViewById(R.id.resetBtn);
        btnPomodoro = view.findViewById(R.id.btnPomodoro);
        btnShortBreak = view.findViewById(R.id.btnShortBreak);
        btnLongBreak = view.findViewById(R.id.btnLongBreak);
        btnMusicToggle = view.findViewById(R.id.btnMusicToggle);
        tvModeLabel = view.findViewById(R.id.tvModeLabel);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        btnMusicToggle.setChecked(isMusicEnabled);

        btnMusicToggle.setOnClickListener(v -> {
            isMusicEnabled = !isMusicEnabled;
            prefs.edit().putBoolean("music_enabled", isMusicEnabled).apply();

            if (!isMusicEnabled && Boolean.TRUE.equals(timerViewModel.isRunning.getValue())) {
                Intent pauseIntent = new Intent(requireContext(), MusicService.class);
                pauseIntent.setAction("PAUSE");
                requireContext().startService(pauseIntent);
            } else if (isMusicEnabled && Boolean.TRUE.equals(timerViewModel.isRunning.getValue())) {
                Intent playIntent = new Intent(requireContext(), MusicService.class);
                playIntent.setAction("PLAY");
                requireContext().startService(playIntent);
            }

            Toast.makeText(getContext(),
                    isMusicEnabled ? "Music enabled" : "Music disabled",
                    Toast.LENGTH_SHORT).show();
        });

        timerViewModel = new ViewModelProvider(requireActivity()).get(TimerViewModel.class);

        timerViewModel.isRunning.observe(getViewLifecycleOwner(), running -> {
            updateButtonText(running);
            setModeButtonsEnabled(!running);
        });

        timerViewModel.timeLeftInMillis.observe(getViewLifecycleOwner(), timeLeft -> {
            updateTimerText(timeLeft);
            updateProgressBar(timeLeft, timerViewModel.defaultTime.getValue());

            if (timeLeft != null && timeLeft <= 0) {
                startPauseBtn.setVisibility(View.GONE);
                resetBtn.setVisibility(View.VISIBLE);
            } else {
                updateButtonVisibility();
            }
        });

        timerViewModel.defaultTime.observe(getViewLifecycleOwner(), defaultTime -> {
            updateProgressBar(timerViewModel.timeLeftInMillis.getValue(), defaultTime);
        });

        timerViewModel.finished.observe(getViewLifecycleOwner(), done -> {
            if (done != null && done && !timerViewModel.isTimerFinishedConsumed()) {
                Context context = requireContext();

                if (isMusicEnabled) {
                    Intent pauseIntent = new Intent(requireContext(), MusicService.class);
                    pauseIntent.setAction("PAUSE");
                    requireContext().startService(pauseIntent);
                }

                if (isVisibleToUser) {
                    Toast.makeText(getContext(), "Time's up!", Toast.LENGTH_SHORT).show();
                }

                String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                if (userId != null) {
                    NotificationCenter.notifyAndLog(
                            requireContext(),
                            userId,
                            null,
                            "timer_finished",
                            "Pomodoro Completed",
                            "Your timer has finished!",
                            "timer"
                    );
                }

                MediaPlayer player = MediaPlayer.create(context, R.raw.done_sound);
                if (player != null) {
                    player.setOnCompletionListener(MediaPlayer::release);
                    player.start();
                }

                timerViewModel.setTimerFinishedConsumed(true);
                startPauseBtn.setVisibility(View.GONE);
                setModeButtonsEnabled(true);
            }
        });

        startPauseBtn.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(timerViewModel.isRunning.getValue())) {
                timerViewModel.pauseTimer();

                if (isMusicEnabled) {
                    Intent pauseIntent = new Intent(requireContext(), MusicService.class);
                    pauseIntent.setAction("PAUSE");
                    requireContext().startService(pauseIntent);
                }

            } else {
                timerViewModel.startTimer();
                hasBeenStarted = true;

                if (isMusicEnabled) {
                    Intent playIntent = new Intent(requireContext(), MusicService.class);
                    playIntent.setAction("PLAY");
                    requireContext().startService(playIntent);
                }
            }
            updateButtonVisibility();
        });

        resetBtn.setOnClickListener(v -> {
            timerViewModel.resetTimer();
            hasBeenStarted = false;
            updateButtonText(false);
            circularProgressBar.setProgress(100);
            updateButtonVisibility();
            setModeButtonsEnabled(true);

            if (isMusicEnabled) {
                Intent pauseIntent = new Intent(requireContext(), MusicService.class);
                pauseIntent.setAction("PAUSE");
                requireContext().startService(pauseIntent);
            }
        });

        btnPomodoro.setOnClickListener(v -> {
            timerViewModel.switchMode(1500000); // 25 minutes
            highlightSelectedMode(btnPomodoro);
            tvModeLabel.setText("Pomodoro");
            hasBeenStarted = false;
            updateButtonText(false);
            updateButtonVisibility();
        });

        btnShortBreak.setOnClickListener(v -> {
            timerViewModel.switchMode(5000); // 5 minutes
            highlightSelectedMode(btnShortBreak);
            tvModeLabel.setText("Short Break");
            hasBeenStarted = false;
            updateButtonText(false);
            updateButtonVisibility();
        });

        btnLongBreak.setOnClickListener(v -> {
            timerViewModel.switchMode(900000); // 15 minutes
            highlightSelectedMode(btnLongBreak);
            tvModeLabel.setText("Long Break");
            hasBeenStarted = false;
            updateButtonText(false);
            updateButtonVisibility();
        });

        highlightSelectedMode(btnPomodoro);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isVisibleToUser = true;
        isTimerFragmentVisible = true;

        if (isMusicEnabled && Boolean.TRUE.equals(timerViewModel.isRunning.getValue())) {
            Intent resumeIntent = new Intent(requireContext(), MusicService.class);
            resumeIntent.setAction("RESUME");
            requireContext().startService(resumeIntent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isVisibleToUser = false;
        isTimerFragmentVisible = false;
    }

    private void updateButtonText(boolean isRunning) {
        if (isRunning) {
            startPauseBtn.setText("Pause");
        } else {
            startPauseBtn.setText(hasBeenStarted ? "Resume" : "Start");
        }
    }

    private void updateButtonVisibility() {
        if (!hasBeenStarted) {
            startPauseBtn.setVisibility(View.VISIBLE);
            resetBtn.setVisibility(View.GONE);
        } else {
            startPauseBtn.setVisibility(View.VISIBLE);
            resetBtn.setVisibility(View.VISIBLE);
        }
    }

    private void updateTimerText(Long timeLeftInMillis) {
        if (timeLeftInMillis == null) return;
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        timerDisplay.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void updateProgressBar(Long timeLeft, Long defaultTime) {
        if (timeLeft == null || defaultTime == null || defaultTime == 0) return;

        int progress = (int) ((timeLeft / (float) defaultTime) * 100);

        progress = Math.max(0, Math.min(100, progress));

        circularProgressBar.setProgress(progress);

        updateProgressBarColor(progress);
    }

    private void updateProgressBarColor(int progress) {
        try {
            android.graphics.drawable.LayerDrawable layerDrawable =
                    (android.graphics.drawable.LayerDrawable) circularProgressBar.getProgressDrawable();

            if (layerDrawable == null) return;

            android.graphics.drawable.Drawable progressLayer = layerDrawable.getDrawable(1);

            if (progressLayer instanceof android.graphics.drawable.RotateDrawable) {
                android.graphics.drawable.RotateDrawable rotateDrawable =
                        (android.graphics.drawable.RotateDrawable) progressLayer;
                android.graphics.drawable.Drawable innerDrawable = rotateDrawable.getDrawable();

                if (innerDrawable instanceof android.graphics.drawable.GradientDrawable) {
                    android.graphics.drawable.GradientDrawable shape =
                            (android.graphics.drawable.GradientDrawable) innerDrawable;

                    int color = interpolateColor(progress);
                    shape.setColor(color);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int interpolateColor(int progress) {
        float ratio = progress / 100f;

        int red = 255;
        int green = (int) (255 * ratio + 215 * (1 - ratio));
        int blue = (int) (255 * ratio + 0 * (1 - ratio));

        return android.graphics.Color.rgb(red, green, blue);
    }

    private void highlightSelectedMode(TextView selected) {
        int selectedBackgroundColor = getThemeColor(R.attr.colorCardBackground);

        int defaultTextColor = getThemeColor(R.attr.colorText);

        // Reset all buttons to default
        btnPomodoro.setBackgroundResource(R.drawable.mode_button_default);
        btnPomodoro.setTextColor(defaultTextColor);

        btnShortBreak.setBackgroundResource(R.drawable.mode_button_default);
        btnShortBreak.setTextColor(defaultTextColor);

        btnLongBreak.setBackgroundResource(R.drawable.mode_button_default);
        btnLongBreak.setTextColor(defaultTextColor);

        // Highlight the selected button
        selected.setBackgroundColor(selectedBackgroundColor);
        selected.setTextColor(getResources().getColor(android.R.color.white));
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private void setModeButtonsEnabled(boolean enabled) {
        btnPomodoro.setEnabled(enabled);
        btnShortBreak.setEnabled(enabled);
        btnLongBreak.setEnabled(enabled);

        float alpha = enabled ? 1f : 0.5f;
        btnPomodoro.setAlpha(alpha);
        btnShortBreak.setAlpha(alpha);
        btnLongBreak.setAlpha(alpha);
    }
}