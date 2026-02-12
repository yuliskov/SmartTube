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
package com.google.android.exoplayer2.source.smoothstreaming;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.offline.FilteringManifestParser;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A SmoothStreaming {@link MediaSource}. */
public final class SsMediaSource extends BaseMediaSource
    implements Loader.Callback<ParsingLoadable<SsManifest>> {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.smoothstreaming");
  }

  /** Factory for {@link SsMediaSource}. */
  public static final class Factory implements AdsMediaSource.MediaSourceFactory {

    private final SsChunkSource.Factory chunkSourceFactory;
    @Nullable private final DataSource.Factory manifestDataSourceFactory;

    @Nullable private ParsingLoadable.Parser<? extends SsManifest> manifestParser;
    @Nullable private List<StreamKey> streamKeys;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private long livePresentationDelayMs;
    private boolean isCreateCalled;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link SsMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@link DataSource} instances that will be used to load
     *     manifest and media data.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultSsChunkSource.Factory(dataSourceFactory), dataSourceFactory);
    }

    /**
     * Creates a new factory for {@link SsMediaSource}s.
     *
     * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
     * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
     *     to load (and refresh) the manifest. May be {@code null} if the factory will only ever be
     *     used to create create media sources with sideloaded manifests via {@link
     *     #createMediaSource(SsManifest, Handler, MediaSourceEventListener)}.
     */
    public Factory(
        SsChunkSource.Factory chunkSourceFactory,
        @Nullable DataSource.Factory manifestDataSourceFactory) {
      this.chunkSourceFactory = Assertions.checkNotNull(chunkSourceFactory);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      livePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS;
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
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
     * Sets the duration in milliseconds by which the default start position should precede the end
     * of the live window for live playbacks. The default value is {@link
     * #DEFAULT_LIVE_PRESENTATION_DELAY_MS}.
     *
     * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
     *     default start position should precede the end of the live window.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setLivePresentationDelayMs(long livePresentationDelayMs) {
      Assertions.checkState(!isCreateCalled);
      this.livePresentationDelayMs = livePresentationDelayMs;
      return this;
    }

    /**
     * Sets the manifest parser to parse loaded manifest data when loading a manifest URI.
     *
     * @param manifestParser A parser for loaded manifest data.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setManifestParser(ParsingLoadable.Parser<? extends SsManifest> manifestParser) {
      Assertions.checkState(!isCreateCalled);
      this.manifestParser = Assertions.checkNotNull(manifestParser);
      return this;
    }

    /**
     * Sets a list of {@link StreamKey stream keys} by which the manifest is filtered.
     *
     * @param streamKeys A list of {@link StreamKey stream keys}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setStreamKeys(List<StreamKey> streamKeys) {
      Assertions.checkState(!isCreateCalled);
      this.streamKeys = streamKeys;
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc.). The default is an instance of {@link
     * DefaultCompositeSequenceableLoaderFactory}.
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc.).
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      Assertions.checkState(!isCreateCalled);
      this.compositeSequenceableLoaderFactory =
          Assertions.checkNotNull(compositeSequenceableLoaderFactory);
      return this;
    }

    /**
     * Returns a new {@link SsMediaSource} using the current parameters and the specified sideloaded
     * manifest.
     *
     * @param manifest The manifest. {@link SsManifest#isLive} must be false.
     * @return The new {@link SsMediaSource}.
     * @throws IllegalArgumentException If {@link SsManifest#isLive} is true.
     */
    public SsMediaSource createMediaSource(SsManifest manifest) {
      Assertions.checkArgument(!manifest.isLive);
      isCreateCalled = true;
      if (streamKeys != null && !streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      return new SsMediaSource(
          manifest,
          /* manifestUri= */ null,
          /* manifestDataSourceFactory= */ null,
          /* manifestParser= */ null,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          loadErrorHandlingPolicy,
          livePresentationDelayMs,
          tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(SsManifest)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead.
     */
    @Deprecated
    public SsMediaSource createMediaSource(
        SsManifest manifest,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      SsMediaSource mediaSource = createMediaSource(manifest);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    /**
     * Returns a new {@link SsMediaSource} using the current parameters.
     *
     * @param manifestUri The manifest {@link Uri}.
     * @return The new {@link SsMediaSource}.
     */
    @Override
    public SsMediaSource createMediaSource(Uri manifestUri) {
      isCreateCalled = true;
      if (manifestParser == null) {
        manifestParser = new SsManifestParser();
      }
      if (streamKeys != null) {
        manifestParser = new FilteringManifestParser<>(manifestParser, streamKeys);
      }
      return new SsMediaSource(
          /* manifest= */ null,
          Assertions.checkNotNull(manifestUri),
          manifestDataSourceFactory,
          manifestParser,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          loadErrorHandlingPolicy,
          livePresentationDelayMs,
          tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(Uri)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead.
     */
    @Deprecated
    public SsMediaSource createMediaSource(
        Uri manifestUri,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      SsMediaSource mediaSource = createMediaSource(manifestUri);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_SS};
    }

  }

  /**
   * The default presentation delay for live streams. The presentation delay is the duration by
   * which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30000;

  /**
   * The minimum period between manifest refreshes.
   */
  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;

  private final boolean sideloadedManifest;
  private final Uri manifestUri;
  private final DataSource.Factory manifestDataSourceFactory;
  private final SsChunkSource.Factory chunkSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final long livePresentationDelayMs;
  private final EventDispatcher manifestEventDispatcher;
  private final ParsingLoadable.Parser<? extends SsManifest> manifestParser;
  private final ArrayList<SsMediaPeriod> mediaPeriods;
  private final @Nullable Object tag;

  private DataSource manifestDataSource;
  private Loader manifestLoader;
  private LoaderErrorThrower manifestLoaderErrorThrower;
  private @Nullable TransferListener mediaTransferListener;

  private long manifestLoadStartTimestamp;
  private SsManifest manifest;

  private Handler manifestRefreshHandler;

  /**
   * Constructs an instance to play a given {@link SsManifest}, which must not be live.
   *
   * @param manifest The manifest. {@link SsManifest#isLive} must be false.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SsMediaSource(
      SsManifest manifest,
      SsChunkSource.Factory chunkSourceFactory,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        manifest,
        chunkSourceFactory,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        eventHandler,
        eventListener);
  }

  /**
   * Constructs an instance to play a given {@link SsManifest}, which must not be live.
   *
   * @param manifest The manifest. {@link SsManifest#isLive} must be false.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SsMediaSource(
      SsManifest manifest,
      SsChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        manifest,
        /* manifestUri= */ null,
        /* manifestDataSourceFactory= */ null,
        /* manifestParser= */ null,
        chunkSourceFactory,
        new DefaultCompositeSequenceableLoaderFactory(),
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        DEFAULT_LIVE_PRESENTATION_DELAY_MS,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, eventListener);
    }
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SsMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        manifestUri,
        manifestDataSourceFactory,
        chunkSourceFactory,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        DEFAULT_LIVE_PRESENTATION_DELAY_MS,
        eventHandler,
        eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SsMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount,
      long livePresentationDelayMs,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, new SsManifestParser(), chunkSourceFactory,
        minLoadableRetryCount, livePresentationDelayMs, eventHandler, eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param manifestParser A parser for loaded manifest data.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SsMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends SsManifest> manifestParser,
      SsChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount,
      long livePresentationDelayMs,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        /* manifest= */ null,
        manifestUri,
        manifestDataSourceFactory,
        manifestParser,
        chunkSourceFactory,
        new DefaultCompositeSequenceableLoaderFactory(),
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        livePresentationDelayMs,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, eventListener);
    }
  }

  private SsMediaSource(
      SsManifest manifest,
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends SsManifest> manifestParser,
      SsChunkSource.Factory chunkSourceFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      long livePresentationDelayMs,
      @Nullable Object tag) {
    Assertions.checkState(manifest == null || !manifest.isLive);
    this.manifest = manifest;
    this.manifestUri = manifestUri == null ? null : SsUtil.fixManifestUri(manifestUri);
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.livePresentationDelayMs = livePresentationDelayMs;
    this.manifestEventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    this.tag = tag;
    sideloadedManifest = manifest != null;
    mediaPeriods = new ArrayList<>();
  }

  // MediaSource implementation.

  @Override
  @Nullable
  public Object getTag() {
    return tag;
  }

  @Override
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    if (sideloadedManifest) {
      manifestLoaderErrorThrower = new LoaderErrorThrower.Dummy();
      processManifest();
    } else {
      manifestDataSource = manifestDataSourceFactory.createDataSource();
      manifestLoader = new Loader("Loader:Manifest");
      manifestLoaderErrorThrower = manifestLoader;
      manifestRefreshHandler = new Handler();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    EventDispatcher eventDispatcher = createEventDispatcher(id);
    SsMediaPeriod period =
        new SsMediaPeriod(
            manifest,
            chunkSourceFactory,
            mediaTransferListener,
            compositeSequenceableLoaderFactory,
            loadErrorHandlingPolicy,
            eventDispatcher,
            manifestLoaderErrorThrower,
            allocator);
    mediaPeriods.add(period);
    return period;
  }

  @Override
  public void releasePeriod(MediaPeriod period) {
    ((SsMediaPeriod) period).release();
    mediaPeriods.remove(period);
  }

  @Override
  public void releaseSourceInternal() {
    manifest = sideloadedManifest ? manifest : null;
    manifestDataSource = null;
    manifestLoadStartTimestamp = 0;
    if (manifestLoader != null) {
      manifestLoader.release();
      manifestLoader = null;
    }
    if (manifestRefreshHandler != null) {
      manifestRefreshHandler.removeCallbacksAndMessages(null);
      manifestRefreshHandler = null;
    }
  }

  // Loader.Callback implementation

  @Override
  public void onLoadCompleted(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    manifestEventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    manifest = loadable.getResult();
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    processManifest();
    scheduleManifestRefresh();
  }

  @Override
  public void onLoadCanceled(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    manifestEventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
  }

  @Override
  public LoadErrorAction onLoadError(
      ParsingLoadable<SsManifest> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    long retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            C.DATA_TYPE_MANIFEST, loadDurationMs, error, errorCount);
    LoadErrorAction loadErrorAction =
        retryDelayMs == C.TIME_UNSET
            ? Loader.DONT_RETRY_FATAL
            : Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs);
    manifestEventDispatcher.loadError(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded(),
        error,
        !loadErrorAction.isRetry());
    return loadErrorAction;
  }

  // Internal methods

  private void processManifest() {
    for (int i = 0; i < mediaPeriods.size(); i++) {
      mediaPeriods.get(i).updateManifest(manifest);
    }

    long startTimeUs = Long.MAX_VALUE;
    long endTimeUs = Long.MIN_VALUE;
    for (StreamElement element : manifest.streamElements) {
      if (element.chunkCount > 0) {
        startTimeUs = Math.min(startTimeUs, element.getStartTimeUs(0));
        endTimeUs = Math.max(endTimeUs, element.getStartTimeUs(element.chunkCount - 1)
            + element.getChunkDurationUs(element.chunkCount - 1));
      }
    }

    Timeline timeline;
    if (startTimeUs == Long.MAX_VALUE) {
      long periodDurationUs = manifest.isLive ? C.TIME_UNSET : 0;
      timeline =
          new SinglePeriodTimeline(
              periodDurationUs,
              /* windowDurationUs= */ 0,
              /* windowPositionInPeriodUs= */ 0,
              /* windowDefaultStartPositionUs= */ 0,
              /* isSeekable= */ true,
              manifest.isLive,
              tag);
    } else if (manifest.isLive) {
      if (manifest.dvrWindowLengthUs != C.TIME_UNSET && manifest.dvrWindowLengthUs > 0) {
        startTimeUs = Math.max(startTimeUs, endTimeUs - manifest.dvrWindowLengthUs);
      }
      long durationUs = endTimeUs - startTimeUs;
      long defaultStartPositionUs = durationUs - C.msToUs(livePresentationDelayMs);
      if (defaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
        // The default start position is too close to the start of the live window. Set it to the
        // minimum default start position provided the window is at least twice as big. Else set
        // it to the middle of the window.
        defaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US, durationUs / 2);
      }
      timeline =
          new SinglePeriodTimeline(
              /* periodDurationUs= */ C.TIME_UNSET,
              durationUs,
              startTimeUs,
              defaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ true,
              tag);
    } else {
      long durationUs = manifest.durationUs != C.TIME_UNSET ? manifest.durationUs
          : endTimeUs - startTimeUs;
      timeline =
          new SinglePeriodTimeline(
              startTimeUs + durationUs,
              durationUs,
              startTimeUs,
              /* windowDefaultStartPositionUs= */ 0,
              /* isSeekable= */ true,
              /* isDynamic= */ false,
              tag);
    }
    refreshSourceInfo(timeline, manifest);
  }

  private void scheduleManifestRefresh() {
    if (!manifest.isLive) {
      return;
    }
    long nextLoadTimestamp = manifestLoadStartTimestamp + MINIMUM_MANIFEST_REFRESH_PERIOD_MS;
    long delayUntilNextLoad = Math.max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
    manifestRefreshHandler.postDelayed(this::startLoadingManifest, delayUntilNextLoad);
  }

  private void startLoadingManifest() {
    if (manifestLoader.hasFatalError()) {
      return;
    }
    ParsingLoadable<SsManifest> loadable = new ParsingLoadable<>(manifestDataSource,
        manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
    long elapsedRealtimeMs =
        manifestLoader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    manifestEventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

}
