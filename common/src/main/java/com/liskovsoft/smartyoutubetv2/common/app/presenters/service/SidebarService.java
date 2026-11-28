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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
    private final static int RESERVED_ID = 100;
    /**
     * A folder is a pinned item that has only a title and a section id from this range
     */
    private final static int FOLDER_START_ID = 100_000;
    /**
     * Item hash -> folder id. The items themselves are always stored in the {@link #mPinnedItems}
     */
    private Map<Integer, Integer> mFolderItems = new HashMap<>();

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

    public int addPinnedItem(Video item) {
        if (mPinnedItems.contains(item)) {
            return -1;
        }

        int idx = Helpers.indexOfFirst(mPinnedItems, obj -> obj.getId() > RESERVED_ID);

        if (idx != -1) {
            mPinnedItems.add(idx, item);
        } else {
            mPinnedItems.add(item);
        }
        persistState();

        return idx;
    }

    public void removePinnedItem(Video item) {
        mPinnedItems.remove(item);
        mFolderItems.remove(item.hashCode());
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
            cleanupFolderItems();
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
        return findShiftedIndex(sectionId, shift) != -1;
    }

    /**
     * Skips the items that aren't visible on the sidebar
     */
    private int findShiftedIndex(int sectionId, int shift) {
        int index = findPinnedItemIndex(sectionId);

        if (index == -1) {
            return -1;
        }

        do {
            index += shift;
        } while (index >= 0 && index < mPinnedItems.size() && isInsideFolder(mPinnedItems.get(index)));

        return index >= 0 && index < mPinnedItems.size() ? index : -1;
    }

    private void shiftSection(int sectionId, int shift) {
        if (!canShiftSection(sectionId, shift)) {
            return;
        }

        int index = findPinnedItemIndex(sectionId);
        int newIndex = findShiftedIndex(sectionId, shift);

        if (index != -1) {
            Video current = mPinnedItems.get(index);
            mPinnedItems.remove(current);

            mPinnedItems.add(newIndex, current);
            persistState();
        }
    }

    /**
     * Pinned items that aren't placed inside the folders
     */
    public List<Video> getTopLevelItems() {
        return findItems(item -> !isInsideFolder(item));
    }

    /**
     * Converts an index of the pinned item to the index of the sidebar section
     */
    public int getTopLevelIndex(int index) {
        int result = index;

        for (int i = 0; i < index && i < mPinnedItems.size(); i++) {
            if (isInsideFolder(mPinnedItems.get(i))) {
                result--;
            }
        }

        return result;
    }

    public List<Video> getFolders() {
        return findItems(SidebarService::isFolder);
    }

    public Video findFolder(int folderId) {
        return Helpers.findFirst(mPinnedItems, item -> isFolder(item) && item.sectionId == folderId);
    }

    public Video findFolderByTitle(String title) {
        return Helpers.findFirst(mPinnedItems, item -> isFolder(item) && Helpers.equals(item.title, title));
    }

    public List<Video> getFolderItems(int folderId) {
        return findItems(item -> item != null && getFolderId(item) == folderId);
    }

    /**
     * Pinned items that could be placed inside a folder
     */
    public List<Video> getFolderCandidates() {
        return findItems(SidebarService::isFolderCandidate);
    }

    /**
     * @return folder id or -1 if the item isn't placed inside a folder
     */
    public int getFolderId(Video item) {
        Integer folderId = item != null && item.sectionId == -1 ? mFolderItems.get(item.hashCode()) : null;

        return folderId != null ? folderId : -1;
    }

    public Video createFolder(String title) {
        int folderId = FOLDER_START_ID;

        while (findPinnedItemIndex(folderId) != -1) {
            folderId++;
        }

        Video folder = new Video();
        folder.title = title;
        folder.sectionId = folderId;

        addPinnedItem(folder);

        return folder;
    }

    /**
     * @param folderId -1 returns the item to the sidebar
     */
    public void moveToFolder(Video item, int folderId) {
        if (!isFolderCandidate(item) || !mPinnedItems.contains(item)) {
            return;
        }

        if (findFolder(folderId) != null) {
            mFolderItems.put(item.hashCode(), folderId);
        } else {
            mFolderItems.remove(item.hashCode());
        }

        persistState();
    }

    public void sortFolder(int folderId) {
        List<Video> items = getFolderItems(folderId);

        // NOTE: Comparator.comparing API >= 24
        Collections.sort(items, (o1, o2) -> String.valueOf(o1.getTitle()).compareToIgnoreCase(String.valueOf(o2.getTitle())));

        int idx = 0;

        for (int i = 0; i < mPinnedItems.size(); i++) {
            Video item = mPinnedItems.get(i);
            if (item != null && getFolderId(item) == folderId) {
                mPinnedItems.set(i, items.get(idx++));
            }
        }

        persistState();
    }

    /**
     * @return id of the folder in case the section is placed inside it
     */
    public int getVisibleSectionId(int sectionId) {
        int index = findPinnedItemIndex(sectionId);
        int folderId = index != -1 ? getFolderId(mPinnedItems.get(index)) : -1;

        return folderId != -1 ? folderId : sectionId;
    }

    public static boolean isFolder(Video item) {
        return item != null && item.sectionId >= FOLDER_START_ID;
    }

    public static boolean isFolderCandidate(Video item) {
        return item != null && item.sectionId == -1 && (item.hasPlaylist() || item.hasChannel() || item.hasReloadPageKey());
    }

    private boolean isInsideFolder(Video item) {
        return getFolderId(item) != -1;
    }

    private interface ItemFilter {
        boolean test(Video item);
    }

    private List<Video> findItems(ItemFilter filter) {
        List<Video> result = new ArrayList<>();

        for (Video item : mPinnedItems) {
            if (filter.test(item)) {
                result.add(item);
            }
        }

        return result;
    }

    private void cleanupFolderItems() {
        Map<Integer, Integer> result = new HashMap<>();

        for (Video item : mPinnedItems) {
            int folderId = getFolderId(item);
            if (isFolderCandidate(item) && findFolder(folderId) != null) {
                result.put(item.hashCode(), folderId);
            }
        }

        mFolderItems = result;
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
        mDefaultSections.put(R.string.header_blocked_channels, MediaGroup.TYPE_BLOCKED_CHANNELS);
        mDefaultSections.put(R.string.header_playlists, MediaGroup.TYPE_USER_PLAYLISTS);
        mDefaultSections.put(R.string.my_videos, MediaGroup.TYPE_MY_VIDEOS);
        mDefaultSections.put(R.string.playback_queue_category_title, MediaGroup.TYPE_PLAYBACK_QUEUE);
        mDefaultSections.put(R.string.header_settings, MediaGroup.TYPE_SETTINGS);
    }

    private void initPinnedItems() {
        for (int sectionId : mDefaultSections.values()) {
            // Hidden sections
            enableSection(sectionId, !Helpers.equalsAny(sectionId, new int[] {
                    MediaGroup.TYPE_NOTIFICATIONS,
                    MediaGroup.TYPE_PLAYBACK_QUEUE,
                    MediaGroup.TYPE_TRENDING,
                    MediaGroup.TYPE_BLOCKED_CHANNELS
            }));
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
            if (item.getId() < RESERVED_ID && item.getId() >= -1 && item.sectionId == -1) {
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
        mFolderItems = Helpers.parseMap(split, 3, Helpers::parseInt, Helpers::parseInt);

        transferOldPinnedItems();

        if (mPinnedItems.isEmpty()) {
            initPinnedItems();
        }

        // Backward compatibility
        enableSection(MediaGroup.TYPE_SETTINGS, true);

        cleanupPinnedItems();
        cleanupFolderItems();
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
        mPrefs.setSidebarData(Helpers.mergeData(mPinnedItems, mBootSectionId, mIsSettingsSectionEnabled, mFolderItems));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
