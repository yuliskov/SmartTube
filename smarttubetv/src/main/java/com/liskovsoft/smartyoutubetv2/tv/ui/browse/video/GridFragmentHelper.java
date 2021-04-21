package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Pair;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class GridFragmentHelper {
    /**
     * Max number of cards that could fit horizontally
     */
    public static int getMaxColsNum(Context context, int cardWidthResId) {
        return getMaxColsNum(context, cardWidthResId, 1.0f);
    }

    /**
     * Max number of cards that could fit horizontally
     */
    public static int getMaxColsNum(Context context, int cardWidthResId, float cardScale) {
        return (int) getColumnsNum(context, cardWidthResId, cardScale);
    }

    private static float getColumnsNum(Context context, int cardWidthResId, float cardScale) {
        float uiScale = MainUIData.instance(context).getUIScale();

        Resources res = context.getResources();

        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        int displayWidthPx = displayMetrics.widthPixels;
        float cardWidthPx = res.getDimensionPixelSize(cardWidthResId) * cardScale;
        float cardSpacingPx = res.getDimensionPixelSize(R.dimen.grid_item_horizontal_spacing);

        // Get into consideration space from grid sides
        return (displayWidthPx - displayWidthPx * 0.1f * uiScale) / (cardWidthPx + cardSpacingPx);
    }

    public static Pair<Integer, Integer> getCardDimensPx(Context context, int cardWidthResId, int cardHeightResId, float cardScale) {
        return getCardDimensPx(context, cardWidthResId, cardHeightResId, cardScale, false);
    }

    /**
     * Calculate card dimension depending on card scale param<br/>
     * Trying to not leave empty space (useful in grids).
     */
    public static Pair<Integer, Integer> getCardDimensPx(Context context, int cardWidthResId, int cardHeightResId, float cardScale, boolean isSingleColumn) {
        Resources res = context.getResources();

        float cardWidthPx = res.getDimensionPixelSize(cardWidthResId) * cardScale;
        float cardHeightPx = res.getDimensionPixelSize(cardHeightResId) * cardScale;

        // Get into consideration space from grid sides
        float colsNum = getColumnsNum(context, cardWidthResId, cardScale);
        int colsNumRounded = (int) colsNum;
        float colsReminder = (colsNum - colsNumRounded) / colsNumRounded;

        float width = cardWidthPx + cardWidthPx * colsReminder;
        float height = cardHeightPx + cardHeightPx * colsReminder;

        if (isSingleColumn) { // single column grids have huge margin
            width -= res.getDimensionPixelSize(R.dimen.grid_horizontal_margin);
        }

        return new Pair<>((int) width, (int) height);
    }
}
