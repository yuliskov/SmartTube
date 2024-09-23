package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionGroup {
    public final String title;
    public final String iconUrl;
    public final List<String> channelIds;
    private static final String ITEM_DELIM = "&sgi;";
    private static final String ARRAY_DELIM = "&sga;";

    public SubscriptionGroup(String title, String iconUrl, String channelId) {
        List<String> channelIds = new ArrayList<>();
        channelIds.add(channelId);
        this.title = title;
        this.iconUrl = iconUrl;
        this.channelIds = channelIds;
    }

    public SubscriptionGroup(String title, String iconUrl, List<String> channelIds) {
        this.title = title;
        this.iconUrl = iconUrl;
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
        String[] split = Helpers.split(ITEM_DELIM, spec);

        String title = Helpers.parseStr(split, 0);
        String groupIconUrl = Helpers.parseStr(split, 1);
        List<String> channelIds = Helpers.parseList(split, 2, itemSpec -> itemSpec);

        return new SubscriptionGroup(title, groupIconUrl, channelIds);
    }

    @NonNull
    @Override
    public String toString() {
        return Helpers.merge(ITEM_DELIM, title, iconUrl, channelIds);
    }
}
