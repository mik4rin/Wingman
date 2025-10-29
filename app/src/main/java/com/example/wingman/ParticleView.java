package com.example.wingman;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleView extends View {

    private class Particle {
        float x, y, radius, alpha, speedY;
        Paint paint;
    }

    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();

    public ParticleView(Context context) {
        super(context);
        initParticles();
    }

    public ParticleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initParticles();
    }

    private void initParticles() {
        for (int i = 0; i < 30; i++) {
            Particle p = new Particle();
            p.x = random.nextInt(1000);
            p.y = random.nextInt(2000);
            p.radius = random.nextFloat() * 6 + 2;
            p.alpha = random.nextFloat();
            p.speedY = random.nextFloat() * 2 + 0.5f;
            p.paint = new Paint();
            p.paint.setColor(Color.WHITE);
            p.paint.setAlpha((int) (p.alpha * 255));
            p.paint.setAntiAlias(true);
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Particle p : particles) {
            canvas.drawCircle(p.x, p.y, p.radius, p.paint);
            p.y -= p.speedY;
            p.alpha -= 0.01;
            if (p.alpha <= 0 || p.y < 0) {
                p.x = random.nextInt(getWidth());
                p.y = getHeight() + p.radius;
                p.alpha = 1f;
                p.speedY = random.nextFloat() * 2 + 0.5f;
            }
            p.paint.setAlpha((int) (p.alpha * 255));
        }

        postInvalidateOnAnimation();
    }
}