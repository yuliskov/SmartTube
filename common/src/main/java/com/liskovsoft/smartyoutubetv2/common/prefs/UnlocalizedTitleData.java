package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class UnlocalizedTitleData {
    private static final String UNLOCALIZED_TITLE_DATA = "unlocalized_title_data";
    private static final int MAX_ENTRIES = 5000;
    @SuppressLint("StaticFieldLeak")
    private static UnlocalizedTitleData sInstance;
    private final AppPrefs mPrefs;
    private LinkedHashMap<String, String> mTitleCache; // videoId -> original title
    private final Runnable mPersistStateInt = this::persistStateInt;
    private boolean mIsDirty;

    private UnlocalizedTitleData(Context context) {
        mPrefs = AppPrefs.instance(context);
        restoreState();
    }

    public static UnlocalizedTitleData instance(Context context) {
        if (sInstance == null) {
            sInstance = new UnlocalizedTitleData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Get a cached original title for the given video ID.
     * @return the original title, or null if not cached
     */
    public String getTitle(String videoId) {
        if (videoId == null || mTitleCache == null) {
            return null;
        }

        return mTitleCache.get(videoId);
    }

    /**
     * Cache an original title for the given video ID.
     * Evicts oldest entries (FIFO) when the cache exceeds MAX_ENTRIES.
     */
    public void putTitle(String videoId, String title) {
        if (videoId == null || title == null) {
            return;
        }

        mTitleCache.put(videoId, title);

        // FIFO eviction: remove oldest entries when exceeding limit
        while (mTitleCache.size() > MAX_ENTRIES) {
            String oldestKey = mTitleCache.keySet().iterator().next();
            mTitleCache.remove(oldestKey);
        }

        mIsDirty = true;
        persistState();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getData(UNLOCALIZED_TITLE_DATA);

        mTitleCache = new LinkedHashMap<>();

        Map<String, String> parsed = Helpers.parseMap(data, Helpers::parseStr, Helpers::parseStr);

        if (!parsed.isEmpty()) {
            mTitleCache.putAll(parsed);
        }
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        if (!mIsDirty) {
            return;
        }

        mPrefs.setData(UNLOCALIZED_TITLE_DATA, Helpers.mergeMap(mTitleCache));
        mIsDirty = false;
    }

    /**
     * Force immediate persistence (e.g. before app exit).
     */
    public void persistNow() {
        Utils.post(mPersistStateInt);
    }
}
