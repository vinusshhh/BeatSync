package com.beatsync.app.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * LiquidGlassPillView:
 * The sliding glass selection capsule.
 * - Clean translucent frosted dark glass fill
 * - Crisp directional specular glass rim stroke (bright top-left to subtle bottom-right)
 * - Zero lens glare / zero halo glow artifacts
 */
public class LiquidGlassPillView extends View {

    private float cornerRadius;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsRect = new RectF();
    private final RectF strokeRect = new RectF();

    public LiquidGlassPillView(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassPillView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidGlassPillView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 24f * density;

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.0f * density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        boundsRect.set(0, 0, w, h);
        float inset = strokePaint.getStrokeWidth() / 2f;
        strokeRect.set(inset, inset, w - inset, h - inset);

        cornerRadius = Math.min(cornerRadius, h / 2f);

        // Elegant translucent frosted glass fill: no bright bubble, just clean glass
        LinearGradient fillGrad = new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.argb(55, 255, 255, 255),  // Soft translucent frosted glass top
                        Color.argb(25, 255, 255, 255),  // Smooth glass body
                        Color.argb(15, 255, 255, 255)   // Deep translucent base
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGrad);

        // Directional specular stroke (bright top-left to subtle bottom-right)
        LinearGradient strokeGrad = new LinearGradient(
                0, 0, w, h,
                new int[]{
                        Color.argb(120, 255, 255, 255), // Crisp top-left highlight
                        Color.argb(50, 200, 220, 255),  // Soft glass edge
                        Color.argb(20, 255, 255, 255),  // Bottom edge
                        Color.argb(60, 255, 255, 255)   // Corner catch
                },
                new float[]{0f, 0.4f, 0.8f, 1f},
                Shader.TileMode.CLAMP
        );
        strokePaint.setShader(strokeGrad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, fillPaint);
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, strokePaint);
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidateOutline();
        invalidate();
    }
}
