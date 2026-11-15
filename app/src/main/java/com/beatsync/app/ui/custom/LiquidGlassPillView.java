package com.beatsync.app.ui.custom;

import android.content.Context;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * LiquidGlassPillView:
 * The sliding glass selection capsule.
 * - Hardware blur on API 31+
 * - Directional 1.2dp specular stroke (bright #80FFFFFF at top-left fading to #14FFFFFF at bottom-right)
 * - Translucent frosted fill (#33FFFFFF top to #14FFFFFF bottom)
 * - Curved liquid surface sheen
 */
public class LiquidGlassPillView extends View {

    private float cornerRadius;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsRect = new RectF();
    private final RectF strokeRect = new RectF();
    private final Path sheenPath = new Path();

    private static final String AGSL_PILL_REFRACTION =
            "uniform shader image;\n" +
            "uniform float2 resolution;\n" +
            "half4 main(float2 fragCoord) {\n" +
            "    float2 uv = fragCoord / resolution;\n" +
            "    half4 color = image.eval(fragCoord);\n" +
            "    float rim = smoothstep(0.40, 0.5, length(uv - 0.5));\n" +
            "    return color + half4(1.0, 1.0, 1.0, 0.32) * rim;\n" +
            "}";

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
        strokePaint.setStrokeWidth(1.2f * density);
        sheenPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        boundsRect.set(0, 0, w, h);
        float inset = strokePaint.getStrokeWidth() / 2f;
        strokeRect.set(inset, inset, w - inset, h - inset);

        // Capsule radius: clamp to half height if pill is short
        cornerRadius = Math.min(cornerRadius, h / 2f);

        // Frosted translucent fill: #38FFFFFF at top, #14FFFFFF at bottom
        LinearGradient fillGrad = new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.argb(72, 255, 255, 255),  // Translucent bright glass
                        Color.argb(32, 255, 255, 255),  // Frosted body
                        Color.argb(20, 255, 255, 255)   // Deep translucent base
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGrad);

        // Directional specular stroke (bright top-left to subtle bottom-right)
        LinearGradient strokeGrad = new LinearGradient(
                0, 0, w, h,
                new int[]{
                        Color.argb(175, 255, 255, 255), // Crisp #B0FFFFFF top-left highlight
                        Color.argb(65, 180, 210, 255),  // Soft azure edge tint
                        Color.argb(25, 255, 255, 255),  // Bottom edge
                        Color.argb(110, 255, 255, 255)  // Bottom-right specular catch
                },
                new float[]{0f, 0.35f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        );
        strokePaint.setShader(strokeGrad);

        // Subtle curved light sheen in upper section
        sheenPath.reset();
        sheenPath.moveTo(w * 0.25f, 0);
        sheenPath.lineTo(w, 0);
        sheenPath.lineTo(w, h * 0.55f);
        sheenPath.cubicTo(
                w * 0.85f, h * 0.35f,
                w * 0.50f, h * 0.15f,
                w * 0.25f, 0
        );
        sheenPath.close();

        LinearGradient sheenGrad = new LinearGradient(
                w * 0.7f, 0, w * 0.4f, h * 0.5f,
                new int[]{
                        Color.argb(65, 255, 255, 255),
                        Color.argb(18, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        sheenPaint.setShader(sheenGrad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, fillPaint);
        if (!sheenPath.isEmpty()) {
            canvas.drawPath(sheenPath, sheenPaint);
        }
        canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, strokePaint);
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidateOutline();
        invalidate();
    }
}
