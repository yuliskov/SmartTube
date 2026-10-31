package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SponsorBlockController.SegmentAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SponsorBlockData {
    public static final int ACTION_UNDEFINED = -1;
    public static final int ACTION_SKIP_ONLY = 0;
    public static final int ACTION_SKIP_WITH_TOAST = 1;
    public static final int ACTION_SHOW_DIALOG = 2;
    public static final int ACTION_DO_NOTHING = 3;
    private static final String SPONSOR_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static SponsorBlockData sInstance;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;
    private final Set<String> mColorCategories = new LinkedHashSet<>();
    private final Set<SegmentAction> mActions = new LinkedHashSet<>();
    private final Set<String> mExcludedChannels = new LinkedHashSet<>();
    private boolean mIsDontSkipSegmentAgainEnabled;
    private boolean mIsPaidContentNotificationEnabled;
    private long mIgnoredDurationMs;
    private Map<String, Integer> mSegmentLocalizedMapping;
    private Map<String, Integer> mSegmentDescriptionMapping;
    private Map<String, Integer> mSegmentColorMapping;
    private Set<String> mAllCategories;

    private SponsorBlockData(Context context) {
        mAppPrefs = AppPrefs.instance(context);
        initLocalizedMapping();
        initColorMapping();
        initAllCategories();
        restoreState();
    }

    public static SponsorBlockData instance(Context context) {
        if (sInstance == null) {
            sInstance = new SponsorBlockData(context.getApplicationContext());
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
        mSegmentLocalizedMapping.put(SponsorSegment.CATEGORY_HOOK, R.string.content_block_hook);

        mSegmentDescriptionMapping = new HashMap<>();
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_SPONSOR, R.string.content_block_sponsor_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_INTRO, R.string.content_block_intro_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_OUTRO, R.string.content_block_outro_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_SELF_PROMO, R.string.content_block_self_promo_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_INTERACTION, R.string.content_block_interaction_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, R.string.content_block_music_off_topic_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_PREVIEW_RECAP, R.string.content_block_preview_recap_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_POI_HIGHLIGHT, R.string.content_block_highlight_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_FILLER, R.string.content_block_filler_desc);
        mSegmentDescriptionMapping.put(SponsorSegment.CATEGORY_HOOK, R.string.content_block_hook_desc);
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
        mSegmentColorMapping.put(SponsorSegment.CATEGORY_HOOK, R.color.sponsorblock_hook);
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
        mAllCategories.add(SponsorSegment.CATEGORY_HOOK);
    }

    public Integer getLocalizedRes(String segmentCategory) {
        return mSegmentLocalizedMapping.get(segmentCategory);
    }

    public Integer getDescriptionRes(String segmentCategory) {
        return mSegmentDescriptionMapping.get(segmentCategory);
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
            if (isSkipEnabled(action)) {
                enabledCategories.add(action.segmentCategory);
            }
        }

        enabledCategories.addAll(mColorCategories);

        return Collections.unmodifiableSet(enabledCategories);
    }

    public void enableColorMarker(String segmentCategory) {
        if (!mAllCategories.contains(segmentCategory)) {
            return;
        }

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

    public void setAction(String segmentCategory, int actionType) {
        for (SegmentAction action : mActions) {
            if (Helpers.equals(action.segmentCategory, segmentCategory)) {
                action.actionType = validateActionType(actionType);
                break;
            }
        }

        persistState();
    }

    public boolean isSponsorBlockEnabled() {
        return mIsSponsorBlockEnabled;
    }

    public void setSponsorBlockEnabled(boolean enabled) {
        mIsSponsorBlockEnabled = enabled;
        persistState();
    }

    public boolean isActionsEnabled() {
        for (SegmentAction action : mActions) {
            if (isSkipEnabled(action)) {
                return true;
            }
        }

        return false;
    }

    public boolean isDontSkipSegmentAgainEnabled() {
        return mIsDontSkipSegmentAgainEnabled;
    }

    public void setDontSkipSegmentAgainEnabled(boolean enabled) {
        mIsDontSkipSegmentAgainEnabled = enabled;
        persistState();
    }

    public boolean isPaidContentNotificationEnabled() {
        return mIsPaidContentNotificationEnabled;
    }

    public void setPaidContentNotificationEnabled(boolean enabled) {
        mIsPaidContentNotificationEnabled = enabled;
        persistState();
    }

    public long getIgnoredDurationMs() {
        return mIgnoredDurationMs;
    }

    public void setIgnoredDurationMs(long durationMs) {
        mIgnoredDurationMs = durationMs;
        persistState();
    }

    public boolean isAltServerEnabled() {
        return GlobalPreferences.instance(mAppPrefs.getContext()).isContentBlockAltServerEnabled();
    }

    public void enableAltServer(boolean enabled) {
        GlobalPreferences.instance(mAppPrefs.getContext()).setContentBlockAltServerEnabled(enabled);
    }

    private void restoreState() {
        String data = mAppPrefs.getData(SPONSOR_BLOCK_DATA);

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
        mIgnoredDurationMs = Helpers.parseLong(split, 11, 5_000);

        if (colorCategories != null) {
            String[] categoriesArr = Helpers.splitArray(colorCategories);

            mColorCategories.clear();

            for (String category : categoriesArr) {
                if (mAllCategories.contains(category)) {
                    mColorCategories.add(category);
                }
            }
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

        restoreActions(actions);

        // Easy add new segments
        for (String segmentCategory : mAllCategories) {
            if (getAction(segmentCategory) == ACTION_UNDEFINED) {
                // Keep these categories opt-in.
                if (SponsorSegment.CATEGORY_FILLER.equals(segmentCategory) ||
                        SponsorSegment.CATEGORY_HOOK.equals(segmentCategory)) {
                    mActions.add(SegmentAction.from(segmentCategory, ACTION_DO_NOTHING));
                } else {
                    mActions.add(SegmentAction.from(segmentCategory, ACTION_SKIP_WITH_TOAST));
                }
            }
        }
    }

    private void restoreActions(String actions) {
        mActions.clear();
        if (actions == null) {
            return;
        }

        Map<String, Integer> restoredActions = new LinkedHashMap<>();
        for (String action : Helpers.splitArray(actions)) {
            String[] parts = action.split(",", -1);
            String category = parts[0];
            if (!mAllCategories.contains(category)) {
                continue;
            }

            int actionType = ACTION_DO_NOTHING;
            if (parts.length == 2) {
                try {
                    actionType = validateActionType(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // A malformed saved value must not enable skipping.
                }
            }

            Integer previous = restoredActions.get(category);
            if (previous != null && previous != actionType) {
                actionType = ACTION_DO_NOTHING;
            }
            restoredActions.put(category, actionType);
        }

        for (Map.Entry<String, Integer> action : restoredActions.entrySet()) {
            mActions.add(SegmentAction.from(action.getKey(), action.getValue()));
        }
    }

    private static int validateActionType(int actionType) {
        return actionType >= ACTION_SKIP_ONLY && actionType <= ACTION_DO_NOTHING
                ? actionType : ACTION_DO_NOTHING;
    }

    private static boolean isSkipEnabled(SegmentAction action) {
        return action.actionType >= ACTION_SKIP_ONLY && action.actionType < ACTION_DO_NOTHING &&
                !SponsorSegment.CATEGORY_POI_HIGHLIGHT.equals(action.segmentCategory);
    }

    private void persistState() {
        String colorCategories = Helpers.mergeArray(mColorCategories.toArray());
        String actions = Helpers.mergeArray(mActions.toArray());
        String excludedChannels = Helpers.mergeArray(mExcludedChannels.toArray());

        mAppPrefs.setData(SPONSOR_BLOCK_DATA, Helpers.mergeData(
                mIsSponsorBlockEnabled, null, null, null,
                null, null, actions, colorCategories, mIsDontSkipSegmentAgainEnabled,
                excludedChannels, mIsPaidContentNotificationEnabled, mIgnoredDurationMs
        ));
    }
}
