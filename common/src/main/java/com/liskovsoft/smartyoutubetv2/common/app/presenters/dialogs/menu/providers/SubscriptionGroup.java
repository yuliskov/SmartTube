package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import androidx.annotation.NonNull;

import java.util.List;

public class SubscriptionGroup {
    public final String groupTitle;
    public final String groupIconUrl;
    public final List<String> channelIds;
    private static final String ITEM_DELIM = "&sgi;";
    private static final String ARRAY_DELIM = "&sga;";

    public SubscriptionGroup(String groupTitle, String groupIconUrl, List<String> channelIds) {
        this.groupTitle = groupTitle;
        this.groupIconUrl = groupIconUrl;
        this.channelIds = channelIds;
    }

    public boolean contains(String channelId) {
        return channelIds != null && channelIds.contains(channelId);
    }

    public void add(String channelId) {
        if (channelIds != null && !channelIds.contains(channelId)) {
            channelIds.add(channelId);
        }
    }

    public void remove(String channelId) {
        if (channelIds != null) {
            channelIds.remove(channelId);
        }
    }

    public static SubscriptionGroup fromString(String spec) {
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return "";
    }
}
