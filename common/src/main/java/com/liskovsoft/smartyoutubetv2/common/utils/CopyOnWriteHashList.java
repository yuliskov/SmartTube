package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteHashList<T> extends CopyOnWriteArrayList<T> {
    @Override
    public boolean add(T item) {
        int index = size() - 1;
        if (item == null || (index >= 0 && indexOf(item) == index)) {
            return false;
        } else if (contains(item)) {
            remove(item);
        }

        return super.add(item);
    }

    @Override
    public void add(int index, T item) {
        if (item == null || (index >= 0 && indexOf(item) == index)) {
            return;
        } else if (contains(item)) {
            remove(item);
        }

        if (index >= 0 && index < size()) {
            super.add(index, item);
        } else {
            super.add(item);
        }
    }
}
