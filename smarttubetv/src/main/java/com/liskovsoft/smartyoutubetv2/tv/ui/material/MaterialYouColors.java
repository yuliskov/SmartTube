package com.liskovsoft.smartyoutubetv2.tv.ui.material;

import android.content.Context;
import android.os.Build;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral1_900);
        }
        return ContextCompat.getColor(context, R.color.shelf_background_dark);
    }

    public static int surfaceVariant(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral2_800);
        }
        return ContextCompat.getColor(context, R.color.card_default_background_dark);
    }

    public static int accent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_accent1_500);
        }
        return ContextCompat.getColor(context, R.color.fastlane_background);
    }
}
