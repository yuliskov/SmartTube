package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoGroup {
    public static final int ACTION_APPEND = 0;
    public static final int ACTION_REPLACE = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_REMOVE_AUTHOR = 3;
    public static final int ACTION_SYNC = 4;
    private static final String TAG = VideoGroup.class.getSimpleName();
    private int mId;
    private String mTitle;
    private List<Video> mVideos;
    private MediaGroup mMediaGroup;
    private BrowseSection mSection;
    private int mPosition = -1;
    private int mAction;

    public static VideoGroup from(BrowseSection category) {
        return from(null, category);
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, null);
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
        return from(Collections.singletonList(item), groupPosition);
    }

    public static VideoGroup from(List<Video> items) {
        return from(items, extractGroupPosition(items));
    }

    public static VideoGroup from(List<Video> items, int groupPosition) {
        VideoGroup videoGroup = new VideoGroup();
        // Getting topmost element. Could help when syncing multi rows fragments.
        Video topItem = findTopmostItemWithGroup(items);
        if (topItem.getGroup() != null) {
            videoGroup.mId = topItem.getGroup().getId();
            videoGroup.mTitle = topItem.getGroup().getTitle();
        }
        videoGroup.mVideos = items;
        videoGroup.mPosition = groupPosition;

        return videoGroup;
    }

    public static VideoGroup from(MediaGroup mediaGroup, BrowseSection section, int groupPosition) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mSection = section;
        videoGroup.mPosition = groupPosition;

        if (mediaGroup == null) {
            return videoGroup;
        }

        // Weak point. Title is used to obtain the group id.
        if (mediaGroup.getTitle() == null && section != null) {
            mediaGroup.setTitle(section.getTitle());
        }

        videoGroup.mMediaGroup = mediaGroup;
        videoGroup.mTitle = mediaGroup.getTitle();
        videoGroup.mId = mediaGroup.getId();
        videoGroup.mVideos = new ArrayList<>();

        if (mediaGroup.getMediaItems() == null) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return videoGroup;
        }

        VideoStateService stateService = VideoStateService.instance(null);

        for (MediaItem item : mediaGroup.getMediaItems()) {
            Video video = Video.from(item);

            if (video.isEmpty()) {
                continue;
            }

            // Group position in multi-grid fragments
            video.groupPosition = videoGroup.mPosition;
            video.setGroup(videoGroup);
            if (stateService != null && video.percentWatched == -1) {
                State state = stateService.getByVideoId(video.videoId);
                // Sync video.
                if (state != null) {
                    video.percentWatched = state.positionMs / (state.durationMs / 100f);
                }
            }
            videoGroup.mVideos.add(video);
        }

        return videoGroup;
    }

    public static VideoGroup fromChapters(List<ChapterItem> chapters, String title) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mTitle = title;
        videoGroup.mVideos = new ArrayList<>();

        for (ChapterItem chapter : chapters) {
            Video video = Video.from(chapter);
            video.setGroup(videoGroup);
            videoGroup.mVideos.add(video);
        }

        return videoGroup;
    }

    public List<Video> getVideos() {
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

        // Important part. The title is converted to unique row id.
        if (mMediaGroup != null) {
            mMediaGroup.setTitle(title);
        }
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

    public boolean isEmpty() {
        return mVideos == null || mVideos.isEmpty();
    }

    /**
     * Group position in multi-grid fragments<br/>
     * It isn't used on other types of fragments.
     */
    public int getPosition() {
        return mPosition;
    }

    public int getAction() {
        return mAction;
    }

    public void setAction(int action) {
        mAction = action;
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
}
