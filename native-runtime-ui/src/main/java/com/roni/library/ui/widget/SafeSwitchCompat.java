package com.roni.library.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/**
 * A {@link SwitchCompat} that can never crash in {@code onMeasure}.
 *
 * <p>Every constructor runs {@link SwitchSafety#harden(SwitchCompat)} before the first measure
 * pass, so a missing {@code android:textOn} / {@code android:textOff} — or a theme that enables
 * {@code showText} without supplying them — degrades to a plain switch instead of a
 * {@code NullPointerException} inside {@code StaticLayout}.</p>
 *
 * <p>Use this instead of {@code SwitchCompat} whenever the widget is created in code.</p>
 */
public class SafeSwitchCompat extends SwitchCompat {

    public SafeSwitchCompat(@NonNull Context context) {
        super(context);
        SwitchSafety.harden(this);
    }

    public SafeSwitchCompat(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        SwitchSafety.harden(this);
    }

    public SafeSwitchCompat(@NonNull Context context, @Nullable AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        SwitchSafety.harden(this);
    }

    @Override
    public void setTextOn(@Nullable CharSequence textOn) {
        super.setTextOn(textOn);
        // A later setTextOn(null) would re-introduce the crash on the next measure pass.
        if (textOn == null) {
            super.setTextOn("");
            super.setShowText(false);
        }
    }

    @Override
    public void setTextOff(@Nullable CharSequence textOff) {
        super.setTextOff(textOff);
        if (textOff == null) {
            super.setTextOff("");
            super.setShowText(false);
        }
    }
}
