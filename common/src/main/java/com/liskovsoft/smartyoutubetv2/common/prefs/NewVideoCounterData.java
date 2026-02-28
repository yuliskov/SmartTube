package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.HashMap;
import java.util.Map;

public class NewVideoCounterData implements ProfileChangeListener {
    private static final String NEW_VIDEO_COUNTER_DATA = "new_video_counter_data";
    @SuppressLint("StaticFieldLeak")
    private static NewVideoCounterData sInstance;
    private final AppPrefs mPrefs;
    // Map: sectionId -> last known video data (timestamp_videoId)
    private Map<Integer, String> mLastKnownVideoData = new HashMap<>();

    private NewVideoCounterData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static NewVideoCounterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new NewVideoCounterData(context.getApplicationContext());
        }

        return sInstance;
    }

    public String getLastKnownVideoData(int sectionId) {
        return mLastKnownVideoData.get(sectionId);
    }

    public void setLastKnownVideoData(int sectionId, String videoData) {
        if (videoData != null) {
            mLastKnownVideoData.put(sectionId, videoData);
            persistState();
        }
    }

    public void removeChannel(int sectionId) {
        mLastKnownVideoData.remove(sectionId);
        persistState();
    }

    private void restoreState() {
        String data = mPrefs.getProfileData(NEW_VIDEO_COUNTER_DATA);

        mLastKnownVideoData = Helpers.parseMap(data, Helpers::parseInt, val -> val);
    }

    private void persistState() {
        mPrefs.setProfileData(NEW_VIDEO_COUNTER_DATA, Helpers.mergeMap(mLastKnownVideoData));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
