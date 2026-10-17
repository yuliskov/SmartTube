package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

/**
 * Tile sizes of the browse pages, scaled by the "Video grid scale" setting (UI scale settings).
 */
public final class ModernTiles {
    private ModernTiles() {
    }

    public static float scale(Context context) {
        float scale = MainUIData.instance(context).getVideoGridScale();
        return scale > 0 ? scale : 1f;
    }

    /**
     * A tile dimension (width, thumbnail height, channel size) multiplied by the grid scale.
     */
    public static int size(Context context, int dimenResId) {
        return Math.round(context.getResources().getDimensionPixelSize(dimenResId) * scale(context));
    }

    /**
     * How many whole tiles fit in the available width.
     */
    public static int columns(int availableWidth, int tileWidth, int gap) {
        return Math.max(1, (availableWidth + gap) / (tileWidth + gap));
    }
}
