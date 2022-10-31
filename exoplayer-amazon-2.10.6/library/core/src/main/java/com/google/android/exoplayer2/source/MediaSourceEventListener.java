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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Interface for callbacks to be notified of {@link MediaSource} events. */
public interface MediaSourceEventListener {

  /** Media source load event information. */
  final class LoadEventInfo {

    /** Defines the requested data. */
    public final DataSpec dataSpec;
    /**
     * The {@link Uri} from which data is being read. The uri will be identical to the one in {@link
     * #dataSpec}.uri unless redirection has occurred. If redirection has occurred, this is the uri
     * after redirection.
     */
    public final Uri uri;
    /** The response headers associated with the load, or an empty map if unavailable. */
    public final Map<String, List<String>> responseHeaders;
    /** The value of {@link SystemClock#elapsedRealtime} at the time of the load event. */
    public final long elapsedRealtimeMs;
    /** The duration of the load up to the event time. */
    public final long loadDurationMs;
    /** The number of bytes that were loaded up to the event time. */
    public final long bytesLoaded;

    /**
     * Creates load event info.
     *
     * @param dataSpec Defines the requested data.
     * @param uri The {@link Uri} from which data is being read. The uri must be identical to the
     *     one in {@code dataSpec.uri} unless redirection has occurred. If redirection has occurred,
     *     this is the uri after redirection.
     * @param responseHeaders The response headers associated with the load, or an empty map if
     *     unavailable.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} at the time of the
     *     load event.
     * @param loadDurationMs The duration of the load up to the event time.
     * @param bytesLoaded The number of bytes that were loaded up to the event time. For compressed
     *     network responses, this is the decompressed size.
     */
    public LoadEventInfo(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      this.dataSpec = dataSpec;
      this.uri = uri;
      this.responseHeaders = responseHeaders;
      this.elapsedRealtimeMs = elapsedRealtimeMs;
      this.loadDurationMs = loadDurationMs;
      this.bytesLoaded = bytesLoaded;
    }
  }

  /** Descriptor for data being loaded or selected by a media source. */
  final class MediaLoadData {

    /** One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data. */
    public final int dataType;
    /**
     * One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds to media of a
     * specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     */
    public final int trackType;
    /**
     * The format of the track to which the data belongs. Null if the data does not belong to a
     * specific track.
     */
    public final @Nullable Format trackFormat;
    /**
     * One of the {@link C} {@code SELECTION_REASON_*} constants if the data belongs to a track.
     * {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     */
    public final int trackSelectionReason;
    /**
     * Optional data associated with the selection of the track to which the data belongs. Null if
     * the data does not belong to a track.
     */
    public final @Nullable Object trackSelectionData;
    /**
     * The start time of the media, or {@link C#TIME_UNSET} if the data does not belong to a
     * specific media period.
     */
    public final long mediaStartTimeMs;
    /**
     * The end time of the media, or {@link C#TIME_UNSET} if the data does not belong to a specific
     * media period or the end time is unknown.
     */
    public final long mediaEndTimeMs;

    /**
     * Creates media load data.
     *
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which
     *     the data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media, or {@link C#TIME_UNSET} if the data does
     *     not belong to a specific media period.
     * @param mediaEndTimeMs The end time of the media, or {@link C#TIME_UNSET} if the data does not
     *     belong to a specific media period or the end time is unknown.
     */
    public MediaLoadData(
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs) {
      this.dataType = dataType;
      this.trackType = trackType;
      this.trackFormat = trackFormat;
      this.trackSelectionReason = trackSelectionReason;
      this.trackSelectionData = trackSelectionData;
      this.mediaStartTimeMs = mediaStartTimeMs;
      this.mediaEndTimeMs = mediaEndTimeMs;
    }
  }

