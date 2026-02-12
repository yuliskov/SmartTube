/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Handler;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;

/**
 * Composite {@link MediaSource} consisting of multiple child sources.
 *
 * @param <T> The type of the id used to identify prepared child sources.
 */
public abstract class CompositeMediaSource<T> extends BaseMediaSource {

  private final HashMap<T, MediaSourceAndListener> childSources;

  private @Nullable Handler eventHandler;
  private @Nullable TransferListener mediaTransferListener;

  /** Create composite media source without child sources. */
  protected CompositeMediaSource() {
    childSources = new HashMap<>();
  }

  @Override
  @CallSuper
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    eventHandler = new Handler();
  }

  @Override
  @CallSuper
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (MediaSourceAndListener childSource : childSources.values()) {
      childSource.mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  @CallSuper
  public void releaseSourceInternal() {
    for (MediaSourceAndListener childSource : childSources.values()) {
      childSource.mediaSource.releaseSource(childSource.listener);
      childSource.mediaSource.removeEventListener(childSource.eventListener);
    }
    childSources.clear();
  }

  /**
   * Called when the source info of a child source has been refreshed.
   *
   * @param id The unique id used to prepare the child source.
   * @param mediaSource The child source whose source info has been refreshed.
   * @param timeline The timeline of the child source.
   * @param manifest The manifest of the child source.
   */
  protected abstract void onChildSourceInfoRefreshed(
      T id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest);

  /**
   * Prepares a child source.
   *
   * <p>{@link #onChildSourceInfoRefreshed(Object, MediaSource, Timeline, Object)} will be called
   * when the child source updates its timeline and/or manifest with the same {@code id} passed to
   * this method.
   *
   * <p>Any child sources that aren't explicitly released with {@link #releaseChildSource(Object)}
   * will be released in {@link #releaseSourceInternal()}.
   *
   * @param id A unique id to identify the child source preparation. Null is allowed as an id.
   * @param mediaSource The child {@link MediaSource}.
   */
  protected final void prepareChildSource(final T id, MediaSource mediaSource) {
    Assertions.checkArgument(!childSources.containsKey(id));
    SourceInfoRefreshListener sourceListener =
        (source, timeline, manifest) -> onChildSourceInfoRefreshed(id, source, timeline, manifest);
    MediaSourceEventListener eventListener = new ForwardingEventListener(id);
    childSources.put(id, new MediaSourceAndListener(mediaSource, sourceListener, eventListener));
    mediaSource.addEventListener(Assertions.checkNotNull(eventHandler), eventListener);
    mediaSource.prepareSource(sourceListener, mediaTransferListener);
  }

  /**
   * Releases a child source.
   *
   * @param id The unique id used to prepare the child source.
   */
  protected final void releaseChildSource(T id) {
    MediaSourceAndListener removedChild = Assertions.checkNotNull(childSources.remove(id));
    removedChild.mediaSource.releaseSource(removedChild.listener);
    removedChild.mediaSource.removeEventListener(removedChild.eventListener);
  }

  /**
   * Returns the window index in the composite source corresponding to the specified window index in
   * a child source. The default implementation does not change the window index.
   *
   * @param id The unique id used to prepare the child source.
   * @param windowIndex A window index of the child source.
   * @return The corresponding window index in the composite source.
   */
  protected int getWindowIndexForChildWindowIndex(T id, int windowIndex) {
    return windowIndex;
  }

  /**
   * Returns the {@link MediaPeriodId} in the composite source corresponding to the specified {@link
   * MediaPeriodId} in a child source. The default implementation does not change the media period
   * id.
   *
   * @param id The unique id used to prepare the child source.
   * @param mediaPeriodId A {@link MediaPeriodId} of the child source.
   * @return The corresponding {@link MediaPeriodId} in the composite source. Null if no
   *     corresponding media period id can be determined.
   */
  protected @Nullable MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      T id, MediaPeriodId mediaPeriodId) {
    return mediaPeriodId;
  }

  /**
   * Returns the media time in the composite source corresponding to the specified media time in a
   * child source. The default implementation does not change the media time.
   *
   * @param id The unique id used to prepare the child source.
   * @param mediaTimeMs A media time of the child source, in milliseconds.
   * @return The corresponding media time in the composite source, in milliseconds.
   */
  protected long getMediaTimeForChildMediaTime(@Nullable T id, long mediaTimeMs) {
    return mediaTimeMs;
  }

  private static final class MediaSourceAndListener {

    public final MediaSource mediaSource;
    public final SourceInfoRefreshListener listener;
    public final MediaSourceEventListener eventListener;

    public MediaSourceAndListener(
        MediaSource mediaSource,
        SourceInfoRefreshListener listener,
        MediaSourceEventListener eventListener) {
      this.mediaSource = mediaSource;
      this.listener = listener;
      this.eventListener = eventListener;
    }
  }

  private final class ForwardingEventListener implements MediaSourceEventListener {

    private final T id;
    private EventDispatcher eventDispatcher;

    public ForwardingEventListener(T id) {
      this.eventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
      this.id = id;
    }

    @Override
    public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.mediaPeriodCreated();
      }
    }

    @Override
    public void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.mediaPeriodReleased();
      }
    }

    @Override
    public void onLoadStarted(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadStarted(loadEventData, maybeUpdateMediaLoadData(mediaLoadData));
      }
    }

    @Override
    public void onLoadCompleted(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadCompleted(loadEventData, maybeUpdateMediaLoadData(mediaLoadData));
      }
    }

    @Override
    public void onLoadCanceled(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadCanceled(loadEventData, maybeUpdateMediaLoadData(mediaLoadData));
      }
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadError(
            loadEventData, maybeUpdateMediaLoadData(mediaLoadData), error, wasCanceled);
      }
    }

    @Override
    public void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.readingStarted();
      }
    }

    @Override
    public void onUpstreamDiscarded(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.upstreamDiscarded(maybeUpdateMediaLoadData(mediaLoadData));
      }
    }

    @Override
    public void onDownstreamFormatChanged(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.downstreamFormatChanged(maybeUpdateMediaLoadData(mediaLoadData));
      }
    }

    /** Updates the event dispatcher and returns whether the event should be dispatched. */
    private boolean maybeUpdateEventDispatcher(
        int childWindowIndex, @Nullable MediaPeriodId childMediaPeriodId) {
      MediaPeriodId mediaPeriodId = null;
      if (childMediaPeriodId != null) {
        mediaPeriodId = getMediaPeriodIdForChildMediaPeriodId(id, childMediaPeriodId);
        if (mediaPeriodId == null) {
          // Media period not found. Ignore event.
          return false;
        }
      }
      int windowIndex = getWindowIndexForChildWindowIndex(id, childWindowIndex);
      if (eventDispatcher.windowIndex != windowIndex
          || !Util.areEqual(eventDispatcher.mediaPeriodId, mediaPeriodId)) {
        eventDispatcher =
            createEventDispatcher(windowIndex, mediaPeriodId, /* mediaTimeOffsetMs= */ 0);
      }
      return true;
    }

    private MediaLoadData maybeUpdateMediaLoadData(MediaLoadData mediaLoadData) {
      long mediaStartTimeMs = getMediaTimeForChildMediaTime(id, mediaLoadData.mediaStartTimeMs);
      long mediaEndTimeMs = getMediaTimeForChildMediaTime(id, mediaLoadData.mediaEndTimeMs);
      if (mediaStartTimeMs == mediaLoadData.mediaStartTimeMs
          && mediaEndTimeMs == mediaLoadData.mediaEndTimeMs) {
        return mediaLoadData;
      }
      return new MediaLoadData(
          mediaLoadData.dataType,
          mediaLoadData.trackType,
          mediaLoadData.trackFormat,
          mediaLoadData.trackSelectionReason,
          mediaLoadData.trackSelectionData,
          mediaStartTimeMs,
          mediaEndTimeMs);
    }
  }
}
