package com.roni.life.os.app.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

/** ছোট UI সহায়ক ফাংশন। */
public final class UiUtils {

    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    /** নির্দিষ্ট রঙের বৃত্তাকার ব্যাকগ্রাউন্ড তৈরি করে। */
    public static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** নির্দিষ্ট রঙ ও কর্নার রেডিয়াসের গোলাকার আয়তক্ষেত্র। */
    public static GradientDrawable roundedRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
