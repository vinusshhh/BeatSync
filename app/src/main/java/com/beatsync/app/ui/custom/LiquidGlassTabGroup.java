package com.beatsync.app.ui.custom;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import java.util.ArrayList;
import java.util.List;

/**
 * LiquidGlassTabGroup:
 * Wraps any horizontal row of buttons or navigation items and renders an Apple iOS 26-style
 * sliding Liquid Glass selection pill behind them.
 * - Hardware blur on API 31+
 * - AndroidX SpringAnimation glide (DAMPING_RATIO_LOW_BOUNCY = 0.75f, STIFFNESS_MEDIUM = 400f)
 * - Width morphing animation
 * - Mid-flight liquid-droplet stretch morph (scaleX: 1.06f, scaleY: 0.95f -> 1.0f)
 * - Touch-drag scrubbing with haptic tick feedback
 * - Visual color/alpha sync for active/inactive tabs
 */
public class LiquidGlassTabGroup extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int position, View tab);
    }

    private LiquidGlassPillView pillView;
    private ViewGroup tabsContainer;
    private final List<View> tabViews = new ArrayList<>();

    private int selectedPosition = 0;
    private int currentPillWidth = 0;
    private OnTabSelectedListener listener;

    private SpringAnimation springTranslationX;
    private ValueAnimator widthAnimator;

    private boolean isDragging = false;
    private float startTouchX = 0f;
    private int dragHoverPosition = -1;

    public LiquidGlassTabGroup(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassTabGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidGlassTabGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        // Create the floating selection pill
        pillView = new LiquidGlassPillView(context);
        pillView.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT));
        addView(pillView, 0); // Placed behind tab views

        springTranslationX = new SpringAnimation(pillView, DynamicAnimation.TRANSLATION_X);
        SpringForce force = new SpringForce();
        force.setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY); // 0.75f
        force.setStiffness(SpringForce.STIFFNESS_MEDIUM); // 400f
        springTranslationX.setSpring(force);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Look for the child ViewGroup (the row of tabs)
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != pillView && child instanceof ViewGroup) {
                tabsContainer = (ViewGroup) child;
                break;
            }
        }
        if (tabsContainer != null) {
            setupTabsFromContainer();
        }
    }

    public void setupTabsFromContainer() {
        tabViews.clear();
        if (tabsContainer == null) return;

        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            View tab = tabsContainer.getChildAt(i);
            tabViews.add(tab);
            tab.setOnClickListener(v -> {
                int pos = tabViews.indexOf(v);
                if (pos >= 0) {
                    selectTab(pos, true);
                }
            });
        }

        post(() -> {
            updateTabsList();
            snapPillToPosition(selectedPosition);
        });
    }

    public void updateTabsList() {
        tabViews.clear();
        if (tabsContainer == null) return;
        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            View tab = tabsContainer.getChildAt(i);
            if (tab.getVisibility() != GONE) {
                tabViews.add(tab);
            }
        }
    }

    public void selectTab(int targetIndex, boolean animate) {
        selectTab(targetIndex, animate, true);
    }

    public void selectTab(int targetIndex, boolean animate, boolean notifyListener) {
        updateTabsList();
        if (tabViews.isEmpty()) return;
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= tabViews.size()) targetIndex = tabViews.size() - 1;

        selectedPosition = targetIndex;
        View targetTab = tabViews.get(targetIndex);

        if (animate && getWidth() > 0 && targetTab.getWidth() > 0) {
            animatePillToTab(targetTab);
        } else {
            snapPillToPosition(targetIndex);
        }

        updateTabVisuals(targetIndex);

        if (notifyListener && listener != null) {
            listener.onTabSelected(targetIndex, targetTab);
        }
    }

    private void snapPillToPosition(int position) {
        if (tabViews.isEmpty() || position < 0 || position >= tabViews.size()) return;
        View tab = tabViews.get(position);
        if (tab.getWidth() <= 0) {
            post(() -> snapPillToPosition(position));
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int verticalPad = (int) (4 * density);
        int horizontalPad = (int) (3 * density);

        int targetLeft = tab.getLeft() + tabsContainer.getLeft() + horizontalPad;
        int targetWidth = Math.max(0, tab.getWidth() - (horizontalPad * 2));
        int targetHeight = Math.max(0, tab.getHeight() - (verticalPad * 2));

        LayoutParams lp = (LayoutParams) pillView.getLayoutParams();
        lp.width = targetWidth;
        lp.height = targetHeight > 0 ? targetHeight : LayoutParams.MATCH_PARENT;
        lp.topMargin = tab.getTop() + tabsContainer.getTop() + verticalPad;
        pillView.setLayoutParams(lp);

        pillView.setTranslationX(targetLeft);
        pillView.setScaleX(1.0f);
        pillView.setScaleY(1.0f);
        currentPillWidth = targetWidth;

        updateTabVisuals(position);
    }

    private void animatePillToTab(View targetTab) {
        float density = getResources().getDisplayMetrics().density;
        int verticalPad = (int) (4 * density);
        int horizontalPad = (int) (3 * density);

        int targetLeft = targetTab.getLeft() + tabsContainer.getLeft() + horizontalPad;
        int targetWidth = Math.max(0, targetTab.getWidth() - (horizontalPad * 2));

        // 1. Spring glide across bar
        springTranslationX.animateToFinalPosition(targetLeft);

        // 2. Liquid Droplet Morph: scaleX pulses to 1.06f, scaleY to 0.95f
        pillView.animate()
                .scaleX(1.06f)
                .scaleY(0.95f)
                .setDuration(120)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    pillView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();

        // 3. Width morph animation
        if (widthAnimator != null && widthAnimator.isRunning()) {
            widthAnimator.cancel();
        }
        int fromWidth = currentPillWidth > 0 ? currentPillWidth : pillView.getWidth();
        widthAnimator = ValueAnimator.ofInt(fromWidth, targetWidth);
        widthAnimator.setDuration(260);
        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.addUpdateListener(anim -> {
            int w = (int) anim.getAnimatedValue();
            currentPillWidth = w;
            LayoutParams lp = (LayoutParams) pillView.getLayoutParams();
            lp.width = w;
            pillView.setLayoutParams(lp);
        });
        widthAnimator.start();
    }

    private void updateTabVisuals(int activeIndex) {
        for (int i = 0; i < tabViews.size(); i++) {
            View tab = tabViews.get(i);
            boolean isActive = (i == activeIndex);
            updateChildElements(tab, isActive);
        }
    }

    private void updateChildElements(View view, boolean isActive) {
        int activeColor = Color.WHITE;
        int inactiveColor = Color.argb(153, 255, 255, 255); // #99FFFFFF

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(isActive ? activeColor : inactiveColor);
            tv.setAlpha(isActive ? 1.0f : 0.72f);
        } else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            iv.setImageTintList(ColorStateList.valueOf(isActive ? activeColor : inactiveColor));
            iv.setAlpha(isActive ? 1.0f : 0.72f);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int j = 0; j < group.getChildCount(); j++) {
                updateChildElements(group.getChildAt(j), isActive);
            }
        }
    }

    // --- Touch Drag Scrubbing & Haptics ---

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            startTouchX = ev.getX();
            isDragging = false;
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(ev.getX() - startTouchX) > 12f) {
                isDragging = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                dragHoverPosition = findTabIndexAtX(x);
                handleDragToX(x);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    handleDragToX(x);
                    int hovered = findTabIndexAtX(x);
                    if (hovered != -1 && hovered != dragHoverPosition) {
                        dragHoverPosition = hovered;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        updateTabVisuals(hovered);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    int finalTab = findTabIndexAtX(x);
                    if (finalTab != -1) {
                        selectTab(finalTab, true);
                    } else {
                        selectTab(selectedPosition, true);
                    }
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void handleDragToX(float touchX) {
        if (pillView == null || currentPillWidth <= 0) return;
        float halfWidth = currentPillWidth / 2f;
        float targetTranslation = touchX - halfWidth;
        pillView.setTranslationX(targetTranslation);
    }

    private int findTabIndexAtX(float x) {
        for (int i = 0; i < tabViews.size(); i++) {
            View tab = tabViews.get(i);
            int left = tab.getLeft() + tabsContainer.getLeft();
            int right = tab.getRight() + tabsContainer.getLeft();
            if (x >= left && x <= right) {
                return i;
            }
        }
        return -1;
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public LiquidGlassPillView getPillView() {
        return pillView;
    }
}
