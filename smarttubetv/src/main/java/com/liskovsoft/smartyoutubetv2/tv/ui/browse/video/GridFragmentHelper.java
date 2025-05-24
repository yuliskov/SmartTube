package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Pair;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashMap;
import java.util.Map;

public class GridFragmentHelper {
    private static final Map<Integer, Integer> sCachedDimens = new HashMap<>();
    private static int sCachedDisplayWidth = -1;

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
        return (int) getMaxColsNumFloat(context, cardWidthResId, cardScale);
    }

    private static float getMaxColsNumFloat(Context context, int cardWidthResId, float cardScale) {
        float uiScale = MainUIData.instance(context).getUIScale();

        Resources res = context.getResources();

        int displayWidthPx;

        if (sCachedDisplayWidth == -1) {
            DisplayMetrics displayMetrics = res.getDisplayMetrics();
            // Take into the account screen orientation (e.g. when running on phone)
            displayWidthPx = sCachedDisplayWidth = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
        } else {
            displayWidthPx = sCachedDisplayWidth;
        }

        float cardWidthPx = getDimensionPixelSize(res, cardWidthResId) * cardScale;
        float cardSpacingPx = getDimensionPixelSize(res, R.dimen.grid_item_horizontal_spacing);

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

        float cardWidthPx = getDimensionPixelSize(res, cardWidthResId) * cardScale;
        float cardHeightPx = getDimensionPixelSize(res, cardHeightResId) * cardScale;

        // Get into consideration the space from the grid sides
        float colsNum = getMaxColsNumFloat(context, cardWidthResId, cardScale);
        int colsNumRounded = (int) colsNum;
        float colsReminder = (colsNum - colsNumRounded) / colsNumRounded;

        float width = cardWidthPx + cardWidthPx * colsReminder;
        float height = cardHeightPx + cardHeightPx * colsReminder;

        if (isSingleColumn) { // single column grids have huge margin
            width -= getDimensionPixelSize(res, R.dimen.grid_horizontal_margin);
        }

        return new Pair<>((int) width, (int) height);
    }

    private static int getDimensionPixelSize(Resources res, int id) {
        Integer size = sCachedDimens.get(id);

        if (size == null) {
            size = res.getDimensionPixelSize(id);
            sCachedDimens.put(id, size);
        }

        return size;
    }
}
