package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.List;

public class UniqueRandom {
    private static final int RANDOM_FAIL_REPEAT_TIMES = 10;
    private List<Integer> mUsedIndexes;
    private int mTotalSize;

    public int getRandomIndex(int size) {
        if (mUsedIndexes == null) {
            mUsedIndexes = new ArrayList<>();
        }

        if (mTotalSize != size || mUsedIndexes.size() == size) {
            mUsedIndexes.clear();
            mTotalSize = size;
        }

        int randomIndex = 0;

        for (int i = 0; i < RANDOM_FAIL_REPEAT_TIMES; i++) {
            randomIndex = Helpers.getRandomIndex(size);
            if (!mUsedIndexes.contains(randomIndex)) {
                mUsedIndexes.add(randomIndex);
                break;
            }
        }

        return randomIndex;
    }
}
