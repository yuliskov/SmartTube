package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WeakHashSet<T> {
    public interface OnItem<T> {
        void onItem(T item);
    }

    private final List<WeakReference<T>> mWeakReferences = new CopyOnWriteArrayList<>(); // ConcurrentModificationException fix

    public boolean add(T item) {
        if (item != null && !contains(item)) {
            cleanup();
            mWeakReferences.add(new WeakReference<>(item));
            return true;
        }

        return false;
    }

    public void remove(T item) {
        if (item != null) {
            Helpers.removeIf(mWeakReferences, next -> item.equals(next.get()));
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

    public boolean contains(T item) {
        return Helpers.containsIf(mWeakReferences, next -> item.equals(next.get()));
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
