package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class WeakHashSet<T> {
    public interface OnItem<T> {
        void onItem(T item);
    }

    private final List<WeakReference<T>> mWeakReferences = new ArrayList<>();

    public boolean add(T t) {
        if (t != null && !contains(t)) {
            cleanup();
            mWeakReferences.add(new WeakReference<>(t));
            return true;
        }

        return false;
    }

    public void remove(T t) {
        if (t != null) {
            Helpers.removeIf(mWeakReferences, item -> t.equals(item.get()));
        }
    }

    public int size() {
        return mWeakReferences.size();
    }

    public void forEach(OnItem<T> onItem) {
        for (WeakReference<T> reference : mWeakReferences) {
            if (reference.get() != null) {
                onItem.onItem(reference.get());
            }
        }
    }

    public boolean contains(T t) {
        return Helpers.containsIf(mWeakReferences, item -> t.equals(item.get()));
    }

    public void clear() {
        mWeakReferences.clear();
    }

    public boolean isEmpty() {
        return mWeakReferences.isEmpty();
    }

    private void cleanup() {
        Helpers.removeIf(mWeakReferences, item -> item.get() == null);
    }
}
