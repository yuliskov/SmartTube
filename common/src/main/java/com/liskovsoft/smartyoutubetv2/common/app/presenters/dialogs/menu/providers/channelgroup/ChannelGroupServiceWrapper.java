package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.ChannelGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.ChannelGroup.Channel;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

public class ChannelGroupServiceWrapper implements ProfileChangeListener {
    public static final int SUBSCRIPTION_GROUP_ID = 1_000;
    @SuppressLint("StaticFieldLeak")
    private static ChannelGroupServiceWrapper sInstance;
    private final Context mContext;
    private final ChannelGroupService mService;
    private final AppPrefs mPrefs;

    private ChannelGroupServiceWrapper(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        mService = YouTubeServiceManager.instance().getChannelGroupService();
        restoreState();
    }

    public static ChannelGroupServiceWrapper instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ChannelGroupServiceWrapper(context.getApplicationContext());
        }

        return sInstance;
    }

    public List<ChannelGroup> getChannelGroups() {
        return mService.getChannelGroups();
    }

    public void addChannelGroup(ChannelGroup group) {
        mService.addChannelGroup(group);
    }

    public void removeChannelGroup(ChannelGroup group) {
        mService.removeChannelGroup(group);
    }

    public String[] getChannelGroupIds(int channelGroupId) {
        return mService.getChannelGroupIds(channelGroupId);
    }

    public ChannelGroup findChannelGroup(int channelGroupId) {
        return mService.findChannelGroup(channelGroupId);
    }

    public ChannelGroup findChannelGroup(String title) {
        return mService.findChannelGroup(title);
    }

    public ChannelGroup createChannelGroup(String title, String iconUrl, List<Channel> channels) {
        return mService.createChannelGroup(title, iconUrl, channels);
    }

    public Channel createChannel(String title, String iconUrl, String channelId) {
        return mService.createChannel(title, iconUrl, channelId);
    }

    public void renameChannelGroup(ChannelGroup channelGroup, String title) {
        mService.renameChannelGroup(channelGroup, title);
    }

    public boolean isEmpty() {
        return mService.isEmpty();
    }

    private void restoreState() {
        String data = mPrefs.getChannelGroupData();
        if (data != null) {
            mPrefs.setChannelGroupData(null);
            mService.exportData(data);
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
