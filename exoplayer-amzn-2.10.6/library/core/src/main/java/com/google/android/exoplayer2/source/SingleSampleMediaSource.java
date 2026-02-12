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
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Loads data at a given {@link Uri} as a single sample belonging to a single {@link MediaPeriod}.
 */
public final class SingleSampleMediaSource extends BaseMediaSource {

  /**
   * Listener of {@link SingleSampleMediaSource} events.
   *
   * @deprecated Use {@link MediaSourceEventListener}.
   */
  @Deprecated
  public interface EventListener {

    /**
     * Called when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SingleSampleMediaSource}.
     * @param e The cause of the failure.
     */
    void onLoadError(int sourceId, IOException e);

  }

  /** Factory for {@link SingleSampleMediaSource}. */
  public static final class Factory {

    private final DataSource.Factory dataSourceFactory;

    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private boolean treatLoadErrorsAsEndOfStream;
    private boolean isCreateCalled;
    private @Nullable Object tag;

    /**
     * Creates a factory for {@link SingleSampleMediaSource}s.
     *
     * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
     *     be obtained.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this.dataSourceFactory = Assertions.checkNotNull(dataSourceFactory);
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
    }

    /**
     * Sets a tag for the media source which will be published in the {@link Timeline} of the source
     * as {@link Timeline.Window#tag}.
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
     * Sets the minimum number of times to retry if a loading error occurs. See {@link
     * #setLoadErrorHandlingPolicy} for the default value.
     *
     * <p>Calling this method is equivalent to calling {@link #setLoadErrorHandlingPolicy} with
     * {@link DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy(int)
     * DefaultLoadErrorHandlingPolicy(minLoadableRetryCount)}
     *
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     * @deprecated Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead.
     */
    @Deprecated
    public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
      return setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount));
    }

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
     *
     * <p>Calling this method overrides any calls to {@link #setMinLoadableRetryCount(int)}.
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
     * Sets whether load errors will be treated as end-of-stream signal (load errors will not be
     * propagated). The default value is false.
     *
     * @param treatLoadErrorsAsEndOfStream If true, load errors will not be propagated by sample
     *     streams, treating them as ended instead. If false, load errors will be propagated
     *     normally by {@link SampleStream#maybeThrowError()}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setTreatLoadErrorsAsEndOfStream(boolean treatLoadErrorsAsEndOfStream) {
      Assertions.checkState(!isCreateCalled);
      this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
      return this;
    }

    /**
     * Returns a new {@link SingleSampleMediaSource} using the current parameters.
     *
     * @param uri The {@link Uri}.
     * @param format The {@link Format} of the media stream.
     * @param durationUs The duration of the media stream in microseconds.
     * @return The new {@link SingleSampleMediaSource}.
     */
    public SingleSampleMediaSource createMediaSource(Uri uri, Format format, long durationUs) {
      isCreateCalled = true;
      return new SingleSampleMediaSource(
          uri,
          dataSourceFactory,
          format,
          durationUs,
          loadErrorHandlingPolicy,
          treatLoadErrorsAsEndOfStream,
          tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(Uri, Format, long)} and {@link
     *     #addEventListener(Handler, MediaSourceEventListener)} instead.
     */
    @Deprecated
    public SingleSampleMediaSource createMediaSource(
        Uri uri,
        Format format,
        long durationUs,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      SingleSampleMediaSource mediaSource = createMediaSource(uri, format, durationUs);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

  }

  private final DataSpec dataSpec;
  private final DataSource.Factory dataSourceFactory;
  private final Format format;
  private final long durationUs;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean treatLoadErrorsAsEndOfStream;
  private final Timeline timeline;
  @Nullable private final Object tag;

  private @Nullable TransferListener transferListener;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SingleSampleMediaSource(
      Uri uri, DataSource.Factory dataSourceFactory, Format format, long durationUs) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        /* treatLoadErrorsAsEndOfStream= */ false,
        /* tag= */ null);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param treatLoadErrorsAsEndOfStream If true, load errors will not be propagated by sample
   *     streams, treating them as ended instead. If false, load errors will be propagated normally
   *     by {@link SampleStream#maybeThrowError()}.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount,
      Handler eventHandler,
      EventListener eventListener,
      int eventSourceId,
      boolean treatLoadErrorsAsEndOfStream) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        treatLoadErrorsAsEndOfStream,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, new EventListenerWrapper(eventListener, eventSourceId));
    }
  }

  private SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      boolean treatLoadErrorsAsEndOfStream,
      @Nullable Object tag) {
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.durationUs = durationUs;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    this.tag = tag;
    dataSpec = new DataSpec(uri, DataSpec.FLAG_ALLOW_GZIP);
    timeline =
        new SinglePeriodTimeline(durationUs, /* isSeekable= */ true, /* isDynamic= */ false, tag);
  }

  // MediaSource implementation.

  @Override
  @Nullable
  public Object getTag() {
    return tag;
  }

  @Override
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    transferListener = mediaTransferListener;
    refreshSourceInfo(timeline, /* manifest= */ null);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return new SingleSampleMediaPeriod(
        dataSpec,
        dataSourceFactory,
        transferListener,
        format,
        durationUs,
        loadErrorHandlingPolicy,
        createEventDispatcher(id),
        treatLoadErrorsAsEndOfStream);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((SingleSampleMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSourceInternal() {
    // Do nothing.
  }

  /**
   * Wraps a deprecated {@link EventListener}, invoking its callback from the equivalent callback in
   * {@link MediaSourceEventListener}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  private static final class EventListenerWrapper extends DefaultMediaSourceEventListener {

    private final EventListener eventListener;
    private final int eventSourceId;

    public EventListenerWrapper(EventListener eventListener, int eventSourceId) {
      this.eventListener = Assertions.checkNotNull(eventListener);
      this.eventSourceId = eventSourceId;
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      eventListener.onLoadError(eventSourceId, error);
    }
  }
}
