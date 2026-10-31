package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;

/**
 * https://bumptech.github.io/glide/doc/configuration.html#disk-cache<br/>
 * https://stackoverflow.com/questions/46108915/how-to-increase-the-cache-size-in-glide-android
 */
@GlideModule
public class GlideCachingModule extends AppGlideModule {
    private final static long CACHE_SIZE = 10 * 1024 * 1024; // 10 MB

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        //if (MyApplication.from(context).isTest())
        //    return; // NOTE: StatFs will crash on robolectric.

        // Limit cache size
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, CACHE_SIZE));
    }
}
