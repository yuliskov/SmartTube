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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for multi segment stream downloaders.
 *
 * @param <M> The type of the manifest object.
 */
public abstract class SegmentDownloader<M extends FilterableManifest<M>> implements Downloader {

  /** Smallest unit of content to be downloaded. */
  protected static class Segment implements Comparable<Segment> {

    /** The start time of the segment in microseconds. */
    public final long startTimeUs;

    /** The {@link DataSpec} of the segment. */
    public final DataSpec dataSpec;

    /** Constructs a Segment. */
    public Segment(long startTimeUs, DataSpec dataSpec) {
      this.startTimeUs = startTimeUs;
      this.dataSpec = dataSpec;
    }

    @Override
    public int compareTo(@NonNull Segment other) {
      return Util.compareLong(startTimeUs, other.startTimeUs);
    }
  }

  private static final long MAX_MERGED_SEGMENT_START_TIME_DIFF_US = 20 * C.MICROS_PER_SECOND;

  private final DataSpec manifestDataSpec;
  private final Cache cache;
  private final CacheDataSource offlineDataSource;
  private final CacheKeyFactory cacheKeyFactory;
  private final PriorityTaskManager priorityTaskManager;
  private final ArrayList<StreamKey> streamKeys;
  private final AtomicBoolean isCanceled;

  private final ResourcePoolProvider<CacheDataSource> cacheDataSources;
  private final ResourcePoolProvider<byte[]> buffers;

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which streams in the manifest should be selected for download.
   *     If empty, all streams are downloaded.
   * @param constructorHelper A {@link DownloaderConstructorHelper} instance.
   */
  public SegmentDownloader(
      Uri manifestUri, List<StreamKey> streamKeys, DownloaderConstructorHelper constructorHelper) {
    this.manifestDataSpec = getCompressibleDataSpec(manifestUri);
    this.streamKeys = new ArrayList<>(streamKeys);
    this.cache = constructorHelper.getCache();
    this.offlineDataSource = constructorHelper.createOfflineCacheDataSource();
    this.cacheKeyFactory = constructorHelper.getCacheKeyFactory();
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
    this.cacheDataSources = new ResourcePoolProvider<>(constructorHelper::createCacheDataSource);
    this.buffers = new ResourcePoolProvider<>(() -> new byte[CacheUtil.DEFAULT_BUFFER_SIZE_BYTES]);
    isCanceled = new AtomicBoolean();
  }

  /**
   * Downloads the selected streams in the media. If multiple streams are selected, they are
   * downloaded in sync with one another.
   *
   * @throws IOException Thrown when there is an error downloading.
   * @throws InterruptedException If the thread has been interrupted.
   */
  @Override
  public final void download(@Nullable ProgressListener progressListener)
      throws IOException, InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(
        Math.max(1, cacheKeyFactory.maxDownloadParallelSegments()));
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      // Get the manifest and all of the segments.
      List<Segment> segments;
      CacheDataSource dataSource = cacheDataSources.obtain();
      try {
        M manifest = getManifest(dataSource, manifestDataSpec);
        if (!streamKeys.isEmpty()) {
          manifest = manifest.copy(streamKeys);
        }
        segments = getSegments(dataSource, manifest, /* allowIncompleteList= */
            false);
      } finally {
        cacheDataSources.release(dataSource);
      }

      // Sort, merge and reassign segment cache key
      Collections.sort(segments);
      // TODO Merge segment can cause in playback with the current approach
      //mergeSegments(segments, cacheKeyFactory);
      reassignCacheKey(segments);

      // Scan the segments, removing any that are fully downloaded.
      int totalSegments = segments.size();
      int segmentsDownloaded = 0;
      long contentLength = 0;
      long bytesDownloaded = 0;
      for (int i = segments.size() - 1; i >= 0; i--) {
        Segment segment = segments.get(i);
        Pair<Long, Long> segmentLengthAndBytesDownloaded =
            CacheUtil.getCached(segment.dataSpec, cache, cacheKeyFactory);
        long segmentLength = segmentLengthAndBytesDownloaded.first;
        long segmentBytesDownloaded = segmentLengthAndBytesDownloaded.second;
        bytesDownloaded += segmentBytesDownloaded;
        if (segmentLength != C.LENGTH_UNSET) {
          if (segmentLength == segmentBytesDownloaded) {
            // The segment is fully downloaded.
            segmentsDownloaded++;
            segments.remove(i);
          }
          if (contentLength != C.LENGTH_UNSET) {
            contentLength += segmentLength;
          }
        } else {
          contentLength = C.LENGTH_UNSET;
        }
      }

