package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class UniqueRandom {
    private static final int RANDOM_FAIL_REPEAT_TIMES = 10;
    //private List<Integer> mUsedIndexes;
    //private int mPlaylistSize;
    //private String mPlaylistId;

    //public int getPlaylistIndex(int playlistSize) {
    //    return getPlaylistIndex(null, playlistSize);
    //}
    //
    //public int getPlaylistIndex(String playlistId, int playlistSize) {
    //    if (mUsedIndexes == null) {
    //        mUsedIndexes = new ArrayList<>();
    //    }
    //
    //    if (!Helpers.equals(mPlaylistId, playlistId) || mPlaylistSize != playlistSize || mUsedIndexes.size() == playlistSize) {
    //        mUsedIndexes.clear();
    //        mPlaylistSize = playlistSize;
    //        mPlaylistId = playlistId;
    //    }
    //
    //    int randomIndex = 0;
    //
    //    for (int i = 0; i < RANDOM_FAIL_REPEAT_TIMES; i++) {
    //        randomIndex = Helpers.getRandomIndex(playlistSize);
    //        if (!mUsedIndexes.contains(randomIndex)) {
    //            mUsedIndexes.add(randomIndex);
    //            break;
    //        }
    //    }
    //
    //    return randomIndex;
    //}

    public static int getRandomIndex(int currentIdx, int playlistSize) {
        if (playlistSize <= 1) {
            return -1;
        }

        int randomIndex = -1;

        for (int i = 0; i < RANDOM_FAIL_REPEAT_TIMES; i++) {
            randomIndex = Helpers.getRandomIndex(playlistSize);
            if (randomIndex != currentIdx) {
                break;
            }
        }

        return randomIndex;
    }
}
