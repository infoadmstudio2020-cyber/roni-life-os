package com.roni.library.ui.widget;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Guards {@link SwitchCompat} against the {@code StaticLayout} null-text crash.
 *
 * <p><b>Why this exists.</b> {@code SwitchCompat.onMeasure()} builds an internal
 * {@code StaticLayout} for the ON/OFF labels whenever {@code showText} is true:</p>
 *
 * <pre>
 * if (mShowText) {
 *     if (mOnLayout == null)  mOnLayout  = makeLayout(mTextOn);
 *     if (mOffLayout == null) mOffLayout = makeLayout(mTextOff);
 * }
 * </pre>
 *
 * <p>{@code makeLayout()} forwards the {@link CharSequence} straight into
 * {@code new StaticLayout(...)}, which immediately calls {@code text.length()}. When
 * {@code mTextOn} or {@code mTextOff} is {@code null} — which happens whenever the widget is
 * constructed without a style that supplies {@code android:textOn} / {@code android:textOff},
 * or when {@code setTextOn(null)} is called — the result is:</p>
 *
 * <pre>
 * java.lang.NullPointerException: Attempt to invoke interface method
 * 'int java.lang.CharSequence.length()' on a null object reference
 *     at android.text.StaticLayout.&lt;init&gt;
 *     at androidx.appcompat.widget.SwitchCompat.makeLayout
 *     at androidx.appcompat.widget.SwitchCompat.onMeasure
 * </pre>
 *
 * <p>The guard is applied from the widget constructor, i.e. before the first measure pass, so
 * the crash cannot occur even if the theme, the style or a later {@code setTextOn(null)} call
 * would otherwise leave the labels null.</p>
 */
public final class SwitchSafety {

    private SwitchSafety() {
    }

    /**
     * Makes a switch safe to measure.
     *
     * <p>Null ON/OFF labels are replaced with empty strings and the internal label rendering is
     * switched off. A null main label is replaced with an empty string as well. Nothing is
     * changed when the labels are already present, so an intentional ON/OFF design keeps
     * working.</p>
     *
     * @param sw the switch to harden; {@code null} is ignored
     */
    public static void harden(@Nullable SwitchCompat sw) {
        if (sw == null) {
            return;
        }

        final boolean missingLabel = needsShowTextDisabled(sw.getTextOn(), sw.getTextOff());

        if (sw.getTextOn() == null) {
            sw.setTextOn("");
        }
        if (sw.getTextOff() == null) {
            sw.setTextOff("");
        }
        if (sw.getText() == null) {
            sw.setText("");
        }

        if (missingLabel) {
            sw.setShowText(false);
        }
    }

    /**
     * Pure decision used by {@link #harden(SwitchCompat)}.
     *
     * <p>Kept free of Android types so it can be unit-tested on a plain JVM.</p>
     *
     * @return true when the internal ON/OFF layout must be disabled
     */
    public static boolean needsShowTextDisabled(@Nullable CharSequence textOn,
                                                @Nullable CharSequence textOff) {
        return textOn == null || textOff == null;
    }
}
