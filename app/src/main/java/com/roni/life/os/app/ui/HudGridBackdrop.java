package com.roni.life.os.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Instrument backdrop: a faint engineering grid fading into an obsidian vignette.
 *
 * <p>Drawn once per size change — it never animates, so it costs nothing while the dashboard
 * scrolls.</p>
 */
public class HudGridBackdrop extends View {

    private static final int GRID = 0x12FFFFFF;
    private static final int GRID_MAJOR = 0x1FFFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader vignette;

    public HudGridBackdrop(Context context) {
        this(context, null);
    }

    public HudGridBackdrop(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HudGridBackdrop(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        vignette = new RadialGradient(w * 0.5f, h * 0.28f, Math.max(w, h) * 0.85f,
                new int[]{0x00000000, 0x66020202, 0xE6020202},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float step = 28f * getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();

        for (float x = 0; x <= w; x += step) {
            boolean major = Math.round(x / step) % 4 == 0;
            paint.setColor(major ? GRID_MAJOR : GRID);
            canvas.drawLine(x, 0, x, h, paint);
        }
        for (float y = 0; y <= h; y += step) {
            boolean major = Math.round(y / step) % 4 == 0;
            paint.setColor(major ? GRID_MAJOR : GRID);
            canvas.drawLine(0, y, w, y, paint);
        }

        if (vignette != null) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(vignette);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
        }
    }
}