      // Download the segments.
      ProgressNotifier progressNotifier = null;
      if (progressListener != null) {
        progressNotifier =
            new ProgressNotifier(
                progressListener,
                contentLength,
                totalSegments,
                bytesDownloaded,
                segmentsDownloaded);
      }
      final int size = segments.size();
      final AbortableCountDownLatch countDownLatch = new AbortableCountDownLatch(size);
      final List<Future<SegmentDownloadTaskResult>> futures = new ArrayList<>(size);
      for (Segment segment : segments) {
        futures.add(
            executorService.submit(
                new SegmentDownloadTask<>(
                    this,
                    segment,
                    progressNotifier,
                    countDownLatch)));
      }
      countDownLatch.await();
      executorService.shutdownNow();
      for (Future<SegmentDownloadTaskResult> future : futures) {
        try {
          SegmentDownloadTaskResult result = future.get();
          if (result.cause != null) {
            throw result.cause;
          }
        } catch (ExecutionException ex) {
          // ignore
        }
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
      executorService.shutdownNow();
    }
  }

  @Override
  public void cancel() {
    isCanceled.set(true);
  }

  @Override
  public final void remove() throws InterruptedException {
    try {
      M manifest = getManifest(offlineDataSource, manifestDataSpec);
      List<Segment> segments = getSegments(offlineDataSource, manifest, true);
      reassignCacheKey(segments);
      for (int i = 0; i < segments.size(); i++) {
        removeDataSpec(segments.get(i).dataSpec);
      }
    } catch (IOException e) {
      // Ignore exceptions when removing.
    } finally {
      // Always attempt to remove the manifest.
      removeDataSpec(manifestDataSpec);
    }
  }

  // Internal methods.

  /**
   * Loads and parses the manifest.
   *
   * @param dataSource The {@link DataSource} through which to load.
   * @param dataSpec The manifest {@link DataSpec}.
   * @return The manifest.
   * @throws IOException If an error occurs reading data.
   */
  protected abstract M getManifest(DataSource dataSource, DataSpec dataSpec) throws IOException;

  /**
   * Returns a list of all downloadable {@link Segment}s for a given manifest.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param allowIncompleteList Whether to continue in the case that a load error prevents all
   *     segments from being listed. If true then a partial segment list will be returned. If false
   *     an {@link IOException} will be thrown.
   * @return The list of downloadable {@link Segment}s.
   * @throws InterruptedException Thrown if the thread was interrupted.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and a load error occurs, or if
   *     the media is not in a form that allows for its segments to be listed.
   */
  protected abstract List<Segment> getSegments(
      DataSource dataSource, M manifest, boolean allowIncompleteList)
      throws InterruptedException, IOException;

  private void removeDataSpec(DataSpec dataSpec) {
    CacheUtil.remove(dataSpec, cache, cacheKeyFactory);
  }

  protected static DataSpec getCompressibleDataSpec(Uri uri) {
    return new DataSpec(
        uri,
        /* absoluteStreamPosition= */ 0,
        /* length= */ C.LENGTH_UNSET,
        /* key= */ null,
        /* flags= */ DataSpec.FLAG_ALLOW_GZIP);
  }

  private static void mergeSegments(List<Segment> segments, CacheKeyFactory keyFactory) {
    HashMap<String, Integer> lastIndexByCacheKey = new HashMap<>();
    int nextOutIndex = 0;
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      String cacheKey = keyFactory.buildCacheKey(segment.dataSpec);
      @Nullable Integer lastIndex = lastIndexByCacheKey.get(cacheKey);
      @Nullable Segment lastSegment = lastIndex == null ? null : segments.get(lastIndex);
      if (lastSegment == null
          || segment.startTimeUs > lastSegment.startTimeUs + MAX_MERGED_SEGMENT_START_TIME_DIFF_US
          || !canMergeSegments(lastSegment.dataSpec, segment.dataSpec)) {
        lastIndexByCacheKey.put(cacheKey, nextOutIndex);
        segments.set(nextOutIndex, segment);
        nextOutIndex++;
      } else {
        long mergedLength =
            segment.dataSpec.length == C.LENGTH_UNSET
                ? C.LENGTH_UNSET
                : lastSegment.dataSpec.length + segment.dataSpec.length;
        DataSpec mergedDataSpec = lastSegment.dataSpec.subrange(/* offset= */ 0, mergedLength);
        segments.set(
            Assertions.checkNotNull(lastIndex),
            new Segment(lastSegment.startTimeUs, mergedDataSpec));
      }
    }
    Util.removeRange(segments, /* fromIndex= */ nextOutIndex, /* toIndex= */ segments.size());
  }

  private static boolean canMergeSegments(DataSpec dataSpec1, DataSpec dataSpec2) {
    return dataSpec1.uri.equals(dataSpec2.uri)
        && dataSpec1.length != C.LENGTH_UNSET
        && (dataSpec1.absoluteStreamPosition + dataSpec1.length == dataSpec2.absoluteStreamPosition)
        && Util.areEqual(dataSpec1.key, dataSpec2.key)
        && dataSpec1.flags == dataSpec2.flags
        && dataSpec1.httpMethod == dataSpec2.httpMethod
        && dataSpec1.httpRequestHeaders.equals(dataSpec2.httpRequestHeaders);
  }

  private void reassignCacheKey(List<Segment> segments) {
    int size = segments.size();
    for (int i = 0; i < size; i++) {
      Segment segment = segments.get(i);
      if (segment.dataSpec.key == null) {
        DataSpec dataSpec = new DataSpec(
            segment.dataSpec.uri,
            segment.dataSpec.httpMethod,
            segment.dataSpec.httpBody,
            segment.dataSpec.absoluteStreamPosition,
            segment.dataSpec.position,
            segment.dataSpec.length,
            cacheKeyFactory.buildCacheKey(segment.dataSpec),
            segment.dataSpec.flags,
            segment.dataSpec.httpRequestHeaders);
        segments.set(i, new Segment(segment.startTimeUs, dataSpec));
      }
    }
  }

  private static final class ProgressNotifier implements CacheUtil.ProgressListener {

    private final ProgressListener progressListener;

    private final long contentLength;
    private final int totalSegments;

    private long bytesDownloaded;
    private int segmentsDownloaded;

    ProgressNotifier(
        ProgressListener progressListener,
        long contentLength,
        int totalSegments,
        long bytesDownloaded,
        int segmentsDownloaded) {
      this.progressListener = progressListener;
      this.contentLength = contentLength;
      this.totalSegments = totalSegments;
      this.bytesDownloaded = bytesDownloaded;
      this.segmentsDownloaded = segmentsDownloaded;
    }

    @Override
    public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
      bytesDownloaded += newBytesCached;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    void onSegmentDownloaded() {
      segmentsDownloaded++;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    private float getPercentDownloaded() {
      if (contentLength != C.LENGTH_UNSET && contentLength != 0) {
        return (bytesDownloaded * 100f) / contentLength;
      } else if (totalSegments != 0) {
        return (segmentsDownloaded * 100f) / totalSegments;
      } else {
        return C.PERCENTAGE_UNSET;
      }
    }
  }

  private static final class SegmentDownloadTaskResult {
    public final Segment segment;
    public final IOException cause;

    public SegmentDownloadTaskResult(Segment segment, IOException cause) {
      this.segment = segment;
      this.cause = cause;
    }
  }

  private static final class SegmentDownloadTask<M extends FilterableManifest<M>> implements Callable<SegmentDownloadTaskResult> {
    private final SegmentDownloader<M> downloader;
    private final Segment segment;
    private final ProgressNotifier progressNotifier;
    private final AbortableCountDownLatch countDownLatch;

    SegmentDownloadTask(
        SegmentDownloader<M> downloader,
        Segment segment,
        ProgressNotifier progressNotifier,
        AbortableCountDownLatch countDownLatch) {
      this.downloader = downloader;
      this.segment = segment;
      this.progressNotifier = progressNotifier;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public SegmentDownloadTaskResult call() {
      boolean abort = false;
      try {
        byte[] buffer = downloader.buffers.obtain();
        CacheDataSource cacheDataSource = downloader.cacheDataSources.obtain();
        try {
          CacheUtil.cache(
              segment.dataSpec,
              downloader.cache,
              downloader.cacheKeyFactory,
              cacheDataSource,
              buffer,
              downloader.priorityTaskManager,
              C.PRIORITY_DOWNLOAD,
              progressNotifier,
              downloader.isCanceled,
              true);
          if (progressNotifier != null) {
            progressNotifier.onSegmentDownloaded();
          }
          return new SegmentDownloadTaskResult(segment, null);
        } finally {
          downloader.cacheDataSources.release(cacheDataSource);
          downloader.buffers.release(buffer);
        }
      } catch (Exception ex) {
        abort = true;
        if (ex instanceof IOException) {
          return new SegmentDownloadTaskResult(segment, (IOException) ex);
        }
        return new SegmentDownloadTaskResult(segment, new IOException(ex));
      } finally {
        countDownLatch.countDown();
        if (abort) {
          countDownLatch.abort();
        }
      }
    }
  }

  private static class AbortableCountDownLatch extends CountDownLatch {
    public AbortableCountDownLatch(int count) {
      super(count);
    }

    public void abort() {
      while(getCount() > 0) {
        countDown();
      }
    }
  }

  private static class ResourcePoolProvider<T> {
    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final ResourceProvider<T> provider;

    ResourcePoolProvider(ResourceProvider<T> provider) {
      this.provider = provider;
    }

    public T obtain() {
      try {
        return pool.remove();
      } catch (NoSuchElementException ex) {
        // create a new resource
      }
      return provider.provide();
    }

    public void release(T resource) {
      pool.add(resource);
    }
  }

  private interface ResourceProvider<T> {
    T provide();
  }
}