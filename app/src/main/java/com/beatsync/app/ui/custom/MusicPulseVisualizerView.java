package com.beatsync.app.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * MusicPulseVisualizerView:
 * A high-fidelity, real-time neon audio pulse spectrum visualizer.
 * Matches Apple / Cyberpunk aesthetic with dual-gradient neon vertical bars:
 * - Neon Magenta / Hot Pink on top
 * - Electric Cyan / Radiant Azure on bottom
 * - Centered vertical oscillation reacting to playback state
 */
public class MusicPulseVisualizerView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private ValueAnimator waveAnimator;
    private float phase = 0f;
    private float currentAmplitude = 1.0f;
    private float targetAmplitude = 1.0f;
    private boolean isPlaying = true;

    private float barWidthPx;
    private float barGapPx;
    private float cornerRadiusPx;

    public MusicPulseVisualizerView(@NonNull Context context) {
        this(context, null);
    }

    public MusicPulseVisualizerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MusicPulseVisualizerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        barWidthPx = 3.2f * density;
        barGapPx = 2.4f * density;
        cornerRadiusPx = 1.6f * density;

        barPaint.setStyle(Paint.Style.FILL);

        // Smooth 60fps continuous phase animation
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(1200);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(animation -> {
            phase += 0.085f;
            if (phase > 1000f) phase = 0f;

            // Smoothly interpolate amplitude between playing and paused
            currentAmplitude += (targetAmplitude - currentAmplitude) * 0.12f;

            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (waveAnimator != null && !waveAnimator.isRunning()) {
            waveAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (waveAnimator != null) {
            waveAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        // Vibrant neon gradient: Hot Magenta (#FF007F) -> Violet (#A855F7) -> Electric Cyan (#00F0FF) -> Blue (#3B82F6)
        LinearGradient gradient = new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.parseColor("#FF007F"), // Hot Pink / Neon Magenta
                        Color.parseColor("#E879F9"), // Radiant Rose Violet
                        Color.parseColor("#A855F7"), // Center Violet
                        Color.parseColor("#00F0FF"), // Electric Cyan
                        Color.parseColor("#3B82F6")  // Deep Sky Blue
                },
                new float[]{0.05f, 0.35f, 0.50f, 0.70f, 0.95f},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(gradient);
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        this.targetAmplitude = playing ? 1.0f : 0.15f;
        if (waveAnimator != null && !waveAnimator.isRunning()) {
            waveAnimator.start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerY = height / 2.0f;
        float maxBarHalfHeight = (height * 0.42f);

        float step = barWidthPx + barGapPx;
        int numBars = (int) (width / step);
        float startX = (width - (numBars * step - barGapPx)) / 2.0f;

        for (int i = 0; i < numBars; i++) {
            float x = startX + i * step;
            float t = (float) i / (float) Math.max(1, numBars - 1); // 0.0 to 1.0

            // Multi-frequency harmonic synthesis matching visualizer spectrum
            double h1 = Math.sin(t * Math.PI * 4.0 + phase * 1.8);
            double h2 = Math.sin(t * Math.PI * 8.0 - phase * 2.6);
            double h3 = Math.cos(t * Math.PI * 14.0 + phase * 3.4);
            double h4 = Math.sin(t * Math.PI * 2.0 + phase * 0.9);

            // Center-weighted Gaussian envelope
            double centerWeight = Math.sin(t * Math.PI);
            double envelope = 0.25 + 0.75 * Math.pow(centerWeight, 1.4);

            double rawVal = Math.abs(h1) * 0.45 + Math.abs(h2) * 0.30 + Math.abs(h3) * 0.15 + Math.abs(h4) * 0.10;
            float barHalfHeight = (float) (maxBarHalfHeight * envelope * (0.15 + 0.85 * rawVal) * currentAmplitude);

            // Minimum bar height for aesthetic baseline
            barHalfHeight = Math.max(barHalfHeight, 3.0f);

            barRect.set(x, centerY - barHalfHeight, x + barWidthPx, centerY + barHalfHeight);
            canvas.drawRoundRect(barRect, cornerRadiusPx, cornerRadiusPx, barPaint);
        }
    }
}
