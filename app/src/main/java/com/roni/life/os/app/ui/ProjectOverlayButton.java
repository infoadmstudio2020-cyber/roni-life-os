package com.roni.life.os.app.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.roni.life.os.app.R;
import com.roni.life.os.app.quickball.QuickBallPreferences;

/**
 * Floating control button drawn on top of the project runtime.
 *
 * <p>Added with {@link Activity#addContentView} rather than as a system overlay, so no
 * {@code SYSTEM_ALERT_WINDOW} permission is involved and the button can never appear over other
 * apps. The host {@link FrameLayout} is not clickable, which lets touches outside the button fall
 * through to the WebView underneath.</p>
 *
 * <p>Behaves like a Quick Ball: it can be dragged anywhere, and on release it parks against the
 * nearest side edge with most of the ball off-screen. Tapping a parked ball slides it back into
 * view; tapping a visible ball opens the control sheet. Position and parked state are persisted,
 * so the ball returns to the same edge after an activity change or an app restart.</p>
 */
public final class ProjectOverlayButton {

    /** Callbacks for the two gestures the button supports. */
    public interface Listener {
        /** Short tap — open the control sheet. */
        void onTap();

        /** Long press — minimise straight to the dashboard. */
        void onLongPress();
    }

    private static final int MARGIN_DP = 12;
    private static final int TOP_DP = 96;

    /** How much of the ball stays off-screen when parked. */
    private static final float HIDDEN_FRACTION = 0.55f;
    /** Alpha multiplier while parked, so the ball reads as inactive. */
    private static final float HIDDEN_ALPHA = 0.72f;
    private static final long SLIDE_MS = 180L;

    private ProjectOverlayButton() {
    }

    /**
     * Attaches the overlay to an activity.
     *
     * @return the overlay root, or {@code null} when the activity is gone
     */
    public static View attach(final Activity activity, final Listener listener) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        final QuickBallPreferences quickBall = QuickBallPreferences.get(activity);
        final int size = (int) (quickBall.getBallSizeDp() * density);
        final int margin = (int) (MARGIN_DP * density);
        final float opacity = quickBall.getBallOpacity();

        final FrameLayout overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Not clickable: touches that miss the button reach the WebView below.
        overlay.setClickable(false);
        overlay.setFocusable(false);

        final ImageView button = new ImageView(activity);
        final FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.TOP | Gravity.START;
        params.topMargin = (int) (TOP_DP * density);
        params.leftMargin = margin;
        button.setLayoutParams(params);
        button.setContentDescription(activity.getString(R.string.pm_overlay_desc));
        button.setImageResource(R.drawable.ic_more_vert);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setColorFilter(Color.WHITE);
        button.setBackground(circle(activity));
        button.setAlpha(opacity);
        button.setElevation(6 * density);
        button.setClickable(true);

        final boolean[] hidden = {quickBall.isBallHidden()};
        final boolean[] leftSide = {true};

        final int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        final int longPressTimeout = ViewConfiguration.getLongPressTimeout();

