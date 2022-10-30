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
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final DataSpec manifestDataSpec;
  private final Cache cache;
  private final CacheDataSource dataSource;
  private final CacheDataSource offlineDataSource;
  private final CacheKeyFactory cacheKeyFactory;
  private final PriorityTaskManager priorityTaskManager;
  private final ArrayList<StreamKey> streamKeys;
  private final AtomicBoolean isCanceled;

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
    this.dataSource = constructorHelper.createCacheDataSource();
    this.offlineDataSource = constructorHelper.createOfflineCacheDataSource();
    this.cacheKeyFactory = constructorHelper.getCacheKeyFactory();
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
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
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      // Get the manifest and all of the segments.
      M manifest = getManifest(dataSource, manifestDataSpec);
      if (!streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      List<Segment> segments = getSegments(dataSource, manifest, /* allowIncompleteList= */ false);

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
      Collections.sort(segments);

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
      byte[] buffer = new byte[BUFFER_SIZE_BYTES];
      for (int i = 0; i < segments.size(); i++) {
        CacheUtil.cache(
            segments.get(i).dataSpec,
            cache,
            cacheKeyFactory,
            dataSource,
            buffer,
            priorityTaskManager,
            C.PRIORITY_DOWNLOAD,
            progressNotifier,
            isCanceled,
            true);
        if (progressNotifier != null) {
          progressNotifier.onSegmentDownloaded();
        }
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
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

  private static final class ProgressNotifier implements CacheUtil.ProgressListener {

    private final ProgressListener progressListener;

    private final long contentLength;
    private final int totalSegments;

    private long bytesDownloaded;
    private int segmentsDownloaded;

    public ProgressNotifier(
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

    public void onSegmentDownloaded() {
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
}
