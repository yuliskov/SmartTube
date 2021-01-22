package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Pair;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;

public class AutoSizeGridFragment extends GridFragment {
    protected int getColumnsNum(int cardWidthResId) {
        return getColumnsNum(cardWidthResId, 1.0f);
    }

    protected int getColumnsNum(int cardWidthResId, float cardScale) {
        return getColumnsNum(getContext(), cardWidthResId, cardScale);
    }

    private static int getColumnsNum(Context context, int cardWidthResId, float cardScale) {
        Resources res = context.getResources();

        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        int displayWidthPx = (int) (displayMetrics.widthPixels * displayMetrics.density);
        int cardWidthDp = (int) (res.getDimension(cardWidthResId) * cardScale);
        int cardSpacingDp = (int) res.getDimension(R.dimen.grid_item_horizontal_spacing);
        int cardWidthPx = Utils.convertDpToPixel(res, cardWidthDp);
        int cardSpacingPx = Utils.convertDpToPixel(res, cardSpacingDp);

        // Get into consideration space from grid sides
        return (displayWidthPx + cardSpacingPx - (int) (displayWidthPx * 0.1f)) / (cardWidthPx + cardSpacingPx);
    }

    public static Pair<Integer, Integer> getCardDimensionPx(Context context, int cardWidthResId, int cardHeightResId, float cardScale) {
        float uiScale = MainUIData.instance(context).getUIScale();

        Resources res = context.getResources();

        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        int displayWidthPx = displayMetrics.widthPixels;
        int cardWidthPx = (int) (res.getDimensionPixelSize(cardWidthResId) * cardScale);
        int cardHeightPx = (int) (res.getDimensionPixelSize(cardHeightResId) * cardScale);
        int cardSpacingPx = res.getDimensionPixelSize(R.dimen.grid_item_horizontal_spacing);

        // Get into consideration space from grid sides
        float colsNum = (displayWidthPx - displayWidthPx * 0.1f * uiScale) / (cardWidthPx + cardSpacingPx);
        int realColsNum = (int) colsNum;
        float colsReminder = (colsNum - realColsNum) / realColsNum;

        int width = cardWidthPx + (int) (cardWidthPx * colsReminder);
        int height = cardHeightPx + (int) (cardHeightPx * colsReminder);

        return new Pair<>(width, height);
    }
}
