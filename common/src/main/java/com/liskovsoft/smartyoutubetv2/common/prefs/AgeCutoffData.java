package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/** Preference: hide videos older than N (discovery only); months use 30-day buckets. */
public class AgeCutoffData implements ProfileChangeListener {
    private static final String AGE_CUTOFF_DATA = "age_cutoff_data";
    /** ~30 days in ms; used for all "month" options. */
    private static final long MS_MONTH = 30L * 24 * 60 * 60 * 1000;
    private static final long MS_DAY = 24L * 60 * 60 * 1000;
    private static final long MS_WEEK = 7L * MS_DAY;

    public static final int INDEX_OFF = 0;
    public static final int INDEX_DAY = 1;
    public static final int INDEX_WEEK = 2;
    /** First index that maps to {@link #MONTH_COUNTS}[0] (1 month). */
    public static final int INDEX_FIRST_MONTH = 3;

    /** Month options for UI; index i matches {@link #INDEX_FIRST_MONTH} + i. */
    public static final int[] MONTH_COUNTS = {
            1, 2, 3, 4, 5, 6, 9, 12, 18, 24, 36, 48, 60, 72
    };

    /** Inclusive: 0 .. INDEX_LAST. */
    public static final int INDEX_LAST = INDEX_FIRST_MONTH + MONTH_COUNTS.length - 1;

    @SuppressLint("StaticFieldLeak")
    private static AgeCutoffData sInstance;
    private final AppPrefs mPrefs;
    private final Context mAppContext;
    private int mIndex;
    private final Runnable mPersistInt = this::persistInt;

    private AgeCutoffData(Context context) {
        mAppContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreData();
    }

    public static AgeCutoffData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AgeCutoffData(context.getApplicationContext());
        }
        return sInstance;
    }

    public int getAgeCutoffIndex() {
        return mIndex;
    }

    public void setAgeCutoffIndex(int index) {
        if (index < INDEX_OFF) {
            index = INDEX_OFF;
        } else if (index > INDEX_LAST) {
            index = INDEX_LAST;
        }
        if (mIndex == index) {
            return;
        }
        mIndex = index;
        persistData();
        onAgeCutoffSettingChanged();
    }

    private void onAgeCutoffSettingChanged() {
        YouTubeServiceManager.instance().invalidateCache();
        MediaServiceManager.instance().clearBrowseContinuationCaches();
        Utils.post(() -> BrowsePresenter.instance(mAppContext).refresh());
    }

    public boolean isAgeCutoffEnabled() {
        return mIndex > INDEX_OFF;
    }

    /**
     * Cutoff window in ms; 0 when disabled.
     */
    public long getCutoffDurationMs() {
        if (mIndex <= INDEX_OFF) {
            return 0;
        }
        if (mIndex == INDEX_DAY) {
            return MS_DAY;
        }
        if (mIndex == INDEX_WEEK) {
            return MS_WEEK;
        }
        int monthOrdinal = mIndex - INDEX_FIRST_MONTH;
        if (monthOrdinal < 0 || monthOrdinal >= MONTH_COUNTS.length) {
            return 0;
        }
        return MONTH_COUNTS[monthOrdinal] * MS_MONTH;
    }

    public long getMinPublishedTimeMs() {
        long d = getCutoffDurationMs();
        if (d <= 0) {
            return Long.MIN_VALUE;
        }
        return System.currentTimeMillis() - d;
    }

    /**
     * Discovery-only: no filtering in history, playlist hub, queue, my videos, or {@link VideoGroup#isSkipAgeCutoff()}.
     */
    public boolean shouldApplyAgeCutoffToGroup(VideoGroup group) {
        if (group == null || !isAgeCutoffEnabled()) {
            return false;
        }
        if (group.isSkipAgeCutoff()) {
            return false;
        }
        int type = group.getType();
        if (type == MediaGroup.TYPE_HISTORY
                || type == MediaGroup.TYPE_USER_PLAYLISTS
                || type == MediaGroup.TYPE_PLAYBACK_QUEUE
                || type == MediaGroup.TYPE_MY_VIDEOS) {
            return false;
        }
        return true;
    }

    public boolean shouldFilterOutVideo(VideoGroup group, Video video) {
        if (video == null || !shouldApplyAgeCutoffToGroup(group)) {
            return false;
        }
        if (video.isLive || video.isUpcoming) {
            return false;
        }
        long pub = video.getPublishedMs();
        if (pub <= 0) {
            return false;
        }
        return pub < getMinPublishedTimeMs();
    }

    public boolean shouldConsiderVisibleCountForContinuation(VideoGroup group) {
        return isAgeCutoffEnabled() && shouldApplyAgeCutoffToGroup(group);
    }

    private void restoreData() {
        String data = mPrefs.getProfileData(AGE_CUTOFF_DATA);
        String[] split = Helpers.splitData(data);
        mIndex = Helpers.parseInt(split, 0, INDEX_OFF);
        if (mIndex < INDEX_OFF || mIndex > INDEX_LAST) {
            mIndex = INDEX_OFF;
        }
    }

    private void persistData() {
        Utils.postDelayed(mPersistInt, 500);
    }

    private void persistInt() {
        mPrefs.setProfileData(AGE_CUTOFF_DATA, Helpers.mergeData(mIndex));
    }

    @Override
    public void onProfileChanged() {
        Utils.removeCallbacks(mPersistInt);
        restoreData();
    }
}