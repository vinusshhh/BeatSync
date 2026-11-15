package com.beatsync.app.ui.utils;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class AnimationHelper {

    /**
     * Tactile AndroidX SpringAnimation Touch Feedback:
     * Compresses on touch down, springs back with DAMPING_RATIO_MEDIUM_BOUNCY and STIFFNESS_LOW on release.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void addPressScaleEffect(View view) {
        addSpringTouchFeedback(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void addRubberPressEffect(View view) {
        addSpringTouchFeedback(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void addSpringTouchFeedback(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateSpring(v, 0.90f, SpringForce.DAMPING_RATIO_NO_BOUNCY, SpringForce.STIFFNESS_MEDIUM);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateSpring(v, 1.0f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_LOW);
                    break;
            }
            return false;
        });
    }

    public static void animateSpring(View view, float targetScale, float dampingRatio, float stiffness) {
        if (view == null) return;
        SpringAnimation animX = new SpringAnimation(view, DynamicAnimation.SCALE_X, targetScale);
        animX.getSpring().setDampingRatio(dampingRatio);
        animX.getSpring().setStiffness(stiffness);

        SpringAnimation animY = new SpringAnimation(view, DynamicAnimation.SCALE_Y, targetScale);
        animY.getSpring().setDampingRatio(dampingRatio);
        animY.getSpring().setStiffness(stiffness);

        animX.start();
        animY.start();
    }

    /**
     * AndroidX Spring Feedback pop for programmatic clicks (e.g. tabs, buttons).
     */
    public static void rubberPop(View view) {
        applySpringScaleFeedback(view);
    }

    public static void applySpringScaleFeedback(View view) {
        if (view == null) return;
        view.setScaleX(0.88f);
        view.setScaleY(0.88f);

        SpringAnimation animX = new SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f);
        animX.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        animX.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);

        SpringAnimation animY = new SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f);
        animY.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        animY.getSpring().setStiffness(SpringForce.STIFFNESS_LOW);

        animX.start();
        animY.start();
    }

    public static void rubberJellyBounce(View view) {
        applySpringScaleFeedback(view);
    }

    public static void animateItemEntrance(View view, int position) {
        animateRubberEntrance(view, position);
    }

    public static void animateRubberEntrance(View view, int position) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.setScaleX(0.86f);
        view.setScaleY(0.86f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setStartDelay(Math.min(position * 35L, 240L))
                .setInterpolator(new OvershootInterpolator(2.5f))
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
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.035f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.035f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY);
        animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }
}
