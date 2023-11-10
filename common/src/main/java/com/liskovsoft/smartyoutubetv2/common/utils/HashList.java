package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.ArrayList;

public class HashList<T> extends ArrayList<T> {
    @Override
    public boolean add(T item) {
        if (item == null || indexOf(item) == size() - 1) {
            return false;
        } else if (contains(item)) {
            remove(item);
        }

        return super.add(item);
    }

    @Override
    public void add(int index, T item) {
        if (item == null || indexOf(item) == index) {
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
