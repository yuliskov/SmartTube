package com.liskovsoft.smartyoutubetv2.common.mvp.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
    private final List<Video> mPlaylist;
    private int mCurrentPosition;

    public Playlist() {
        mPlaylist = new ArrayList<>();
        mCurrentPosition = 0;
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
        mCurrentPosition = currentPosition;
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
            mCurrentPosition++;
            return mPlaylist.get(mCurrentPosition);
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
            mCurrentPosition--;
            return mPlaylist.get(mCurrentPosition);
        }
        return null;
    }

    public void insert(int index, Video video) {
        if (index < 0 || index > size()) {
            add(video);
            setCurrentPosition(size() - 1);
        } else {
            add(index, video);
            setCurrentPosition(index);
        }
    }

    public int indexOf(Video video) {
        return mPlaylist.indexOf(video);
    }
}