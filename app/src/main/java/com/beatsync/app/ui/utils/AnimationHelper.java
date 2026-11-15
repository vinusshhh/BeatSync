package com.beatsync.app.ui.utils;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

public class AnimationHelper {

    @SuppressLint("ClickableViewAccessibility")
    public static void addPressScaleEffect(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.92f)
                            .scaleY(0.92f)
                            .setDuration(120)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(280)
                            .setInterpolator(new OvershootInterpolator(2.4f))
                            .start();
                    break;
            }
            return false;
        });
    }

    public static void animateItemEntrance(View view, int position) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(30f);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setStartDelay(Math.min(position * 30L, 240L))
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    public static ObjectAnimator createContinuousRotation(View view, long durationMs) {
        if (view == null) return null;
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        animator.setDuration(durationMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        return animator;
    }

    public static ObjectAnimator createLiquidBreathing(View view) {
        if (view == null) return null;
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.025f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.025f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY);
        animator.setDuration(3000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new DecelerateInterpolator());
        return animator;
    }
}
