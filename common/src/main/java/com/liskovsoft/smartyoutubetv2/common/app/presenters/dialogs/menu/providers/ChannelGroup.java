package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.List;

public class ChannelGroup {
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
        List<Channel> channels = new ArrayList<>();
        channels.add(channel);
        this.title = title;
        this.iconUrl = iconUrl;
        this.channels = channels;
    }

    public ChannelGroup(String title, String iconUrl, List<Channel> channels) {
        this.title = title;
        this.iconUrl = iconUrl;
        this.channels = channels;
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

        String title = Helpers.parseStr(split, 0);
        String groupIconUrl = Helpers.parseStr(split, 1);
        List<Channel> channels = Helpers.parseList(split, 2, LIST_DELIM, Channel::fromString);

        return new ChannelGroup(title, groupIconUrl, channels);
    }

    @NonNull
    @Override
    public String toString() {
        return Helpers.merge(ITEM_DELIM, title, iconUrl, Helpers.mergeList(LIST_DELIM, channels));
    }
}
