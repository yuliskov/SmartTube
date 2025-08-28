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

    public List<ItemGroup> getChannelGroups() {
        return mService.getChannelGroups();
    }

    public void addChannelGroup(ItemGroup group) {
        mService.addChannelGroup(group);
    }

    public void removeChannelGroup(ItemGroup group) {
        mService.removeChannelGroup(group);
    }

    public String[] findChannelIdsForGroup(String channelGroupId) {
        return mService.findChannelIdsForGroup(channelGroupId);
    }

    public ItemGroup findChannelGroupById(String channelGroupId) {
        return mService.findChannelGroupById(channelGroupId);
    }

    public ItemGroup findChannelGroupByTitle(String title) {
        return mService.findChannelGroupByTitle(title);
    }

    public ItemGroup createChannelGroup(String title, String iconUrl, List<Item> channels) {
        return mService.createChannelGroup(title, iconUrl, channels);
    }

    public Item createChannel(String title, String iconUrl, String channelId) {
        return mService.createChannel(channelId, title, iconUrl);
    }

    public void renameChannelGroup(ItemGroup channelGroup, String title) {
        mService.renameChannelGroup(channelGroup, title);
    }

    public Observable<List<ItemGroup>> importGroupsObserve(Uri uri) {
        return mService.importGroupsObserve(uri);
    }

    public Observable<List<ItemGroup>> importGroupsObserve(File file) {
        return mService.importGroupsObserve(file);
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
