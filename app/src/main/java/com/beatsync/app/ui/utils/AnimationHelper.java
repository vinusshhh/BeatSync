package com.beatsync.app.ui.utils;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

public class AnimationHelper {

    /**
     * Tactile Rubber Spring Touch Interaction:
     * Squashes inward on press down, pops outward with an elastic rubber oscillation on release.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void addPressScaleEffect(View view) {
        addRubberPressEffect(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void addRubberPressEffect(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.86f)
                            .scaleY(0.86f)
                            .setDuration(80)
                            .setInterpolator(new DecelerateInterpolator(2.0f))
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Rubber spring rebound with high overshoot tension (4.2f)
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(360)
                            .setInterpolator(new OvershootInterpolator(4.2f))
                            .start();
                    break;
            }
            return false;
        });
    }

    /**
     * Tactile Rubber Pop:
     * Punches up to 1.18x and rubber-bounces back into position.
     */
    public static void rubberPop(View view) {
        if (view == null) return;
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .setDuration(110)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(280)
                            .setInterpolator(new OvershootInterpolator(4.5f))
                            .start();
                })
                .start();
    }

    /**
     * Jelly / Rubber Squish Bounce:
     * Squashes wide (1.20x, 0.82y), stretches tall (0.90x, 1.15y), and settles with spring overshoot.
     */
    public static void rubberJellyBounce(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.22f)
                .scaleY(0.82f)
                .setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(0.90f)
                            .scaleY(1.14f)
                            .setDuration(110)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> {
                                view.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(240)
                                        .setInterpolator(new OvershootInterpolator(3.8f))
                                        .start();
                            })
                            .start();
                })
                .start();
    }

    /**
     * Elastic Spring Item Entrance:
     * Elements spring and bounce into view like elastic cards.
     */
    public static void animateItemEntrance(View view, int position) {
        animateRubberEntrance(view, position);
    }

    public static void animateRubberEntrance(View view, int position) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.setScaleX(0.84f);
        view.setScaleY(0.84f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setStartDelay(Math.min(position * 35L, 240L))
                .setInterpolator(new OvershootInterpolator(2.8f))
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
