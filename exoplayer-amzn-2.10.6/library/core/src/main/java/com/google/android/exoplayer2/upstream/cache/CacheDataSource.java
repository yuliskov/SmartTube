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

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DataSpec.HttpMethod;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TeeDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link DataSource} that reads and writes a {@link Cache}. Requests are fulfilled from the cache
 * when possible. When data is not cached it is requested from an upstream {@link DataSource} and
 * written into the cache.
 */
public final class CacheDataSource implements DataSource {

  /**
   * Flags controlling the CacheDataSource's behavior. Possible flag values are {@link
   * #FLAG_BLOCK_ON_CACHE}, {@link #FLAG_IGNORE_CACHE_ON_ERROR} and {@link
   * #FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {
        FLAG_BLOCK_ON_CACHE,
        FLAG_IGNORE_CACHE_ON_ERROR,
        FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS
      })
  public @interface Flags {}
  /**
   * A flag indicating whether we will block reads if the cache key is locked. If unset then data is
   * read from upstream if the cache key is locked, regardless of whether the data is cached.
   */
  public static final int FLAG_BLOCK_ON_CACHE = 1;

  /**
   * A flag indicating whether the cache is bypassed following any cache related error. If set
   * then cache related exceptions may be thrown for one cycle of open, read and close calls.
   * Subsequent cycles of these calls will then bypass the cache.
   */
  public static final int FLAG_IGNORE_CACHE_ON_ERROR = 1 << 1; // 2

  /**
   * A flag indicating that the cache should be bypassed for requests whose lengths are unset. This
   * flag is provided for legacy reasons only.
   */
  public static final int FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS = 1 << 2; // 4

  /**
   * Reasons the cache may be ignored. One of {@link #CACHE_IGNORED_REASON_ERROR} or {@link
   * #CACHE_IGNORED_REASON_UNSET_LENGTH}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CACHE_IGNORED_REASON_ERROR, CACHE_IGNORED_REASON_UNSET_LENGTH})
  public @interface CacheIgnoredReason {}

  /** Cache not ignored. */
  private static final int CACHE_NOT_IGNORED = -1;

  /** Cache ignored due to a cache related error. */
  public static final int CACHE_IGNORED_REASON_ERROR = 0;

  /** Cache ignored due to a request with an unset length. */
  public static final int CACHE_IGNORED_REASON_UNSET_LENGTH = 1;

  /**
   * Listener of {@link CacheDataSource} events.
   */
  public interface EventListener {

    /**
     * Called when bytes have been read from the cache.
     *
     * @param cacheSizeBytes Current cache size in bytes.
     * @param cachedBytesRead Total bytes read from the cache since this method was last called.
     */
    void onCachedBytesRead(long cacheSizeBytes, long cachedBytesRead);

    /**
     * Called when the current request ignores cache.
     *
     * @param reason Reason cache is bypassed.
     */
    void onCacheIgnored(@CacheIgnoredReason int reason);
  }

  /** Minimum number of bytes to read before checking cache for availability. */
  private static final long MIN_READ_BEFORE_CHECKING_CACHE = 100 * 1024;

  private final Cache cache;
  private final DataSource cacheReadDataSource;
  private final @Nullable DataSource cacheWriteDataSource;
  private final DataSource upstreamDataSource;
  private final CacheKeyFactory cacheKeyFactory;
  @Nullable private final EventListener eventListener;

  private final boolean blockOnCache;
  private final boolean ignoreCacheOnError;
  private final boolean ignoreCacheForUnsetLengthRequests;

  private @Nullable DataSource currentDataSource;
  private boolean currentDataSpecLengthUnset;
  @Nullable private Uri uri;
  @Nullable private Uri actualUri;
  @HttpMethod private int httpMethod;
  private int flags;
  private @Nullable String key;
  private long readPosition;
  private long bytesRemaining;
  private @Nullable CacheSpan currentHoleSpan;
  private boolean seenCacheError;
  private boolean currentRequestIgnoresCache;
  private long totalCachedBytesRead;
  private long checkCachePosition;

  /**
   * Constructs an instance with default {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache.
   *
   * @param cache The cache.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   */
  public CacheDataSource(Cache cache, DataSource upstream) {
    this(cache, upstream, /* flags= */ 0);
  }

