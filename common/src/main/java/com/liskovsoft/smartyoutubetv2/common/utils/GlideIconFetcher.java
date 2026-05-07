package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GlideIconFetcher {
    public static void fetchDrawables(Context context, List<String> iconUrls, Callback onFetch) {
        if (iconUrls == null || iconUrls.isEmpty()) {
            onFetch.onDone(null);
            return;
        }

        List<Drawable> result = new ArrayList<>(iconUrls.size());
        AtomicInteger counter = new AtomicInteger(0);

        // pre-fill to keep ordering
        for (int i = 0; i < iconUrls.size(); i++) {
            result.add(null);
        }

        for (int i = 0; i < iconUrls.size(); i++) {
            final int index = i;
            String url = iconUrls.get(i);

            if (url == null) {
                result.set(index, null);
                counter.incrementAndGet();
                continue;
            }

            Glide.with(context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, Transition<? super Drawable> transition) {
                            result.set(index, resource);

                            if (counter.incrementAndGet() == iconUrls.size()) {
                                onFetch.onDone(result);
                            }
                        }

                        @Override
                        public void onLoadFailed(Drawable errorDrawable) {
                            // null is fine for failed loads
                            result.set(index, null);

                            if (counter.incrementAndGet() == iconUrls.size()) {
                                onFetch.onDone(result);
                            }
                        }

                        @Override
                        public void onLoadCleared(Drawable placeholder) {
                            // optional cleanup
                        }
                    });
        }
    }

    public interface Callback {
        void onDone(List<Drawable> icons);
    }
}
