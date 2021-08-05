package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
    private static final int PLAYLIST_MAX_SIZE = 20;
    private List<Video> mPlaylist;
    private int mCurrentIndex;
    private static Playlist sInstance;

    private Playlist() {
        mPlaylist = new ArrayList<>();
        mCurrentIndex = -1;
    }

    public static Playlist instance() {
        if (sInstance == null) {
            sInstance = new Playlist();
        }

        return sInstance;
    }

    /**
     * Clears the videos from the playlist.
     */
    public void clear() {
        mPlaylist.clear();
        mCurrentIndex = -1;
    }

    ///**
    // * Adds a video to the end of the playlist.
    // *
    // * @param video to be added to the playlist.
    // */
    //public void add(Video video) {
    //    if (Video.isEmpty(video)) {
    //        return;
    //    }
    //
    //    if (Video.equals(video, getCurrent())) {
    //        mPlaylist.set(mCurrentPosition, video);
    //    } else {
    //        mPlaylist.add(++mCurrentPosition, video);
    //
    //        // Video opened from the browser or suggestions.
    //        // In this case remove all next items.
    //        trimPlaylist();
    //        stripPrevItem();
    //    }
    //}

    /**
     * Adds a video to the end of the playlist.
     *
     * @param video to be added to the playlist.
     */
    public void add(Video video) {
        if (Video.isEmpty(video)) {
            return;
        }

        int index = mPlaylist.indexOf(video);

        if (index >= 0) {
            mPlaylist.remove(video);

            // Shift video stack index if needed
            if (index <= mCurrentIndex) {
                --mCurrentIndex;
            }
        }

        mPlaylist.add(++mCurrentIndex, video);

        // Video opened from the browser or suggestions.
        // In this case remove all next items.
        trimPlaylist();
        stripPrevItem();
    }

    /**
     * Trim playlist if one exceeds needed size or current element not last in the list
     */
    private void trimPlaylist() {
        int fromIndex = 0;
        int toIndex = mCurrentIndex + 1;

        boolean isLastElement = mCurrentIndex == (mPlaylist.size() - 1);
        boolean playlistTooBig = mPlaylist.size() > PLAYLIST_MAX_SIZE;

        if (playlistTooBig) {
            fromIndex = mPlaylist.size() - PLAYLIST_MAX_SIZE;
        }

        if (!isLastElement || playlistTooBig) {
            mPlaylist = mPlaylist.subList(fromIndex, toIndex);
            mCurrentIndex = mPlaylist.size() - 1;
        }
    }

    /**
     * Moves to the next video in the playlist. If already at the end of the playlist, null will
     * be returned and the position will not change.
     *
     * @return The next video in the playlist.
     */
    public Video next() {
        if ((mCurrentIndex + 1) < mPlaylist.size()) {
            return mPlaylist.get(++mCurrentIndex);
        }

        return null;
    }

    /**
     * Moves to the previous video in the playlist. If the playlist is already at the beginning,
     * null will be returned and the position will not change.
     *
     * @return The previous video in the playlist.
     */
    public Video previous() {
        if ((mCurrentIndex - 1) >= 0) {
            return mPlaylist.get(--mCurrentIndex);
        }

        return null;
    }

    public void setCurrent(Video video) {
        if (Video.isEmpty(video)) {
            return;
        }

        int currentPosition = mPlaylist.indexOf(video);

        if (currentPosition >= 0) {
            mCurrentIndex = currentPosition;
        } else {
            add(video);
        }
    }

    public Video getCurrent() {
        if (mCurrentIndex < mPlaylist.size() && mCurrentIndex >= 0) {
            return mPlaylist.get(mCurrentIndex);
        }

        return null;
    }

    public List<Video> getAll() {
        return mPlaylist;
    }

    /**
     * Do some cleanup to prevent possible OOM exception
     */
    private void stripPrevItem() {
        int prevPosition = mCurrentIndex - 1;

        if (prevPosition < mPlaylist.size() && prevPosition >= 0) {
            Video prevItem = mPlaylist.get(prevPosition);
            if (prevItem != null) {
                prevItem.mediaItem = null;
                prevItem.nextMediaItem = null;
            }
        }
    }
}