  /**
   * Constructs an instance with default {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache.
   *
   * @param cache The cache.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param flags A combination of {@link #FLAG_BLOCK_ON_CACHE}, {@link #FLAG_IGNORE_CACHE_ON_ERROR}
   *     and {@link #FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS}, or 0.
   */
  public CacheDataSource(Cache cache, DataSource upstream, @Flags int flags) {
    this(
        cache,
        upstream,
        new FileDataSource(),
        new CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        flags,
        /* eventListener= */ null);
  }

  /**
   * Constructs an instance with arbitrary {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache. One use of this constructor is to allow data to be transformed
   * before it is written to disk.
   *
   * @param cache The cache.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param cacheReadDataSource A {@link DataSource} for reading data from the cache.
   * @param cacheWriteDataSink A {@link DataSink} for writing data to the cache. If null, cache is
   *     accessed read-only.
   * @param flags A combination of {@link #FLAG_BLOCK_ON_CACHE}, {@link #FLAG_IGNORE_CACHE_ON_ERROR}
   *     and {@link #FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS}, or 0.
   * @param eventListener An optional {@link EventListener} to receive events.
   */
  public CacheDataSource(
      Cache cache,
      DataSource upstream,
      DataSource cacheReadDataSource,
      @Nullable DataSink cacheWriteDataSink,
      @Flags int flags,
      @Nullable EventListener eventListener) {
    this(
        cache,
        upstream,
        cacheReadDataSource,
        cacheWriteDataSink,
        flags,
        eventListener,
        /* cacheKeyFactory= */ null);
  }

