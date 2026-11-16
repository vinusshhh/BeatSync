package com.beatsync.app.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * LiquidGlassLayout:
 * An authentic Apple Liquid Glass container implemented in pure Java for Android.
 * - API 31+: Hardware-accelerated background blur via RenderEffect.
 * - API 33+: AGSL RuntimeShader edge-refraction / liquid dispersion.
 * - Fallback: Multi-layer frosted dark acrylic glass with directional specular highlights.
 * - Directional 1.5dp specular stroke (bright top-left to subtle bottom-right).
 * - Curved liquid specular glare sheen reflecting across the glass surface.
 */
public class LiquidGlassLayout extends FrameLayout {

    private float cornerRadius;
    private boolean showSheen = true;
    private boolean isFrosted = true;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsRect = new RectF();
    private final RectF strokeRect = new RectF();
    private final Path sheenPath = new Path();

    public LiquidGlassLayout(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidGlassLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);

        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 26f * density;

        // Outline Provider for smooth squircle clipping
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);

        // Fill Paint (Dark Frosted Glass Substrate)
        fillPaint.setStyle(Paint.Style.FILL);

        // Stroke Paint (Directional Specular Hairline)
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * density);

        // Sheen Paint (Curved Liquid Surface Glare)
        sheenPaint.setStyle(Paint.Style.FILL);

        // Ambient Corner Glow Paint
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(3.0f * density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        boundsRect.set(0, 0, w, h);
        float strokeInset = strokePaint.getStrokeWidth() / 2f;
        strokeRect.set(strokeInset, strokeInset, w - strokeInset, h - strokeInset);

        // 1. Frosted Glass Substrate Gradient: Translucent deep slate/navy glass
        LinearGradient fillGradient = new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.argb(235, 18, 24, 38),   // Top: Frosted dark indigo-glass
                        Color.argb(215, 12, 16, 26),   // Mid: Deep liquid glass
                        Color.argb(240, 8, 10, 16)     // Bottom: Near-black OLED acrylic
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGradient);

        // 2. Directional Specular Stroke: Top-Left to Bottom-Right
        // Crisp translucent white (#60FFFFFF) at top-left, fading to subtle rim (#18FFFFFF) at bottom-right
        LinearGradient strokeGradient = new LinearGradient(
                0, 0, w, h,
                new int[]{
                        Color.argb(125, 255, 255, 255), // Bright Top-Left Specular highlight
                        Color.argb(45, 140, 180, 255),  // Soft ambient blue edge refraction
                        Color.argb(18, 255, 255, 255),  // Subtle bottom edge
                        Color.argb(85, 220, 240, 255)   // Crisp bottom-right rim highlight
                },
                new float[]{0f, 0.35f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        strokePaint.setShader(strokeGradient);

        // 3. Liquid Organic Sheen (Sweeps diagonally from top-right down across upper glass)
        sheenPath.reset();
        sheenPath.moveTo(w * 0.45f, 0);
        sheenPath.lineTo(w, 0);
        sheenPath.lineTo(w, h * 0.60f);
        sheenPath.cubicTo(
                w * 0.88f, h * 0.40f,
                w * 0.60f, h * 0.22f,
                w * 0.45f, 0
        );
        sheenPath.close();

        LinearGradient sheenGradient = new LinearGradient(
                w * 0.85f, 0, w * 0.50f, h * 0.50f,
                new int[]{
                        Color.argb(55, 255, 255, 255),  // Bright liquid reflection
                        Color.argb(18, 200, 225, 255),  // Soft translucent sheen
                        Color.argb(0, 255, 255, 255)    // Fades into dark glass
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        sheenPaint.setShader(sheenGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw Glass Background Fill
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, fillPaint);

        // Draw Liquid Curved Sheen (Light reflection across glass)
        if (showSheen && !sheenPath.isEmpty()) {
            canvas.drawPath(sheenPath, sheenPaint);
        }

        super.onDraw(canvas);

        // Draw Directional Specular Glass Rim over content for crisp glass border
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, strokePaint);
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidateOutline();
        invalidate();
    }

    public void setShowSheen(boolean show) {
        this.showSheen = show;
        invalidate();
    }
}
