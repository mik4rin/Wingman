package com.example.wingman;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.shimmer.ShimmerFrameLayout;

public class SplashActivity extends AppCompatActivity {

    private ImageView logoImage;
    private ShimmerFrameLayout shimmerFrameLayout;
    private RelativeLayout rootLayout;
    private ProgressBar loadingProgressBar;
    private TextView loadingText;
    private TextView percentageText;
    private View glowView;
    private GradientDrawable gradientDrawable;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_splash);

        rootLayout = findViewById(R.id.rootLayout);
        logoImage = findViewById(R.id.logoImage);
        shimmerFrameLayout = findViewById(R.id.shimmer_view);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingText = findViewById(R.id.loadingText);
        percentageText = findViewById(R.id.percentageText);
        glowView = findViewById(R.id.glowView);

        setupDarkGradientBackground();
        animateProgressBar();
        glowView.setAlpha(0.4f);

        mediaPlayer = MediaPlayer.create(this, R.raw.heron_call);
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }

        shimmerFrameLayout.startShimmer();

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(1200);
        fadeIn.setFillAfter(true);
        logoImage.startAnimation(fadeIn);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AlphaAnimation fadeOutLogo = new AlphaAnimation(1f, 0f);
            fadeOutLogo.setDuration(800);
            fadeOutLogo.setFillAfter(true);

            fadeOutLogo.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    shimmerFrameLayout.stopShimmer();
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                    Intent intent = new Intent(SplashActivity.this, StartActivity.class);
                    startActivity(intent);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
                @Override public void onAnimationRepeat(Animation animation) {}
            });

            logoImage.startAnimation(fadeOutLogo);

        }, 3500);
    }

    private void animateProgressBar() {
        String[] loadingMessages = {
                "Initializing Wingman...",
                "Loading Resources...",
                "Preparing Experience...",
                "Almost Ready..."
        };

        ValueAnimator progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(3500);
        progressAnimator.setInterpolator(new DecelerateInterpolator());

        progressAnimator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            loadingProgressBar.setProgress(progress);
            percentageText.setText(progress + "%");

            if (progress < 25) {
                loadingText.setText(loadingMessages[0]);
            } else if (progress < 50) {
                loadingText.setText(loadingMessages[1]);
            } else if (progress < 75) {
                loadingText.setText(loadingMessages[2]);
            } else {
                loadingText.setText(loadingMessages[3]);
            }
        });

        progressAnimator.start();
    }

    private void setupDarkGradientBackground() {
        int[] colors = new int[]{
                Color.parseColor("#0A1128"),
                Color.parseColor("#001F54"),
                Color.parseColor("#034078"),
                Color.parseColor("#001F3F")
        };

        gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{colors[0], colors[1], colors[2]}
        );
        rootLayout.setBackground(gradientDrawable);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(10000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            long playTime = animation.getCurrentPlayTime();
            int startIndex = (int) ((playTime / 5000) % colors.length);
            int endIndex = (startIndex + 1) % colors.length;

            int colorStart = colors[startIndex];
            int colorEnd = colors[endIndex];

            int blended1 = blendColors(colorStart, colorEnd, fraction);
            int blended2 = blendColors(colorEnd, colors[(endIndex + 1) % colors.length], fraction);
            int blended3 = blendColors(colors[(startIndex + 2) % colors.length], colorStart, fraction);

            gradientDrawable.setColors(new int[]{blended1, blended2, blended3});
        });

        animator.start();
    }

    private int blendColors(int from, int to, float ratio) {
        final float inverseRatio = 1f - ratio;
        float r = (Color.red(from) * inverseRatio) + (Color.red(to) * ratio);
        float g = (Color.green(from) * inverseRatio) + (Color.green(to) * ratio);
        float b = (Color.blue(from) * inverseRatio) + (Color.blue(to) * ratio);
        return Color.rgb((int) r, (int) g, (int) b);
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}