  /**
   * Constructs an instance with arbitrary {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache. One use of this constructor is to allow data to be transformed
   * before it is written to disk.
   *
   * @param cache The cache.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param cacheReadDataSource A {@link DataSource} for reading data from the cache.
   * @param cacheWriteDataSink A {@link DataSink} for writing data to the cache. If null, cache is
   *     accessed read-only.
   * @param flags A combination of {@link #FLAG_BLOCK_ON_CACHE}, {@link #FLAG_IGNORE_CACHE_ON_ERROR}
   *     and {@link #FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS}, or 0.
   * @param eventListener An optional {@link EventListener} to receive events.
   * @param cacheKeyFactory An optional factory for cache keys.
   */
  public CacheDataSource(
      Cache cache,
      DataSource upstream,
      DataSource cacheReadDataSource,
      @Nullable DataSink cacheWriteDataSink,
      @Flags int flags,
      @Nullable EventListener eventListener,
      @Nullable CacheKeyFactory cacheKeyFactory) {
    this.cache = cache;
    this.cacheReadDataSource = cacheReadDataSource;
    this.cacheKeyFactory =
        cacheKeyFactory != null ? cacheKeyFactory : CacheUtil.DEFAULT_CACHE_KEY_FACTORY;
    this.blockOnCache = (flags & FLAG_BLOCK_ON_CACHE) != 0;
    this.ignoreCacheOnError = (flags & FLAG_IGNORE_CACHE_ON_ERROR) != 0;
    this.ignoreCacheForUnsetLengthRequests =
        (flags & FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS) != 0;
    this.upstreamDataSource = upstream;
    if (cacheWriteDataSink != null) {
      this.cacheWriteDataSource = new TeeDataSource(upstream, cacheWriteDataSink);
    } else {
      this.cacheWriteDataSource = null;
    }
    this.eventListener = eventListener;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
    cacheReadDataSource.addTransferListener(transferListener);
    upstreamDataSource.addTransferListener(transferListener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    try {
      key = cacheKeyFactory.buildCacheKey(dataSpec);
      uri = dataSpec.uri;
      actualUri = getRedirectedUriOrDefault(cache, key, /* defaultUri= */ uri);
      httpMethod = dataSpec.httpMethod;
      flags = dataSpec.flags;
      readPosition = dataSpec.position;

      int reason = shouldIgnoreCacheForRequest(dataSpec);
      currentRequestIgnoresCache = reason != CACHE_NOT_IGNORED;
      if (currentRequestIgnoresCache) {
        notifyCacheIgnored(reason);
      }

      if (dataSpec.length != C.LENGTH_UNSET || currentRequestIgnoresCache) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = ContentMetadata.getContentLength(cache.getContentMetadata(key));
        if (bytesRemaining != C.LENGTH_UNSET) {
          bytesRemaining -= dataSpec.position;
          if (bytesRemaining <= 0) {
            throw new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE);
          }
        }
      }
      openNextSource(false);
      return bytesRemaining;
    } catch (Throwable e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    }
    if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    try {
      if (readPosition >= checkCachePosition) {
        openNextSource(true);
      }
      int bytesRead = currentDataSource.read(buffer, offset, readLength);
      if (bytesRead != C.RESULT_END_OF_INPUT) {
        if (isReadingFromCache()) {
          totalCachedBytesRead += bytesRead;
        }
        readPosition += bytesRead;
        if (bytesRemaining != C.LENGTH_UNSET) {
          bytesRemaining -= bytesRead;
        }
      } else if (currentDataSpecLengthUnset) {
        setNoBytesRemainingAndMaybeStoreLength();
      } else if (bytesRemaining > 0 || bytesRemaining == C.LENGTH_UNSET) {
        closeCurrentSource();
        openNextSource(false);
        return read(buffer, offset, readLength);
      }
      return bytesRead;
    } catch (IOException e) {
      if (currentDataSpecLengthUnset && CacheUtil.isCausedByPositionOutOfRange(e)) {
        setNoBytesRemainingAndMaybeStoreLength();
        return C.RESULT_END_OF_INPUT;
      }
      handleBeforeThrow(e);
      throw e;
    } catch (Throwable e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  @Override
  public @Nullable Uri getUri() {
    return actualUri;
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    // TODO: Implement.
    return isReadingFromUpstream()
        ? upstreamDataSource.getResponseHeaders()
        : Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    uri = null;
    actualUri = null;
    httpMethod = DataSpec.HTTP_METHOD_GET;
    notifyBytesRead();
    try {
      closeCurrentSource();
    } catch (Throwable e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  /**
   * Opens the next source. If the cache contains data spanning the current read position then
   * {@link #cacheReadDataSource} is opened to read from it. Else {@link #upstreamDataSource} is
   * opened to read from the upstream source and write into the cache.
   *
   * <p>There must not be a currently open source when this method is called, except in the case
   * that {@code checkCache} is true. If {@code checkCache} is true then there must be a currently
   * open source, and it must be {@link #upstreamDataSource}. It will be closed and a new source
   * opened if it's possible to switch to reading from or writing to the cache. If a switch isn't
   * possible then the current source is left unchanged.
   *
   * @param checkCache If true tries to switch to reading from or writing to cache instead of
   *     reading from {@link #upstreamDataSource}, which is the currently open source.
   */
  private void openNextSource(boolean checkCache) throws IOException {
    CacheSpan nextSpan;
    if (currentRequestIgnoresCache) {
      nextSpan = null;
    } else if (blockOnCache) {
      try {
        nextSpan = cache.startReadWrite(key, readPosition);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InterruptedIOException();
      }
    } else {
      nextSpan = cache.startReadWriteNonBlocking(key, readPosition);
    }

    DataSpec nextDataSpec;
    DataSource nextDataSource;
    if (nextSpan == null) {
      // The data is locked in the cache, or we're ignoring the cache. Bypass the cache and read
      // from upstream.
      nextDataSource = upstreamDataSource;
      nextDataSpec =
          new DataSpec(
              uri, httpMethod, null, readPosition, readPosition, bytesRemaining, key, flags);
    } else if (nextSpan.isCached) {
      // Data is cached, read from cache.
      Uri fileUri = Uri.fromFile(nextSpan.file);
      long filePosition = readPosition - nextSpan.position;
      long length = nextSpan.length - filePosition;
      if (bytesRemaining != C.LENGTH_UNSET) {
        length = Math.min(length, bytesRemaining);
      }
      nextDataSpec = new DataSpec(fileUri, readPosition, filePosition, length, key, flags);
      nextDataSource = cacheReadDataSource;
    } else {
      // Data is not cached, and data is not locked, read from upstream with cache backing.
      long length;
      if (nextSpan.isOpenEnded()) {
        length = bytesRemaining;
      } else {
        length = nextSpan.length;
        if (bytesRemaining != C.LENGTH_UNSET) {
          length = Math.min(length, bytesRemaining);
        }
      }
      nextDataSpec =
          new DataSpec(uri, httpMethod, null, readPosition, readPosition, length, key, flags);
      if (cacheWriteDataSource != null) {
        nextDataSource = cacheWriteDataSource;
      } else {
        nextDataSource = upstreamDataSource;
        cache.releaseHoleSpan(nextSpan);
        nextSpan = null;
      }
    }

    checkCachePosition =
        !currentRequestIgnoresCache && nextDataSource == upstreamDataSource
            ? readPosition + MIN_READ_BEFORE_CHECKING_CACHE
            : Long.MAX_VALUE;
    if (checkCache) {
      Assertions.checkState(isBypassingCache());
      if (nextDataSource == upstreamDataSource) {
        // Continue reading from upstream.
        return;
      }
      // We're switching to reading from or writing to the cache.
      try {
        closeCurrentSource();
      } catch (Throwable e) {
        if (nextSpan.isHoleSpan()) {
          // Release the hole span before throwing, else we'll hold it forever.
          cache.releaseHoleSpan(nextSpan);
        }
        throw e;
      }
    }

    if (nextSpan != null && nextSpan.isHoleSpan()) {
      currentHoleSpan = nextSpan;
    }
    currentDataSource = nextDataSource;
    currentDataSpecLengthUnset = nextDataSpec.length == C.LENGTH_UNSET;
    long resolvedLength = nextDataSource.open(nextDataSpec);

    // Update bytesRemaining, actualUri and (if writing to cache) the cache metadata.
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    if (currentDataSpecLengthUnset && resolvedLength != C.LENGTH_UNSET) {
      bytesRemaining = resolvedLength;
      ContentMetadataMutations.setContentLength(mutations, readPosition + bytesRemaining);
    }
    if (isReadingFromUpstream()) {
      actualUri = currentDataSource.getUri();
      boolean isRedirected = !uri.equals(actualUri);
      ContentMetadataMutations.setRedirectedUri(mutations, isRedirected ? actualUri : null);
    }
    if (isWritingToCache()) {
      cache.applyContentMetadataMutations(key, mutations);
    }
  }

  private void setNoBytesRemainingAndMaybeStoreLength() throws IOException {
    bytesRemaining = 0;
    if (isWritingToCache()) {
      ContentMetadataMutations mutations = new ContentMetadataMutations();
      ContentMetadataMutations.setContentLength(mutations, readPosition);
      cache.applyContentMetadataMutations(key, mutations);
    }
  }

  private static Uri getRedirectedUriOrDefault(Cache cache, String key, Uri defaultUri) {
    Uri redirectedUri = ContentMetadata.getRedirectedUri(cache.getContentMetadata(key));
    return redirectedUri != null ? redirectedUri : defaultUri;
  }

  private boolean isReadingFromUpstream() {
    return !isReadingFromCache();
  }

  private boolean isBypassingCache() {
    return currentDataSource == upstreamDataSource;
  }

  private boolean isReadingFromCache() {
    return currentDataSource == cacheReadDataSource;
  }

  private boolean isWritingToCache() {
    return currentDataSource == cacheWriteDataSource;
  }

  private void closeCurrentSource() throws IOException {
    if (currentDataSource == null) {
      return;
    }
    try {
      currentDataSource.close();
    } finally {
      currentDataSource = null;
      currentDataSpecLengthUnset = false;
      if (currentHoleSpan != null) {
        cache.releaseHoleSpan(currentHoleSpan);
        currentHoleSpan = null;
      }
    }
  }

  private void handleBeforeThrow(Throwable exception) {
    if (isReadingFromCache() || exception instanceof CacheException) {
      seenCacheError = true;
    }
  }

  private int shouldIgnoreCacheForRequest(DataSpec dataSpec) {
    if (ignoreCacheOnError && seenCacheError) {
      return CACHE_IGNORED_REASON_ERROR;
    } else if (ignoreCacheForUnsetLengthRequests && dataSpec.length == C.LENGTH_UNSET) {
      return CACHE_IGNORED_REASON_UNSET_LENGTH;
    } else {
      return CACHE_NOT_IGNORED;
    }
  }

  private void notifyCacheIgnored(@CacheIgnoredReason int reason) {
    if (eventListener != null) {
      eventListener.onCacheIgnored(reason);
    }
  }

  private void notifyBytesRead() {
    if (eventListener != null && totalCachedBytesRead > 0) {
      eventListener.onCachedBytesRead(cache.getCacheSpace(), totalCachedBytesRead);
      totalCachedBytesRead = 0;
    }
  }

}
