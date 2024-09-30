package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroup.Channel;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChannelGroupService implements ProfileChangeListener {
    public static final int SUBSCRIPTION_GROUP_ID = 1_000;
    @SuppressLint("StaticFieldLeak")
    private static ChannelGroupService sInstance;
    private final Context mContext;
    private List<ChannelGroup> mChannelGroups;
    private final AppPrefs mPrefs;

    private ChannelGroupService(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static ChannelGroupService instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ChannelGroupService(context.getApplicationContext());
        }

        return sInstance;
    }

    public List<ChannelGroup> getChannelGroups() {
        return mChannelGroups;
    }

    public void addChannelGroup(ChannelGroup group) {
        // Move to the top
        mChannelGroups.remove(group);
        mChannelGroups.add(0, group);
        persistState();
    }

    public void removeChannelGroup(ChannelGroup group) {
        if (mChannelGroups.contains(group)) {
            mChannelGroups.remove(group);
            persistState();
        }
    }

    public String[] getChannelGroupIds(int channelGroupId) {
        if (channelGroupId == -1) {
            return null;
        }

        List<String> result = new ArrayList<>();

        ChannelGroup channelGroup = null;

        for (ChannelGroup group : getChannelGroups()) {
            if (group.id == channelGroupId) {
                channelGroup = group;
                break;
            }
        }

        if (channelGroup != null) {
            for (Channel channel : channelGroup.channels) {
                result.add(channel.channelId);
            }
        }

        return result.toArray(new String[]{});
    }

    public ChannelGroup findChannelGroup(int channelGroupId) {
        if (channelGroupId == -1) {
            return null;
        }

        for (ChannelGroup group : getChannelGroups()) {
            if (group.id == channelGroupId) {
                return group;
            }
        }

        return null;
    }

    public ChannelGroup findChannelGroup(String title) {
        if (title == null) {
            return null;
        }

        for (ChannelGroup group : getChannelGroups()) {
            if (Helpers.equals(group.title, title)) {
                return group;
            }
        }

        return null;
    }

    public void subscribe(Video item, boolean subscribe) {
        ChannelGroup group = findChannelGroup(SUBSCRIPTION_GROUP_ID);

        if (group == null) {
            group = new ChannelGroup(SUBSCRIPTION_GROUP_ID, mContext.getString(R.string.header_subscriptions), null, new ArrayList<>());
        }

        if (subscribe) {
            group.add(new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
        } else {
            group.remove(item.channelId);
        }

        if (!group.isEmpty()) {
            addChannelGroup(group);
        } else {
            removeChannelGroup(group);
        }
    }

    public boolean isSubscribed(String channelId) {
        ChannelGroup group = findChannelGroup(SUBSCRIPTION_GROUP_ID);

        return group != null && group.contains(channelId);
    }

    private void restoreState() {
        String data = mPrefs.getChannelGroupData();

        String[] split = Helpers.splitData(data);

        mChannelGroups = Helpers.parseList(split, 0, ChannelGroup::fromString);

        cleanupChannelGroups();
    }

    public void persistState() {
        mPrefs.setChannelGroupData(Helpers.mergeData(mChannelGroups));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    private void cleanupChannelGroups() {
        Collection<Video> pinnedItems = SidebarService.instance(mContext).getPinnedItems();

        Helpers.removeIf(mChannelGroups, value -> {
            return value.id != SUBSCRIPTION_GROUP_ID && !pinnedItems.contains(Video.from(value));
        });
    }
}
