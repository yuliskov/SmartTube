/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;

/** A {@link DataSource.Factory} that produces {@link CacheDataSource}. */
public final class CacheDataSourceFactory implements DataSource.Factory {

  private final Cache cache;
  private final DataSource.Factory upstreamFactory;
  private final DataSource.Factory cacheReadDataSourceFactory;
  @CacheDataSource.Flags private final int flags;
  @Nullable private final DataSink.Factory cacheWriteDataSinkFactory;
  @Nullable private final CacheDataSource.EventListener eventListener;
  @Nullable private final CacheKeyFactory cacheKeyFactory;

  /**
   * Constructs a factory which creates {@link CacheDataSource} instances with default {@link
   * DataSource} and {@link DataSink} instances for reading and writing the cache.
   *
   * @param cache The cache.
   * @param upstreamFactory A {@link DataSource.Factory} for creating upstream {@link DataSource}s
   *     for reading data not in the cache.
   */
  public CacheDataSourceFactory(Cache cache, DataSource.Factory upstreamFactory) {
    this(cache, upstreamFactory, /* flags= */ 0);
  }

  /** @see CacheDataSource#CacheDataSource(Cache, DataSource, int) */
  public CacheDataSourceFactory(
      Cache cache, DataSource.Factory upstreamFactory, @CacheDataSource.Flags int flags) {
    this(
        cache,
        upstreamFactory,
        new FileDataSourceFactory(),
        new CacheDataSinkFactory(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        flags,
        /* eventListener= */ null);
  }

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource, DataSource, DataSink, int,
   *     CacheDataSource.EventListener)
   */
  public CacheDataSourceFactory(
      Cache cache,
      DataSource.Factory upstreamFactory,
      DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @CacheDataSource.Flags int flags,
      @Nullable CacheDataSource.EventListener eventListener) {
    this(
        cache,
        upstreamFactory,
        cacheReadDataSourceFactory,
        cacheWriteDataSinkFactory,
        flags,
        eventListener,
        /* cacheKeyFactory= */ null);
  }

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource, DataSource, DataSink, int,
   *     CacheDataSource.EventListener, CacheKeyFactory)
   */
  public CacheDataSourceFactory(
      Cache cache,
      DataSource.Factory upstreamFactory,
      DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @CacheDataSource.Flags int flags,
      @Nullable CacheDataSource.EventListener eventListener,
      @Nullable CacheKeyFactory cacheKeyFactory) {
    this.cache = cache;
    this.upstreamFactory = upstreamFactory;
    this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
    this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
    this.flags = flags;
    this.eventListener = eventListener;
    this.cacheKeyFactory = cacheKeyFactory;
  }

  @Override
  public CacheDataSource createDataSource() {
    return new CacheDataSource(
        cache,
        upstreamFactory.createDataSource(),
        cacheReadDataSourceFactory.createDataSource(),
        cacheWriteDataSinkFactory == null ? null : cacheWriteDataSinkFactory.createDataSink(),
        flags,
        eventListener,
        cacheKeyFactory);
  }

}
