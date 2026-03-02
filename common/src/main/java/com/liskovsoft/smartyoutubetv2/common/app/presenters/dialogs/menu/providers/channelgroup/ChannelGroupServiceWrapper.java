package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.List;

import io.reactivex.Observable;

public class ChannelGroupServiceWrapper implements ProfileChangeListener {
    @SuppressLint("StaticFieldLeak")
    private static ChannelGroupServiceWrapper sInstance;
    private final AppPrefs mPrefs;
    private ChannelGroupService mService;

    private ChannelGroupServiceWrapper(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static ChannelGroupServiceWrapper instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ChannelGroupServiceWrapper(context.getApplicationContext());
        }

        return sInstance;
    }

    public List<ItemGroup> getChannelGroups() {
        return getService().getChannelGroups();
    }

    public void addChannelGroup(ItemGroup group) {
        getService().addChannelGroup(group);
    }

    public void removeChannelGroup(ItemGroup group) {
        getService().removeChannelGroup(group);
    }

    public String[] findChannelIdsForGroup(String channelGroupId) {
        return getService().findChannelIdsForGroup(channelGroupId);
    }

    public ItemGroup findChannelGroupById(String channelGroupId) {
        return getService().findChannelGroupById(channelGroupId);
    }

    public ItemGroup findChannelGroupByTitle(String title) {
        return getService().findChannelGroupByTitle(title);
    }

    public ItemGroup createChannelGroup(String title, String iconUrl, List<Item> channels) {
        return getService().createChannelGroup(title, iconUrl, channels);
    }

    public Item createChannel(String title, String iconUrl, String channelId) {
        return getService().createChannel(channelId, title, iconUrl);
    }

    public void renameChannelGroup(ItemGroup channelGroup, String title) {
        getService().renameChannelGroup(channelGroup, title);
    }

    public Observable<List<ItemGroup>> importGroupsObserve(Uri uri) {
        return getService().importGroupsObserve(uri);
    }

    public Observable<List<ItemGroup>> importGroupsObserve(File file) {
        return getService().importGroupsObserve(file);
    }

    public boolean isEmpty() {
        return getService().isEmpty();
    }

    private void restoreState() {
        String data = mPrefs.getChannelGroupData();
        if (data != null) {
            mPrefs.setChannelGroupData(null);
            getService().exportData(data);
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    private ChannelGroupService getService() {
        if (mService == null) {
            mService = YouTubeServiceManager.instance().getChannelGroupService();
        }

        return mService;
    }
}
