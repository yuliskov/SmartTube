package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

public class VideoGroup {
    /**
     * Add at the end of the existing group
     */
    public static final int ACTION_APPEND = 0;
    /**
     * Clear whole fragment and then add this group
     */
    public static final int ACTION_REPLACE = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_REMOVE_AUTHOR = 3;
    public static final int ACTION_SYNC = 4;
    /**
     * Add at the begin of the existing group
     */
    public static final int ACTION_PREPEND = 5;
    private static final String TAG = VideoGroup.class.getSimpleName();
    private int mId;
    private String mTitle;
    private List<Video> mVideos;
    private MediaGroup mMediaGroup;
    private BrowseSection mSection;
    private int mPosition = -1;
    private int mAction;
    private int mType = -1;
    public boolean isQueue;

    public static VideoGroup from(BrowseSection category) {
        return from(null, category);
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, (BrowseSection) null);
    }

    public static VideoGroup from(BrowseSection category, int groupPosition) {
        return from(null, category, groupPosition);
    }

    public static VideoGroup from(MediaGroup mediaGroup, BrowseSection category) {
        return from(mediaGroup, category, -1);
    }

    public static VideoGroup from(Video item) {
        return from(item, extractGroupPosition(item));
    }

    public static VideoGroup from(Video item, int groupPosition) {
        return from(new ArrayList<>(Collections.singletonList(item)), groupPosition);
    }

    public static VideoGroup from(List<Video> items) {
        return from(items, extractGroupPosition(items));
    }

    public static VideoGroup from(List<Video> items, int groupPosition) {
        VideoGroup videoGroup = new VideoGroup();
        // Getting topmost element. Could help when syncing multi rows fragments.
        Video topItem = findTopmostItemWithGroup(items);
        if (topItem != null && topItem.getGroup() != null) {
            videoGroup.mId = topItem.getGroup().getId();
            videoGroup.mTitle = topItem.getGroup().getTitle();
        }
        videoGroup.mVideos = items;
        videoGroup.mPosition = groupPosition;

        for (Video item : items) {
            // Section as playlist fix. Don't change the root.
            if (item.getGroup() == null) {
                item.setGroup(videoGroup);
            }
        }

        return videoGroup;
    }

    public static VideoGroup from(MediaGroup mediaGroup, BrowseSection section, int groupPosition) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mSection = section;
        videoGroup.mPosition = groupPosition;
        videoGroup.mId = videoGroup.hashCode();
        videoGroup.mVideos = new ArrayList<>();
        videoGroup.mMediaGroup = mediaGroup;
        videoGroup.mTitle = mediaGroup != null && mediaGroup.getTitle() != null ?
                mediaGroup.getTitle() : section != null ? section.getTitle() : null;

        if (mediaGroup == null) {
            return videoGroup;
        }

        if (mediaGroup.getMediaItems() == null) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return videoGroup;
        }

        for (MediaItem item : mediaGroup.getMediaItems()) {
            Video video = Video.from(item);

            videoGroup.add(video);
        }

        return videoGroup;
    }

    public static VideoGroup from(VideoGroup baseGroup, MediaGroup mediaGroup) {
        baseGroup.mMediaGroup = mediaGroup;

        if (mediaGroup == null) {
            return baseGroup;
        }

        if (mediaGroup.isEmpty()) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return baseGroup;
        }

        for (MediaItem item : mediaGroup.getMediaItems()) {
            Video video = Video.from(item);

            baseGroup.add(video);
        }

        baseGroup.mAction = ACTION_APPEND;

        return baseGroup;
    }

    public static VideoGroup fromChapters(List<ChapterItem> chapters, String title) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mTitle = title;
        videoGroup.mVideos = new ArrayList<>();

        for (ChapterItem chapter : chapters) {
            Video video = Video.from(chapter);

            videoGroup.add(video);
        }

        return videoGroup;
    }

    public List<Video> getVideos() {
        // NOTE: Don't make the collection read only
        // The collection will be filtered inside VideoGroupObjectAdapter
        return mVideos;
    }

    public String getTitle() {
        return mTitle;
    }

    /**
     * The title is converted to unique row id.
     */
    public void setTitle(String title) {
        mTitle = title;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public MediaGroup getMediaGroup() {
        return mMediaGroup;
    }

    public BrowseSection getSection() {
        return mSection;
    }

    public boolean isShorts() {
        if (isEmpty()) {
            return false;
        }

        for (int i = 0; i < Math.min(8, mVideos.size()); i++) {
             if (!mVideos.get(i).isShorts)
                 return false;
        }

        return true;
    }

    /**
     * Group position in multi-grid fragments<br/>
     * It isn't used on other types of fragments.
     */
    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public int getAction() {
        return mAction;
    }

    public void setAction(int action) {
        mAction = action;

        if (action == ACTION_PREPEND) {
            mPosition = 0;
        }
    }

    public int getType() {
        return mType != -1 ? mType : getMediaGroup() != null ? getMediaGroup().getType() : -1;
    }

    public void setType(int type) {
        mType = type;
    }

    /**
     * Lightweight copy (without nested videos)
     */
    public VideoGroup copy() {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mId = mId;
        videoGroup.mTitle = mTitle;
        videoGroup.mPosition = mPosition;

        return videoGroup;
    }

    /**
     * Getting topmost element. Could help when syncing multi rows fragments.
     */
    private static Video findTopmostItemWithGroup(List<Video> items) {
        if (items.isEmpty()) {
            return null;
        }

        for (int i = (items.size() - 1); i >= 0; i--) {
            Video video = items.get(i);
            if (video.getGroup() != null) {
                return video;
            }
        }

        return items.get(items.size() - 1); // No group. Fallback to last item then.
    }

    private static int extractGroupPosition(List<Video> items) {
        if (items == null || items.isEmpty()) {
            return -1;
        }

        return extractGroupPosition(findTopmostItemWithGroup(items));
    }

    private static int extractGroupPosition(Video item) {
        int groupPosition = -1;

        if (item != null) {
            groupPosition = item.groupPosition;
        }

        return groupPosition;
    }

    public void removeAllBefore(Video video) {
        if (mVideos == null) {
            return;
        }

        int index = mVideos.indexOf(video);

        if (index == -1) {
            return;
        }

        mVideos = mVideos.subList(index + 1, mVideos.size());
    }

    /**
     * Remove playlist id from all videos
     */
    public void stripPlaylistInfo() {
        if (mVideos == null) {
            return;
        }

        for (Video video : mVideos) {
            video.playlistId = null;
            video.remotePlaylistId = null;
        }
    }

    public Video findVideoById(String videoId) {
        if (mVideos == null) {
            return null;
        }

        Video result = null;

        for (Video video : mVideos) {
            if (Helpers.equals(videoId, video.videoId)) {
                result = video;
                break;
            }
        }

        return result;
    }

    public void clear() {
        if (mVideos == null) {
            return;
        }

        mVideos.clear();
    }

    public boolean contains(Video video) {
        if (mVideos == null) {
            return false;
        }

        return mVideos.contains(video);
    }

    public int getSize() {
        if (mVideos == null) {
            return -1;
        }

        return mVideos.size();
    }

    public int indexOf(Video video) {
        if (mVideos == null) {
            return -1;
        }

        return mVideos.indexOf(video);
    }

    public Video get(int idx) {
        if (mVideos == null) {
            return null;
        }

        return mVideos.get(idx);
    }

    public void remove(Video video) {
        if (mVideos == null) {
            return;
        }

        try {
            // ConcurrentModificationException fix?
            mVideos.remove(video);
        } catch (UnsupportedOperationException | ConcurrentModificationException e) { // read only collection
            e.printStackTrace();
        }
    }

    public boolean isEmpty() {
        try {
            return mVideos == null || mVideos.isEmpty();
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
        }

        return true;
    }

    public void add(Video video) {
        int size = getSize();
        add(size != -1 ? size : 0, video);
    }

    public void add(int idx, Video video) {
        if (video == null || video.isEmpty()) {
            return;
        }

        if (mVideos == null) {
            mVideos = new ArrayList<>();
        }

        // Group position in multi-grid fragments
        video.groupPosition = mPosition;
        video.setGroup(this);

        VideoStateService stateService = VideoStateService.instance(null);
        if (stateService != null && (video.percentWatched == -1 || video.percentWatched == 100)) {
            State state = stateService.getByVideoId(video.videoId);
            video.sync(state);
        }

        mVideos.add(idx, video);
    }
}
