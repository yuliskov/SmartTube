package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SidebarService implements ProfileChangeListener {
    @SuppressLint("StaticFieldLeak")
    private static SidebarService sInstance;
    private final Context mContext;
    private List<Video> mPinnedItems;
    private final Map<Integer, Integer> mDefaultSections = new LinkedHashMap<>();
    private final AppPrefs mPrefs;
    private boolean mIsSettingsSectionEnabled;
    private int mBootSectionId;

    private SidebarService(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        initSections();
        restoreState();
    }

    public static SidebarService instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new SidebarService(context.getApplicationContext());
        }

        return sInstance;
    }

    public Collection<Video> getPinnedItems() {
        return Collections.unmodifiableList(mPinnedItems);
    }

    public void addPinnedItem(Video item) {
        if (mPinnedItems.contains(item)) {
            return;
        }

        mPinnedItems.add(item);
        persistState();
    }

    public void removePinnedItem(Video item) {
        mPinnedItems.remove(item);
        persistState();
    }

    public void enableSection(int sectionId, boolean enabled) {
        if (enabled) {
            if (sectionId == MediaGroup.TYPE_SETTINGS) {
                mIsSettingsSectionEnabled = true; // prevent Settings lock
            }

            //Video item = new Video();
            //item.sectionId = sectionId;
            //
            //if (mPinnedItems.contains(item)) { // don't reorder if item already exists
            //    return;
            //}

            Video section = Helpers.findFirst(mPinnedItems, item -> item != null && item.sectionId == sectionId);

            if (section != null) { // don't reorder if item already exists
                return;
            }

            Video item = new Video();
            item.sectionId = sectionId;

            int index = getDefaultSectionIndex(sectionId);

            if (index == -1 || index > mPinnedItems.size()) {
                mPinnedItems.add(item);
            } else {
                mPinnedItems.add(index, item);
            }
        } else {
            Helpers.removeIf(mPinnedItems, item -> item == null || item.sectionId == sectionId);
        }

        persistState();
    }

    public Map<Integer, Integer> getDefaultSections() {
        return mDefaultSections;
    }

    private int getDefaultSectionIndex(int sectionId) {
        int index = -1;

        Collection<Integer> values = mDefaultSections.values();

        for (int item : values) {
            index++;
            if (item == sectionId) {
                break;
            }
        }

        return index;
    }

    /**
     * Contains sections and pinned items!
     */
    public boolean isSectionPinned(int sectionId) {
        Video section = Helpers.findFirst(mPinnedItems, item -> getSectionId(item) == sectionId);
        return section != null; // by default enable all pinned items
    }

    public int getSectionIndex(int sectionId) {
        // 1) Distinguish section from pinned item
        // 2) Add pinned items after the sections

        int index = findPinnedItemIndex(sectionId);

        return index;
    }

    public void renameSection(int sectionId, String newTitle) {
        int index = findPinnedItemIndex(sectionId);
        if (index != -1) {
            Video video = mPinnedItems.get(index);
            video.title = newTitle;
            persistState();
        }
    }

    public void moveSectionUp(int sectionId) {
        shiftSection(sectionId, -1);
    }

    public void moveSectionDown(int sectionId) {
        shiftSection(sectionId, 1);
    }

    public boolean canMoveSectionUp(int sectionId) {
        return canShiftSection(sectionId, -1);
    }

    public boolean canMoveSectionDown(int sectionId) {
        return canShiftSection(sectionId, 1);
    }

    private boolean canShiftSection(int sectionId, int shift) {
        int index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            return  index + shift >= 0 && index + shift < mPinnedItems.size();
        }

        return false;
    }

    private void shiftSection(int sectionId, int shift) {
        if (!canShiftSection(sectionId, shift)) {
            return;
        }

        int index = findPinnedItemIndex(sectionId);

        if (index != -1) {
            Video current = mPinnedItems.get(index);
            mPinnedItems.remove(current);

            mPinnedItems.add(index + shift, current);
            persistState();
        }
    }

    private int findPinnedItemIndex(int sectionId) {
        int index = -1;

        for (Video item : mPinnedItems) {
            // Distinguish pinned items by hashCode or extra field (default section)!
            if (getSectionId(item) == sectionId) {
                index = mPinnedItems.indexOf(item);
                break;
            }
        }

        return index;
    }

    public void setBootSectionId(int sectionId) {
        mBootSectionId = sectionId;

        persistState();
    }

    public int getBootSectionId() {
        return mBootSectionId;
    }

    public void enableSettingsSection(boolean enabled) {
        mIsSettingsSectionEnabled = enabled;

        persistState();
    }

    public boolean isSettingsSectionEnabled() {
        return mIsSettingsSectionEnabled;
    }

    private int getSectionId(Video item) {
        if (item == null) {
            return -1;
        }

        return item.sectionId == -1 ? item.getId() : item.sectionId;
    }

    private void initSections() {
        mDefaultSections.put(R.string.header_notifications, MediaGroup.TYPE_NOTIFICATIONS);
        mDefaultSections.put(R.string.header_home, MediaGroup.TYPE_HOME);
        mDefaultSections.put(R.string.header_shorts, MediaGroup.TYPE_SHORTS);
        mDefaultSections.put(R.string.header_trending, MediaGroup.TYPE_TRENDING);
        mDefaultSections.put(R.string.header_kids_home, MediaGroup.TYPE_KIDS_HOME);
        mDefaultSections.put(R.string.header_sports, MediaGroup.TYPE_SPORTS);
        mDefaultSections.put(R.string.badge_live, MediaGroup.TYPE_LIVE);
        mDefaultSections.put(R.string.header_gaming, MediaGroup.TYPE_GAMING);
        mDefaultSections.put(R.string.header_news, MediaGroup.TYPE_NEWS);
        mDefaultSections.put(R.string.header_music, MediaGroup.TYPE_MUSIC);
        mDefaultSections.put(R.string.header_channels, MediaGroup.TYPE_CHANNEL_UPLOADS);
        mDefaultSections.put(R.string.header_subscriptions, MediaGroup.TYPE_SUBSCRIPTIONS);
        mDefaultSections.put(R.string.header_history, MediaGroup.TYPE_HISTORY);
        mDefaultSections.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
        mDefaultSections.put(R.string.my_videos, MediaGroup.TYPE_MY_VIDEOS);
        mDefaultSections.put(R.string.header_settings, MediaGroup.TYPE_SETTINGS);
    }

    private void initPinnedItems() {
        for (int sectionId : mDefaultSections.values()) {
            // Notifications is broken
            enableSection(sectionId, sectionId != MediaGroup.TYPE_NOTIFICATIONS);
            //enableSection(sectionId, true);
        }
    }

    private void cleanupPinnedItems() {
        Helpers.removeDuplicates(mPinnedItems);

        Helpers.removeIf(mPinnedItems, item -> {
            if (item == null) {
                return true;
            }

            item.videoId = null;

            // Fix id collision between pinned and default sections
            if (item.getId() < 100 && item.getId() >= -1 && item.sectionId == -1) {
                return true;
            }

            return !item.hasPlaylist() && item.channelId == null && item.sectionId == -1 && item.channelGroupId == null && !item.hasReloadPageKey();
        });
    }

    private void restoreState() {
        String data = mPrefs.getSidebarData();

        String[] split = Helpers.splitData(data);

        mPinnedItems = Helpers.parseList(split, 0, Video::fromString);
        mBootSectionId = Helpers.parseInt(split, 1, MediaGroup.TYPE_HOME);
        mIsSettingsSectionEnabled = Helpers.parseBoolean(split, 2, true);

        transferOldPinnedItems();

        if (mPinnedItems.isEmpty()) {
            initPinnedItems();
        }

        // Backward compatibility
        enableSection(MediaGroup.TYPE_SETTINGS, true);

        cleanupPinnedItems();
    }

    private void transferOldPinnedItems() {
        if (mPinnedItems != null && !mPinnedItems.isEmpty()) {
            return;
        }

        List<Video> oldPinnedItems = GeneralData.instance(mContext).getOldPinnedItems();

        if (oldPinnedItems != null && !oldPinnedItems.isEmpty()) {
            mPinnedItems = oldPinnedItems;
        }
    }

    public void persistState() {
        mPrefs.setSidebarData(Helpers.mergeData(mPinnedItems, mBootSectionId, mIsSettingsSectionEnabled));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