        button.setOnTouchListener(new View.OnTouchListener() {

            private float downRawX;
            private float downRawY;
            private int startLeft;
            private int startTop;
            private long downAt;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startLeft = params.leftMargin;
                        startTop = params.topMargin;
                        downAt = System.currentTimeMillis();
                        dragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging
                                && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            dragging = true;
                            // Dragging always brings the ball back into view.
                            hidden[0] = false;
                            view.setAlpha(opacity);
                        }
                        if (dragging) {
                            params.leftMargin = clampHorizontal(
                                    startLeft + (int) dx, overlay.getWidth(), size);
                            params.topMargin = startTop + (int) dy;
                            view.setLayoutParams(params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            park(overlay, params, button, size, margin, opacity,
                                    hidden, leftSide, quickBall);
                        } else if (System.currentTimeMillis() - downAt
                                >= longPressTimeout) {
                            if (listener != null) {
                                listener.onLongPress();
                            }
                        } else if (hidden[0]) {
                            // First tap on a parked ball only brings it back.
                            reveal(overlay, params, button, size, margin, opacity,
                                    hidden, leftSide, quickBall);
                        } else {
                            view.performClick();
                            if (listener != null) {
                                listener.onTap();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            park(overlay, params, button, size, margin, opacity,
                                    hidden, leftSide, quickBall);
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });

        overlay.addView(button);

        try {
            activity.addContentView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            return null;
        }

        // Restore the saved position once the real width is known.
        overlay.post(new Runnable() {
            @Override
            public void run() {
                int width = overlay.getWidth();
                if (width <= 0) {
                    return;
                }
                int savedX = quickBall.getBallX();
                int savedY = quickBall.getBallY();
                if (savedX != Integer.MIN_VALUE) {
                    leftSide[0] = savedX + size / 2 < width / 2;
                    params.leftMargin = hidden[0]
                            ? parkedLeft(width, size, leftSide[0])
                            : clampHorizontal(savedX, width, size);
                    if (savedY != Integer.MIN_VALUE) {
                        params.topMargin = savedY;
                    }
                } else {
                    leftSide[0] = false;
                    params.leftMargin = width - size - margin;
                }
                clampVertical(params, overlay.getHeight(), size, margin);
                button.setAlpha(hidden[0] ? opacity * HIDDEN_ALPHA : opacity);
                button.setLayoutParams(params);
            }
        });

        return overlay;
    }

    /** Parks the ball against the nearest side edge, mostly off-screen. */
    private static void park(FrameLayout overlay,
                             FrameLayout.LayoutParams params,
                             ImageView button,
                             int size,
                             int margin,
                             float opacity,
                             boolean[] hidden,
                             boolean[] leftSide,
                             QuickBallPreferences preferences) {
        int width = overlay.getWidth();
        if (width <= 0) {
            return;
        }
        leftSide[0] = params.leftMargin + size / 2 < width / 2;
        clampVertical(params, overlay.getHeight(), size, margin);
        slideTo(params, button, parkedLeft(width, size, leftSide[0]));
        hidden[0] = true;
        button.setAlpha(opacity * HIDDEN_ALPHA);
        persist(preferences, params, true);
    }

    /** Slides a parked ball fully back into view. */
    private static void reveal(FrameLayout overlay,
                               FrameLayout.LayoutParams params,
                               ImageView button,
                               int size,
                               int margin,
                               float opacity,
                               boolean[] hidden,
                               boolean[] leftSide,
                               QuickBallPreferences preferences) {
        int width = overlay.getWidth();
        if (width <= 0) {
            return;
        }
        int target = leftSide[0] ? margin : width - size - margin;
        slideTo(params, button, target);
        hidden[0] = false;
        button.setAlpha(opacity);
        persist(preferences, params, false);
    }

    private static int parkedLeft(int width, int size, boolean leftSide) {
        int offset = (int) (size * HIDDEN_FRACTION);
        return leftSide ? -offset : width - size + offset;
    }

    private static void slideTo(FrameLayout.LayoutParams params, ImageView button, int targetLeft) {
        int start = params.leftMargin;
        if (start == targetLeft) {
            params.leftMargin = targetLeft;
            button.setLayoutParams(params);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(start, targetLeft);
        animator.setDuration(SLIDE_MS);
        animator.addUpdateListener(animation -> {
            params.leftMargin = (int) animation.getAnimatedValue();
            button.setLayoutParams(params);
        });
        animator.start();
    }

    private static void persist(QuickBallPreferences preferences,
                                FrameLayout.LayoutParams params,
                                boolean hidden) {
        preferences.setBallX(params.leftMargin);
        preferences.setBallY(params.topMargin);
        preferences.setBallHidden(hidden);
    }

    /** Keeps at least a sliver of the ball reachable while dragging. */
    private static int clampHorizontal(int left, int width, int size) {
        if (width <= 0) {
            return left;
        }
        int min = -(int) (size * 0.75f);
        int max = width - (int) (size * 0.25f);
        return Math.max(min, Math.min(left, max));
    }

    private static void clampVertical(FrameLayout.LayoutParams params,
                                      int height,
                                      int size,
                                      int margin) {
        if (height <= 0) {
            return;
        }
        int maxTop = Math.max(margin, height - size - margin);
        params.topMargin = Math.max(margin, Math.min(params.topMargin, maxTop));
    }

    private static GradientDrawable circle(Activity activity) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(ContextCompat.getColor(activity, R.color.brand_primary));
        shape.setStroke((int) (1 * activity.getResources().getDisplayMetrics().density),
                ContextCompat.getColor(activity, R.color.brand_primary_dark));
        return shape;
    }
}
