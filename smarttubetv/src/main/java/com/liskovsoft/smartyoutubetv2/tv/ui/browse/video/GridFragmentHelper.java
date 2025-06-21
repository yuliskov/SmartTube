package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Pair;

import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashMap;
import java.util.Map;

public class GridFragmentHelper {
    private static final Map<Integer, Pair<Integer, Integer>> sCardDimensPx = new HashMap<>();
    private static final Map<Integer, Integer> sMaxColsNum = new HashMap<>();
    private static final Runnable sInvalidate = GridFragmentHelper::invalidate;

    private static void invalidate() {
        sCardDimensPx.clear();
        sMaxColsNum.clear();
    }

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
        Integer maxColsNum = sMaxColsNum.get(cardWidthResId);

        if (maxColsNum != null) {
            return maxColsNum;
        }

        ViewManager.instance(context).addOnFinish(sInvalidate);

        maxColsNum = (int) getMaxColsNumFloat(context, cardWidthResId, cardScale);

        sMaxColsNum.put(cardWidthResId, maxColsNum);

        return maxColsNum;
    }

    private static float getMaxColsNumFloat(Context context, int cardWidthResId, float cardScale) {
        float uiScale = MainUIData.instance(context).getUIScale();

        Resources res = context.getResources();

        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        // Take into the account screen orientation (e.g. when running on phone)
        int displayWidthPx = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);

        float cardWidthPx = res.getDimensionPixelSize(cardWidthResId) * cardScale;
        float cardSpacingPx = res.getDimensionPixelSize(R.dimen.grid_item_horizontal_spacing);

        // Get into consideration space from grid sides
        return (displayWidthPx - displayWidthPx * 0.1f * uiScale) / (cardWidthPx + cardSpacingPx);
    }

    public static Pair<Integer, Integer> getCardDimensPx(Context context, int cardWidthResId, int cardHeightResId, float cardScale) {
        return getCardDimensPx(context, cardWidthResId, cardHeightResId, cardScale, false);
    }

    public static Pair<Integer, Integer> getCardDimensPx(Context context, int cardWidthResId, int cardHeightResId, float cardScale, boolean isSingleColumn) {
        Pair<Integer, Integer> cardDimensPx = sCardDimensPx.get(cardWidthResId);
        if (cardDimensPx != null) {
            return cardDimensPx;
        }

        cardDimensPx = getCardDimensPxInt(context, cardWidthResId, cardHeightResId, cardScale, isSingleColumn);

        sCardDimensPx.put(cardWidthResId, cardDimensPx);

        return cardDimensPx;
    }

    /**
     * Calculate card dimension depending on card scale param<br/>
     * Trying to not leave empty space (useful in grids).
     */
    private static Pair<Integer, Integer> getCardDimensPxInt(Context context, int cardWidthResId, int cardHeightResId, float cardScale, boolean isSingleColumn) {
        Resources res = context.getResources();

        float cardWidthPx = res.getDimensionPixelSize(cardWidthResId) * cardScale;
        float cardHeightPx = res.getDimensionPixelSize(cardHeightResId) * cardScale;

        // Get into consideration the space from the grid sides
        float colsNum = getMaxColsNumFloat(context, cardWidthResId, cardScale);
        int colsNumRounded = (int) colsNum;
        float colsReminder = (colsNum - colsNumRounded) / colsNumRounded;

        float width = cardWidthPx + cardWidthPx * colsReminder;
        float height = cardHeightPx + cardHeightPx * colsReminder;

        if (isSingleColumn) { // single column grids have a huge margin
            width -= res.getDimensionPixelSize(R.dimen.grid_horizontal_margin);
        }

        return new Pair<>((int) width, (int) height);
    }
}
