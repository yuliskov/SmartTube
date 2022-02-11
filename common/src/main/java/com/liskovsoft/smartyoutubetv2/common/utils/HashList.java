package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.ArrayList;

public class HashList<T> extends ArrayList<T> {
    @Override
    public boolean add(T t) {
        if (contains(t)) {
            return false;
        }

        return super.add(t);
    }

    @Override
    public void add(int index, T element) {
        if (indexOf(element) == index) {
            return;
        } else if (contains(element)) {
            remove(element);
        }

        if (index >= 0 && index < size()) {
            super.add(index, element);
        } else {
            super.add(element);
        }
    }
}
