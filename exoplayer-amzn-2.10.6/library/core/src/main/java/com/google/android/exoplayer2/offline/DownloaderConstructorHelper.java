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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.PriorityDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSinkFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.PriorityTaskManager;

/** A helper class that holds necessary parameters for {@link Downloader} construction. */
public final class DownloaderConstructorHelper {

  private final Cache cache;
  @Nullable private final CacheKeyFactory cacheKeyFactory;
  @Nullable private final PriorityTaskManager priorityTaskManager;
  private final CacheDataSourceFactory onlineCacheDataSourceFactory;
  private final CacheDataSourceFactory offlineCacheDataSourceFactory;

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   */
  public DownloaderConstructorHelper(Cache cache, DataSource.Factory upstreamFactory) {
    this(
        cache,
        upstreamFactory,
        /* cacheReadDataSourceFactory= */ null,
        /* cacheWriteDataSinkFactory= */ null,
        /* priorityTaskManager= */ null);
  }

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   * @param cacheReadDataSourceFactory A {@link DataSource.Factory} for creating {@link DataSource}s
   *     for reading data from the cache. If null then a {@link FileDataSourceFactory} will be used.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for creating {@link DataSource}s
   *     for writing data to the cache. If null then a {@link CacheDataSinkFactory} will be used.
   * @param priorityTaskManager A {@link PriorityTaskManager} to use when downloading. If non-null,
   *     downloaders will register as tasks with priority {@link C#PRIORITY_DOWNLOAD} whilst
   *     downloading.
   */
  public DownloaderConstructorHelper(
      Cache cache,
      DataSource.Factory upstreamFactory,
      @Nullable DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager) {
    this(
        cache,
        upstreamFactory,
        cacheReadDataSourceFactory,
        cacheWriteDataSinkFactory,
        priorityTaskManager,
        /* cacheKeyFactory= */ null);
  }

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   * @param cacheReadDataSourceFactory A {@link DataSource.Factory} for creating {@link DataSource}s
   *     for reading data from the cache. If null then a {@link FileDataSourceFactory} will be used.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for creating {@link DataSource}s
   *     for writing data to the cache. If null then a {@link CacheDataSinkFactory} will be used.
   * @param priorityTaskManager A {@link PriorityTaskManager} to use when downloading. If non-null,
   *     downloaders will register as tasks with priority {@link C#PRIORITY_DOWNLOAD} whilst
   *     downloading.
   * @param cacheKeyFactory An optional factory for cache keys.
   */
  public DownloaderConstructorHelper(
      Cache cache,
      DataSource.Factory upstreamFactory,
      @Nullable DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager,
      @Nullable CacheKeyFactory cacheKeyFactory) {
    if (priorityTaskManager != null) {
      upstreamFactory =
          new PriorityDataSourceFactory(upstreamFactory, priorityTaskManager, C.PRIORITY_DOWNLOAD);
    }
    DataSource.Factory readDataSourceFactory =
        cacheReadDataSourceFactory != null
            ? cacheReadDataSourceFactory
            : new FileDataSourceFactory();
    if (cacheWriteDataSinkFactory == null) {
      cacheWriteDataSinkFactory =
          new CacheDataSinkFactory(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE);
    }
    onlineCacheDataSourceFactory =
        new CacheDataSourceFactory(
            cache,
            upstreamFactory,
            readDataSourceFactory,
            cacheWriteDataSinkFactory,
            CacheDataSource.FLAG_BLOCK_ON_CACHE,
            /* eventListener= */ null,
            cacheKeyFactory);
    offlineCacheDataSourceFactory =
        new CacheDataSourceFactory(
            cache,
            DummyDataSource.FACTORY,
            readDataSourceFactory,
            null,
            CacheDataSource.FLAG_BLOCK_ON_CACHE,
            /* eventListener= */ null,
            cacheKeyFactory);
    this.cache = cache;
    this.priorityTaskManager = priorityTaskManager;
    this.cacheKeyFactory = cacheKeyFactory;
  }

  /** Returns the {@link Cache} instance. */
  public Cache getCache() {
    return cache;
  }

  /** Returns the {@link CacheKeyFactory}. */
  public CacheKeyFactory getCacheKeyFactory() {
    return cacheKeyFactory != null ? cacheKeyFactory : CacheUtil.DEFAULT_CACHE_KEY_FACTORY;
  }

  /** Returns a {@link PriorityTaskManager} instance. */
  public PriorityTaskManager getPriorityTaskManager() {
    // Return a dummy PriorityTaskManager if none is provided. Create a new PriorityTaskManager
    // each time so clients don't affect each other over the dummy PriorityTaskManager instance.
    return priorityTaskManager != null ? priorityTaskManager : new PriorityTaskManager();
  }

  /** Returns a new {@link CacheDataSource} instance. */
  public CacheDataSource createCacheDataSource() {
    return onlineCacheDataSourceFactory.createDataSource();
  }

  /**
   * Returns a new {@link CacheDataSource} instance which accesses cache read-only and throws an
   * exception on cache miss.
   */
  public CacheDataSource createOfflineCacheDataSource() {
    return offlineCacheDataSourceFactory.createDataSource();
  }
}
