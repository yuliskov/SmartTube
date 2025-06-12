package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a playlist of videos.
 */
public class Playlist {
    private static final int PLAYLIST_MAX_SIZE = 50;
    private final List<Video> mPlaylist;
    private final List<Video> mSyncedItems;
    private int mCurrentIndex;
    private static Playlist sInstance;

    private Playlist() {
        mPlaylist = new ArrayList<>();
        mSyncedItems = new ArrayList<>();
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

    public void clearPosition() {
        mCurrentIndex = -1;
    }

    /**
     * Used to sync list with remotely added items
     */
    public void addAll(List<Video> videos) {
        mPlaylist.removeAll(videos);
        mPlaylist.addAll(videos);
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

        Video current = getCurrent();

        // Skip add currently playing item
        // And replace to correct position sync in fragments
        if (video.equals(current)) {
            replace(current, video);
            return;
        }

        boolean isLastElement = mPlaylist.size() > 0 && video.equals(mPlaylist.get(mPlaylist.size() - 1));

        remove(video);

        mPlaylist.add(video);

        // Replacing last element? Increase index then.
        if (isLastElement) {
            mCurrentIndex++;
        }

        // Video opened from the browser or suggestions.
        // In this case remove all next items.
        trimPlaylist();
        stripPrevItem();
    }

    public void next(Video video) {
        if (Video.isEmpty(video)) {
            return;
        }

        remove(video);

        int nextIdx = mPlaylist.size() > mCurrentIndex ? mCurrentIndex + 1 : mPlaylist.size() - 1;

        // IndexOutOfBoundsException fix
        if (nextIdx < 0) {
            return;
        }

        mPlaylist.add(nextIdx, video);

        // Video opened from the browser or suggestions.
        // In this case remove all next items.
        trimPlaylist();
        stripPrevItem();
    }

    public void remove(Video video) {
        if (Video.isEmpty(video)) {
            return;
        }

        // Skip remove currently playing item
        if (video.equals(getCurrent())) {
            return;
        }

        int index = mPlaylist.indexOf(video);

        // If contains
        if (index >= 0) {
            mPlaylist.remove(video);

            // Shift video stack index if needed
            // Don't remove current index. Except this is the last element.
            // Give a chance to replace current element.
            if (index < mCurrentIndex) {
                mCurrentIndex--;
            }

            // Index out of bounds as the result of previous operation.
            // Select last element in this case.
            if (mCurrentIndex >= mPlaylist.size()) {
                mCurrentIndex = mPlaylist.size() - 1;
            }
        }
    }

    public boolean contains(Video video) {
        if (Video.isEmpty(video)) {
            return false;
        }

        return mPlaylist.contains(video);
    }

    public boolean containsAfterCurrent(Video video) {
        if (Video.isEmpty(video)) {
            return false;
        }

        List<Video> afterCurrent = getAllAfterCurrent();

        return afterCurrent != null && afterCurrent.contains(video);
    }

    ///**
    // * Trim playlist if one exceeds needed size or current element not last in the list
    // */
    //private void trimPlaylist() {
    //    int fromIndex = 0;
    //    int toIndex = mCurrentIndex + 1;
    //
    //    boolean isLastElement = mCurrentIndex == (mPlaylist.size() - 1);
    //    boolean playlistTooBig = mPlaylist.size() > PLAYLIST_MAX_SIZE;
    //
    //    if (playlistTooBig) {
    //        fromIndex = mPlaylist.size() - PLAYLIST_MAX_SIZE;
    //    }
    //
    //    if (!isLastElement || playlistTooBig) {
    //        mPlaylist = mPlaylist.subList(fromIndex, toIndex);
    //        mCurrentIndex = mPlaylist.size() - 1;
    //    }
    //}

    /**
     * Moves to the next video in the playlist. If already at the end of the playlist, null will
     * be returned and the position will not change.
     *
     * @return The next video in the playlist.
     */
    public Video getNext() {
        if (mCurrentIndex >= 0 && (mCurrentIndex + 1) < mPlaylist.size()) {
            return mPlaylist.get(mCurrentIndex + 1);
        }

        return null;
    }

    /**
     * Moves to the previous video in the playlist. If the playlist is already at the beginning,
     * null will be returned and the position will not change.
     *
     * @return The previous video in the playlist.
     */
    public Video getPrevious() {
        if ((mCurrentIndex - 1) >= 0) {
            return mPlaylist.get(mCurrentIndex - 1);
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
            mCurrentIndex = mPlaylist.size() - 1;
        }
    }

    public Video getCurrent() {
        if (mCurrentIndex < mPlaylist.size() && mCurrentIndex >= 0) {
            return mPlaylist.get(mCurrentIndex);
        }

        return null;
    }

    public List<Video> getAll() {
        return Collections.unmodifiableList(mPlaylist);
    }

    public List<Video> getChangedItems() {
        return Collections.unmodifiableList(mSyncedItems);
    }

    public boolean hasNext() {
        return getNext() != null;
    }

    public List<Video> getAllAfterCurrent() {
        if (mCurrentIndex == -1) {
            return mPlaylist;
        }

        int fromIndex = mCurrentIndex + 1;
        if (fromIndex > 0 && fromIndex < mPlaylist.size()) {
            return mPlaylist.subList(fromIndex, mPlaylist.size());
        }

        return null;
    }

    public void removeAllAfterCurrent() {
        if (mCurrentIndex == -1) {
            return;
        }

        int fromIndex = mCurrentIndex + 1;
        int size = mPlaylist.size();
        if (fromIndex > 0 && fromIndex < size) {
            //mPlaylist = mPlaylist.subList(0, fromIndex);
            mPlaylist.subList(fromIndex, size).clear();
        }
    }

    /**
     * Trim playlist if one exceeds needed size or current element not last in the list
     */
    private void trimPlaylist() {
        int size = mPlaylist.size();
        boolean playlistTooBig = size > PLAYLIST_MAX_SIZE;

        if (playlistTooBig) {
            //int fromIndex = mPlaylist.size() - PLAYLIST_MAX_SIZE;
            //int toIndex = mPlaylist.size();
            //mPlaylist = mPlaylist.subList(fromIndex, toIndex);
            int toIndex = size - PLAYLIST_MAX_SIZE;
            mPlaylist.subList(0, toIndex).clear();
            mCurrentIndex -= toIndex;
        }
    }

    /**
     * Do some cleanup to prevent possible OOM exception
     */
    private void stripPrevItem() {
        if (mCurrentIndex == -1) {
            return;
        }

        int prevPosition = mCurrentIndex - 1;

        if (prevPosition < mPlaylist.size() && prevPosition >= 0) {
            Video prevItem = mPlaylist.get(prevPosition);
            if (prevItem != null) {
                prevItem.mediaItem = null;
                prevItem.nextMediaItem = null;
                prevItem.shuffleMediaItem = null;
            }
        }
    }

    private void replace(Video origin, Video newItem) {
        int index = mPlaylist.indexOf(origin);

        if (index != -1) {
            mPlaylist.set(index, newItem);
        }
    }

    public void onNewSession() {
        mSyncedItems.clear();
    }

    /**
     * Since all items are clones (saves memory) we need to sync sometimes.
     */
    public void sync(Video origin) {
        if (origin == null) {
            return;
        }

        // Sync to maintain order. Item may be cloned.
        for (Video video : mPlaylist) {
            if (video.equals(origin)) {
                video.sync(origin);
                break;
            }
        }

        // Replace if exists. Item may be cloned.
        mSyncedItems.remove(origin);
        mSyncedItems.add(origin);
    }
}