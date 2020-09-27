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

    public void clearNext() {
        if (mCurrentPosition > 0 && (mCurrentPosition + 1) < mPlaylist.size()) {
            mPlaylist = mPlaylist.subList(mCurrentPosition + 1, mPlaylist.size());
        }
    }

    /**
     * Adds a video to the end of the playlist.
     *
     * @param video to be added to the playlist.
     */
    public void add(Video video) {
        if (video.equals(getCurrent())) {
            mPlaylist.set(mCurrentPosition, video);
        } else {
            mPlaylist.add(++mCurrentPosition, video);
            trimPlaylist();
        }
    }

    private void trimPlaylist() {
        int fromIndex = 0;
        int toIndex = mCurrentPosition + 1;

        boolean isLastElement = mCurrentPosition == (mPlaylist.size() - 1);
        boolean playlistTooBig = mPlaylist.size() > 50;

        if (playlistTooBig) {
            fromIndex = mPlaylist.size() - 50;
        }

        if (!isLastElement || playlistTooBig) {
            mPlaylist = mPlaylist.subList(fromIndex, toIndex);
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
}