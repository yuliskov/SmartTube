package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.List;

public class ChannelGroup {
    public final int id;
    public final String title;
    public final String iconUrl;
    public final List<Channel> channels;
    private static final String ITEM_DELIM = "&sgi;";
    private static final String LIST_DELIM = "&sga;";

    public static class Channel {
        public final String title;
        public final String iconUrl;
        public final String channelId;
        private static final String ITEM_DELIM = "&ci;";

        public Channel(String title, String iconUrl, String channelId) {
            this.title = title;
            this.iconUrl = iconUrl;
            this.channelId = channelId;
        }

        public static Channel fromString(String spec) {
            String[] split = Helpers.split(ITEM_DELIM, spec);

            String title = Helpers.parseStr(split, 0);
            String groupIconUrl = Helpers.parseStr(split, 1);
            String channelId = Helpers.parseStr(split, 2);

            return new Channel(title, groupIconUrl, channelId);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj instanceof Channel && Helpers.equals(((Channel) obj).channelId, channelId);
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.merge(ITEM_DELIM, title, iconUrl, channelId);
        }
    }

    public ChannelGroup(String title, String iconUrl, Channel channel) {
        this(title, iconUrl, createChannels(channel));
    }

    public ChannelGroup(String title, String iconUrl, List<Channel> channels) {
        this(Helpers.getRandomIndex(Integer.MAX_VALUE), title, iconUrl, channels);
    }

    private ChannelGroup(int id, String title, String iconUrl, List<Channel> channels) {
        this.id = id;
        this.title = title;
        this.iconUrl = iconUrl;
        this.channels = channels;
    }

    private static @NonNull List<Channel> createChannels(Channel channel) {
        List<Channel> channels = new ArrayList<>();
        channels.add(channel);
        return channels;
    }

    public boolean contains(String channelId) {
        return Helpers.containsIf(channels, value -> Helpers.equals(value.channelId, channelId));
    }

    public boolean isEmpty() {
        return channels == null || channels.isEmpty();
    }

    public void add(Channel channel) {
        if (channels != null && !channels.contains(channel)) {
            channels.add(channel);
        }
    }

    public void remove(String channelId) {
        if (channels != null) {
            Helpers.removeIf(channels, value -> Helpers.equals(value.channelId, channelId));
        }
    }

    public static ChannelGroup fromString(String spec) {
        String[] split = Helpers.split(ITEM_DELIM, spec);

        int id = Helpers.parseInt(split, 0);
        String title = Helpers.parseStr(split, 1);
        String groupIconUrl = Helpers.parseStr(split, 2);
        List<Channel> channels = Helpers.parseList(split, 3, LIST_DELIM, Channel::fromString);

        return new ChannelGroup(id, title, groupIconUrl, channels);
    }

    @NonNull
    @Override
    public String toString() {
        return Helpers.merge(ITEM_DELIM, id, title, iconUrl, Helpers.mergeList(LIST_DELIM, channels));
    }
}
