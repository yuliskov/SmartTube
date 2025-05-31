package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class UniqueRandom {
    private static final int RANDOM_FAIL_REPEAT_TIMES = 10;

    public static int getRandomIndex(int currentIdx, int playlistSize) {
        if (playlistSize <= 1) {
            return 0;
        }

        int randomIndex = 0;

        for (int i = 0; i < RANDOM_FAIL_REPEAT_TIMES; i++) {
            randomIndex = Helpers.getRandomIndex(playlistSize);
            if (randomIndex != currentIdx) {
                break;
            }
        }

        return randomIndex;
    }
}
