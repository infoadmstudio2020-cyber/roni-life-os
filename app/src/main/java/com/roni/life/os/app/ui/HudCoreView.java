package com.roni.life.os.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Core visual of the Industrial HUD: an obsidian sphere suspended in the void, cut by neon
 * laser lines and ringed by instrument ticks.
 *
 * <p>The only motion is a slow breathing pulse and a single sweeping arc — restrained state
 * feedback, never decorative bounce.</p>
 */
public class HudCoreView extends View {

    private static final int ACCENT = 0xFFFF4500;
    private static final int LINE = 0xFF333333;
    private static final int LINE_SOFT = 0xFF1E1E1E;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float phase;
    private float load = 0.72f;
    private ValueAnimator animator;

    public HudCoreView(Context context) {
        this(context, null);
    }

    public HudCoreView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HudCoreView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1f));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** Engine readiness in the 0..1 range; drives the outer arc. */
    public void setLoad(float value) {
        this.load = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(6000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = Math.min(w, h) * 0.30f;

        drawTicks(canvas, cx, cy, radius);
        drawRings(canvas, cx, cy, radius);
        drawSphere(canvas, cx, cy, radius);
        drawLasers(canvas, cx, cy, radius);
        drawArc(canvas, cx, cy, radius);
        drawCrosshair(canvas, cx, cy, radius);
    }

    private void drawTicks(Canvas canvas, float cx, float cy, float radius) {
        float outer = radius * 1.42f;
        float inner = radius * 1.30f;
        float longInner = radius * 1.22f;
        stroke.setColor(LINE);
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            boolean major = i % 5 == 0;
            float start = major ? longInner : inner;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            stroke.setColor(major ? 0xFF555555 : LINE_SOFT);
            canvas.drawLine(cx + cos * start, cy + sin * start,
                    cx + cos * outer, cy + sin * outer, stroke);
        }
    }

    private void drawRings(Canvas canvas, float cx, float cy, float radius) {
        stroke.setColor(LINE_SOFT);
        canvas.drawCircle(cx, cy, radius * 1.16f, stroke);
        stroke.setColor(LINE);
        canvas.drawCircle(cx, cy, radius * 1.02f, stroke);
    }

    private void drawSphere(Canvas canvas, float cx, float cy, float radius) {
        // Obsidian body: a dark radial falloff with a single cold rim highlight.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx - radius * 0.35f, cy - radius * 0.4f,
                radius * 1.5f,
                new int[]{0xFF1C1C1C, 0xFF0A0A0A, 0xFF000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        stroke.setColor(0xFF3A3A3A);
        stroke.setStrokeWidth(dp(1f));
        canvas.drawCircle(cx, cy, radius, stroke);

        // Specular sliver.
        stroke.setColor(0x33FFFFFF);
        stroke.setStrokeWidth(dp(1.5f));
        arcBounds.set(cx - radius * 0.82f, cy - radius * 0.82f,
                cx + radius * 0.82f, cy + radius * 0.82f);
        canvas.drawArc(arcBounds, 200f, 55f, false, stroke);
        stroke.setStrokeWidth(dp(1f));
    }

    private void drawLasers(Canvas canvas, float cx, float cy, float radius) {
        float breath = 0.55f + 0.45f * (float) Math.sin(phase * Math.PI * 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));

        // Three fixed laser cuts, each with a travelling highlight.
        float[] angles = {-38f, 12f, 148f};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i]);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float startX = cx + cos * radius * 0.15f;
            float startY = cy + sin * radius * 0.15f;
            float endX = cx + cos * radius * 1.55f;
            float endY = cy + sin * radius * 1.55f;

            paint.setColor(0x33FF4500);
            canvas.drawLine(startX, startY, endX, endY, paint);

            float travel = (phase + i * 0.33f) % 1f;
            float px = startX + (endX - startX) * travel;
            float py = startY + (endY - startY) * travel;
            paint.setColor(0xCCFF4500);
            paint.setStrokeWidth(dp(2f));
            canvas.drawLine(px, py, px + cos * dp(10f), py + sin * dp(10f), paint);
            paint.setStrokeWidth(dp(1f));
        }

        // Breathing halo.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (18 + 26 * breath), 255, 69, 0));
        canvas.drawCircle(cx, cy, radius * 1.06f, paint);
    }

    private void drawArc(Canvas canvas, float cx, float cy, float radius) {
        float r = radius * 1.16f;
        arcBounds.set(cx - r, cy - r, cx + r, cy + r);

        stroke.setColor(LINE_SOFT);
        stroke.setStrokeWidth(dp(2f));
        canvas.drawArc(arcBounds, 0f, 360f, false, stroke);

        stroke.setColor(ACCENT);
        canvas.drawArc(arcBounds, -90f, 360f * load, false, stroke);

        // Sweeping scanner.
        stroke.setColor(0xFFFF8A4D);
        canvas.drawArc(arcBounds, -90f + phase * 360f, 26f, false, stroke);
        stroke.setStrokeWidth(dp(1f));
    }

    private void drawCrosshair(Canvas canvas, float cx, float cy, float radius) {
        stroke.setColor(0x55FF4500);
        float gap = radius * 0.18f;
        float arm = radius * 0.34f;
        canvas.drawLine(cx - arm, cy, cx - gap, cy, stroke);
        canvas.drawLine(cx + gap, cy, cx + arm, cy, stroke);
        canvas.drawLine(cx, cy - arm, cx, cy - gap, stroke);
        canvas.drawLine(cx, cy + gap, cx, cy + arm, stroke);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
