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
package com.google.android.exoplayer2.source.dash;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.ParserException;
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
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
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
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A DASH {@link MediaSource}. */
public final class DashMediaSource extends BaseMediaSource {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.dash");
  }

  /** Factory for {@link DashMediaSource}s. */
  public static final class Factory implements AdsMediaSource.MediaSourceFactory {

    private final DashChunkSource.Factory chunkSourceFactory;
    @Nullable private final DataSource.Factory manifestDataSourceFactory;

    @Nullable private ParsingLoadable.Parser<? extends DashManifest> manifestParser;
    @Nullable private List<StreamKey> streamKeys;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private long livePresentationDelayMs;
    private boolean livePresentationDelayOverridesManifest;
    private boolean isCreateCalled;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link DashMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@link DataSource} instances that will be used to load
     *     manifest and media data.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory);
    }

    /**
     * Creates a new factory for {@link DashMediaSource}s.
     *
     * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
     * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
     *     to load (and refresh) the manifest. May be {@code null} if the factory will only ever be
     *     used to create create media sources with sideloaded manifests via {@link
     *     #createMediaSource(DashManifest, Handler, MediaSourceEventListener)}.
     */
    public Factory(
        DashChunkSource.Factory chunkSourceFactory,
        @Nullable DataSource.Factory manifestDataSourceFactory) {
      this.chunkSourceFactory = Assertions.checkNotNull(chunkSourceFactory);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      livePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS;
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
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

    /** @deprecated Use {@link #setLivePresentationDelayMs(long, boolean)}. */
    @Deprecated
    @SuppressWarnings("deprecation")
    public Factory setLivePresentationDelayMs(long livePresentationDelayMs) {
      if (livePresentationDelayMs == DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS) {
        return setLivePresentationDelayMs(DEFAULT_LIVE_PRESENTATION_DELAY_MS, false);
      } else {
        return setLivePresentationDelayMs(livePresentationDelayMs, true);
      }
    }

    /**
     * Sets the duration in milliseconds by which the default start position should precede the end
     * of the live window for live playbacks. The {@code overridesManifest} parameter specifies
     * whether the value is used in preference to one in the manifest, if present. The default value
     * is {@link #DEFAULT_LIVE_PRESENTATION_DELAY_MS}, and by default {@code overridesManifest} is
     * false.
     *
     * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
     *     default start position should precede the end of the live window.
     * @param overridesManifest Whether the value is used in preference to one in the manifest, if
     *     present.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setLivePresentationDelayMs(
        long livePresentationDelayMs, boolean overridesManifest) {
      Assertions.checkState(!isCreateCalled);
      this.livePresentationDelayMs = livePresentationDelayMs;
      this.livePresentationDelayOverridesManifest = overridesManifest;
      return this;
    }

    /**
     * Sets the manifest parser to parse loaded manifest data when loading a manifest URI.
     *
     * @param manifestParser A parser for loaded manifest data.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setManifestParser(
        ParsingLoadable.Parser<? extends DashManifest> manifestParser) {
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
     * loads data from multiple streams (video, audio etc...). The default is an instance of {@link
     * DefaultCompositeSequenceableLoaderFactory}.
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc...).
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
     * Returns a new {@link DashMediaSource} using the current parameters and the specified
     * sideloaded manifest.
     *
     * @param manifest The manifest. {@link DashManifest#dynamic} must be false.
     * @return The new {@link DashMediaSource}.
     * @throws IllegalArgumentException If {@link DashManifest#dynamic} is true.
     */
    public DashMediaSource createMediaSource(DashManifest manifest) {
      Assertions.checkArgument(!manifest.dynamic);
      isCreateCalled = true;
      if (streamKeys != null && !streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      return new DashMediaSource(
          manifest,
          /* manifestUri= */ null,
          /* manifestDataSourceFactory= */ null,
          /* manifestParser= */ null,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          loadErrorHandlingPolicy,
          livePresentationDelayMs,
          livePresentationDelayOverridesManifest,
          tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(DashManifest)} and {@link
     *     #addEventListener(Handler, MediaSourceEventListener)} instead.
     */
    @Deprecated
    public DashMediaSource createMediaSource(
        DashManifest manifest,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      DashMediaSource mediaSource = createMediaSource(manifest);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    /**
     * Returns a new {@link DashMediaSource} using the current parameters.
     *
     * @param manifestUri The manifest {@link Uri}.
     * @return The new {@link DashMediaSource}.
     */
    @Override
    public DashMediaSource createMediaSource(Uri manifestUri) {
      isCreateCalled = true;
      if (manifestParser == null) {
        manifestParser = new DashManifestParser();
      }
      if (streamKeys != null) {
        manifestParser = new FilteringManifestParser<>(manifestParser, streamKeys);
      }
      return new DashMediaSource(
          /* manifest= */ null,
          Assertions.checkNotNull(manifestUri),
          manifestDataSourceFactory,
          manifestParser,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          loadErrorHandlingPolicy,
          livePresentationDelayMs,
          livePresentationDelayOverridesManifest,
          tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(Uri)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead.
     */
    @Deprecated
    public DashMediaSource createMediaSource(
        Uri manifestUri,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      DashMediaSource mediaSource = createMediaSource(manifestUri);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_DASH};
    }
  }

  /**
   * The default presentation delay for live streams. The presentation delay is the duration by
   * which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30000;
  /** @deprecated Use {@link #DEFAULT_LIVE_PRESENTATION_DELAY_MS}. */
  @Deprecated
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_FIXED_MS =
      DEFAULT_LIVE_PRESENTATION_DELAY_MS;
  /** @deprecated Use of this parameter is no longer necessary. */
  @Deprecated public static final long DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS = -1;

  /**
   * The interval in milliseconds between invocations of {@link
   * SourceInfoRefreshListener#onSourceInfoRefreshed(MediaSource, Timeline, Object)} when the
   * source's {@link Timeline} is changing dynamically (for example, for incomplete live streams).
   */
  private static final int NOTIFY_MANIFEST_INTERVAL_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;

  private static final String TAG = "DashMediaSource";

  private final boolean sideloadedManifest;
  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final long livePresentationDelayMs;
  private final boolean livePresentationDelayOverridesManifest;
  private final EventDispatcher manifestEventDispatcher;
  private final ParsingLoadable.Parser<? extends DashManifest> manifestParser;
  private final ManifestCallback manifestCallback;
  private final Object manifestUriLock;
  private final SparseArray<DashMediaPeriod> periodsById;
  private final Runnable refreshManifestRunnable;
  private final Runnable simulateManifestRefreshRunnable;
  private final PlayerEmsgCallback playerEmsgCallback;
  private final LoaderErrorThrower manifestLoadErrorThrower;
  private final @Nullable Object tag;

  private DataSource dataSource;
  private Loader loader;
  private @Nullable TransferListener mediaTransferListener;

  private IOException manifestFatalError;
  private Handler handler;

  private Uri initialManifestUri;
  private Uri manifestUri;
  private DashManifest manifest;
  private boolean manifestLoadPending;
  private long manifestLoadStartTimestampMs;
  private long manifestLoadEndTimestampMs;
  private long elapsedRealtimeOffsetMs;

  private int staleManifestReloadAttempt;
  private long expiredManifestPublishTimeUs;

  private int firstPeriodId;

  /**
   * Constructs an instance to play a given {@link DashManifest}, which must be static.
   *
   * @param manifest The manifest. {@link DashManifest#dynamic} must be false.
   * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DashMediaSource(
      DashManifest manifest,
      DashChunkSource.Factory chunkSourceFactory,
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
   * Constructs an instance to play a given {@link DashManifest}, which must be static.
   *
   * @param manifest The manifest. {@link DashManifest#dynamic} must be false.
   * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public DashMediaSource(
      DashManifest manifest,
      DashChunkSource.Factory chunkSourceFactory,
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
        /* livePresentationDelayOverridesManifest= */ false,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, eventListener);
    }
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be dynamic or
   * static.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DashMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        manifestUri,
        manifestDataSourceFactory,
        chunkSourceFactory,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,
        eventHandler,
        eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be dynamic or
   * static.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window. Use {@link
   *     #DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS} to use the value specified by the
   *     manifest, if present.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DashMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount,
      long livePresentationDelayMs,
      Handler eventHandler,
      MediaSourceEventListener eventListener) {
    this(
        manifestUri,
        manifestDataSourceFactory,
        new DashManifestParser(),
        chunkSourceFactory,
        minLoadableRetryCount,
        livePresentationDelayMs,
        eventHandler,
        eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be dynamic or
   * static.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param manifestParser A parser for loaded manifest data.
   * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window. Use {@link
   *     #DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS} to use the value specified by the
   *     manifest, if present.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DashMediaSource(
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends DashManifest> manifestParser,
      DashChunkSource.Factory chunkSourceFactory,
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
        livePresentationDelayMs == DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS
            ? DEFAULT_LIVE_PRESENTATION_DELAY_MS
            : livePresentationDelayMs,
        livePresentationDelayMs != DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, eventListener);
    }
  }

  private DashMediaSource(
      DashManifest manifest,
      Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends DashManifest> manifestParser,
      DashChunkSource.Factory chunkSourceFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      long livePresentationDelayMs,
      boolean livePresentationDelayOverridesManifest,
      @Nullable Object tag) {
    this.initialManifestUri = manifestUri;
    this.manifest = manifest;
    this.manifestUri = manifestUri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.livePresentationDelayMs = livePresentationDelayMs;
    this.livePresentationDelayOverridesManifest = livePresentationDelayOverridesManifest;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.tag = tag;
    sideloadedManifest = manifest != null;
    manifestEventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    manifestUriLock = new Object();
    periodsById = new SparseArray<>();
    playerEmsgCallback = new DefaultPlayerEmsgCallback();
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    if (sideloadedManifest) {
      Assertions.checkState(!manifest.dynamic);
      manifestCallback = null;
      refreshManifestRunnable = null;
      simulateManifestRefreshRunnable = null;
      manifestLoadErrorThrower = new LoaderErrorThrower.Dummy();
    } else {
      manifestCallback = new ManifestCallback();
      manifestLoadErrorThrower = new ManifestLoadErrorThrower();
      refreshManifestRunnable = this::startLoadingManifest;
      simulateManifestRefreshRunnable = () -> processManifest(false);
    }
  }

  /**
   * Manually replaces the manifest {@link Uri}.
   *
   * @param manifestUri The replacement manifest {@link Uri}.
   */
  public void replaceManifestUri(Uri manifestUri) {
    synchronized (manifestUriLock) {
      this.manifestUri = manifestUri;
      this.initialManifestUri = manifestUri;
    }
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
      processManifest(false);
    } else {
      dataSource = manifestDataSourceFactory.createDataSource();
      loader = new Loader("Loader:DashMediaSource");
      handler = new Handler();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoadErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(
      MediaPeriodId periodId, Allocator allocator, long startPositionUs) {
    int periodIndex = (Integer) periodId.periodUid - firstPeriodId;
    EventDispatcher periodEventDispatcher =
        createEventDispatcher(periodId, manifest.getPeriod(periodIndex).startMs);
    DashMediaPeriod mediaPeriod =
        new DashMediaPeriod(
            firstPeriodId + periodIndex,
            manifest,
            periodIndex,
            chunkSourceFactory,
            mediaTransferListener,
            loadErrorHandlingPolicy,
            periodEventDispatcher,
            elapsedRealtimeOffsetMs,
            manifestLoadErrorThrower,
            allocator,
            compositeSequenceableLoaderFactory,
            playerEmsgCallback);
    periodsById.put(mediaPeriod.id, mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    DashMediaPeriod dashMediaPeriod = (DashMediaPeriod) mediaPeriod;
    dashMediaPeriod.release();
    periodsById.remove(dashMediaPeriod.id);
  }

  @Override
  public void releaseSourceInternal() {
    manifestLoadPending = false;
    dataSource = null;
    if (loader != null) {
      loader.release();
      loader = null;
    }
    manifestLoadStartTimestampMs = 0;
    manifestLoadEndTimestampMs = 0;
    manifest = sideloadedManifest ? manifest : null;
    manifestUri = initialManifestUri;
    manifestFatalError = null;
    if (handler != null) {
      handler.removeCallbacksAndMessages(null);
      handler = null;
    }
    elapsedRealtimeOffsetMs = 0;
    staleManifestReloadAttempt = 0;
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    firstPeriodId = 0;
    periodsById.clear();
  }

  // PlayerEmsgCallback callbacks.

  /* package */ void onDashManifestRefreshRequested() {
    handler.removeCallbacks(simulateManifestRefreshRunnable);
    startLoadingManifest();
  }

  /* package */ void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
    if (this.expiredManifestPublishTimeUs == C.TIME_UNSET
        || this.expiredManifestPublishTimeUs < expiredManifestPublishTimeUs) {
      this.expiredManifestPublishTimeUs = expiredManifestPublishTimeUs;
    }
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(ParsingLoadable<DashManifest> loadable,
      long elapsedRealtimeMs, long loadDurationMs) {
    manifestEventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    DashManifest newManifest = loadable.getResult();

    int oldPeriodCount = manifest == null ? 0 : manifest.getPeriodCount();
    int removedPeriodCount = 0;
    long newFirstPeriodStartTimeMs = newManifest.getPeriod(0).startMs;
    while (removedPeriodCount < oldPeriodCount
        && manifest.getPeriod(removedPeriodCount).startMs < newFirstPeriodStartTimeMs) {
      removedPeriodCount++;
    }

    if (newManifest.dynamic) {
      boolean isManifestStale = false;
      if (oldPeriodCount - removedPeriodCount > newManifest.getPeriodCount()) {
        // After discarding old periods, we should never have more periods than listed in the new
        // manifest. That would mean that a previously announced period is no longer advertised. If
        // this condition occurs, assume that we are hitting a manifest server that is out of sync
        // and
        // behind.
        Log.w(TAG, "Loaded out of sync manifest");
        isManifestStale = true;
      } else if (expiredManifestPublishTimeUs != C.TIME_UNSET
          && newManifest.publishTimeMs * 1000 <= expiredManifestPublishTimeUs) {
        // If we receive a dynamic manifest that's older than expected (i.e. its publish time has
        // expired, or it's dynamic and we know the presentation has ended), then this manifest is
        // stale.
        Log.w(
            TAG,
            "Loaded stale dynamic manifest: "
                + newManifest.publishTimeMs
                + ", "
                + expiredManifestPublishTimeUs);
        isManifestStale = true;
      }

      if (isManifestStale) {
        if (staleManifestReloadAttempt++
            < loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type)) {
          scheduleManifestRefresh(getManifestLoadRetryDelayMillis());
        } else {
          manifestFatalError = new DashManifestStaleException();
        }
        return;
      }
      staleManifestReloadAttempt = 0;
    }

    manifest = newManifest;
    manifestLoadPending &= manifest.dynamic;
    manifestLoadStartTimestampMs = elapsedRealtimeMs - loadDurationMs;
    manifestLoadEndTimestampMs = elapsedRealtimeMs;
    if (manifest.location != null) {
      synchronized (manifestUriLock) {
        // This condition checks that replaceManifestUri wasn't called between the start and end of
        // this load. If it was, we ignore the manifest location and prefer the manual replacement.
        @SuppressWarnings("ReferenceEquality")
        boolean isSameUriInstance = loadable.dataSpec.uri == manifestUri;
        if (isSameUriInstance) {
          manifestUri = manifest.location;
        }
      }
    }

    if (oldPeriodCount == 0) {
      if (manifest.dynamic && manifest.utcTiming != null) {
        resolveUtcTimingElement(manifest.utcTiming);
      } else {
        processManifest(true);
      }
    } else {
      firstPeriodId += removedPeriodCount;
      processManifest(true);
    }
  }

  /* package */ LoadErrorAction onManifestLoadError(
      ParsingLoadable<DashManifest> loadable,
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

  /* package */ void onUtcTimestampLoadCompleted(ParsingLoadable<Long> loadable,
      long elapsedRealtimeMs, long loadDurationMs) {
    manifestEventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    onUtcTimestampResolved(loadable.getResult() - elapsedRealtimeMs);
  }

  /* package */ LoadErrorAction onUtcTimestampLoadError(
      ParsingLoadable<Long> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error) {
    manifestEventDispatcher.loadError(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded(),
        error,
        true);
    onUtcTimestampResolutionError(error);
    return Loader.DONT_RETRY;
  }

  /* package */ void onLoadCanceled(ParsingLoadable<?> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    manifestEventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
  }

  // Internal methods.

  private void resolveUtcTimingElement(UtcTimingElement timingElement) {
    String scheme = timingElement.schemeIdUri;
    if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")) {
      resolveUtcTimingElementDirect(timingElement);
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2012")) {
      resolveUtcTimingElementHttp(timingElement, new Iso8601Parser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")) {
      resolveUtcTimingElementHttp(timingElement, new XsDateTimeParser());
    } else {
      // Unsupported scheme.
      onUtcTimestampResolutionError(new IOException("Unsupported UTC timing scheme"));
    }
  }

  private void resolveUtcTimingElementDirect(UtcTimingElement timingElement) {
    try {
      long utcTimestampMs = Util.parseXsDateTime(timingElement.value);
      onUtcTimestampResolved(utcTimestampMs - manifestLoadEndTimestampMs);
    } catch (ParserException e) {
      onUtcTimestampResolutionError(e);
    }
  }

  private void resolveUtcTimingElementHttp(UtcTimingElement timingElement,
      ParsingLoadable.Parser<Long> parser) {
    startLoading(new ParsingLoadable<>(dataSource, Uri.parse(timingElement.value),
        C.DATA_TYPE_TIME_SYNCHRONIZATION, parser), new UtcTimestampCallback(), 1);
  }

  private void onUtcTimestampResolved(long elapsedRealtimeOffsetMs) {
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    processManifest(true);
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve UtcTiming element.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    processManifest(true);
  }

  private void processManifest(boolean scheduleRefresh) {
    // Update any periods.
    for (int i = 0; i < periodsById.size(); i++) {
      int id = periodsById.keyAt(i);
      if (id >= firstPeriodId) {
        periodsById.valueAt(i).updateManifest(manifest, id - firstPeriodId);
      } else {
        // This period has been removed from the manifest so it doesn't need to be updated.
      }
    }
    // Update the window.
    boolean windowChangingImplicitly = false;
    int lastPeriodIndex = manifest.getPeriodCount() - 1;
    PeriodSeekInfo firstPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(manifest.getPeriod(0),
        manifest.getPeriodDurationUs(0));
    PeriodSeekInfo lastPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
        manifest.getPeriod(lastPeriodIndex), manifest.getPeriodDurationUs(lastPeriodIndex));
    // Get the period-relative start/end times.
    long currentStartTimeUs = firstPeriodSeekInfo.availableStartTimeUs;
    long currentEndTimeUs = lastPeriodSeekInfo.availableEndTimeUs;
    if (manifest.dynamic && !lastPeriodSeekInfo.isIndexExplicit) {
      // The manifest describes an incomplete live stream. Update the start/end times to reflect the
      // live stream duration and the manifest's time shift buffer depth.
      long liveStreamDurationUs = getNowUnixTimeUs() - C.msToUs(manifest.availabilityStartTimeMs);
      long liveStreamEndPositionInLastPeriodUs = liveStreamDurationUs
          - C.msToUs(manifest.getPeriod(lastPeriodIndex).startMs);
      currentEndTimeUs = Math.min(liveStreamEndPositionInLastPeriodUs, currentEndTimeUs);
      if (manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
        long timeShiftBufferDepthUs = C.msToUs(manifest.timeShiftBufferDepthMs);
        long offsetInPeriodUs = currentEndTimeUs - timeShiftBufferDepthUs;
        int periodIndex = lastPeriodIndex;
        while (offsetInPeriodUs < 0 && periodIndex > 0) {
          offsetInPeriodUs += manifest.getPeriodDurationUs(--periodIndex);
        }
        if (periodIndex == 0) {
          currentStartTimeUs = Math.max(currentStartTimeUs, offsetInPeriodUs);
        } else {
          // The time shift buffer starts after the earliest period.
          // TODO: Does this ever happen?
          currentStartTimeUs = manifest.getPeriodDurationUs(0);
        }
      }
      windowChangingImplicitly = true;
    }
    long windowDurationUs = currentEndTimeUs - currentStartTimeUs;
    for (int i = 0; i < manifest.getPeriodCount() - 1; i++) {
      windowDurationUs += manifest.getPeriodDurationUs(i);
    }
    long windowDefaultStartPositionUs = 0;
    if (manifest.dynamic) {
      long presentationDelayForManifestMs = livePresentationDelayMs;
      if (!livePresentationDelayOverridesManifest
          && manifest.suggestedPresentationDelayMs != C.TIME_UNSET) {
        presentationDelayForManifestMs = manifest.suggestedPresentationDelayMs;
      }
      // Snap the default position to the start of the segment containing it.
      windowDefaultStartPositionUs = windowDurationUs - C.msToUs(presentationDelayForManifestMs);
      if (windowDefaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
        // The default start position is too close to the start of the live window. Set it to the
        // minimum default start position provided the window is at least twice as big. Else set
        // it to the middle of the window.
        windowDefaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US,
            windowDurationUs / 2);
      }
    }
    long windowStartTimeMs = manifest.availabilityStartTimeMs
        + manifest.getPeriod(0).startMs + C.usToMs(currentStartTimeUs);
    DashTimeline timeline =
        new DashTimeline(
            manifest.availabilityStartTimeMs,
            windowStartTimeMs,
            firstPeriodId,
            currentStartTimeUs,
            windowDurationUs,
            windowDefaultStartPositionUs,
            manifest,
            tag);
    refreshSourceInfo(timeline, manifest);

    if (!sideloadedManifest) {
      // Remove any pending simulated refresh.
      handler.removeCallbacks(simulateManifestRefreshRunnable);
      // If the window is changing implicitly, post a simulated manifest refresh to update it.
      if (windowChangingImplicitly) {
        handler.postDelayed(simulateManifestRefreshRunnable, NOTIFY_MANIFEST_INTERVAL_MS);
      }
      if (manifestLoadPending) {
        startLoadingManifest();
      } else if (scheduleRefresh
          && manifest.dynamic
          && manifest.minUpdatePeriodMs != C.TIME_UNSET) {
        // Schedule an explicit refresh if needed.
        long minUpdatePeriodMs = manifest.minUpdatePeriodMs;
        if (minUpdatePeriodMs == 0) {
          // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
          // minimumUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is
          // explicit signaling in the stream, according to:
          // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service
          minUpdatePeriodMs = 5000;
        }
        long nextLoadTimestampMs = manifestLoadStartTimestampMs + minUpdatePeriodMs;
        long delayUntilNextLoadMs =
            Math.max(0, nextLoadTimestampMs - SystemClock.elapsedRealtime());
        scheduleManifestRefresh(delayUntilNextLoadMs);
      }
    }
  }

  private void scheduleManifestRefresh(long delayUntilNextLoadMs) {
    handler.postDelayed(refreshManifestRunnable, delayUntilNextLoadMs);
  }

  private void startLoadingManifest() {
    handler.removeCallbacks(refreshManifestRunnable);
    if (loader.hasFatalError()) {
      return;
    }
    if (loader.isLoading()) {
      manifestLoadPending = true;
      return;
    }
    Uri manifestUri;
    synchronized (manifestUriLock) {
      manifestUri = this.manifestUri;
    }
    manifestLoadPending = false;
    startLoading(
        new ParsingLoadable<>(dataSource, manifestUri, C.DATA_TYPE_MANIFEST, manifestParser),
        manifestCallback,
        loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST));
  }

  private long getManifestLoadRetryDelayMillis() {
    return Math.min((staleManifestReloadAttempt - 1) * 1000, 5000);
  }

  private <T> void startLoading(ParsingLoadable<T> loadable,
      Loader.Callback<ParsingLoadable<T>> callback, int minRetryCount) {
    long elapsedRealtimeMs = loader.startLoading(loadable, callback, minRetryCount);
    manifestEventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetMs != 0) {
      return C.msToUs(SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs);
    } else {
      return C.msToUs(System.currentTimeMillis());
    }
  }

  private static final class PeriodSeekInfo {

    public static PeriodSeekInfo createPeriodSeekInfo(
        com.google.android.exoplayer2.source.dash.manifest.Period period, long durationUs) {
      int adaptationSetCount = period.adaptationSets.size();
      long availableStartTimeUs = 0;
      long availableEndTimeUs = Long.MAX_VALUE;
      boolean isIndexExplicit = false;
      boolean seenEmptyIndex = false;

      boolean haveAudioVideoAdaptationSets = false;
      for (int i = 0; i < adaptationSetCount; i++) {
        int type = period.adaptationSets.get(i).type;
        if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO) {
          haveAudioVideoAdaptationSets = true;
          break;
        }
      }

      for (int i = 0; i < adaptationSetCount; i++) {
        AdaptationSet adaptationSet = period.adaptationSets.get(i);
        // Exclude text adaptation sets from duration calculations, if we have at least one audio
        // or video adaptation set. See: https://github.com/google/ExoPlayer/issues/4029
        if (haveAudioVideoAdaptationSets && adaptationSet.type == C.TRACK_TYPE_TEXT) {
          continue;
        }

        DashSegmentIndex index = adaptationSet.representations.get(0).getIndex();
        if (index == null) {
          return new PeriodSeekInfo(true, 0, durationUs);
        }
        isIndexExplicit |= index.isExplicit();
        int segmentCount = index.getSegmentCount(durationUs);
        if (segmentCount == 0) {
          seenEmptyIndex = true;
          availableStartTimeUs = 0;
          availableEndTimeUs = 0;
        } else if (!seenEmptyIndex) {
          long firstSegmentNum = index.getFirstSegmentNum();
          long adaptationSetAvailableStartTimeUs = index.getTimeUs(firstSegmentNum);
          availableStartTimeUs = Math.max(availableStartTimeUs, adaptationSetAvailableStartTimeUs);
          if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED) {
            long lastSegmentNum = firstSegmentNum + segmentCount - 1;
            long adaptationSetAvailableEndTimeUs = index.getTimeUs(lastSegmentNum)
                + index.getDurationUs(lastSegmentNum, durationUs);
            availableEndTimeUs = Math.min(availableEndTimeUs, adaptationSetAvailableEndTimeUs);
          }
        }
      }
      return new PeriodSeekInfo(isIndexExplicit, availableStartTimeUs, availableEndTimeUs);
    }

    public final boolean isIndexExplicit;
    public final long availableStartTimeUs;
    public final long availableEndTimeUs;

    private PeriodSeekInfo(boolean isIndexExplicit, long availableStartTimeUs,
        long availableEndTimeUs) {
      this.isIndexExplicit = isIndexExplicit;
      this.availableStartTimeUs = availableStartTimeUs;
      this.availableEndTimeUs = availableEndTimeUs;
    }

  }

  private static final class DashTimeline extends Timeline {

    private final long presentationStartTimeMs;
    private final long windowStartTimeMs;

    private final int firstPeriodId;
    private final long offsetInFirstPeriodUs;
    private final long windowDurationUs;
    private final long windowDefaultStartPositionUs;
    private final DashManifest manifest;
    private final @Nullable Object windowTag;

    public DashTimeline(
        long presentationStartTimeMs,
        long windowStartTimeMs,
        int firstPeriodId,
        long offsetInFirstPeriodUs,
        long windowDurationUs,
        long windowDefaultStartPositionUs,
        DashManifest manifest,
        @Nullable Object windowTag) {
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.firstPeriodId = firstPeriodId;
      this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
      this.windowDurationUs = windowDurationUs;
      this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
      this.manifest = manifest;
      this.windowTag = windowTag;
    }

    @Override
    public int getPeriodCount() {
      return manifest.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIdentifiers) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      Object id = setIdentifiers ? manifest.getPeriod(periodIndex).id : null;
      Object uid = setIdentifiers ? (firstPeriodId + periodIndex) : null;
      return period.set(id, uid, 0, manifest.getPeriodDurationUs(periodIndex),
          C.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
              - offsetInFirstPeriodUs);
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(
        int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
      Assertions.checkIndex(windowIndex, 0, 1);
      long windowDefaultStartPositionUs = getAdjustedWindowDefaultStartPositionUs(
          defaultPositionProjectionUs);
      Object tag = setTag ? windowTag : null;
      boolean isDynamic =
          manifest.dynamic
              && manifest.minUpdatePeriodMs != C.TIME_UNSET
              && manifest.durationMs == C.TIME_UNSET;
      return window.set(
          tag,
          presentationStartTimeMs,
          windowStartTimeMs,
          /* isSeekable= */ true,
          isDynamic,
          windowDefaultStartPositionUs,
          windowDurationUs,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ getPeriodCount() - 1,
          offsetInFirstPeriodUs);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int periodId = (int) uid;
      int periodIndex = periodId - firstPeriodId;
      return periodIndex < 0 || periodIndex >= getPeriodCount() ? C.INDEX_UNSET : periodIndex;
    }

    private long getAdjustedWindowDefaultStartPositionUs(long defaultPositionProjectionUs) {
      long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
      if (!manifest.dynamic) {
        return windowDefaultStartPositionUs;
      }
      if (defaultPositionProjectionUs > 0) {
        windowDefaultStartPositionUs += defaultPositionProjectionUs;
        if (windowDefaultStartPositionUs > windowDurationUs) {
          // The projection takes us beyond the end of the live window.
          return C.TIME_UNSET;
        }
      }
      // Attempt to snap to the start of the corresponding video segment.
      int periodIndex = 0;
      long defaultStartPositionInPeriodUs = offsetInFirstPeriodUs + windowDefaultStartPositionUs;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      while (periodIndex < manifest.getPeriodCount() - 1
          && defaultStartPositionInPeriodUs >= periodDurationUs) {
        defaultStartPositionInPeriodUs -= periodDurationUs;
        periodIndex++;
        periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      }
      com.google.android.exoplayer2.source.dash.manifest.Period period =
          manifest.getPeriod(periodIndex);
      int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
      if (videoAdaptationSetIndex == C.INDEX_UNSET) {
        // No video adaptation set for snapping.
        return windowDefaultStartPositionUs;
      }
      // If there are multiple video adaptation sets with unaligned segments, the initial time may
      // not correspond to the start of a segment in both, but this is an edge case.
      DashSegmentIndex snapIndex = period.adaptationSets.get(videoAdaptationSetIndex)
          .representations.get(0).getIndex();
      if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
        // Video adaptation set does not include a non-empty index for snapping.
        return windowDefaultStartPositionUs;
      }
      long segmentNum = snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
      return windowDefaultStartPositionUs + snapIndex.getTimeUs(segmentNum)
          - defaultStartPositionInPeriodUs;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      return firstPeriodId + periodIndex;
    }
  }

  private final class DefaultPlayerEmsgCallback implements PlayerEmsgCallback {

    @Override
    public void onDashManifestRefreshRequested() {
      DashMediaSource.this.onDashManifestRefreshRequested();
    }

    @Override
    public void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
      DashMediaSource.this.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
    }
  }

  private final class ManifestCallback implements Loader.Callback<ParsingLoadable<DashManifest>> {

    @Override
    public void onLoadCompleted(ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs, long loadDurationMs) {
      onManifestLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs, long loadDurationMs, boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onManifestLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error, errorCount);
    }

  }

  private final class UtcTimestampCallback implements Loader.Callback<ParsingLoadable<Long>> {

    @Override
    public void onLoadCompleted(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
        long loadDurationMs) {
      onUtcTimestampLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
        long loadDurationMs, boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<Long> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onUtcTimestampLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }

  }

  private static final class XsDateTimeParser implements ParsingLoadable.Parser<Long> {

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      return Util.parseXsDateTime(firstLine);
    }

  }

  /* package */ static final class Iso8601Parser implements ParsingLoadable.Parser<Long> {

    private static final Pattern TIMESTAMP_WITH_TIMEZONE_PATTERN =
        Pattern.compile("(.+?)(Z|((\\+|-|−)(\\d\\d)(:?(\\d\\d))?))");

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine =
          new BufferedReader(new InputStreamReader(inputStream, Charset.forName(C.UTF8_NAME)))
              .readLine();
      try {
        Matcher matcher = TIMESTAMP_WITH_TIMEZONE_PATTERN.matcher(firstLine);
        if (!matcher.matches()) {
          throw new ParserException("Couldn't parse timestamp: " + firstLine);
        }
        // Parse the timestamp.
        String timestampWithoutTimezone = matcher.group(1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long timestampMs = format.parse(timestampWithoutTimezone).getTime();
        // Parse the timezone.
        String timezone = matcher.group(2);
        if ("Z".equals(timezone)) {
          // UTC (no offset).
        } else {
          long sign = "+".equals(matcher.group(4)) ? 1 : -1;
          long hours = Long.parseLong(matcher.group(5));
          String minutesString = matcher.group(7);
          long minutes = TextUtils.isEmpty(minutesString) ? 0 : Long.parseLong(minutesString);
          long timestampOffsetMs = sign * (((hours * 60) + minutes) * 60 * 1000);
          timestampMs -= timestampOffsetMs;
        }
        return timestampMs;
      } catch (ParseException e) {
        throw new ParserException(e);
      }
    }

  }

  /**
   * A {@link LoaderErrorThrower} that throws fatal {@link IOException} that has occurred during
   * manifest loading from the manifest {@code loader}, or exception with the loaded manifest.
   */
  /* package */ final class ManifestLoadErrorThrower implements LoaderErrorThrower {

    @Override
    public void maybeThrowError() throws IOException {
      loader.maybeThrowError();
      maybeThrowManifestError();
    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {
      loader.maybeThrowError(minRetryCount);
      maybeThrowManifestError();
    }

    private void maybeThrowManifestError() throws IOException {
      if (manifestFatalError != null) {
        throw manifestFatalError;
      }
    }
  }
}
