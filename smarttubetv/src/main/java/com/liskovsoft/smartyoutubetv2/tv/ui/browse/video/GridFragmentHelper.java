package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Pair;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;

import java.util.HashMap;
import java.util.Map;

public class GridFragmentHelper {
    private static final Map<Integer, Pair<Integer, Integer>> sCardDimensPx = new HashMap<>();
    private static final Map<Integer, Float> sMaxColsNum = new HashMap<>();
    private static final Runnable sInvalidate = GridFragmentHelper::invalidate;
    private static final int MIN_ADAPTER_SIZE = 6;

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
        return (int) getMaxColsNumFloat(context, cardWidthResId, cardScale);
    }

    private static float getMaxColsNumFloat(Context context, int cardWidthResId, float cardScale) {
        Float maxColsNum = sMaxColsNum.get(cardWidthResId);

        if (maxColsNum != null) {
            return maxColsNum;
        }

        ViewManager.instance(context).addOnFinish(sInvalidate);

        maxColsNum = getMaxColsNumFloatInt(context, cardWidthResId, cardScale);

        sMaxColsNum.put(cardWidthResId, maxColsNum);

        return maxColsNum;
    }

    private static float getMaxColsNumFloatInt(Context context, int cardWidthResId, float cardScale) {
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

    public interface RowFreezer {
        void freeze(boolean freeze);
    }

    public static VideoGroupObjectAdapter findRelatedAdapter(Map<Integer, VideoGroupObjectAdapter> mediaGroupAdapters, VideoGroup group, RowFreezer freezer) {
        if (group == null || group.getMediaGroup() == null || mediaGroupAdapters == null) {
            return null;
        }

        int mediaGroupId = group.getId();

        VideoGroupObjectAdapter existingAdapter = mediaGroupAdapters.get(mediaGroupId);

        // Find out could we continue an existing one (vertical scroll YouTube layout)
        if (existingAdapter == null) {
            Float value = sMaxColsNum.get(group.isShorts() ? R.dimen.shorts_card_width : R.dimen.card_width);
            int minAdapterSize = value != null ? value.intValue() : MIN_ADAPTER_SIZE;

            for (VideoGroupObjectAdapter adapter : mediaGroupAdapters.values()) {
                if (isMatchedRowFound(adapter, group)) {
                    // Remain other rows of the same type untitled (usually the such rows share the same titles)
                    group.setTitle(null);

                    if (adapter.size() < minAdapterSize && group.getSize() < minAdapterSize) {
                        int missingCount = minAdapterSize - adapter.size();
                        if (group.getSize() > missingCount) {
                            // Split the group to match 'minAdapterSize'
                            VideoGroup missingGroup = VideoGroup.from(group.getVideos().subList(0, missingCount));
                            group.removeAllBefore(missingCount);
                            freezer.freeze(true);
                            adapter.add(missingGroup);
                            freezer.freeze(false);
                        } else {
                            existingAdapter = adapter; // continue inside a caller
                        }
                        break;
                    }
                }
            }
        }

        return existingAdapter;
    }

    private static boolean isMatchedRowFound(VideoGroupObjectAdapter adapter, VideoGroup group) {
        VideoGroup lastGroup = adapter.getAll().get(adapter.size() - 1).getGroup();
        boolean matchedRowFound = lastGroup != null
                && lastGroup.getMediaGroup() != null
                && lastGroup.getMediaGroup().getNextPageKey() == null
                && group.getMediaGroup().getNextPageKey() == null
                && lastGroup.isShorts() == group.isShorts()
                && (Helpers.equals(lastGroup.getTitle(), group.getTitle())
                    || lastGroup.getTitle() == null); // we could set title to null in the previous iteration
        return matchedRowFound;
    }
}
