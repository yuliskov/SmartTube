package com.liskovsoft.smartyoutubetv2.tv.ui.material;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Material You tokens for the transitional Leanback surface.
 *
 * Android 12+ supplies Monet resources at runtime. Older Android TV releases retain the active
 * app palette, so this utility is useful independently of any Relay integration.
 */
public final class MaterialYouColors {
    private MaterialYouColors() {}

    public static int surface(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral1_900);
        }
        return themeColor(context, R.attr.shelfBackground, R.color.shelf_background_dark);
    }

    public static int surfaceVariant(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral2_800);
        }
        return themeColor(context, R.attr.cardDefaultBackground, R.color.card_default_background_dark);
    }

    public static int accent(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_accent1_500);
        }
        Integer brandAccent = resolvedThemeColor(context, R.attr.brandAccentColor);
        if (brandAccent != null) {
            return brandAccent;
        }
        return ContextCompat.getColor(context, R.color.fastlane_background);
    }

    /**
     * The active focus surface. Use the accent itself instead of placing a bright outline over a
     * darker tonal container; nested Leanback focus scaling can expose that second color as a
     * thin edge artifact.
     */
    public static int accentContainer(Context context) {
        return accent(context);
    }

    /** A slightly lifted surface used by cards that need separation from the background. */
    public static int surfaceContainerHigh(Context context) {
        return blend(surfaceVariant(context), Color.WHITE, 0.055f);
    }

    /**
     * A restrained Material TV focus surface. Content remains readable and artwork stays dominant
     * while the active palette is still visible in the outline and subtle tonal lift.
     */
    public static int focusedCardSurface(Context context) {
        return blend(surfaceContainerHigh(context), accent(context), 0.10f);
    }

    public static int focusedCardOutline(Context context) {
        return blend(accent(context), Color.WHITE, 0.28f);
    }

    public static int outline(Context context) {
        return withAlpha(blend(surfaceVariant(context), Color.WHITE, 0.28f), 0x70);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int from, int to, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        float inverse = 1.0f - clamped;
        return Color.argb(
                Math.round(Color.alpha(from) * inverse + Color.alpha(to) * clamped),
                Math.round(Color.red(from) * inverse + Color.red(to) * clamped),
                Math.round(Color.green(from) * inverse + Color.green(to) * clamped),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * clamped));
    }

    private static boolean usesAutomaticScheme(Context context) {
        // Identify the stable Automatic entry by name rather than its theme resource. Automatic
        // now has a real theme so legacy Leanback widgets receive the same Monet palette too.
        return com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(context)
                .getColorScheme().nameResId == com.liskovsoft.smartyoutubetv2.common.R.string.color_scheme_automatic;
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        Integer color = resolvedThemeColor(context, attribute);
        return color != null ? color : ContextCompat.getColor(context, fallback);
    }

    private static Integer resolvedThemeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId);
            }
            return value.data;
        }
        return null;
    }

    public static GradientDrawable roundedSurface(Context context, int color, float cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(cornerDp * context.getResources().getDisplayMetrics().density);
        return drawable;
    }

    /** Theme-aware, single-layer player control surface with no competing focus outline. */
    public static StateListDrawable playerControlSurface(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                ovalSurface(blend(accent(context), Color.BLACK, 0.12f)));
        states.addState(
                new int[]{android.R.attr.state_focused},
                ovalSurface(accent(context)));
        states.addState(
                new int[]{},
                ovalSurface(surfaceVariant(context)));
        return states;
    }

    public static GradientDrawable playerControlSurface(Context context, boolean focused) {
        return ovalSurface(focused ? accent(context) : surfaceVariant(context));
    }

    private static GradientDrawable ovalSurface(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /**
     * A low-contrast outlined surface for navigation and utility cards. The outline gives
     * dark-TV layouts definition without relying on large, always-visible focus rectangles.
     */
    public static GradientDrawable outlinedSurface(
            Context context, int color, float cornerDp, int outlineColor, float outlineDp) {
        GradientDrawable drawable = roundedSurface(context, color, cornerDp);
        int outlinePx = Math.max(1, Math.round(outlineDp * context.getResources().getDisplayMetrics().density));
        drawable.setStroke(outlinePx, outlineColor);
        return drawable;
    }
}
