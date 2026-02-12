package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlacklistData implements ProfileChangeListener {
    private static final String BLACKLIST_DATA = "blacklist_data";
    @SuppressLint("StaticFieldLeak")
    private static BlacklistData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private Set<String> mBlacklistedChannelIds;
    private Map<String, String> mBlacklistedChannelNames; // channelId -> channel name
    private final Runnable mPersistStateInt = this::persistStateInt;
    private final List<BlacklistChangeListener> mListeners = new ArrayList<>();

    public interface BlacklistChangeListener {
        void onBlacklistChanged();
    }

    private BlacklistData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static BlacklistData instance(Context context) {
        if (sInstance == null) {
            sInstance = new BlacklistData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Add a channel to the blacklist
     * 
     * @param channelId   The channel ID to blacklist
     * @param channelName The channel name (optional, for display purposes)
     */
    public void addBlacklistedChannel(String channelId, String channelName) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }

        mBlacklistedChannelIds.add(channelId);
        if (channelName != null && !channelName.isEmpty()) {
            mBlacklistedChannelNames.put(channelId, channelName);
        }
        persistState();
        notifyListeners();
    }

    /**
     * Remove a channel from the blacklist
     * 
     * @param channelId The channel ID to remove
     */
    public void removeBlacklistedChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }

        mBlacklistedChannelIds.remove(channelId);
        mBlacklistedChannelNames.remove(channelId);
        persistState();
        notifyListeners();
    }

    /**
     * Check if a channel is blacklisted
     * 
     * @param channelId The channel ID to check
     * @return true if the channel is blacklisted
     */
    public boolean isChannelBlacklisted(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return false;
        }

        return mBlacklistedChannelIds.contains(channelId);
    }

    /**
     * Get all blacklisted channel IDs
     * 
     * @return Unmodifiable set of blacklisted channel IDs
     */
    public Set<String> getBlacklistedChannels() {
        return Collections.unmodifiableSet(mBlacklistedChannelIds);
    }

    /**
     * Get the name of a blacklisted channel
     * 
     * @param channelId The channel ID
     * @return The channel name, or null if not available
     */
    public String getBlacklistedChannelName(String channelId) {
        return mBlacklistedChannelNames.get(channelId);
    }

    /**
     * Get all blacklisted channels with their names
     * 
     * @return Unmodifiable map of channelId -> channel name
     */
    public Map<String, String> getBlacklistedChannelsWithNames() {
        return Collections.unmodifiableMap(mBlacklistedChannelNames);
    }

    /**
     * Get the count of blacklisted channels
     * 
     * @return Number of blacklisted channels
     */
    public int getBlacklistedChannelCount() {
        return mBlacklistedChannelIds.size();
    }

    /**
     * Clear all blacklisted channels
     */
    public void clearBlacklist() {
        mBlacklistedChannelIds.clear();
        mBlacklistedChannelNames.clear();
        persistState();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(BLACKLIST_DATA);

        String[] split = Helpers.splitData(data);

        List<String> channelIdList = Helpers.parseStrList(split, 0);
        mBlacklistedChannelNames = Helpers.parseMap(split, 1, Helpers::parseStr, Helpers::parseStr);

        if (channelIdList == null) {
            mBlacklistedChannelIds = new HashSet<>();
        } else {
            mBlacklistedChannelIds = new HashSet<>(channelIdList);
        }

        if (mBlacklistedChannelNames == null) {
            mBlacklistedChannelNames = new HashMap<>();
        }
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        // Convert Set to List for persistence
        List<String> channelIdList = new ArrayList<>(mBlacklistedChannelIds);
        mPrefs.setProfileData(BLACKLIST_DATA, Helpers.mergeData(
                channelIdList,
                mBlacklistedChannelNames));
    }

    public void addListener(BlacklistChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(BlacklistChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (BlacklistChangeListener listener : mListeners) {
            listener.onBlacklistChanged();
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
