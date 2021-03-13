package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
    private static final int PLAYLIST_MAX_SIZE = 20;
    private List<Video> mPlaylist;
    private int mCurrentPosition;
    private static Playlist sInstance;

    private Playlist() {
        mPlaylist = new ArrayList<>();
        mCurrentPosition = -1;
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
        mCurrentPosition = -1;
    }

    /**
     * Adds a video to the end of the playlist.
     *
     * @param video to be added to the playlist.
     */
    public void add(Video video) {
        if (Video.isEmpty(video)) {
            return;
        }

        if (Video.equals(video, getCurrent())) {
            mPlaylist.set(mCurrentPosition, video);
        } else {
            mPlaylist.add(++mCurrentPosition, video);
        }

        trimPlaylist();
        stripPrevItem();
    }

    /**
     * Trim playlist if one exceeds needed size or current element not last in the list
     */
    private void trimPlaylist() {
        int fromIndex = 0;
        int toIndex = mCurrentPosition + 1;

        boolean isLastElement = mCurrentPosition == (mPlaylist.size() - 1);
        boolean playlistTooBig = mPlaylist.size() > PLAYLIST_MAX_SIZE;

        if (playlistTooBig) {
            fromIndex = mPlaylist.size() - PLAYLIST_MAX_SIZE;
        }

        if (!isLastElement || playlistTooBig) {
            mPlaylist = mPlaylist.subList(fromIndex, toIndex);
            mCurrentPosition = mPlaylist.size() - 1;
        }
    }

    /**
     * Moves to the next video in the playlist. If already at the end of the playlist, null will
     * be returned and the position will not change.
     *
     * @return The next video in the playlist.
     */
    public Video next() {
        if ((mCurrentPosition + 1) < mPlaylist.size()) {
            return mPlaylist.get(++mCurrentPosition);
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
        if ((mCurrentPosition - 1) >= 0) {
            return mPlaylist.get(--mCurrentPosition);
        }

        return null;
    }

    public Video getCurrent() {
        if (mCurrentPosition < mPlaylist.size() && mCurrentPosition >= 0) {
            return mPlaylist.get(mCurrentPosition);
        }

        return null;
    }

    /**
     * Do some cleanup to prevent possible OOM exception
     */
    private void stripPrevItem() {
        int prevPosition = mCurrentPosition - 1;

        if (prevPosition < mPlaylist.size() && prevPosition >= 0) {
            Video prevItem = mPlaylist.get(prevPosition);
            if (prevItem != null) {
                prevItem.mediaItem = null;
                prevItem.nextMediaItem = null;
            }
        }
    }
}