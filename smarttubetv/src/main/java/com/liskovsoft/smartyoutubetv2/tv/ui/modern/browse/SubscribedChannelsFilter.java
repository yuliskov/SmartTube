package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Lists of subscribed channels of the modern interface (Subscriptions, Channels).<br/>
 * The service lists the recent channels, then an "A-Z" divider, then every channel: the divider isn't a channel
 * (no picture, its videos can't load) and the recent channels would be listed twice.
 */
final class SubscribedChannelsFilter {
    private final Set<String> mAdded = new HashSet<>();

    /**
     * A new list starts (e.g. the section is loaded again).
     */
    void reset() {
        mAdded.clear();
    }

    boolean accept(Video channel) {
        if (channel == null || TextUtils.isEmpty(channel.cardImageUrl)) {
            return false;
        }

        String key = channel.getTitle();
        return key == null || mAdded.add(key);
    }

    /**
     * Removes the items that aren't listed.
     */
    void filter(List<Video> channels) {
        if (channels == null) {
            return;
        }

        for (Iterator<Video> iterator = channels.iterator(); iterator.hasNext(); ) {
            if (!accept(iterator.next())) {
                iterator.remove();
            }
        }
    }
}
