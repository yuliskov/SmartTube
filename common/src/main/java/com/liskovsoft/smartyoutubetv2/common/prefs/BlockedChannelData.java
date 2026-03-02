package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockedChannelData implements ProfileChangeListener {
    private static final String BLOCKED_CHANNEL_DATA = "blocked_channel_data";
    @SuppressLint("StaticFieldLeak")
    private static BlockedChannelData sInstance;
    private final AppPrefs mPrefs;
    private Set<String> mChannelIds;
    private Map<String, String> mChannelIdsWithNames;
    private final Runnable mPersistStateInt = this::persistStateInt;
    private final List<BlockedChannelListener> mListeners = new ArrayList<>();

    public interface BlockedChannelListener {
        void onChanged();
    }

    private BlockedChannelData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static BlockedChannelData instance(Context context) {
        if (sInstance == null) {
            sInstance = new BlockedChannelData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Add a channel to the blacklist
     *
     * @param channelId   The channel ID to blacklist
     * @param channelName The channel name (optional, for display purposes)
     */
    public void addChannel(String channelId, String channelName) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }

        mChannelIds.add(channelId);
        if (channelName != null && !channelName.isEmpty()) {
            mChannelIdsWithNames.put(channelId, channelName);
        }
        persistState();
        notifyListeners();
    }

    /**
     * Remove a channel from the blacklist
     *
     * @param channelId The channel ID to remove
     */
    public void removeChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }

        mChannelIds.remove(channelId);
        mChannelIdsWithNames.remove(channelId);
        persistState();
        notifyListeners();
    }

    /**
     * Check if a channel is blacklisted
     *
     * @param channelId The channel ID to check
     * @return true if the channel is blacklisted
     */
    public boolean containsChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return false;
        }

        return mChannelIds.contains(channelId);
    }

    /**
     * Get all blacklisted channel IDs
     *
     * @return Unmodifiable set of blacklisted channel IDs
     */
    public Set<String> getChannelIds() {
        return Collections.unmodifiableSet(mChannelIds);
    }

    /**
     * Get the name of a blacklisted channel
     *
     * @param channelId The channel ID
     * @return The channel name, or null if not available
     */
    public String getChannelName(String channelId) {
        return mChannelIdsWithNames.get(channelId);
    }

    /**
     * Get all blacklisted channels with their names
     *
     * @return Unmodifiable map of channelId -> channel name
     */
    public Map<String, String> getChannelIdsWithNames() {
        return Collections.unmodifiableMap(mChannelIdsWithNames);
    }

    /**
     * Get the count of blacklisted channels
     *
     * @return Number of blacklisted channels
     */
    public int getChannelCount() {
        return mChannelIds.size();
    }

    /**
     * Clear all blacklisted channels
     */
    public void clear() {
        mChannelIds.clear();
        mChannelIdsWithNames.clear();
        persistState();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(BLOCKED_CHANNEL_DATA);

        String[] split = Helpers.splitData(data);

        List<String> channelIdList = Helpers.parseStrList(split, 0);
        mChannelIdsWithNames = Helpers.parseMap(split, 1, Helpers::parseStr, Helpers::parseStr);

        mChannelIds = new HashSet<>(channelIdList);
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        // Convert Set to List for persistence
        List<String> channelIdList = new ArrayList<>(mChannelIds);
        mPrefs.setProfileData(BLOCKED_CHANNEL_DATA, Helpers.mergeData(
                channelIdList, mChannelIdsWithNames));
    }

    public void addListener(BlockedChannelListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(BlockedChannelListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (BlockedChannelListener listener : mListeners) {
            listener.onChanged();
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}