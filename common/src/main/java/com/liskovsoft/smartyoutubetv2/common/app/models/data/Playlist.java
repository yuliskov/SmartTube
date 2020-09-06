package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
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
        mPlaylist.add(++mCurrentPosition, video);

        removeAllAfterCurrent();
    }

    private void removeAllAfterCurrent() {
        boolean alreadyLastElement = mCurrentPosition == (mPlaylist.size() - 1);
        if (alreadyLastElement) {
            return;
        }

        mPlaylist = mPlaylist.subList(0, mCurrentPosition + 1);
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
}