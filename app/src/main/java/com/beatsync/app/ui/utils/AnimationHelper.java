package com.beatsync.app.ui.utils;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class AnimationHelper {

    @SuppressLint("ClickableViewAccessibility")
    public static void addPressScaleEffect(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    public static void animateItemEntrance(View view, int position) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .setStartDelay(Math.min(position * 35L, 300L))
                .setInterpolator(new DecelerateInterpolator())
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
}