  /**
   * Called when a media period is created by the media source.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the created media period.
   */
  void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId);

  /**
   * Called when a media period is released by the media source.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the released media period.
   */
  void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId);

  /**
   * Called when a load begins.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The value of {@link
   *     LoadEventInfo#uri} won't reflect potential redirection yet and {@link
   *     LoadEventInfo#responseHeaders} will be empty.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData);

  /**
   * Called when a load ends.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData);

  /**
   * Called when a load is canceled.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData);

  /**
   * Called when a load error occurs.
   *
   * <p>The error may or may not have resulted in the load being canceled, as indicated by the
   * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
   * <em>not</em> be called in addition to this method.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error and continue. Hence applications should
   * <em>not</em> implement this method to display a user visible error or initiate an application
   * level retry ({@link Player.EventListener#onPlayerError} is the appropriate place to implement
   * such behavior). This method is called to provide the application with an opportunity to log the
   * error if it wishes to do so.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled);

  /**
   * Called when a media period is first being read from.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the media period being read from.
   */
  void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId);

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the media being discarded.
   */
  void onUpstreamDiscarded(
      int windowIndex, MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData);

  /**
   * Called when a downstream format change occurs (i.e. when the format of the media being read
   * from one or more {@link SampleStream}s provided by the source changes).
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the newly selected downstream data.
   */
  void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData);

  /** Dispatches events to {@link MediaSourceEventListener}s. */
  final class EventDispatcher {

    /** The timeline window index reported with the events. */
    public final int windowIndex;
    /** The {@link MediaPeriodId} reported with the events. */
    public final @Nullable MediaPeriodId mediaPeriodId;

    private final CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers;
    private final long mediaTimeOffsetMs;

    /** Creates an event dispatcher. */
    public EventDispatcher() {
      this(
          /* listenerAndHandlers= */ new CopyOnWriteArrayList<>(),
          /* windowIndex= */ 0,
          /* mediaPeriodId= */ null,
          /* mediaTimeOffsetMs= */ 0);
    }

    private EventDispatcher(
        CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers,
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        long mediaTimeOffsetMs) {
      this.listenerAndHandlers = listenerAndHandlers;
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
      this.mediaTimeOffsetMs = mediaTimeOffsetMs;
    }

    /**
     * Creates a view of the event dispatcher with pre-configured window index, media period id, and
     * media time offset.
     *
     * @param windowIndex The timeline window index to be reported with the events.
     * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events.
     * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
     * @return A view of the event dispatcher with the pre-configured parameters.
     */
    @CheckResult
    public EventDispatcher withParameters(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
      return new EventDispatcher(
          listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    /**
     * Adds a listener to the event dispatcher.
     *
     * @param handler A handler on the which listener events will be posted.
     * @param eventListener The listener to be added.
     */
    public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
      Assertions.checkArgument(handler != null && eventListener != null);
      listenerAndHandlers.add(new ListenerAndHandler(handler, eventListener));
    }

    /**
     * Removes a listener from the event dispatcher.
     *
     * @param eventListener The listener to be removed.
     */
    public void removeEventListener(MediaSourceEventListener eventListener) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        if (listenerAndHandler.listener == eventListener) {
          listenerAndHandlers.remove(listenerAndHandler);
        }
      }
    }

    /** Dispatches {@link #onMediaPeriodCreated(int, MediaPeriodId)}. */
    public void mediaPeriodCreated() {
      MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onMediaPeriodCreated(windowIndex, mediaPeriodId));
      }
    }

    /** Dispatches {@link #onMediaPeriodReleased(int, MediaPeriodId)}. */
    public void mediaPeriodReleased() {
      MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onMediaPeriodReleased(windowIndex, mediaPeriodId));
      }
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs) {
      loadStarted(
          dataSpec,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs);
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(
        DataSpec dataSpec,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        long elapsedRealtimeMs) {
      loadStarted(
          new LoadEventInfo(
              dataSpec,
              dataSpec.uri,
              /* responseHeaders= */ Collections.emptyMap(),
              elapsedRealtimeMs,
              /* loadDurationMs= */ 0,
              /* bytesLoaded= */ 0),
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onLoadStarted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCompleted(
          dataSpec,
          uri,
          responseHeaders,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded);
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCompleted(
          new LoadEventInfo(
              dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadCompleted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCanceled(
          dataSpec,
          uri,
          responseHeaders,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded);
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCanceled(
          new LoadEventInfo(
              dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadCanceled(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded,
        IOException error,
        boolean wasCanceled) {
      loadError(
          dataSpec,
          uri,
          responseHeaders,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded,
          error,
          wasCanceled);
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        DataSpec dataSpec,
        Uri uri,
        Map<String, List<String>> responseHeaders,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded,
        IOException error,
        boolean wasCanceled) {
      loadError(
          new LoadEventInfo(
              dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)),
          error,
          wasCanceled);
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadError(
                    windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData, error, wasCanceled));
      }
    }

    /** Dispatches {@link #onReadingStarted(int, MediaPeriodId)}. */
    public void readingStarted() {
      MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onReadingStarted(windowIndex, mediaPeriodId));
      }
    }

    /** Dispatches {@link #onUpstreamDiscarded(int, MediaPeriodId, MediaLoadData)}. */
    public void upstreamDiscarded(int trackType, long mediaStartTimeUs, long mediaEndTimeUs) {
      upstreamDiscarded(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              /* trackFormat= */ null,
              C.SELECTION_REASON_ADAPTIVE,
              /* trackSelectionData= */ null,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onUpstreamDiscarded(int, MediaPeriodId, MediaLoadData)}. */
    public void upstreamDiscarded(MediaLoadData mediaLoadData) {
      MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onUpstreamDiscarded(windowIndex, mediaPeriodId, mediaLoadData));
      }
    }

    /** Dispatches {@link #onDownstreamFormatChanged(int, MediaPeriodId, MediaLoadData)}. */
    public void downstreamFormatChanged(
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaTimeUs) {
      downstreamFormatChanged(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaTimeUs),
              /* mediaEndTimeMs= */ C.TIME_UNSET));
    }

    /** Dispatches {@link #onDownstreamFormatChanged(int, MediaPeriodId, MediaLoadData)}. */
    public void downstreamFormatChanged(MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        final MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onDownstreamFormatChanged(windowIndex, mediaPeriodId, mediaLoadData));
      }
    }

    private long adjustMediaTime(long mediaTimeUs) {
      long mediaTimeMs = C.usToMs(mediaTimeUs);
      return mediaTimeMs == C.TIME_UNSET ? C.TIME_UNSET : mediaTimeOffsetMs + mediaTimeMs;
    }

    private void postOrRun(Handler handler, Runnable runnable) {
      if (handler.getLooper() == Looper.myLooper()) {
        runnable.run();
      } else {
        handler.post(runnable);
      }
    }

    private static final class ListenerAndHandler {

      public final Handler handler;
      public final MediaSourceEventListener listener;

      public ListenerAndHandler(Handler handler, MediaSourceEventListener listener) {
        this.handler = handler;
        this.listener = listener;
      }
    }
  }
}
