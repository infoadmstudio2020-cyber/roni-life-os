package com.roni.life.os.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Segmented instrument meter. Filled segments are neon orange, empty segments are the panel
 * line colour — the only progress animation allowed by the HUD spec.
 */
public class HudMeterView extends View {

    private static final int ACCENT = 0xFFFF4500;
    private static final int EMPTY = 0xFF1E1E1E;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int segments = 28;
    private float target = 0f;
    private float shown = 0f;
    private ValueAnimator animator;

    public HudMeterView(Context context) {
        this(context, null);
    }

    public HudMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HudMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setSegments(int count) {
        this.segments = Math.max(4, count);
        invalidate();
    }

    /** Sets the fill ratio (0..1) and animates the bar to it. */
    public void setProgress(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - target) < 0.001f) {
            return;
        }
        target = clamped;
        if (isInEditMode()) {
            shown = target;
            invalidate();
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(650L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            shown = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float gap = Math.max(1f, 2f * getResources().getDisplayMetrics().density);
        float segWidth = (w - gap * (segments - 1)) / segments;
        if (segWidth <= 0) {
            return;
        }

        int filled = Math.round(shown * segments);
        for (int i = 0; i < segments; i++) {
            float left = i * (segWidth + gap);
            paint.setColor(i < filled ? ACCENT : EMPTY);
            canvas.drawRect(left, 0, left + segWidth, h, paint);
        }
    }
}
