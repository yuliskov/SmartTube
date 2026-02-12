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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Provides one period that loads data from a {@link Uri} and extracted using an {@link Extractor}.
 *
 * <p>If the possible input stream container formats are known, pass a factory that instantiates
 * extractors for them to the constructor. Otherwise, pass a {@link DefaultExtractorsFactory} to use
 * the default extractors. When reading a new stream, the first {@link Extractor} in the array of
 * extractors created by the factory that returns {@code true} from {@link Extractor#sniff} will be
 * used to extract samples from the input stream.
 *
 * <p>Note that the built-in extractor for FLV streams does not support seeking.
 */
public final class ProgressiveMediaSource extends BaseMediaSource
    implements ProgressiveMediaPeriod.Listener {

  /** Factory for {@link ProgressiveMediaSource}s. */
  public static final class Factory implements AdsMediaSource.MediaSourceFactory {

    private final DataSource.Factory dataSourceFactory;

    private ExtractorsFactory extractorsFactory;
    @Nullable private String customCacheKey;
    @Nullable private Object tag;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private int continueLoadingCheckIntervalBytes;
    private boolean isCreateCalled;

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s, using the extractors provided by
     * {@link DefaultExtractorsFactory}.
     *
     * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, new DefaultExtractorsFactory());
    }

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
     * @param extractorsFactory A factory for extractors used to extract media from its container.
     */
    public Factory(DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.extractorsFactory = extractorsFactory;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      continueLoadingCheckIntervalBytes = DEFAULT_LOADING_CHECK_INTERVAL_BYTES;
    }

    /**
     * Sets the factory for {@link Extractor}s to process the media stream. The default value is an
     * instance of {@link DefaultExtractorsFactory}.
     *
     * @param extractorsFactory A factory for {@link Extractor}s to process the media stream. If the
     *     possible formats are known, pass a factory that instantiates extractors for those
     *     formats.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     * @deprecated Pass the {@link ExtractorsFactory} via {@link #Factory(DataSource.Factory,
     *     ExtractorsFactory)}. This is necessary so that proguard can treat the default extractors
     *     factory as unused.
     */
    @Deprecated
    public Factory setExtractorsFactory(ExtractorsFactory extractorsFactory) {
      Assertions.checkState(!isCreateCalled);
      this.extractorsFactory = extractorsFactory;
      return this;
    }

    /**
     * Sets the custom key that uniquely identifies the original stream. Used for cache indexing.
     * The default value is {@code null}.
     *
     * @param customCacheKey A custom key that uniquely identifies the original stream. Used for
     *     cache indexing.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setCustomCacheKey(String customCacheKey) {
      Assertions.checkState(!isCreateCalled);
      this.customCacheKey = customCacheKey;
      return this;
    }

    /**
     * Sets a tag for the media source which will be published in the {@link
     * com.google.android.exoplayer2.Timeline} of the source as {@link
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     *
     * @param tag A tag for the media source.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setTag(Object tag) {
      Assertions.checkState(!isCreateCalled);
      this.tag = tag;
      return this;
    }

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
     *
     * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      Assertions.checkState(!isCreateCalled);
      this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
      return this;
    }

    /**
     * Sets the number of bytes that should be loaded between each invocation of {@link
     * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}. The default value is
     * {@link #DEFAULT_LOADING_CHECK_INTERVAL_BYTES}.
     *
     * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between
     *     each invocation of {@link
     *     MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setContinueLoadingCheckIntervalBytes(int continueLoadingCheckIntervalBytes) {
      Assertions.checkState(!isCreateCalled);
      this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
      return this;
    }

    /**
     * Returns a new {@link ProgressiveMediaSource} using the current parameters.
     *
     * @param uri The {@link Uri}.
     * @return The new {@link ProgressiveMediaSource}.
     */
    @Override
    public ProgressiveMediaSource createMediaSource(Uri uri) {
      isCreateCalled = true;
      return new ProgressiveMediaSource(
          uri,
          dataSourceFactory,
          extractorsFactory,
          loadErrorHandlingPolicy,
          customCacheKey,
          continueLoadingCheckIntervalBytes,
          tag);
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_OTHER};
    }
  }

  /**
   * The default number of bytes that should be loaded between each each invocation of {@link
   * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
   */
  public static final int DEFAULT_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

  private final Uri uri;
  private final DataSource.Factory dataSourceFactory;
  private final ExtractorsFactory extractorsFactory;
  private final LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy;
  @Nullable private final String customCacheKey;
  private final int continueLoadingCheckIntervalBytes;
  @Nullable private final Object tag;

  private long timelineDurationUs;
  private boolean timelineIsSeekable;
  @Nullable private TransferListener transferListener;

  // TODO: Make private when ExtractorMediaSource is deleted.
  /* package */ ProgressiveMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy,
      @Nullable String customCacheKey,
      int continueLoadingCheckIntervalBytes,
      @Nullable Object tag) {
    this.uri = uri;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorsFactory = extractorsFactory;
    this.loadableLoadErrorHandlingPolicy = loadableLoadErrorHandlingPolicy;
    this.customCacheKey = customCacheKey;
    this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
    this.timelineDurationUs = C.TIME_UNSET;
    this.tag = tag;
  }

  @Override
  @Nullable
  public Object getTag() {
    return tag;
  }

  @Override
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    transferListener = mediaTransferListener;
    notifySourceInfoRefreshed(timelineDurationUs, timelineIsSeekable);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    DataSource dataSource = dataSourceFactory.createDataSource();
    if (transferListener != null) {
      dataSource.addTransferListener(transferListener);
    }
    return new ProgressiveMediaPeriod(
        uri,
        dataSource,
        extractorsFactory.createExtractors(),
        loadableLoadErrorHandlingPolicy,
        createEventDispatcher(id),
        this,
        allocator,
        customCacheKey,
        continueLoadingCheckIntervalBytes);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((ProgressiveMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSourceInternal() {
    // Do nothing.
  }

  // ProgressiveMediaPeriod.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(long durationUs, boolean isSeekable) {
    // If we already have the duration from a previous source info refresh, use it.
    durationUs = durationUs == C.TIME_UNSET ? timelineDurationUs : durationUs;
    if (timelineDurationUs == durationUs && timelineIsSeekable == isSeekable) {
      // Suppress no-op source info changes.
      return;
    }
    notifySourceInfoRefreshed(durationUs, isSeekable);
  }

  // Internal methods.

  private void notifySourceInfoRefreshed(long durationUs, boolean isSeekable) {
    timelineDurationUs = durationUs;
    timelineIsSeekable = isSeekable;
    // TODO: Make timeline dynamic until its duration is known. This is non-trivial. See b/69703223.
    refreshSourceInfo(
        new SinglePeriodTimeline(
            timelineDurationUs, timelineIsSeekable, /* isDynamic= */ false, tag),
        /* manifest= */ null);
  }
}
