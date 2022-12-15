/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A downloader for progressive media streams.
 */
public final class ProgressiveDownloader implements Downloader {

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final DataSpec dataSpec;
  private final Cache cache;
  private final CacheDataSource dataSource;
  private final CacheKeyFactory cacheKeyFactory;
  private final PriorityTaskManager priorityTaskManager;
  private final AtomicBoolean isCanceled;

  /**
   * @param uri Uri of the data to be downloaded.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param constructorHelper A {@link DownloaderConstructorHelper} instance.
   */
  public ProgressiveDownloader(
      Uri uri, @Nullable String customCacheKey, DownloaderConstructorHelper constructorHelper) {
    this.dataSpec =
        new DataSpec(
            uri,
            /* absoluteStreamPosition= */ 0,
            C.LENGTH_UNSET,
            customCacheKey,
            /* flags= */ DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION);
    this.cache = constructorHelper.getCache();
    this.dataSource = constructorHelper.createCacheDataSource();
    this.cacheKeyFactory = constructorHelper.getCacheKeyFactory();
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
    isCanceled = new AtomicBoolean();
  }

  @Override
  public void download(@Nullable ProgressListener progressListener)
      throws InterruptedException, IOException {
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      CacheUtil.cache(
          dataSpec,
          cache,
          cacheKeyFactory,
          dataSource,
          new byte[BUFFER_SIZE_BYTES],
          priorityTaskManager,
          C.PRIORITY_DOWNLOAD,
          progressListener == null ? null : new ProgressForwarder(progressListener),
          isCanceled,
          /* enableEOFException= */ true);
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
    }
  }

  @Override
  public void cancel() {
    isCanceled.set(true);
  }

  @Override
  public void remove() {
    CacheUtil.remove(dataSpec, cache, cacheKeyFactory);
  }

  private static final class ProgressForwarder implements CacheUtil.ProgressListener {

    private final ProgressListener progessListener;

    public ProgressForwarder(ProgressListener progressListener) {
      this.progessListener = progressListener;
    }

    @Override
    public void onProgress(long contentLength, long bytesCached, long newBytesCached) {
      float percentDownloaded =
          contentLength == C.LENGTH_UNSET || contentLength == 0
              ? C.PERCENTAGE_UNSET
              : ((bytesCached * 100f) / contentLength);
      progessListener.onProgress(contentLength, bytesCached, percentDownloaded);
    }
  }
}
