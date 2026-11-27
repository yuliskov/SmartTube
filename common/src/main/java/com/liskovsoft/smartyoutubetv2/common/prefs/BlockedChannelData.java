package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class BlockedChannelData implements ProfileChangeListener {
    private static final String BLOCKED_CHANNEL_DATA = "blocked_channel_data";
    @SuppressLint("StaticFieldLeak")
    private static BlockedChannelData sInstance;
    private final AppPrefs mPrefs;
    private final List<Channel> mChannels = new ArrayList<>();
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

        public boolean matches(Channel channel) {
            if (channelId != null && channel.channelId != null) {
                return Helpers.equals(channelId, channel.channelId);
            }

            return channelName != null && channel.channelName != null &&
                    Helpers.compareToIgnoreCase(channelName, channel.channelName) == 0;
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
        removeMatches(channel);
        mChannels.add(channel);

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

        Channel channel = new Channel(channelId, channelName);
        removeMatches(channel);

        persistState();
        notifyListeners();
    }

    private void removeMatches(Channel channel) {
        Iterator<Channel> iterator = mChannels.iterator();

        while (iterator.hasNext()) {
            if (channel.matches(iterator.next())) {
                iterator.remove();
            }
        }
    }

    /**
     * Check if a channel is blocked
     */
    public boolean containsChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName) || mChannels.isEmpty()) {
            return false;
        }

        Channel channel = new Channel(channelId, channelName);
        for (Channel item : mChannels) {
            if (channel.matches(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all blocked channels with their names
     *
     * @return List of channelId -> channel name
     */
    public List<Pair<String, String>> getChannelIdsWithNames() {
        List<Pair<String, String>> result = new ArrayList<>();

        List<Channel> channels = new ArrayList<>(mChannels);
        Collections.sort(channels, (channel1, channel2) -> Helpers.compareToIgnoreCase(channel1.channelName, channel2.channelName));

        for (Channel item : channels) {
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
        persistState();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(BLOCKED_CHANNEL_DATA);

        String[] split = Helpers.splitData(data);

        mChannels.clear();
        mChannels.addAll(Helpers.parseList(split, 0, Channel::fromString));
        // null

        restoreOldData(split);
    }

    private void restoreOldData(String[] split) {
        Map<String, String> channelIdsWithNames = Helpers.parseMap(split, 1, Helpers::parseStr, Helpers::parseStr);

        if (!channelIdsWithNames.isEmpty()) {
            for (Entry<String, String> entry : channelIdsWithNames.entrySet()) {
                Channel channel = new Channel(entry.getKey(), entry.getValue());
                if (!containsChannel(channel.channelId, channel.channelName)) {
                    mChannels.add(channel);
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