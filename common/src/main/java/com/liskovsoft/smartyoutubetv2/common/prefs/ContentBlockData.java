package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ContentBlockController.SegmentAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ContentBlockData {
    public static final int ACTION_UNDEFINED = -1;
    public static final int ACTION_SKIP_ONLY = 0;
    public static final int ACTION_SKIP_WITH_TOAST = 1;
    public static final int ACTION_SHOW_DIALOG = 2;
    public static final int ACTION_DO_NOTHING = 3;
    private static final String CONTENT_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static ContentBlockData sInstance;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;
    private final Set<String> mColorCategories = new LinkedHashSet<>();
    private final Set<SegmentAction> mActions = new LinkedHashSet<>();
    private final Set<String> mExcludedChannels = new LinkedHashSet<>();
    private boolean mIsDontSkipSegmentAgainEnabled;
    private boolean mIsPaidContentNotificationEnabled;
    private Map<String, Integer> mSegmentLocalizedMapping;
    private Map<String, Integer> mSegmentColorMapping;
    private Set<String> mAllCategories;

    private ContentBlockData(Context context) {
        mAppPrefs = AppPrefs.instance(context);
        initLocalizedMapping();
        initColorMapping();
        initAllCategories();
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
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_POI_HIGHLIGHT, R.string.content_block_highlight);
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_FILLER, R.string.content_block_filler);
    }

    private void initColorMapping() {
        mSegmentColorMapping = new HashMap<>();
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_SPONSOR, R.color.green);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_INTRO, R.color.cyan);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_OUTRO, R.color.blue);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_SELF_PROMO, R.color.yellow);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_INTERACTION, R.color.magenta);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, R.color.orange_peel);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_PREVIEW_RECAP, R.color.light_blue);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_POI_HIGHLIGHT, R.color.light_pink);
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_FILLER, R.color.electric_violet);
    }

    private void initAllCategories() {
        mAllCategories = new LinkedHashSet<>();
        mAllCategories.add(SponsorSegment.CATEGORY_SPONSOR);
        mAllCategories.add(SponsorSegment.CATEGORY_INTRO);
        mAllCategories.add(SponsorSegment.CATEGORY_OUTRO);
        mAllCategories.add(SponsorSegment.CATEGORY_INTERACTION);
        mAllCategories.add(SponsorSegment.CATEGORY_SELF_PROMO);
        mAllCategories.add(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC);
        mAllCategories.add(SponsorSegment.CATEGORY_PREVIEW_RECAP);
        mAllCategories.add(SponsorSegment.CATEGORY_POI_HIGHLIGHT);
        mAllCategories.add(SponsorSegment.CATEGORY_FILLER);
    }

    public Integer getLocalizedRes(String segmentCategory) {
        return mSegmentLocalizedMapping.get(segmentCategory);
    }

    public Integer getColorRes(String segmentCategory) {
        return mSegmentColorMapping.get(segmentCategory);
    }

    public Set<String> getAllCategories() {
        return Collections.unmodifiableSet(mAllCategories);
    }

    public Set<String> getEnabledCategories() {
        Set<String> enabledCategories = new HashSet<>();

        for (SegmentAction action : mActions) {
            if (action.actionType != ACTION_DO_NOTHING) {
                enabledCategories.add(action.segmentCategory);
            }
        }

        enabledCategories.addAll(mColorCategories);

        return Collections.unmodifiableSet(enabledCategories);
    }

    public void enableColorMarker(String segmentCategory) {
        mColorCategories.add(segmentCategory);
        persistState();
    }

    public void disableColorMarker(String segmentCategory) {
        mColorCategories.remove(segmentCategory);
        persistState();
    }

    public boolean isColorMarkerEnabled(String segmentCategory) {
        return mColorCategories.contains(segmentCategory);
    }

    public boolean isColorMarkersEnabled() {
        return !mColorCategories.isEmpty();
    }

    public void excludeChannel(String channelId) {
        mExcludedChannels.add(channelId);
        persistState();
    }

    public void stopExcludingChannel(String channelId) {
        mExcludedChannels.remove(channelId);
        persistState();
    }

    public boolean isChannelExcluded(String channelId) {
        return mExcludedChannels.contains(channelId);
    }

    public void toggleExcludeChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        if (isChannelExcluded(channelId)) {
            stopExcludingChannel(channelId);
        } else {
            excludeChannel(channelId);
        }
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

        return ACTION_UNDEFINED;
    }

    public boolean isSponsorBlockEnabled() {
        return mIsSponsorBlockEnabled;
    }

    public void enableSponsorBlock(boolean enabled) {
        mIsSponsorBlockEnabled = enabled;
        persistState();
    }

    public void persistActions() {
        persistState();
    }

    public boolean isActionsEnabled() {
        for (SegmentAction action : mActions) {
            if (action.actionType != ACTION_DO_NOTHING) {
                return true;
            }
        }

        return false;
    }

    public boolean isDontSkipSegmentAgainEnabled() {
        return mIsDontSkipSegmentAgainEnabled;
    }

    public void enableDontSkipSegmentAgain(boolean enabled) {
        mIsDontSkipSegmentAgainEnabled = enabled;
        persistState();
    }

    public boolean isPaidContentNotificationEnabled() {
        return mIsPaidContentNotificationEnabled;
    }

    public void enablePaidContentNotification(boolean enabled) {
        mIsPaidContentNotificationEnabled = enabled;
        persistState();
    }

    public boolean isAltServerEnabled() {
        return GlobalPreferences.instance(mAppPrefs.getContext()).isContentBlockAltServerEnabled();
    }

    public void enableAltServer(boolean enabled) {
        GlobalPreferences.instance(mAppPrefs.getContext()).enableContentBlockAltServer(enabled);
    }

    private void restoreState() {
        String data = mAppPrefs.getData(CONTENT_BLOCK_DATA);

        String[] split = Helpers.splitData(data);

        mIsSponsorBlockEnabled = Helpers.parseBoolean(split, 0, VERSION.SDK_INT > 19); // Android 4 may have memory problems
        // categories: index 2
        // don't skip segment
        // colorMarkers: index 4
        String actions = Helpers.parseStr(split, 6);
        String colorCategories = Helpers.parseStr(split, 7);
        mIsDontSkipSegmentAgainEnabled = Helpers.parseBoolean(split, 8, false);
        String excludedChannels = Helpers.parseStr(split, 9);
        mIsPaidContentNotificationEnabled = Helpers.parseBoolean(split, 10, false);

        if (colorCategories != null) {
            String[] categoriesArr = Helpers.splitArray(colorCategories);

            mColorCategories.clear();

            mColorCategories.addAll(Arrays.asList(categoriesArr));
        } else {
            mColorCategories.clear();

            mColorCategories.addAll(mAllCategories);
        }

        if (excludedChannels != null) {
            String[] channelsArr = Helpers.splitArray(excludedChannels);

            mExcludedChannels.clear();

            mExcludedChannels.addAll(Arrays.asList(channelsArr));
        } else {
            mExcludedChannels.clear();
        }

        if (actions != null) {
            String[] actionsArr = Helpers.splitArray(actions);

            mActions.clear();

            for (String action : actionsArr) {
                mActions.add(SegmentAction.from(action));
            }
        }

        // Easy add new segments
        for (String segmentCategory : mAllCategories) {
            if (getAction(segmentCategory) == ACTION_UNDEFINED) {
                // Disable filler category by default
                // This category is very extreme and is recommended to be disabled by default because of that.
                if (SponsorSegment.CATEGORY_FILLER.equals(segmentCategory)) {
                    mActions.add(SegmentAction.from(segmentCategory, ACTION_DO_NOTHING));
                } else {
                    mActions.add(SegmentAction.from(segmentCategory, ACTION_SKIP_WITH_TOAST));
                }
            }
        }
    }

    private void persistState() {
        String colorCategories = Helpers.mergeArray(mColorCategories.toArray());
        String actions = Helpers.mergeArray(mActions.toArray());
        String excludedChannels = Helpers.mergeArray(mExcludedChannels.toArray());

        mAppPrefs.setData(CONTENT_BLOCK_DATA, Helpers.mergeData(
                mIsSponsorBlockEnabled, null, null, null,
                null, null, actions, colorCategories, mIsDontSkipSegmentAgainEnabled,
                excludedChannels, mIsPaidContentNotificationEnabled
        ));
    }
}
