package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered sections of the navigation rail.<br/>
 * Index semantics match the classic headers: -1 or out of range appends.
 */
public class RailModel {
    private static final Set<Integer> PRIMARY = new HashSet<>(Arrays.asList(
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY,
            MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_MY_VIDEOS,
            MediaGroup.TYPE_PLAYBACK_QUEUE, MediaGroup.TYPE_NOTIFICATIONS
    ));
    private final List<BrowseSection> mSections = new ArrayList<>();

    public void add(int index, BrowseSection section) {
        remove(section.getId());

        if (index < 0 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }
    }

    public void remove(int id) {
        int index = indexOf(id);

        if (index != -1) {
            mSections.remove(index);
        }
    }

    public void clear() {
        mSections.clear();
    }

    public int indexOf(int id) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == id) {
                return i;
            }
        }

        return -1;
    }

    public int size() {
        return mSections.size();
    }

    public BrowseSection get(int index) {
        return mSections.get(index);
    }

    public boolean hasDividerBefore(int position) {
        if (position <= 0 || position >= mSections.size() || isPrimary(position)) {
            return false;
        }

        boolean hasPrimaryBefore = false;

        for (int i = 0; i < position; i++) {
            if (isPrimary(i)) {
                hasPrimaryBefore = true;
            } else {
                return false; // divider already placed before an earlier secondary entry
            }
        }

        return hasPrimaryBefore;
    }

    public static boolean isPinnedBottom(int id) {
        return id == MediaGroup.TYPE_SETTINGS;
    }

    private boolean isPrimary(int position) {
        return PRIMARY.contains(mSections.get(position).getId());
    }
}
