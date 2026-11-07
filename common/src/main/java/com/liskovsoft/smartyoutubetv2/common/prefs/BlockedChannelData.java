package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BlockedChannelData implements ProfileChangeListener {
    private static final String BLOCKED_CHANNEL_DATA = "blocked_channel_data";
    @SuppressLint("StaticFieldLeak")
    private static BlockedChannelData sInstance;
    private final AppPrefs mPrefs;
    private final TreeSet<Channel> mChannels =
            new TreeSet<>((channel1, channel2) -> {
                int result = Helpers.compareToIgnoreCase(channel1.channelName, channel2.channelName);
                if (result != 0) {
                    return result;
                }

                if (channel1.channelId == null) {
                    return channel2.channelId == null ? 0 : -1;
                }

                return channel2.channelId == null ? 1 : channel1.channelId.compareTo(channel2.channelId);
            });
    private final Map<String, Channel> mChannelsById = new HashMap<>();
    private final Map<String, Set<Channel>> mChannelsByName = new TreeMap<>(String::compareToIgnoreCase);
    private final Runnable mPersistStateInt = this::persistStateInt;
    private final List<BlockedChannelListener> mListeners = new ArrayList<>();

    private static class Channel {
        public String channelId;
        public String channelName;

        public Channel(String channelId, String channelName) {
            this.channelId = channelId;
            this.channelName = channelName;
        }

        public static Channel fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 2) {
                return null;
            }

            return new Channel(Helpers.parseStr(split[0]), Helpers.parseStr(split[1]));
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(channelId, channelName);
        }
    }

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
     * Add a channel to the block list
     */
    public void addChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName)) {
            return;
        }

        Channel channel = new Channel(channelId, channelName);
        removeMatchingChannels(channel);
        addStoredChannel(channel);

        persistState();
        notifyListeners();
    }

    /**
     * Remove a channel from the list
     */
    public void removeChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName) || mChannels.isEmpty()) {
            return;
        }

        removeMatchingChannels(new Channel(channelId, channelName));

        persistState();
        notifyListeners();
    }

    /**
     * Check if a channel is blocked
     */
    public boolean containsChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName) || mChannels.isEmpty()) {
            return false;
        }

        Channel channel = new Channel(channelId, channelName);

        if (channelId != null && mChannelsById.containsKey(channelId)) {
            return true;
        }

        Set<Channel> sameName = channelName == null ? null : mChannelsByName.get(channelName);

        if (sameName == null) {
            return false;
        }

        for (Channel item : sameName) {
            if (channelId == null || item.channelId == null) {
                return true;
            }
        }

        return false;
    }

    private void removeMatchingChannels(Channel channel) {
        Set<Channel> matches = new HashSet<>();

        if (channel.channelId != null) {
            Channel sameId = mChannelsById.get(channel.channelId);
            if (sameId != null) {
                matches.add(sameId);
            }
        }

        Set<Channel> sameName = channel.channelName == null ? null : mChannelsByName.get(channel.channelName);

        if (sameName != null) {
            for (Channel item : sameName) {
                if (channel.channelId == null || item.channelId == null) {
                    matches.add(item);
                }
            }
        }

        for (Channel item : matches) {
            removeStoredChannel(item);
        }
    }

    private void addStoredChannel(Channel channel) {
        if (!mChannels.add(channel)) {
            return;
        }

        if (channel.channelId != null) {
            mChannelsById.put(channel.channelId, channel);
        }

        if (channel.channelName != null) {
            mChannelsByName.computeIfAbsent(channel.channelName, key -> new HashSet<>()).add(channel);
        }
    }

    private void removeStoredChannel(Channel channel) {
        mChannels.remove(channel);

        if (channel.channelId != null) {
            mChannelsById.remove(channel.channelId);
        }

        if (channel.channelName != null) {
            Set<Channel> sameName = mChannelsByName.get(channel.channelName);
            if (sameName != null) {
                sameName.remove(channel);
                if (sameName.isEmpty()) {
                    mChannelsByName.remove(channel.channelName);
                }
            }
        }
    }

    /**
     * Get all blocked channels with their names
     *
     * @return List of channelId -> channel name
     */
    public List<Pair<String, String>> getChannelIdsWithNames() {
        List<Pair<String, String>> result = new ArrayList<>();

        for (Channel item : mChannels) {
            result.add(new Pair<>(item.channelId, item.channelName));
        }

        return result;
    }

    /**
     * Get the count of blacklisted channels
     *
     * @return Number of blacklisted channels
     */
    public int getChannelCount() {
        return mChannels.size();
    }

    public boolean isEmpty() {
        return mChannels.isEmpty();
    }

    /**
     * Clear all blacklisted channels
     */
    public void clear() {
        mChannels.clear();
        mChannelsById.clear();
        mChannelsByName.clear();
        persistState();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(BLOCKED_CHANNEL_DATA);

        String[] split = Helpers.splitData(data);

        mChannels.clear();
        mChannelsById.clear();
        mChannelsByName.clear();

        for (Channel channel : Helpers.parseList(split, 0, Channel::fromString)) {
            removeMatchingChannels(channel);
            addStoredChannel(channel);
        }
        // null

        restoreOldData(split);
    }

    private void restoreOldData(String[] split) {
        Map<String, String> channelIdsWithNames = Helpers.parseMap(split, 1, Helpers::parseStr, Helpers::parseStr);

        if (!channelIdsWithNames.isEmpty()) {
            for (Entry<String, String> entry : channelIdsWithNames.entrySet()) {
                Channel channel = new Channel(entry.getKey(), entry.getValue());
                if (!containsChannel(channel.channelId, channel.channelName)) {
                    addStoredChannel(channel);
                }
            }
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
        mPrefs.setProfileData(BLOCKED_CHANNEL_DATA, Helpers.mergeData(
                mChannels, null));
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