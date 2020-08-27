package com.liskovsoft.smartyoutubetv2.common.mvp.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {

    private List<Video> playlist;
    private int currentPosition;

    public Playlist() {
        playlist = new ArrayList<>();
        currentPosition = 0;
    }

    /**
     * Clears the videos from the playlist.
     */
    public void clear() {
        playlist.clear();
    }

    /**
     * Adds a video to the end of the playlist.
     *
     * @param video to be added to the playlist.
     */
    public void add(Video video) {
        playlist.add(video);
    }

    /**
     * Sets current position in the playlist.
     *
     * @param currentPosition
     */
    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    /**
     * Returns the size of the playlist.
     *
     * @return The size of the playlist.
     */
    public int size() {
        return playlist.size();
    }

    /**
     * Moves to the next video in the playlist. If already at the end of the playlist, null will
     * be returned and the position will not change.
     *
     * @return The next video in the playlist.
     */
    public Video next() {
        if ((currentPosition + 1) < size()) {
            currentPosition++;
            return playlist.get(currentPosition);
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
        if (currentPosition - 1 >= 0) {
            currentPosition--;
            return playlist.get(currentPosition);
        }
        return null;
    }
}