package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.ContentBlockManager.SegmentAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ContentBlockData {
    public static final String SPONSOR_BLOCK_NAME = "SponsorBlock";
    public static final String SPONSOR_BLOCK_URL = "https://sponsor.ajay.app";
    public static final int ACTION_SKIP_ONLY = 0;
    public static final int ACTION_SKIP_WITH_TOAST = 1;
    public static final int ACTION_SHOW_DIALOG = 2;
    public static final int ACTION_DO_NOTHING = 3;
    private static final String CONTENT_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static ContentBlockData sInstance;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;
    private final Set<String> mCategories = new LinkedHashSet<>();
    private final Set<SegmentAction> mActions = new LinkedHashSet<>();
    private boolean mIsSkipEachSegmentOnceEnabled;
    private boolean mIsColorMarkersEnabled;
    private Map<String, Integer> mSegmentLocalizedMapping;
    private Map<String, Integer> mSegmentColorMapping;

    private ContentBlockData(Context context) {
        mAppPrefs = AppPrefs.instance(context);
        initLocalizedMapping();
        initColorMapping();
        restoreState();
    }

    public static ContentBlockData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ContentBlockData(context.getApplicationContext());
        }

        return sInstance;
    }

    private void initLocalizedMapping() {
        mSegmentLocalizedMapping = new HashMap<>();
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_SPONSOR, R.string.content_block_sponsor);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_INTRO, R.string.content_block_intro);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_OUTRO, R.string.content_block_outro);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_SELF_PROMO, R.string.content_block_self_promo);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_INTERACTION, R.string.content_block_interaction);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, R.string.content_block_music_off_topic);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_PREVIEW_RECAP, R.string.content_block_preview_recap);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_HIGHLIGHT, R.string.content_block_highlight);
    }

    private void initColorMapping() {
        mSegmentColorMapping = new HashMap<>();
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_SPONSOR, R.color.green);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_INTRO, R.color.cyan);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_OUTRO, R.color.blue);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_SELF_PROMO, R.color.yellow);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_INTERACTION, R.color.magenta);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, R.color.brown);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_PREVIEW_RECAP, R.color.light_blue);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_HIGHLIGHT, R.color.white);
    }

    public Map<String, Integer> getLocalizedResMapping() {
        return mSegmentLocalizedMapping;
    }

    public Map<String, Integer> getColorResMapping() {
        return mSegmentColorMapping;
    }

    public boolean isSponsorBlockEnabled() {
        return mIsSponsorBlockEnabled;
    }

    public void enableSponsorBlock(boolean enabled) {
        mIsSponsorBlockEnabled = enabled;
        persistData();
    }

    public Set<String> getCategories() {
        return mCategories;
    }

    public void addCategory(String categoryKey) {
        mCategories.add(categoryKey);
        persistData();
    }

    public void removeCategory(String categoryKey) {
        mCategories.remove(categoryKey);
        persistData();
    }

    public Set<SegmentAction> getActions() {
        return Collections.unmodifiableSet(mActions);
    }

    public int getAction(String segmentCategory) {
        for (SegmentAction action : mActions) {
            if (Helpers.equals(action.segmentCategory, segmentCategory)) {
                return action.actionType;
            }
        }

        return ACTION_DO_NOTHING;
    }

    public void persistActions() {
        persistData();
    }

    public boolean isAnyActionEnabled() {
        for (SegmentAction action : mActions) {
            if (action.actionType != ACTION_DO_NOTHING) {
                return true;
            }
        }

        return false;
    }

    public boolean isSkipEachSegmentOnceEnabled() {
        return mIsSkipEachSegmentOnceEnabled;
    }

    public void enableSkipEachSegmentOnce(boolean enabled) {
        mIsSkipEachSegmentOnceEnabled = enabled;
        persistData();
    }

    public boolean isColorMarkersEnabled() {
        return mIsColorMarkersEnabled;
    }

    public void enableColorMarkers(boolean enabled) {
        mIsColorMarkersEnabled = enabled;
        persistData();
    }

    private void restoreState() {
        String data = mAppPrefs.getData(CONTENT_BLOCK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsSponsorBlockEnabled = Helpers.parseBoolean(split, 0, VERSION.SDK_INT > 19); // Android 4 may have memory problems
        String categories = Helpers.parseStr(split, 2);
        mIsSkipEachSegmentOnceEnabled = Helpers.parseBoolean(split, 3, true);
        mIsColorMarkersEnabled = Helpers.parseBoolean(split, 4, true);
        String actions = Helpers.parseStr(split, 6);

        if (categories != null) {
            String[] categoriesArr = Helpers.splitArray(categories);

            mCategories.clear();

            mCategories.addAll(Arrays.asList(categoriesArr));
        } else {
            mCategories.clear();

            mCategories.addAll(Arrays.asList(
                        SponsorSegment.CATEGORY_SPONSOR,
                        SponsorSegment.CATEGORY_INTRO,
                        SponsorSegment.CATEGORY_OUTRO,
                        SponsorSegment.CATEGORY_INTERACTION,
                        SponsorSegment.CATEGORY_SELF_PROMO,
                        SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC,
                        SponsorSegment.CATEGORY_PREVIEW_RECAP,
                        SponsorSegment.CATEGORY_HIGHLIGHT
                    )
            );
        }

        if (actions != null) {
            String[] actionsArr = Helpers.splitArray(actions);

            mActions.clear();

            for (String action : actionsArr) {
                mActions.add(SegmentAction.from(action));
            }
        } else {
            mActions.clear();

            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_SPONSOR, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_INTRO, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_OUTRO, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_INTERACTION, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_SELF_PROMO, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_PREVIEW_RECAP, ACTION_SKIP_WITH_TOAST));
            mActions.add(SegmentAction.from(SponsorSegment.CATEGORY_HIGHLIGHT, ACTION_SKIP_WITH_TOAST));
        }
    }

    private void persistData() {
        String categories = Helpers.mergeArray(mCategories.toArray());
        String actions = Helpers.mergeArray(mActions.toArray());

        mAppPrefs.setData(CONTENT_BLOCK_DATA, Helpers.mergeObject(
                mIsSponsorBlockEnabled, null, categories, mIsSkipEachSegmentOnceEnabled, mIsColorMarkersEnabled, null, actions
        ));
    }
}
