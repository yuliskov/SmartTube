package com.liskovsoft.smartyoutubetv2.common.mvp.models.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
    private final List<Video> mPlaylist;
    private int mCurrentPosition;
    private static Playlist sInstance;

    private Playlist() {
        mPlaylist = new ArrayList<>();
        mCurrentPosition = 0;
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
    }

    /**
     * Adds a video to the end of the playlist.
     *
     * @param video to be added to the playlist.
     */
    public void add(Video video) {
        mPlaylist.add(video);
    }

    public void add(int index, Video video) {
        mPlaylist.add(index, video);
    }

    /**
     * Sets current position in the playlist.
     *
     * @param currentPosition
     */
    public void setCurrentPosition(int currentPosition) {
        if (currentPosition < size() && currentPosition >= 0) {
            mCurrentPosition = currentPosition;
        }
    }

    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    /**
     * Returns the size of the playlist.
     *
     * @return The size of the playlist.
     */
    public int size() {
        return mPlaylist.size();
    }

    /**
     * Moves to the next video in the playlist. If already at the end of the playlist, null will
     * be returned and the position will not change.
     *
     * @return The next video in the playlist.
     */
    public Video next() {
        if ((mCurrentPosition + 1) < size()) {
            return mPlaylist.get(mCurrentPosition + 1);
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
        if (mCurrentPosition - 1 >= 0) {
            return mPlaylist.get(mCurrentPosition - 1);
        }
        return null;
    }

    public void insertAfterCurrent(Video video) {
        int index = mCurrentPosition + 1;

        if (index > size()) {
            add(video);
        } else {
            add(index, video);
        }
    }

    public int indexOf(Video video) {
        return mPlaylist.indexOf(video);
    }
}