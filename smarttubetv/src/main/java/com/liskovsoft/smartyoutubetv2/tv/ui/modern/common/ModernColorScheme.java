package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Color scheme of the modern interface (User interface > Color scheme), see values/modern_schemes.xml.
 * Layouts read the scheme through theme attributes, code through these helpers.
 */
public final class ModernColorScheme {
    private ModernColorScheme() {
    }

    /**
     * Call right after {@code setTheme()} with one of the modern themes.
     */
    public static void apply(Activity activity) {
        int overlayResId = MainUIData.instance(activity).getColorScheme().modernThemeResId;

        if (overlayResId > 0) {
            activity.getTheme().applyStyle(overlayResId, true);
        }

        // The window reads its style from the manifest theme before setTheme(): set the background of the scheme
        TypedArray window = activity.obtainStyledAttributes(new int[] {android.R.attr.windowBackground});
        activity.getWindow().setBackgroundDrawable(window.getDrawable(0));
        window.recycle();
    }

    public static int background(Context context) {
        return color(context, R.attr.modernBackground, R.color.modern_bg);
    }

    /**
     * Expanded rail, like the headers of the classic interface.
     */
    public static int brand(Context context) {
        return color(context, R.attr.modernBrand, R.color.modern_bg);
    }

    /**
     * Watch progress, seek bar, live badge, voice search.
     */
    public static int accent(Context context) {
        return color(context, R.attr.modernAccent, R.color.modern_progress);
    }

    /**
     * Logo of the top bar, 0 when the scheme hides it (OLED).
     */
    public static int logo(Context context) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(R.attr.modernLogo, value, true)) {
            return value.resourceId;
        }

        return R.mipmap.app_logo_semi_red;
    }

    public static int drawable(Context context, int attr, int defaultResId) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(attr, value, true) && value.resourceId != 0) {
            return value.resourceId;
        }

        return defaultResId;
    }

    /**
     * Live badge of the tiles.
     */
    public static Drawable liveBackground(Context context) {
        Drawable background = DrawableCompat.wrap(ContextCompat.getDrawable(context, R.drawable.modern_live_bg).mutate());
        DrawableCompat.setTint(background, accent(context));
        return background;
    }

    /**
     * Voice search button: white when focused, accent while listening.
     */
    public static Drawable micBackground(Context context) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, oval(ContextCompat.getColor(context, R.color.modern_rail_selected_pill)));
        background.addState(new int[] {android.R.attr.state_activated}, oval(accent(context)));
        background.addState(new int[] {}, oval(ContextCompat.getColor(context, R.color.modern_white_10)));
        return background;
    }

    private static Drawable oval(int color) {
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(color);
        return oval;
    }

    private static int color(Context context, int attr, int defaultResId) {
        TypedValue value = new TypedValue();

        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return ContextCompat.getColor(context, defaultResId);
        }

        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }

        return ContextCompat.getColor(context, value.resourceId != 0 ? value.resourceId : defaultResId);
    }
}
