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
package com.google.android.exoplayer2;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParameterListener;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implements the internal behavior of {@link ExoPlayerImpl}. */
/* package */ final class ExoPlayerImplInternal
    implements Handler.Callback,
        MediaPeriod.Callback,
        TrackSelector.InvalidationListener,
        MediaSource.SourceInfoRefreshListener,
        PlaybackParameterListener,
        PlayerMessage.Sender {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PLAYBACK_INFO_CHANGED = 0;
  public static final int MSG_PLAYBACK_PARAMETERS_CHANGED = 1;
  public static final int MSG_ERROR = 2;

  // Internal messages
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_SET_SEEK_PARAMETERS = 5;
  private static final int MSG_STOP = 6;
  private static final int MSG_RELEASE = 7;
  private static final int MSG_REFRESH_SOURCE_INFO = 8;
  private static final int MSG_PERIOD_PREPARED = 9;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 10;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 11;
  private static final int MSG_SET_REPEAT_MODE = 12;
  private static final int MSG_SET_SHUFFLE_ENABLED = 13;
  private static final int MSG_SET_FOREGROUND_MODE = 14;
  private static final int MSG_SEND_MESSAGE = 15;
  private static final int MSG_SEND_MESSAGE_TO_TARGET_THREAD = 16;
  private static final int MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL = 17;

  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final TrackSelectorResult emptyTrackSelectorResult;
  private final LoadControl loadControl;
  private final BandwidthMeter bandwidthMeter;
  private final HandlerWrapper handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final DefaultMediaClock mediaClock;
  private final PlaybackInfoUpdate playbackInfoUpdate;
  private final ArrayList<PendingMessageInfo> pendingMessages;
  private final Clock clock;
  private final MediaPeriodQueue queue;

  @SuppressWarnings("unused")
  private SeekParameters seekParameters;

  private PlaybackInfo playbackInfo;
  private MediaSource mediaSource;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  @Player.RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private boolean foregroundMode;

  private int pendingPrepareCount;
  private SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private int nextPendingMessageIndex;

  public ExoPlayerImplInternal(
      Renderer[] renderers,
      TrackSelector trackSelector,
      TrackSelectorResult emptyTrackSelectorResult,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      boolean playWhenReady,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Handler eventHandler,
      Clock clock) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.emptyTrackSelectorResult = emptyTrackSelectorResult;
    this.loadControl = loadControl;
    this.bandwidthMeter = bandwidthMeter;
    this.playWhenReady = playWhenReady;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.eventHandler = eventHandler;
    this.clock = clock;
    this.queue = new MediaPeriodQueue();

    backBufferDurationUs = loadControl.getBackBufferDurationUs();
    retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe();

    seekParameters = SeekParameters.DEFAULT;
    playbackInfo =
        PlaybackInfo.createDummy(/* startPositionUs= */ C.TIME_UNSET, emptyTrackSelectorResult);
    playbackInfoUpdate = new PlaybackInfoUpdate();
    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    mediaClock = new DefaultMediaClock(this, clock);
    pendingMessages = new ArrayList<>();
    enabledRenderers = new Renderer[0];
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackSelector.init(/* listener= */ this, bandwidthMeter);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new HandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = clock.createHandler(internalPlaybackThread.getLooper(), this);
  }

  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    handler
        .obtainMessage(MSG_PREPARE, resetPosition ? 1 : 0, resetState ? 1 : 0, mediaSource)
        .sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void setRepeatMode(@Player.RepeatMode int repeatMode) {
    handler.obtainMessage(MSG_SET_REPEAT_MODE, repeatMode, 0).sendToTarget();
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    handler.obtainMessage(MSG_SET_SHUFFLE_ENABLED, shuffleModeEnabled ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(Timeline timeline, int windowIndex, long positionUs) {
    handler.obtainMessage(MSG_SEEK_TO, new SeekPosition(timeline, windowIndex, positionUs))
        .sendToTarget();
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    handler.obtainMessage(MSG_SET_PLAYBACK_PARAMETERS, playbackParameters).sendToTarget();
  }

  public void setSeekParameters(SeekParameters seekParameters) {
    handler.obtainMessage(MSG_SET_SEEK_PARAMETERS, seekParameters).sendToTarget();
  }

  public void stop(boolean reset) {
    handler.obtainMessage(MSG_STOP, reset ? 1 : 0, 0).sendToTarget();
  }

  @Override
  public synchronized void sendMessage(PlayerMessage message) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      message.markAsProcessed(/* isDelivered= */ false);
      return;
    }
    handler.obtainMessage(MSG_SEND_MESSAGE, message).sendToTarget();
  }

  public synchronized void setForegroundMode(boolean foregroundMode) {
    if (foregroundMode) {
      handler.obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 1, 0).sendToTarget();
    } else {
      AtomicBoolean processedFlag = new AtomicBoolean();
      handler
          .obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 0, 0, processedFlag)
          .sendToTarget();
      boolean wasInterrupted = false;
      while (!processedFlag.get() && !released) {
        try {
          wait();
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
      if (wasInterrupted) {
        // Restore the interrupted status.
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized void release() {
    if (released) {
      return;
    }
    handler.sendEmptyMessage(MSG_RELEASE);
    boolean wasInterrupted = false;
    while (!released) {
      try {
        wait();
      } catch (InterruptedException e) {
        wasInterrupted = true;
      }
    }
    if (wasInterrupted) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }
  }

  public Looper getPlaybackLooper() {
    return internalPlaybackThread.getLooper();
  }

  // MediaSource.SourceInfoRefreshListener implementation.

  @Override
  public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
    handler.obtainMessage(MSG_REFRESH_SOURCE_INFO,
        new MediaSourceRefreshInfo(source, timeline, manifest)).sendToTarget();
  }

  // MediaPeriod.Callback implementation.

  @Override
  public void onPrepared(MediaPeriod source) {
    handler.obtainMessage(MSG_PERIOD_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
  }

  // TrackSelector.InvalidationListener implementation.

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  // DefaultMediaClock.PlaybackParameterListener implementation.

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    handler
        .obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL, playbackParameters)
        .sendToTarget();
  }

  // Handler.Callback implementation.

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE:
          prepareInternal(
              (MediaSource) msg.obj,
              /* resetPosition= */ msg.arg1 != 0,
              /* resetState= */ msg.arg2 != 0);
          break;
        case MSG_SET_PLAY_WHEN_READY:
          setPlayWhenReadyInternal(msg.arg1 != 0);
          break;
        case MSG_SET_REPEAT_MODE:
          setRepeatModeInternal(msg.arg1);
          break;
        case MSG_SET_SHUFFLE_ENABLED:
          setShuffleModeEnabledInternal(msg.arg1 != 0);
          break;
        case MSG_DO_SOME_WORK:
          doSomeWork();
          break;
        case MSG_SEEK_TO:
          seekToInternal((SeekPosition) msg.obj);
          break;
        case MSG_SET_PLAYBACK_PARAMETERS:
          setPlaybackParametersInternal((PlaybackParameters) msg.obj);
          break;
        case MSG_SET_SEEK_PARAMETERS:
          setSeekParametersInternal((SeekParameters) msg.obj);
          break;
        case MSG_SET_FOREGROUND_MODE:
          setForegroundModeInternal(
              /* foregroundMode= */ msg.arg1 != 0, /* processedFlag= */ (AtomicBoolean) msg.obj);
          break;
        case MSG_STOP:
          stopInternal(
              /* forceResetRenderers= */ false,
              /* resetPositionAndState= */ msg.arg1 != 0,
              /* acknowledgeStop= */ true);
          break;
        case MSG_PERIOD_PREPARED:
          handlePeriodPrepared((MediaPeriod) msg.obj);
          break;
        case MSG_REFRESH_SOURCE_INFO:
          handleSourceInfoRefreshed((MediaSourceRefreshInfo) msg.obj);
          break;
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED:
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          break;
        case MSG_TRACK_SELECTION_INVALIDATED:
          reselectTracksInternal();
          break;
        case MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL:
          handlePlaybackParameters((PlaybackParameters) msg.obj);
          break;
        case MSG_SEND_MESSAGE:
          sendMessageInternal((PlayerMessage) msg.obj);
          break;
        case MSG_SEND_MESSAGE_TO_TARGET_THREAD:
          sendMessageToTargetThread((PlayerMessage) msg.obj);
          break;
        case MSG_RELEASE:
          releaseInternal();
          // Return immediately to not send playback info updates after release.
          return true;
        default:
          return false;
      }
      maybeNotifyPlaybackInfoChanged();
    } catch (ExoPlaybackException e) {
      Log.e(TAG, "Playback error.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal(
          /* forceResetRenderers= */ true,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      maybeNotifyPlaybackInfoChanged();
    } catch (IOException e) {
      Log.e(TAG, "Source error.", e);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForSource(e)).sendToTarget();
      stopInternal(
          /* forceResetRenderers= */ false,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      maybeNotifyPlaybackInfoChanged();
    } catch (RuntimeException | OutOfMemoryError e) {
      Log.e(TAG, "Internal runtime error.", e);
      ExoPlaybackException error =
          e instanceof OutOfMemoryError
              ? ExoPlaybackException.createForOutOfMemoryError((OutOfMemoryError) e)
              : ExoPlaybackException.createForUnexpected((RuntimeException) e);
      eventHandler.obtainMessage(MSG_ERROR, error).sendToTarget();
      stopInternal(
          /* forceResetRenderers= */ true,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      maybeNotifyPlaybackInfoChanged();
    }
    return true;
  }

  // Private methods.

  private void setState(int state) {
    if (playbackInfo.playbackState != state) {
      playbackInfo = playbackInfo.copyWithPlaybackState(state);
    }
  }

  private void setIsLoading(boolean isLoading) {
    if (playbackInfo.isLoading != isLoading) {
      playbackInfo = playbackInfo.copyWithIsLoading(isLoading);
    }
  }

  private void maybeNotifyPlaybackInfoChanged() {
    if (playbackInfoUpdate.hasPendingUpdate(playbackInfo)) {
      eventHandler
          .obtainMessage(
              MSG_PLAYBACK_INFO_CHANGED,
              playbackInfoUpdate.operationAcks,
              playbackInfoUpdate.positionDiscontinuity
                  ? playbackInfoUpdate.discontinuityReason
                  : C.INDEX_UNSET,
              playbackInfo)
          .sendToTarget();
      playbackInfoUpdate.reset(playbackInfo);
    }
  }

  private void prepareInternal(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    pendingPrepareCount++;
    resetInternal(
        /* resetRenderers= */ false, /* releaseMediaSource= */ true, resetPosition, resetState);
    loadControl.onPrepared();
    this.mediaSource = mediaSource;
    setState(Player.STATE_BUFFERING);
    mediaSource.prepareSource(/* listener= */ this, bandwidthMeter.getTransferListener());
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    rebuffering = false;
    this.playWhenReady = playWhenReady;
    if (!playWhenReady) {
      stopRenderers();
      updatePlaybackPositions();
    } else {
      if (playbackInfo.playbackState == Player.STATE_READY) {
        startRenderers();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      } else if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    }
  }

  private void setRepeatModeInternal(@Player.RepeatMode int repeatMode)
      throws ExoPlaybackException {
    this.repeatMode = repeatMode;
    if (!queue.updateRepeatMode(repeatMode)) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled)
      throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    if (!queue.updateShuffleModeEnabled(shuffleModeEnabled)) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void seekToCurrentPosition(boolean sendDiscontinuity) throws ExoPlaybackException {
    // Renderers may have read from a period that's been removed. Seek back to the current
    // position of the playing period to make sure none of the removed period is played.
    MediaPeriodId periodId = queue.getPlayingPeriod().info.id;
    long newPositionUs =
        seekToPeriodPosition(periodId, playbackInfo.positionUs, /* forceDisableRenderers= */ true);
    if (newPositionUs != playbackInfo.positionUs) {
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              periodId,
              newPositionUs,
              playbackInfo.contentPositionUs,
              getTotalBufferedDurationUs());
      if (sendDiscontinuity) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    mediaClock.start();
    for (Renderer renderer : enabledRenderers) {
      renderer.start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    mediaClock.stop();
    for (Renderer renderer : enabledRenderers) {
      ensureStopped(renderer);
    }
  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    if (!queue.hasPlayingPeriod()) {
      return;
    }

    // Update the playback position.
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    long periodPositionUs = playingPeriodHolder.mediaPeriod.readDiscontinuity();
    if (periodPositionUs != C.TIME_UNSET) {
      resetRendererPosition(periodPositionUs);
      // A MediaPeriod may report a discontinuity at the current playback position to ensure the
      // renderers are flushed. Only report the discontinuity externally if the position changed.
      if (periodPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            playbackInfo.copyWithNewPosition(
                playbackInfo.periodId,
                periodPositionUs,
                playbackInfo.contentPositionUs,
                getTotalBufferedDurationUs());
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    } else {
      rendererPositionUs = mediaClock.syncAndGetPositionUs();
      periodPositionUs = playingPeriodHolder.toPeriodTime(rendererPositionUs);
      maybeTriggerPendingMessages(playbackInfo.positionUs, periodPositionUs);
      playbackInfo.positionUs = periodPositionUs;
    }

    // Update the buffered position and total buffered duration.
    MediaPeriodHolder loadingPeriod = queue.getLoadingPeriod();
    playbackInfo.bufferedPositionUs = loadingPeriod.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = clock.uptimeMillis();
    updatePeriods();
    if (!queue.hasPlayingPeriod()) {
      // We're still waiting for the first period to be prepared.
      maybeThrowPeriodPrepareError();
      scheduleNextWork(operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
    long rendererPositionElapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

    playingPeriodHolder.mediaPeriod.discardBuffer(playbackInfo.positionUs - backBufferDurationUs,
        retainBackBufferFromKeyframe);

    boolean renderersEnded = true;
    boolean renderersReadyOrEnded = true;
    for (Renderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be called
      // again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(rendererPositionUs, rendererPositionElapsedRealtimeUs);
      renderersEnded = renderersEnded && renderer.isEnded();
      // Determine whether the renderer is ready (or ended). We override to assume the renderer is
      // ready if it needs the next sample stream. This is necessary to avoid getting stuck if
      // tracks in the current period have uneven durations. See:
      // https://github.com/google/ExoPlayer/issues/1874
      boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded()
          || rendererWaitingForNextStream(renderer);
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowStreamError();
      }
      renderersReadyOrEnded = renderersReadyOrEnded && rendererReadyOrEnded;
    }
    if (!renderersReadyOrEnded) {
      maybeThrowPeriodPrepareError();
    }

    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    if (renderersEnded
        && (playingPeriodDurationUs == C.TIME_UNSET
            || playingPeriodDurationUs <= playbackInfo.positionUs)
        && playingPeriodHolder.info.isFinal) {
      setState(Player.STATE_ENDED);
      stopRenderers();
    } else if (playbackInfo.playbackState == Player.STATE_BUFFERING
        && shouldTransitionToReadyState(renderersReadyOrEnded)) {
      setState(Player.STATE_READY);
      if (playWhenReady) {
        startRenderers();
      }
    } else if (playbackInfo.playbackState == Player.STATE_READY
        && !(enabledRenderers.length == 0 ? isTimelineReady() : renderersReadyOrEnded)) {
      rebuffering = playWhenReady;
      setState(Player.STATE_BUFFERING);
      stopRenderers();
    }

    if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      for (Renderer renderer : enabledRenderers) {
        renderer.maybeThrowStreamError();
      }
    }

    if ((playWhenReady && playbackInfo.playbackState == Player.STATE_READY)
        || playbackInfo.playbackState == Player.STATE_BUFFERING) {
      scheduleNextWork(operationStartTimeMs, RENDERING_INTERVAL_MS);
    } else if (enabledRenderers.length != 0 && playbackInfo.playbackState != Player.STATE_ENDED) {
      scheduleNextWork(operationStartTimeMs, IDLE_INTERVAL_MS);
    } else {
      handler.removeMessages(MSG_DO_SOME_WORK);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    handler.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + intervalMs);
  }

  private void seekToInternal(SeekPosition seekPosition) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);

    MediaPeriodId periodId;
    long periodPositionUs;
    long contentPositionUs;
    boolean seekPositionAdjusted;
    Pair<Object, Long> resolvedSeekPosition =
        resolveSeekPosition(seekPosition, /* trySubsequentPeriods= */ true);
    if (resolvedSeekPosition == null) {
      // The seek position was valid for the timeline that it was performed into, but the
      // timeline has changed or is not ready and a suitable seek position could not be resolved.
      periodId = playbackInfo.getDummyFirstMediaPeriodId(shuffleModeEnabled, window);
      periodPositionUs = C.TIME_UNSET;
      contentPositionUs = C.TIME_UNSET;
      seekPositionAdjusted = true;
    } else {
      // Update the resolved seek position to take ads into account.
      Object periodUid = resolvedSeekPosition.first;
      contentPositionUs = resolvedSeekPosition.second;
      periodId = queue.resolveMediaPeriodIdForAds(periodUid, contentPositionUs);
      if (periodId.isAd()) {
        periodPositionUs = 0;
        seekPositionAdjusted = true;
      } else {
        periodPositionUs = resolvedSeekPosition.second;
        seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
      }
    }

    try {
      if (mediaSource == null || pendingPrepareCount > 0) {
        // Save seek position for later, as we are still waiting for a prepared source.
        pendingInitialSeekPosition = seekPosition;
      } else if (periodPositionUs == C.TIME_UNSET) {
        // End playback, as we didn't manage to find a valid seek position.
        setState(Player.STATE_ENDED);
        resetInternal(
            /* resetRenderers= */ false,
            /* releaseMediaSource= */ false,
            /* resetPosition= */ true,
            /* resetState= */ false);
      } else {
        // Execute the seek in the current media periods.
        long newPeriodPositionUs = periodPositionUs;
        if (periodId.equals(playbackInfo.periodId)) {
          MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
          if (playingPeriodHolder != null && newPeriodPositionUs != 0) {
            newPeriodPositionUs =
                playingPeriodHolder.mediaPeriod.getAdjustedSeekPositionUs(
                    newPeriodPositionUs, seekParameters);
          }
          if (C.usToMs(newPeriodPositionUs) == C.usToMs(playbackInfo.positionUs)) {
            // Seek will be performed to the current position. Do nothing.
            periodPositionUs = playbackInfo.positionUs;
            return;
          }
        }
        newPeriodPositionUs = seekToPeriodPosition(periodId, newPeriodPositionUs);
        seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
        periodPositionUs = newPeriodPositionUs;
      }
    } finally {
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              periodId, periodPositionUs, contentPositionUs, getTotalBufferedDurationUs());
      if (seekPositionAdjusted) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
      }
    }
  }

  private long seekToPeriodPosition(MediaPeriodId periodId, long periodPositionUs)
      throws ExoPlaybackException {
    // Force disable renderers if they are reading from a period other than the one being played.
    return seekToPeriodPosition(
        periodId, periodPositionUs, queue.getPlayingPeriod() != queue.getReadingPeriod());
  }

  private long seekToPeriodPosition(
      MediaPeriodId periodId, long periodPositionUs, boolean forceDisableRenderers)
      throws ExoPlaybackException {
    stopRenderers();
    rebuffering = false;
    setState(Player.STATE_BUFFERING);

    // Clear the timeline, but keep the requested period if it is already prepared.
    MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder newPlayingPeriodHolder = oldPlayingPeriodHolder;
    while (newPlayingPeriodHolder != null) {
      if (periodId.equals(newPlayingPeriodHolder.info.id) && newPlayingPeriodHolder.prepared) {
        queue.removeAfter(newPlayingPeriodHolder);
        break;
      }
      newPlayingPeriodHolder = queue.advancePlayingPeriod();
    }

    // Disable all renderers if the period being played is changing, if the seek results in negative
    // renderer timestamps, or if forced.
    if (forceDisableRenderers
        || oldPlayingPeriodHolder != newPlayingPeriodHolder
        || (newPlayingPeriodHolder != null
            && newPlayingPeriodHolder.toRendererTime(periodPositionUs) < 0)) {
      for (Renderer renderer : enabledRenderers) {
        disableRenderer(renderer);
      }
      enabledRenderers = new Renderer[0];
      oldPlayingPeriodHolder = null;
      if (newPlayingPeriodHolder != null) {
        newPlayingPeriodHolder.setRendererOffset(/* rendererPositionOffsetUs= */ 0);
      }
    }

    // Update the holders.
    if (newPlayingPeriodHolder != null) {
      updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
      if (newPlayingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = newPlayingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
        newPlayingPeriodHolder.mediaPeriod.discardBuffer(
            periodPositionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      queue.clear(/* keepFrontPeriodUid= */ true);
      // New period has not been prepared.
      playbackInfo =
          playbackInfo.copyWithTrackInfo(TrackGroupArray.EMPTY, emptyTrackSelectorResult);
      resetRendererPosition(periodPositionUs);
    }

    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    return periodPositionUs;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    rendererPositionUs =
        !queue.hasPlayingPeriod()
            ? periodPositionUs
            : queue.getPlayingPeriod().toRendererTime(periodPositionUs);
    mediaClock.resetPosition(rendererPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.resetPosition(rendererPositionUs);
    }
    notifyTrackSelectionDiscontinuity();
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    mediaClock.setPlaybackParameters(playbackParameters);
  }

  private void setSeekParametersInternal(SeekParameters seekParameters) {
    this.seekParameters = seekParameters;
  }

  private void setForegroundModeInternal(
      boolean foregroundMode, @Nullable AtomicBoolean processedFlag) {
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      if (!foregroundMode) {
        for (Renderer renderer : renderers) {
          if (renderer.getState() == Renderer.STATE_DISABLED) {
            renderer.reset();
          }
        }
      }
    }
    if (processedFlag != null) {
      synchronized (this) {
        processedFlag.set(true);
        notifyAll();
      }
    }
  }

  private void stopInternal(
      boolean forceResetRenderers, boolean resetPositionAndState, boolean acknowledgeStop) {
    resetInternal(
        /* resetRenderers= */ forceResetRenderers || !foregroundMode,
        /* releaseMediaSource= */ true,
        /* resetPosition= */ resetPositionAndState,
        /* resetState= */ resetPositionAndState);
    playbackInfoUpdate.incrementPendingOperationAcks(
        pendingPrepareCount + (acknowledgeStop ? 1 : 0));
    pendingPrepareCount = 0;
    loadControl.onStopped();
    setState(Player.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal(
        /* resetRenderers= */ true,
        /* releaseMediaSource= */ true,
        /* resetPosition= */ true,
        /* resetState= */ true);
    loadControl.onReleased();
    setState(Player.STATE_IDLE);
    internalPlaybackThread.quit();
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal(
      boolean resetRenderers,
      boolean releaseMediaSource,
      boolean resetPosition,
      boolean resetState) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    mediaClock.stop();
    rendererPositionUs = 0;
    for (Renderer renderer : enabledRenderers) {
      try {
        disableRenderer(renderer);
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Disable failed.", e);
      }
    }
    if (resetRenderers) {
      for (Renderer renderer : renderers) {
        try {
          renderer.reset();
        } catch (RuntimeException e) {
          // There's nothing we can do.
          Log.e(TAG, "Reset failed.", e);
        }
      }
    }
    enabledRenderers = new Renderer[0];

    if (resetPosition) {
      pendingInitialSeekPosition = null;
    } else if (resetState) {
      // When resetting the state, also reset the period-based PlaybackInfo position and convert
      // existing position to initial seek instead.
      resetPosition = true;
      if (pendingInitialSeekPosition == null && !playbackInfo.timeline.isEmpty()) {
        playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
        long windowPositionUs = playbackInfo.positionUs + period.getPositionInWindowUs();
        pendingInitialSeekPosition =
            new SeekPosition(Timeline.EMPTY, period.windowIndex, windowPositionUs);
      }
    }

    queue.clear(/* keepFrontPeriodUid= */ !resetPosition);
    setIsLoading(false);
    if (resetState) {
      queue.setTimeline(Timeline.EMPTY);
      for (PendingMessageInfo pendingMessageInfo : pendingMessages) {
        pendingMessageInfo.message.markAsProcessed(/* isDelivered= */ false);
      }
      pendingMessages.clear();
      nextPendingMessageIndex = 0;
    }
    MediaPeriodId mediaPeriodId =
        resetPosition
            ? playbackInfo.getDummyFirstMediaPeriodId(shuffleModeEnabled, window)
            : playbackInfo.periodId;
    // Set the start position to TIME_UNSET so that a subsequent seek to 0 isn't ignored.
    long startPositionUs = resetPosition ? C.TIME_UNSET : playbackInfo.positionUs;
    long contentPositionUs = resetPosition ? C.TIME_UNSET : playbackInfo.contentPositionUs;
    playbackInfo =
        new PlaybackInfo(
            resetState ? Timeline.EMPTY : playbackInfo.timeline,
            resetState ? null : playbackInfo.manifest,
            mediaPeriodId,
            startPositionUs,
            contentPositionUs,
            playbackInfo.playbackState,
            /* isLoading= */ false,
            resetState ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
            resetState ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
            mediaPeriodId,
            startPositionUs,
            /* totalBufferedDurationUs= */ 0,
            startPositionUs);
    if (releaseMediaSource) {
      if (mediaSource != null) {
        mediaSource.releaseSource(/* listener= */ this);
        mediaSource = null;
      }
    }
  }

  private void sendMessageInternal(PlayerMessage message) throws ExoPlaybackException {
    if (message.getPositionMs() == C.TIME_UNSET) {
      // If no delivery time is specified, trigger immediate message delivery.
      sendMessageToTarget(message);
    } else if (mediaSource == null || pendingPrepareCount > 0) {
      // Still waiting for initial timeline to resolve position.
      pendingMessages.add(new PendingMessageInfo(message));
    } else {
      PendingMessageInfo pendingMessageInfo = new PendingMessageInfo(message);
      if (resolvePendingMessagePosition(pendingMessageInfo)) {
        pendingMessages.add(pendingMessageInfo);
        // Ensure new message is inserted according to playback order.
        Collections.sort(pendingMessages);
      } else {
        message.markAsProcessed(/* isDelivered= */ false);
      }
    }
  }

  private void sendMessageToTarget(PlayerMessage message) throws ExoPlaybackException {
    if (message.getHandler().getLooper() == handler.getLooper()) {
      deliverMessage(message);
      if (playbackInfo.playbackState == Player.STATE_READY
          || playbackInfo.playbackState == Player.STATE_BUFFERING) {
        // The message may have caused something to change that now requires us to do work.
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } else {
      handler.obtainMessage(MSG_SEND_MESSAGE_TO_TARGET_THREAD, message).sendToTarget();
    }
  }

  private void sendMessageToTargetThread(final PlayerMessage message) {
    Handler handler = message.getHandler();
    handler.post(
        () -> {
          try {
            deliverMessage(message);
          } catch (ExoPlaybackException e) {
            Log.e(TAG, "Unexpected error delivering message on external thread.", e);
            throw new RuntimeException(e);
          }
        });
  }

  private void deliverMessage(PlayerMessage message) throws ExoPlaybackException {
    if (message.isCanceled()) {
      return;
    }
    try {
      message.getTarget().handleMessage(message.getType(), message.getPayload());
    } finally {
      message.markAsProcessed(/* isDelivered= */ true);
    }
  }

  private void resolvePendingMessagePositions() {
    for (int i = pendingMessages.size() - 1; i >= 0; i--) {
      if (!resolvePendingMessagePosition(pendingMessages.get(i))) {
        // Unable to resolve a new position for the message. Remove it.
        pendingMessages.get(i).message.markAsProcessed(/* isDelivered= */ false);
        pendingMessages.remove(i);
      }
    }
    // Re-sort messages by playback order.
    Collections.sort(pendingMessages);
  }

  private boolean resolvePendingMessagePosition(PendingMessageInfo pendingMessageInfo) {
    if (pendingMessageInfo.resolvedPeriodUid == null) {
      // Position is still unresolved. Try to find window in current timeline.
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(
              new SeekPosition(
                  pendingMessageInfo.message.getTimeline(),
                  pendingMessageInfo.message.getWindowIndex(),
                  C.msToUs(pendingMessageInfo.message.getPositionMs())),
              /* trySubsequentPeriods= */ false);
      if (periodPosition == null) {
        return false;
      }
      pendingMessageInfo.setResolvedPosition(
          playbackInfo.timeline.getIndexOfPeriod(periodPosition.first),
          periodPosition.second,
          periodPosition.first);
    } else {
      // Position has been resolved for a previous timeline. Try to find the updated period index.
      int index = playbackInfo.timeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid);
      if (index == C.INDEX_UNSET) {
        return false;
      }
      pendingMessageInfo.resolvedPeriodIndex = index;
    }
    return true;
  }

  private void maybeTriggerPendingMessages(long oldPeriodPositionUs, long newPeriodPositionUs)
      throws ExoPlaybackException {
    if (pendingMessages.isEmpty() || playbackInfo.periodId.isAd()) {
      return;
    }
    // If this is the first call from the start position, include oldPeriodPositionUs in potential
    // trigger positions.
    if (playbackInfo.startPositionUs == oldPeriodPositionUs) {
      oldPeriodPositionUs--;
    }
    // Correct next index if necessary (e.g. after seeking, timeline changes, or new messages)
    int currentPeriodIndex =
        playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    PendingMessageInfo previousInfo =
        nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    while (previousInfo != null
        && (previousInfo.resolvedPeriodIndex > currentPeriodIndex
            || (previousInfo.resolvedPeriodIndex == currentPeriodIndex
                && previousInfo.resolvedPeriodTimeUs > oldPeriodPositionUs))) {
      nextPendingMessageIndex--;
      previousInfo =
          nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    }
    PendingMessageInfo nextInfo =
        nextPendingMessageIndex < pendingMessages.size()
            ? pendingMessages.get(nextPendingMessageIndex)
            : null;
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && (nextInfo.resolvedPeriodIndex < currentPeriodIndex
            || (nextInfo.resolvedPeriodIndex == currentPeriodIndex
                && nextInfo.resolvedPeriodTimeUs <= oldPeriodPositionUs))) {
      nextPendingMessageIndex++;
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
    // Check if any message falls within the covered time span.
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && nextInfo.resolvedPeriodIndex == currentPeriodIndex
        && nextInfo.resolvedPeriodTimeUs > oldPeriodPositionUs
        && nextInfo.resolvedPeriodTimeUs <= newPeriodPositionUs) {
      try {
        sendMessageToTarget(nextInfo.message);
      } finally {
        if (nextInfo.message.getDeleteAfterDelivery() || nextInfo.message.isCanceled()) {
          pendingMessages.remove(nextPendingMessageIndex);
        } else {
          nextPendingMessageIndex++;
        }
      }
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
  }

  private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == Renderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void disableRenderer(Renderer renderer) throws ExoPlaybackException {
    mediaClock.onRendererDisabled(renderer);
    ensureStopped(renderer);
    renderer.disable();
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (!queue.hasPlayingPeriod()) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    float playbackSpeed = mediaClock.getPlaybackParameters().speed;
    // Reselect tracks on each period in turn, until the selection changes.
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    boolean selectionsChangedForReadPeriod = true;
    TrackSelectorResult newTrackSelectorResult;
    while (true) {
      if (periodHolder == null || !periodHolder.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      newTrackSelectorResult = periodHolder.selectTracks(playbackSpeed, playbackInfo.timeline);
      if (newTrackSelectorResult != null) {
        // Selected tracks have changed for this period.
        break;
      }
      if (periodHolder == readingPeriodHolder) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      periodHolder = periodHolder.getNext();
    }

    if (selectionsChangedForReadPeriod) {
      // Update streams and rebuffer for the new selection, recreating all streams if reading ahead.
      MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
      boolean recreateStreams = queue.removeAfter(playingPeriodHolder);

      boolean[] streamResetFlags = new boolean[renderers.length];
      long periodPositionUs =
          playingPeriodHolder.applyTrackSelection(
              newTrackSelectorResult, playbackInfo.positionUs, recreateStreams, streamResetFlags);
      if (playbackInfo.playbackState != Player.STATE_ENDED
          && periodPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            playbackInfo.copyWithNewPosition(
                playbackInfo.periodId,
                periodPositionUs,
                playbackInfo.contentPositionUs,
                getTotalBufferedDurationUs());
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        resetRendererPosition(periodPositionUs);
      }

      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
        SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
        if (sampleStream != null) {
          enabledRendererCount++;
        }
        if (rendererWasEnabledFlags[i]) {
          if (sampleStream != renderer.getStream()) {
            // We need to disable the renderer.
            disableRenderer(renderer);
          } else if (streamResetFlags[i]) {
            // The renderer will continue to consume from its current stream, but needs to be reset.
            renderer.resetPosition(rendererPositionUs);
          }
        }
      }
      playbackInfo =
          playbackInfo.copyWithTrackInfo(
              playingPeriodHolder.getTrackGroups(), playingPeriodHolder.getTrackSelectorResult());
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      queue.removeAfter(periodHolder);
      if (periodHolder.prepared) {
        long loadingPeriodPositionUs =
            Math.max(
                periodHolder.info.startPositionUs, periodHolder.toPeriodTime(rendererPositionUs));
        periodHolder.applyTrackSelection(newTrackSelectorResult, loadingPeriodPositionUs, false);
      }
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ true);
    if (playbackInfo.playbackState != Player.STATE_ENDED) {
      maybeContinueLoading();
      updatePlaybackPositions();
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void updateTrackSelectionPlaybackSpeed(float playbackSpeed) {
    MediaPeriodHolder periodHolder = queue.getFrontPeriod();
    while (periodHolder != null && periodHolder.prepared) {
      TrackSelection[] trackSelections = periodHolder.getTrackSelectorResult().selections.getAll();
      for (TrackSelection trackSelection : trackSelections) {
        if (trackSelection != null) {
          trackSelection.onPlaybackSpeed(playbackSpeed);
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private void notifyTrackSelectionDiscontinuity() {
    MediaPeriodHolder periodHolder = queue.getFrontPeriod();
    while (periodHolder != null) {
      TrackSelectorResult trackSelectorResult = periodHolder.getTrackSelectorResult();
      if (trackSelectorResult != null) {
        TrackSelection[] trackSelections = trackSelectorResult.selections.getAll();
        for (TrackSelection trackSelection : trackSelections) {
          if (trackSelection != null) {
            trackSelection.onDiscontinuity();
          }
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private boolean shouldTransitionToReadyState(boolean renderersReadyOrEnded) {
    if (enabledRenderers.length == 0) {
      // If there are no enabled renderers, determine whether we're ready based on the timeline.
      return isTimelineReady();
    }
    if (!renderersReadyOrEnded) {
      return false;
    }
    if (!playbackInfo.isLoading) {
      // Renderers are ready and we're not loading. Transition to ready, since the alternative is
      // getting stuck waiting for additional media that's not being loaded.
      return true;
    }
    // Renderers are ready and we're loading. Ask the LoadControl whether to transition.
    MediaPeriodHolder loadingHolder = queue.getLoadingPeriod();
    boolean bufferedToEnd = loadingHolder.isFullyBuffered() && loadingHolder.info.isFinal;
    return bufferedToEnd
        || loadControl.shouldStartPlayback(
            getTotalBufferedDurationUs(), mediaClock.getPlaybackParameters().speed, rebuffering);
  }

  private boolean isTimelineReady() {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder nextPeriodHolder = playingPeriodHolder.getNext();
    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    return playingPeriodDurationUs == C.TIME_UNSET
        || playbackInfo.positionUs < playingPeriodDurationUs
        || (nextPeriodHolder != null
            && (nextPeriodHolder.prepared || nextPeriodHolder.info.id.isAd()));
  }

  private void maybeThrowSourceInfoRefreshError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder != null) {
      // Defer throwing until we read all available media periods.
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
    }
    mediaSource.maybeThrowSourceInfoRefreshError();
  }

  private void maybeThrowPeriodPrepareError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (loadingPeriodHolder != null
        && !loadingPeriodHolder.prepared
        && (readingPeriodHolder == null || readingPeriodHolder.getNext() == loadingPeriodHolder)) {
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      loadingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }
  }

  private void handleSourceInfoRefreshed(MediaSourceRefreshInfo sourceRefreshInfo)
      throws ExoPlaybackException {
    if (sourceRefreshInfo.source != mediaSource) {
      // Stale event.
      return;
    }
    playbackInfoUpdate.incrementPendingOperationAcks(pendingPrepareCount);
    pendingPrepareCount = 0;

    Timeline oldTimeline = playbackInfo.timeline;
    Timeline timeline = sourceRefreshInfo.timeline;
    Object manifest = sourceRefreshInfo.manifest;
    queue.setTimeline(timeline);
    playbackInfo = playbackInfo.copyWithTimeline(timeline, manifest);
    resolvePendingMessagePositions();

    MediaPeriodId newPeriodId = playbackInfo.periodId;
    long oldContentPositionUs =
        playbackInfo.periodId.isAd() ? playbackInfo.contentPositionUs : playbackInfo.positionUs;
    long newContentPositionUs = oldContentPositionUs;
    if (pendingInitialSeekPosition != null) {
      // Resolve initial seek position.
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(pendingInitialSeekPosition, /* trySubsequentPeriods= */ true);
      pendingInitialSeekPosition = null;
      if (periodPosition == null) {
        // The seek position was valid for the timeline that it was performed into, but the
        // timeline has changed and a suitable seek position could not be resolved in the new one.
        handleSourceInfoRefreshEndedPlayback();
        return;
      }
      newContentPositionUs = periodPosition.second;
      newPeriodId = queue.resolveMediaPeriodIdForAds(periodPosition.first, newContentPositionUs);
    } else if (oldContentPositionUs == C.TIME_UNSET && !timeline.isEmpty()) {
      // Resolve unset start position to default position.
      Pair<Object, Long> defaultPosition =
          getPeriodPosition(
              timeline, timeline.getFirstWindowIndex(shuffleModeEnabled), C.TIME_UNSET);
      newPeriodId = queue.resolveMediaPeriodIdForAds(defaultPosition.first, defaultPosition.second);
      if (!newPeriodId.isAd()) {
        // Keep unset start position if we need to play an ad first.
        newContentPositionUs = defaultPosition.second;
      }
    } else if (timeline.getIndexOfPeriod(newPeriodId.periodUid) == C.INDEX_UNSET) {
      // The current period isn't in the new timeline. Attempt to resolve a subsequent period whose
      // window we can restart from.
      Object newPeriodUid = resolveSubsequentPeriod(newPeriodId.periodUid, oldTimeline, timeline);
      if (newPeriodUid == null) {
        // We failed to resolve a suitable restart position.
        handleSourceInfoRefreshEndedPlayback();
        return;
      }
      // We resolved a subsequent period. Start at the default position in the corresponding window.
      Pair<Object, Long> defaultPosition =
          getPeriodPosition(
              timeline, timeline.getPeriodByUid(newPeriodUid, period).windowIndex, C.TIME_UNSET);
      newContentPositionUs = defaultPosition.second;
      newPeriodId = queue.resolveMediaPeriodIdForAds(defaultPosition.first, newContentPositionUs);
    } else {
      // Recheck if the current ad still needs to be played or if we need to start playing an ad.
      newPeriodId =
          queue.resolveMediaPeriodIdForAds(playbackInfo.periodId.periodUid, newContentPositionUs);
      if (!playbackInfo.periodId.isAd() && !newPeriodId.isAd()) {
        // Drop update if we keep playing the same content (MediaPeriod.periodUid are identical) and
        // only MediaPeriodId.nextAdGroupIndex may have changed. This postpones a potential
        // discontinuity until we reach the former next ad group position.
        newPeriodId = playbackInfo.periodId;
      }
    }

    if (playbackInfo.periodId.equals(newPeriodId) && oldContentPositionUs == newContentPositionUs) {
      // We can keep the current playing period. Update the rest of the queued periods.
      if (!queue.updateQueuedPeriods(rendererPositionUs, getMaxRendererReadPositionUs())) {
        seekToCurrentPosition(/* sendDiscontinuity= */ false);
      }
    } else {
      // Something changed. Seek to new start position.
      MediaPeriodHolder periodHolder = queue.getFrontPeriod();
      if (periodHolder != null) {
        // Update the new playing media period info if it already exists.
        while (periodHolder.getNext() != null) {
          periodHolder = periodHolder.getNext();
          if (periodHolder.info.id.equals(newPeriodId)) {
            periodHolder.info = queue.getUpdatedMediaPeriodInfo(periodHolder.info);
          }
        }
      }
      // Actually do the seek.
      long newPositionUs = newPeriodId.isAd() ? 0 : newContentPositionUs;
      long seekedToPositionUs = seekToPeriodPosition(newPeriodId, newPositionUs);
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              newPeriodId, seekedToPositionUs, newContentPositionUs, getTotalBufferedDurationUs());
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private long getMaxRendererReadPositionUs() {
    MediaPeriodHolder readingHolder = queue.getReadingPeriod();
    if (readingHolder == null) {
      return 0;
    }
    long maxReadPositionUs = readingHolder.getRendererOffset();
    for (int i = 0; i < renderers.length; i++) {
      if (renderers[i].getState() == Renderer.STATE_DISABLED
          || renderers[i].getStream() != readingHolder.sampleStreams[i]) {
        // Ignore disabled renderers and renderers with sample streams from previous periods.
        continue;
      }
      long readingPositionUs = renderers[i].getReadingPositionUs();
      if (readingPositionUs == C.TIME_END_OF_SOURCE) {
        return C.TIME_END_OF_SOURCE;
      } else {
        maxReadPositionUs = Math.max(readingPositionUs, maxReadPositionUs);
      }
    }
    return maxReadPositionUs;
  }

  private void handleSourceInfoRefreshEndedPlayback() {
    setState(Player.STATE_ENDED);
    // Reset, but retain the source so that it can still be used should a seek occur.
    resetInternal(
        /* resetRenderers= */ false,
        /* releaseMediaSource= */ false,
        /* resetPosition= */ true,
        /* resetState= */ false);
  }

  /**
   * Given a period index into an old timeline, finds the first subsequent period that also exists
   * in a new timeline. The uid of this period in the new timeline is returned.
   *
   * @param oldPeriodUid The index of the period in the old timeline.
   * @param oldTimeline The old timeline.
   * @param newTimeline The new timeline.
   * @return The uid in the new timeline of the first subsequent period, or null if no such period
   *     was found.
   */
  private @Nullable Object resolveSubsequentPeriod(
      Object oldPeriodUid, Timeline oldTimeline, Timeline newTimeline) {
    int oldPeriodIndex = oldTimeline.getIndexOfPeriod(oldPeriodUid);
    int newPeriodIndex = C.INDEX_UNSET;
    int maxIterations = oldTimeline.getPeriodCount();
    for (int i = 0; i < maxIterations && newPeriodIndex == C.INDEX_UNSET; i++) {
      oldPeriodIndex = oldTimeline.getNextPeriodIndex(oldPeriodIndex, period, window, repeatMode,
          shuffleModeEnabled);
      if (oldPeriodIndex == C.INDEX_UNSET) {
        // We've reached the end of the old timeline.
        break;
      }
      newPeriodIndex = newTimeline.getIndexOfPeriod(oldTimeline.getUidOfPeriod(oldPeriodIndex));
    }
    return newPeriodIndex == C.INDEX_UNSET ? null : newTimeline.getUidOfPeriod(newPeriodIndex);
  }

  /**
   * Converts a {@link SeekPosition} into the corresponding (periodUid, periodPositionUs) for the
   * internal timeline.
   *
   * @param seekPosition The position to resolve.
   * @param trySubsequentPeriods Whether the position can be resolved to a subsequent matching
   *     period if the original period is no longer available.
   * @return The resolved position, or null if resolution was not successful.
   * @throws IllegalSeekPositionException If the window index of the seek position is outside the
   *     bounds of the timeline.
   */
  private Pair<Object, Long> resolveSeekPosition(
      SeekPosition seekPosition, boolean trySubsequentPeriods) {
    Timeline timeline = playbackInfo.timeline;
    Timeline seekTimeline = seekPosition.timeline;
    if (timeline.isEmpty()) {
      // We don't have a valid timeline yet, so we can't resolve the position.
      return null;
    }
    if (seekTimeline.isEmpty()) {
      // The application performed a blind seek with an empty timeline (most likely based on
      // knowledge of what the future timeline will be). Use the internal timeline.
      seekTimeline = timeline;
    }
    // Map the SeekPosition to a position in the corresponding timeline.
    Pair<Object, Long> periodPosition;
    try {
      periodPosition = seekTimeline.getPeriodPosition(window, period, seekPosition.windowIndex,
          seekPosition.windowPositionUs);
    } catch (IndexOutOfBoundsException e) {
      // The window index of the seek position was outside the bounds of the timeline.
      return null;
    }
    if (timeline == seekTimeline) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPosition;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(periodPosition.first);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      return periodPosition;
    }
    if (trySubsequentPeriods) {
      // Try and find a subsequent period from the seek timeline in the internal timeline.
      Object periodUid = resolveSubsequentPeriod(periodPosition.first, seekTimeline, timeline);
      if (periodUid != null) {
        // We found one. Map the SeekPosition onto the corresponding default position.
        return getPeriodPosition(
            timeline, timeline.getPeriod(periodIndex, period).windowIndex, C.TIME_UNSET);
      }
    }
    // We didn't find one. Give up.
    return null;
  }

  /**
   * Calls {@link Timeline#getPeriodPosition(Timeline.Window, Timeline.Period, int, long)} using the
   * current timeline.
   */
  private Pair<Object, Long> getPeriodPosition(
      Timeline timeline, int windowIndex, long windowPositionUs) {
    return timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
  }

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (mediaSource == null) {
      // The player has no media source yet.
      return;
    }
    if (pendingPrepareCount > 0) {
      // We're waiting to get information about periods.
      mediaSource.maybeThrowSourceInfoRefreshError();
      return;
    }

    // Update the loading period if required.
    maybeUpdateLoadingPeriod();
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder == null || loadingPeriodHolder.isFullyBuffered()) {
      setIsLoading(false);
    } else if (!playbackInfo.isLoading) {
      maybeContinueLoading();
    }

    if (!queue.hasPlayingPeriod()) {
      // We're waiting for the first period to be prepared.
      return;
    }

    // Advance the playing period if necessary.
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    boolean advancedPlayingPeriod = false;
    while (playWhenReady
        && playingPeriodHolder != readingPeriodHolder
        && rendererPositionUs >= playingPeriodHolder.getNext().getStartPositionRendererTime()) {
      // All enabled renderers' streams have been read to the end, and the playback position reached
      // the end of the playing period, so advance playback to the next period.
      if (advancedPlayingPeriod) {
        // If we advance more than one period at a time, notify listeners after each update.
        maybeNotifyPlaybackInfoChanged();
      }
      int discontinuityReason =
          playingPeriodHolder.info.isLastInTimelinePeriod
              ? Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
              : Player.DISCONTINUITY_REASON_AD_INSERTION;
      MediaPeriodHolder oldPlayingPeriodHolder = playingPeriodHolder;
      playingPeriodHolder = queue.advancePlayingPeriod();
      updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              playingPeriodHolder.info.id,
              playingPeriodHolder.info.startPositionUs,
              playingPeriodHolder.info.contentPositionUs,
              getTotalBufferedDurationUs());
      playbackInfoUpdate.setPositionDiscontinuity(discontinuityReason);
      updatePlaybackPositions();
      advancedPlayingPeriod = true;
    }

    if (readingPeriodHolder.info.isFinal) {
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
        // Defer setting the stream as final until the renderer has actually consumed the whole
        // stream in case of playlist changes that cause the stream to be no longer final.
        if (sampleStream != null && renderer.getStream() == sampleStream
            && renderer.hasReadStreamToEnd()) {
          renderer.setCurrentStreamFinal();
        }
      }
      return;
    }

    // Advance the reading period if necessary.
    if (readingPeriodHolder.getNext() == null) {
      // We don't have a successor to advance the reading period to.
      return;
    }

    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
      if (renderer.getStream() != sampleStream
          || (sampleStream != null && !renderer.hasReadStreamToEnd())) {
        // The current reading period is still being read by at least one renderer.
        return;
      }
    }

    if (!readingPeriodHolder.getNext().prepared) {
      // The successor is not prepared yet.
      maybeThrowPeriodPrepareError();
      return;
    }

    TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();
    readingPeriodHolder = queue.advanceReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();

    boolean initialDiscontinuity =
        readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET;
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      boolean rendererWasEnabled = oldTrackSelectorResult.isRendererEnabled(i);
      if (!rendererWasEnabled) {
        // The renderer was disabled and will be enabled when we play the next period.
      } else if (initialDiscontinuity) {
        // The new period starts with a discontinuity, so the renderer will play out all data then
        // be disabled and re-enabled when it starts playing the next period.
        renderer.setCurrentStreamFinal();
      } else if (!renderer.isCurrentStreamFinal()) {
        TrackSelection newSelection = newTrackSelectorResult.selections.get(i);
        boolean newRendererEnabled = newTrackSelectorResult.isRendererEnabled(i);
        boolean isNoSampleRenderer = rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE;
        RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[i];
        RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[i];
        if (newRendererEnabled && newConfig.equals(oldConfig) && !isNoSampleRenderer) {
          // Replace the renderer's SampleStream so the transition to playing the next period can
          // be seamless.
          // This should be avoided for no-sample renderer, because skipping ahead for such
          // renderer doesn't have any benefit (the renderer does not consume the sample stream),
          // and it will change the provided rendererOffsetUs while the renderer is still
          // rendering from the playing media period.
          Format[] formats = getFormats(newSelection);
          renderer.replaceStream(formats, readingPeriodHolder.sampleStreams[i],
              readingPeriodHolder.getRendererOffset());
        } else {
          // The renderer will be disabled when transitioning to playing the next period, because
          // there's no new selection, or because a configuration change is required, or because
          // it's a no-sample renderer for which rendererOffsetUs should be updated only when
          // starting to play the next period. Mark the SampleStream as final to play out any
          // remaining data.
          renderer.setCurrentStreamFinal();
        }
      }
    }
  }

  private void maybeUpdateLoadingPeriod() throws IOException {
    queue.reevaluateBuffer(rendererPositionUs);
    if (queue.shouldLoadNextMediaPeriod()) {
      MediaPeriodInfo info = queue.getNextMediaPeriodInfo(rendererPositionUs, playbackInfo);
      if (info == null) {
        maybeThrowSourceInfoRefreshError();
      } else {
        MediaPeriod mediaPeriod =
            queue.enqueueNextMediaPeriod(
                rendererCapabilities,
                trackSelector,
                loadControl.getAllocator(),
                mediaSource,
                info);
        mediaPeriod.prepare(this, info.startPositionUs);
        setIsLoading(true);
        handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
      }
    }
  }

  private void handlePeriodPrepared(MediaPeriod mediaPeriod) throws ExoPlaybackException {
    if (!queue.isLoading(mediaPeriod)) {
      // Stale event.
      return;
    }
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    loadingPeriodHolder.handlePrepared(
        mediaClock.getPlaybackParameters().speed, playbackInfo.timeline);
    updateLoadControlTrackSelection(
        loadingPeriodHolder.getTrackGroups(), loadingPeriodHolder.getTrackSelectorResult());
    if (!queue.hasPlayingPeriod()) {
      // This is the first prepared period, so start playing it.
      MediaPeriodHolder playingPeriodHolder = queue.advancePlayingPeriod();
      resetRendererPosition(playingPeriodHolder.info.startPositionUs);
      updatePlayingPeriodRenderers(/* oldPlayingPeriodHolder= */ null);
    }
    maybeContinueLoading();
  }

  private void handleContinueLoadingRequested(MediaPeriod mediaPeriod) {
    if (!queue.isLoading(mediaPeriod)) {
      // Stale event.
      return;
    }
    queue.reevaluateBuffer(rendererPositionUs);
    maybeContinueLoading();
  }

  private void handlePlaybackParameters(PlaybackParameters playbackParameters)
      throws ExoPlaybackException {
    eventHandler.obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED, playbackParameters).sendToTarget();
    updateTrackSelectionPlaybackSpeed(playbackParameters.speed);
    for (Renderer renderer : renderers) {
      if (renderer != null) {
        renderer.setOperatingRate(playbackParameters.speed);
      }
    }
  }

  private void maybeContinueLoading() {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    long nextLoadPositionUs = loadingPeriodHolder.getNextLoadPositionUs();
    if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
      setIsLoading(false);
      return;
    }
    long bufferedDurationUs =
        getTotalBufferedDurationUs(/* bufferedPositionInLoadingPeriodUs= */ nextLoadPositionUs);
    boolean continueLoading =
        loadControl.shouldContinueLoading(
            bufferedDurationUs, mediaClock.getPlaybackParameters().speed);
    setIsLoading(continueLoading);
    if (continueLoading) {
      loadingPeriodHolder.continueLoading(rendererPositionUs);
    }
  }

  @SuppressWarnings("ParameterNotNullable")
  private void updatePlayingPeriodRenderers(@Nullable MediaPeriodHolder oldPlayingPeriodHolder)
      throws ExoPlaybackException {
    MediaPeriodHolder newPlayingPeriodHolder = queue.getPlayingPeriod();
    if (newPlayingPeriodHolder == null || oldPlayingPeriodHolder == newPlayingPeriodHolder) {
      return;
    }
    int enabledRendererCount = 0;
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
      if (newPlayingPeriodHolder.getTrackSelectorResult().isRendererEnabled(i)) {
        enabledRendererCount++;
      }
      if (rendererWasEnabledFlags[i]
          && (!newPlayingPeriodHolder.getTrackSelectorResult().isRendererEnabled(i)
              || (renderer.isCurrentStreamFinal()
                  && renderer.getStream() == oldPlayingPeriodHolder.sampleStreams[i]))) {
        // The renderer should be disabled before playing the next period, either because it's not
        // needed to play the next period, or because we need to re-enable it as its current stream
        // is final and it's not reading ahead.
        disableRenderer(renderer);
      }
    }
    playbackInfo =
        playbackInfo.copyWithTrackInfo(
            newPlayingPeriodHolder.getTrackGroups(),
            newPlayingPeriodHolder.getTrackSelectorResult());
    enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int totalEnabledRendererCount)
      throws ExoPlaybackException {
    enabledRenderers = new Renderer[totalEnabledRendererCount];
    int enabledRendererCount = 0;
    TrackSelectorResult trackSelectorResult = queue.getPlayingPeriod().getTrackSelectorResult();
    // Reset all disabled renderers before enabling any new ones. This makes sure resources released
    // by the disabled renderers will be available to renderers that are being enabled.
    for (int i = 0; i < renderers.length; i++) {
      if (!trackSelectorResult.isRendererEnabled(i)) {
        renderers[i].reset();
      }
    }
    // Enable the renderers.
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectorResult.isRendererEnabled(i)) {
        enableRenderer(i, rendererWasEnabledFlags[i], enabledRendererCount++);
      }
    }
  }

  private void enableRenderer(
      int rendererIndex, boolean wasRendererEnabled, int enabledRendererIndex)
      throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    Renderer renderer = renderers[rendererIndex];
    enabledRenderers[enabledRendererIndex] = renderer;
    if (renderer.getState() == Renderer.STATE_DISABLED) {
      TrackSelectorResult trackSelectorResult = playingPeriodHolder.getTrackSelectorResult();
      RendererConfiguration rendererConfiguration =
          trackSelectorResult.rendererConfigurations[rendererIndex];
      TrackSelection newSelection = trackSelectorResult.selections.get(rendererIndex);
      Format[] formats = getFormats(newSelection);
      // The renderer needs enabling with its new track selection.
      boolean playing = playWhenReady && playbackInfo.playbackState == Player.STATE_READY;
      // Consider as joining only if the renderer was previously disabled.
      boolean joining = !wasRendererEnabled && playing;
      // Enable the renderer.
      renderer.enable(rendererConfiguration, formats,
          playingPeriodHolder.sampleStreams[rendererIndex], rendererPositionUs,
          joining, playingPeriodHolder.getRendererOffset());
      mediaClock.onRendererEnabled(renderer);
      // Start the renderer if playing.
      if (playing) {
        renderer.start();
      }
    }
  }

  private boolean rendererWaitingForNextStream(Renderer renderer) {
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    MediaPeriodHolder nextPeriodHolder = readingPeriodHolder.getNext();
    return nextPeriodHolder != null && nextPeriodHolder.prepared && renderer.hasReadStreamToEnd();
  }

  private void handleLoadingMediaPeriodChanged(boolean loadingTrackSelectionChanged) {
    MediaPeriodHolder loadingMediaPeriodHolder = queue.getLoadingPeriod();
    MediaPeriodId loadingMediaPeriodId =
        loadingMediaPeriodHolder == null ? playbackInfo.periodId : loadingMediaPeriodHolder.info.id;
    boolean loadingMediaPeriodChanged =
        !playbackInfo.loadingMediaPeriodId.equals(loadingMediaPeriodId);
    if (loadingMediaPeriodChanged) {
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(loadingMediaPeriodId);
    }
    playbackInfo.bufferedPositionUs =
        loadingMediaPeriodHolder == null
            ? playbackInfo.positionUs
            : loadingMediaPeriodHolder.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();
    if ((loadingMediaPeriodChanged || loadingTrackSelectionChanged)
        && loadingMediaPeriodHolder != null
        && loadingMediaPeriodHolder.prepared) {
      updateLoadControlTrackSelection(
          loadingMediaPeriodHolder.getTrackGroups(),
          loadingMediaPeriodHolder.getTrackSelectorResult());
    }
  }

  private long getTotalBufferedDurationUs() {
    return getTotalBufferedDurationUs(playbackInfo.bufferedPositionUs);
  }

  private long getTotalBufferedDurationUs(long bufferedPositionInLoadingPeriodUs) {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder == null) {
      return 0;
    }
    long totalBufferedDurationUs =
        bufferedPositionInLoadingPeriodUs - loadingPeriodHolder.toPeriodTime(rendererPositionUs);
    return Math.max(0, totalBufferedDurationUs);
  }

  private void updateLoadControlTrackSelection(
      TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
    loadControl.onTracksSelected(renderers, trackGroups, trackSelectorResult.selections);
  }

  private static Format[] getFormats(TrackSelection newSelection) {
    // Build an array of formats contained by the selection.
    int length = newSelection != null ? newSelection.length() : 0;
    Format[] formats = new Format[length];
    for (int i = 0; i < length; i++) {
      formats[i] = newSelection.getFormat(i);
    }
    return formats;
  }

  private static final class SeekPosition {

    public final Timeline timeline;
    public final int windowIndex;
    public final long windowPositionUs;

    public SeekPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.windowPositionUs = windowPositionUs;
    }
  }

  private static final class PendingMessageInfo implements Comparable<PendingMessageInfo> {

    public final PlayerMessage message;

    public int resolvedPeriodIndex;
    public long resolvedPeriodTimeUs;
    public @Nullable Object resolvedPeriodUid;

    public PendingMessageInfo(PlayerMessage message) {
      this.message = message;
    }

    public void setResolvedPosition(int periodIndex, long periodTimeUs, Object periodUid) {
      resolvedPeriodIndex = periodIndex;
      resolvedPeriodTimeUs = periodTimeUs;
      resolvedPeriodUid = periodUid;
    }

    @Override
    public int compareTo(@NonNull PendingMessageInfo other) {
      if ((resolvedPeriodUid == null) != (other.resolvedPeriodUid == null)) {
        // PendingMessageInfos with a resolved period position are always smaller.
        return resolvedPeriodUid != null ? -1 : 1;
      }
      if (resolvedPeriodUid == null) {
        // Don't sort message with unresolved positions.
        return 0;
      }
      // Sort resolved media times by period index and then by period position.
      int comparePeriodIndex = resolvedPeriodIndex - other.resolvedPeriodIndex;
      if (comparePeriodIndex != 0) {
        return comparePeriodIndex;
      }
      return Util.compareLong(resolvedPeriodTimeUs, other.resolvedPeriodTimeUs);
    }
  }

  private static final class MediaSourceRefreshInfo {

    public final MediaSource source;
    public final Timeline timeline;
    public final Object manifest;

    public MediaSourceRefreshInfo(MediaSource source, Timeline timeline, Object manifest) {
      this.source = source;
      this.timeline = timeline;
      this.manifest = manifest;
    }
  }

  private static final class PlaybackInfoUpdate {

    private PlaybackInfo lastPlaybackInfo;
    private int operationAcks;
    private boolean positionDiscontinuity;
    private @DiscontinuityReason int discontinuityReason;

    public boolean hasPendingUpdate(PlaybackInfo playbackInfo) {
      return playbackInfo != lastPlaybackInfo || operationAcks > 0 || positionDiscontinuity;
    }

    public void reset(PlaybackInfo playbackInfo) {
      lastPlaybackInfo = playbackInfo;
      operationAcks = 0;
      positionDiscontinuity = false;
    }

    public void incrementPendingOperationAcks(int operationAcks) {
      this.operationAcks += operationAcks;
    }

    public void setPositionDiscontinuity(@DiscontinuityReason int discontinuityReason) {
      if (positionDiscontinuity
          && this.discontinuityReason != Player.DISCONTINUITY_REASON_INTERNAL) {
        // We always prefer non-internal discontinuity reasons. We also assume that we won't report
        // more than one non-internal discontinuity per message iteration.
        Assertions.checkArgument(discontinuityReason == Player.DISCONTINUITY_REASON_INTERNAL);
        return;
      }
      positionDiscontinuity = true;
      this.discontinuityReason = discontinuityReason;
    }
  }

}
