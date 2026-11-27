package com.roni.life.os.app.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Rule-of-thirds grid plus an accelerometer driven horizon level. */
public final class CameraGridOverlay extends View implements SensorEventListener {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean gridEnabled = true;
    private boolean levelEnabled;
    private float roll;

    public CameraGridOverlay(Context context) {
        this(context, null);
    }

    public CameraGridOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(Color.argb(90, 255, 255, 255));
        gridPaint.setStrokeWidth(1f);
        levelPaint.setColor(Color.argb(220, 120, 255, 160));
        levelPaint.setStrokeWidth(3f);
        setWillNotDraw(false);
    }

    public void setGridEnabled(boolean value) {
        gridEnabled = value;
        invalidate();
    }

    public void setLevelEnabled(boolean value) {
        levelEnabled = value;
        if (value) {
            SensorManager manager =
                    (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            if (manager != null) {
                Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometer != null) {
                    manager.registerListener(this, accelerometer,
                            SensorManager.SENSOR_DELAY_UI);
                }
            }
        } else {
            SensorManager manager =
                    (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            if (manager != null) {
                manager.unregisterListener(this);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (gridEnabled) {
            for (int i = 1; i < 3; i++) {
                float x = width * i / 3f;
                float y = height * i / 3f;
                canvas.drawLine(x, 0, x, height, gridPaint);
                canvas.drawLine(0, y, width, y, gridPaint);
            }
        }
        if (levelEnabled) {
            float centerY = height / 2f;
            float half = width * 0.18f;
            float offset = (float) Math.tan(Math.toRadians(Math.max(-12f, Math.min(12f, roll))))
                    * (width / 2f);
            canvas.drawLine(width / 2f - half, centerY + offset,
                    width / 2f + half, centerY + offset, levelPaint);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        float x = event.values[0];
        float y = event.values[1];
        roll = (float) Math.toDegrees(Math.atan2(x, y));
        invalidate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDetachedFromWindow() {
        SensorManager manager =
                (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (manager != null) {
            manager.unregisterListener(this);
        }
        super.onDetachedFromWindow();
    }
}
