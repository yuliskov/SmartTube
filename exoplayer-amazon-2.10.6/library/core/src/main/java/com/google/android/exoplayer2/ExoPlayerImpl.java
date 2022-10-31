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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An {@link ExoPlayer} implementation. Instances can be obtained from {@link ExoPlayerFactory}. */
/* package */ final class ExoPlayerImpl extends BasePlayer implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  /**
   * This empty track selector result can only be used for {@link PlaybackInfo#trackSelectorResult}
   * when the player does not have any track selection made (such as when player is reset, or when
   * player seeks to an unprepared period). It will not be used as result of any {@link
   * TrackSelector#selectTracks(RendererCapabilities[], TrackGroupArray, MediaPeriodId, Timeline)}
   * operation.
   */
  /* package */ final TrackSelectorResult emptyTrackSelectorResult;

  private final Renderer[] renderers;
  private final TrackSelector trackSelector;
  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final Handler internalPlayerHandler;
  private final CopyOnWriteArrayList<ListenerHolder> listeners;
  private final Timeline.Period period;
  private final ArrayDeque<Runnable> pendingListenerNotifications;

  private MediaSource mediaSource;
  private boolean playWhenReady;
  @PlaybackSuppressionReason private int playbackSuppressionReason;
  @RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingOperationAcks;
  private boolean hasPendingPrepare;
  private boolean hasPendingSeek;
  private boolean foregroundMode;
  private PlaybackParameters playbackParameters;
  private SeekParameters seekParameters;
  private @Nullable ExoPlaybackException playbackError;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private int maskingPeriodIndex;
  private long maskingWindowPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param clock The {@link Clock} that will be used by the instance.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      Clock clock,
      Looper looper) {
    Log.i(TAG, "Init " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    Assertions.checkState(renderers.length > 0);
    this.renderers = Assertions.checkNotNull(renderers);
    this.trackSelector = Assertions.checkNotNull(trackSelector);
    this.playWhenReady = false;
    this.repeatMode = Player.REPEAT_MODE_OFF;
    this.shuffleModeEnabled = false;
    this.listeners = new CopyOnWriteArrayList<>();
    emptyTrackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[renderers.length],
            new TrackSelection[renderers.length],
            null);
    period = new Timeline.Period();
    playbackParameters = PlaybackParameters.DEFAULT;
    seekParameters = SeekParameters.DEFAULT;
    playbackSuppressionReason = PLAYBACK_SUPPRESSION_REASON_NONE;
    eventHandler =
        new Handler(looper) {
          @Override
          public void handleMessage(Message msg) {
            ExoPlayerImpl.this.handleEvent(msg);
          }
        };
    playbackInfo = PlaybackInfo.createDummy(/* startPositionUs= */ 0, emptyTrackSelectorResult);
    pendingListenerNotifications = new ArrayDeque<>();
    internalPlayer =
        new ExoPlayerImplInternal(
            renderers,
            trackSelector,
            emptyTrackSelectorResult,
            loadControl,
            bandwidthMeter,
            playWhenReady,
            repeatMode,
            shuffleModeEnabled,
            eventHandler,
            clock);
    internalPlayerHandler = new Handler(internalPlayer.getPlaybackLooper());
  }

  @Override
  @Nullable
  public AudioComponent getAudioComponent() {
    return null;
  }

  @Override
  @Nullable
  public VideoComponent getVideoComponent() {
    return null;
  }

  @Override
  @Nullable
  public TextComponent getTextComponent() {
    return null;
  }

  @Override
  @Nullable
  public MetadataComponent getMetadataComponent() {
    return null;
  }

  @Override
  public Looper getPlaybackLooper() {
    return internalPlayer.getPlaybackLooper();
  }

  @Override
  public Looper getApplicationLooper() {
    return eventHandler.getLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    listeners.addIfAbsent(new ListenerHolder(listener));
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    for (ListenerHolder listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  @Override
  public int getPlaybackState() {
    return playbackInfo.playbackState;
  }

  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return playbackSuppressionReason;
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    return playbackError;
  }

  @Override
  public void retry() {
    if (mediaSource != null
        && (playbackError != null || playbackInfo.playbackState == Player.STATE_IDLE)) {
      prepare(mediaSource, /* resetPosition= */ false, /* resetState= */ false);
    }
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, /* resetPosition= */ true, /* resetState= */ true);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    playbackError = null;
    this.mediaSource = mediaSource;
    PlaybackInfo playbackInfo =
        getResetPlaybackInfo(
            resetPosition, resetState, /* playbackState= */ Player.STATE_BUFFERING);
    // Trigger internal prepare first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this prepare. The internal player can't change the playback info immediately
    // because it uses a callback.
    hasPendingPrepare = true;
    pendingOperationAcks++;
    internalPlayer.prepare(mediaSource, resetPosition, resetState);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        TIMELINE_CHANGE_REASON_RESET,
        /* seekProcessed= */ false);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    setPlayWhenReady(playWhenReady, PLAYBACK_SUPPRESSION_REASON_NONE);
  }

  public void setPlayWhenReady(
      boolean playWhenReady, @PlaybackSuppressionReason int playbackSuppressionReason) {
    boolean oldIsPlaying = isPlaying();
    boolean oldInternalPlayWhenReady =
        this.playWhenReady && this.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
    boolean internalPlayWhenReady =
        playWhenReady && playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
    if (oldInternalPlayWhenReady != internalPlayWhenReady) {
      internalPlayer.setPlayWhenReady(internalPlayWhenReady);
    }
    boolean playWhenReadyChanged = this.playWhenReady != playWhenReady;
    boolean suppressionReasonChanged = this.playbackSuppressionReason != playbackSuppressionReason;
    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = playbackSuppressionReason;
    boolean isPlaying = isPlaying();
    boolean isPlayingChanged = oldIsPlaying != isPlaying;
    if (playWhenReadyChanged || suppressionReasonChanged || isPlayingChanged) {
      int playbackState = playbackInfo.playbackState;
      notifyListeners(
          listener -> {
            if (playWhenReadyChanged) {
              listener.onPlayerStateChanged(playWhenReady, playbackState);
            }
            if (suppressionReasonChanged) {
              listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
            }
            if (isPlayingChanged) {
              listener.onIsPlayingChanged(isPlaying);
            }
          });
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (this.repeatMode != repeatMode) {
      this.repeatMode = repeatMode;
      internalPlayer.setRepeatMode(repeatMode);
      notifyListeners(listener -> listener.onRepeatModeChanged(repeatMode));
    }
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    if (this.shuffleModeEnabled != shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      internalPlayer.setShuffleModeEnabled(shuffleModeEnabled);
      notifyListeners(listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
    }
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return shuffleModeEnabled;
  }

  @Override
  public boolean isLoading() {
    return playbackInfo.isLoading;
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    Timeline timeline = playbackInfo.timeline;
    if (windowIndex < 0 || (!timeline.isEmpty() && windowIndex >= timeline.getWindowCount())) {
      throw new IllegalSeekPositionException(timeline, windowIndex, positionMs);
    }
    hasPendingSeek = true;
    pendingOperationAcks++;
    if (isPlayingAd()) {
      // TODO: Investigate adding support for seeking during ads. This is complicated to do in
      // general because the midroll ad preceding the seek destination must be played before the
      // content position can be played, if a different ad is playing at the moment.
      Log.w(TAG, "seekTo ignored because an ad is playing");
      eventHandler
          .obtainMessage(
              ExoPlayerImplInternal.MSG_PLAYBACK_INFO_CHANGED,
              /* operationAcks */ 1,
              /* positionDiscontinuityReason */ C.INDEX_UNSET,
              playbackInfo)
          .sendToTarget();
      return;
    }
    maskingWindowIndex = windowIndex;
    if (timeline.isEmpty()) {
      maskingWindowPositionMs = positionMs == C.TIME_UNSET ? 0 : positionMs;
      maskingPeriodIndex = 0;
    } else {
      long windowPositionUs = positionMs == C.TIME_UNSET
          ? timeline.getWindow(windowIndex, window).getDefaultPositionUs() : C.msToUs(positionMs);
      Pair<Object, Long> periodUidAndPosition =
          timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
      maskingWindowPositionMs = C.usToMs(windowPositionUs);
      maskingPeriodIndex = timeline.getIndexOfPeriod(periodUidAndPosition.first);
    }
    internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
    notifyListeners(listener -> listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK));
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    if (playbackParameters == null) {
      playbackParameters = PlaybackParameters.DEFAULT;
    }
    internalPlayer.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    if (seekParameters == null) {
      seekParameters = SeekParameters.DEFAULT;
    }
    if (!this.seekParameters.equals(seekParameters)) {
      this.seekParameters = seekParameters;
      internalPlayer.setSeekParameters(seekParameters);
    }
  }

  @Override
  public SeekParameters getSeekParameters() {
    return seekParameters;
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      internalPlayer.setForegroundMode(foregroundMode);
    }
  }

  @Override
  public void stop(boolean reset) {
    if (reset) {
      playbackError = null;
      mediaSource = null;
    }
    PlaybackInfo playbackInfo =
        getResetPlaybackInfo(
            /* resetPosition= */ reset,
            /* resetState= */ reset,
            /* playbackState= */ Player.STATE_IDLE);
    // Trigger internal stop first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this stop. The internal player can't change the playback info immediately
    // because it uses a callback.
    pendingOperationAcks++;
    internalPlayer.stop(reset);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        TIMELINE_CHANGE_REASON_RESET,
        /* seekProcessed= */ false);
  }

  @Override
  public void release() {
    Log.i(TAG, "Release " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "] ["
        + ExoPlayerLibraryInfo.registeredModules() + "]");
    mediaSource = null;
    internalPlayer.release();
    eventHandler.removeCallbacksAndMessages(null);
    playbackInfo =
        getResetPlaybackInfo(
            /* resetPosition= */ false,
            /* resetState= */ false,
            /* playbackState= */ Player.STATE_IDLE);
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void sendMessages(ExoPlayerMessage... messages) {
    for (ExoPlayerMessage message : messages) {
      createMessage(message.target).setType(message.messageType).setPayload(message.message).send();
    }
  }

  @Override
  public PlayerMessage createMessage(Target target) {
    return new PlayerMessage(
        internalPlayer,
        target,
        playbackInfo.timeline,
        getCurrentWindowIndex(),
        internalPlayerHandler);
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    List<PlayerMessage> playerMessages = new ArrayList<>();
    for (ExoPlayerMessage message : messages) {
      playerMessages.add(
          createMessage(message.target)
              .setType(message.messageType)
              .setPayload(message.message)
              .send());
    }
    boolean wasInterrupted = false;
    for (PlayerMessage message : playerMessages) {
      boolean blockMessage = true;
      while (blockMessage) {
        try {
          message.blockUntilDelivered();
          blockMessage = false;
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
    }
    if (wasInterrupted) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public int getCurrentPeriodIndex() {
    if (shouldMaskPosition()) {
      return maskingPeriodIndex;
    } else {
      return playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    }
  }

  @Override
  public int getCurrentWindowIndex() {
    if (shouldMaskPosition()) {
      return maskingWindowIndex;
    } else {
      return playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period)
          .windowIndex;
    }
  }

  @Override
  public long getDuration() {
    if (isPlayingAd()) {
      MediaPeriodId periodId = playbackInfo.periodId;
      playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
      long adDurationUs = period.getAdDurationUs(periodId.adGroupIndex, periodId.adIndexInAdGroup);
      return C.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    if (shouldMaskPosition()) {
      return maskingWindowPositionMs;
    } else if (playbackInfo.periodId.isAd()) {
      return C.usToMs(playbackInfo.positionUs);
    } else {
      return periodPositionUsToWindowPositionMs(playbackInfo.periodId, playbackInfo.positionUs);
    }
  }

  @Override
  public long getBufferedPosition() {
    if (isPlayingAd()) {
      return playbackInfo.loadingMediaPeriodId.equals(playbackInfo.periodId)
          ? C.usToMs(playbackInfo.bufferedPositionUs)
          : getDuration();
    }
    return getContentBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    return C.usToMs(playbackInfo.totalBufferedDurationUs);
  }

  @Override
  public boolean isPlayingAd() {
    return !shouldMaskPosition() && playbackInfo.periodId.isAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return isPlayingAd() ? playbackInfo.periodId.adGroupIndex : C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return isPlayingAd() ? playbackInfo.periodId.adIndexInAdGroup : C.INDEX_UNSET;
  }

  @Override
  public long getContentPosition() {
    if (isPlayingAd()) {
      playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
      return playbackInfo.contentPositionUs == C.TIME_UNSET
          ? playbackInfo.timeline.getWindow(getCurrentWindowIndex(), window).getDefaultPositionMs()
          : period.getPositionInWindowMs() + C.usToMs(playbackInfo.contentPositionUs);
    } else {
      return getCurrentPosition();
    }
  }

  @Override
  public long getContentBufferedPosition() {
    if (shouldMaskPosition()) {
      return maskingWindowPositionMs;
    }
    if (playbackInfo.loadingMediaPeriodId.windowSequenceNumber
        != playbackInfo.periodId.windowSequenceNumber) {
      return playbackInfo.timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }
    long contentBufferedPositionUs = playbackInfo.bufferedPositionUs;
    if (playbackInfo.loadingMediaPeriodId.isAd()) {
      Timeline.Period loadingPeriod =
          playbackInfo.timeline.getPeriodByUid(playbackInfo.loadingMediaPeriodId.periodUid, period);
      contentBufferedPositionUs =
          loadingPeriod.getAdGroupTimeUs(playbackInfo.loadingMediaPeriodId.adGroupIndex);
      if (contentBufferedPositionUs == C.TIME_END_OF_SOURCE) {
        contentBufferedPositionUs = loadingPeriod.durationUs;
      }
    }
    return periodPositionUsToWindowPositionMs(
        playbackInfo.loadingMediaPeriodId, contentBufferedPositionUs);
  }

  @Override
  public int getRendererCount() {
    return renderers.length;
  }

  @Override
  public int getRendererType(int index) {
    return renderers[index].getTrackType();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return playbackInfo.trackGroups;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return playbackInfo.trackSelectorResult.selections;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return playbackInfo.timeline;
  }

  @Override
  public Object getCurrentManifest() {
    return playbackInfo.manifest;
  }

  // Not private so it can be called from an inner class without going through a thunk method.
  /* package */ void handleEvent(Message msg) {
    switch (msg.what) {
      case ExoPlayerImplInternal.MSG_PLAYBACK_INFO_CHANGED:
        handlePlaybackInfo(
            (PlaybackInfo) msg.obj,
            /* operationAcks= */ msg.arg1,
            /* positionDiscontinuity= */ msg.arg2 != C.INDEX_UNSET,
            /* positionDiscontinuityReason= */ msg.arg2);
        break;
      case ExoPlayerImplInternal.MSG_PLAYBACK_PARAMETERS_CHANGED:
        PlaybackParameters playbackParameters = (PlaybackParameters) msg.obj;
        if (!this.playbackParameters.equals(playbackParameters)) {
          this.playbackParameters = playbackParameters;
          notifyListeners(listener -> listener.onPlaybackParametersChanged(playbackParameters));
        }
        break;
      case ExoPlayerImplInternal.MSG_ERROR:
        ExoPlaybackException playbackError = (ExoPlaybackException) msg.obj;
        this.playbackError = playbackError;
        notifyListeners(listener -> listener.onPlayerError(playbackError));
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private void handlePlaybackInfo(
      PlaybackInfo playbackInfo,
      int operationAcks,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason) {
    pendingOperationAcks -= operationAcks;
    if (pendingOperationAcks == 0) {
      if (playbackInfo.startPositionUs == C.TIME_UNSET) {
        // Replace internal unset start position with externally visible start position of zero.
        playbackInfo =
            playbackInfo.resetToNewPosition(
                playbackInfo.periodId, /* startPositionUs= */ 0, playbackInfo.contentPositionUs);
      }
      if (!this.playbackInfo.timeline.isEmpty() && playbackInfo.timeline.isEmpty()) {
        // Update the masking variables, which are used when the timeline becomes empty.
        maskingPeriodIndex = 0;
        maskingWindowIndex = 0;
        maskingWindowPositionMs = 0;
      }
      @Player.TimelineChangeReason
      int timelineChangeReason =
          hasPendingPrepare
              ? Player.TIMELINE_CHANGE_REASON_PREPARED
              : Player.TIMELINE_CHANGE_REASON_DYNAMIC;
      boolean seekProcessed = hasPendingSeek;
      hasPendingPrepare = false;
      hasPendingSeek = false;
      updatePlaybackInfo(
          playbackInfo,
          positionDiscontinuity,
          positionDiscontinuityReason,
          timelineChangeReason,
          seekProcessed);
    }
  }

  private PlaybackInfo getResetPlaybackInfo(
      boolean resetPosition, boolean resetState, int playbackState) {
    if (resetPosition) {
      maskingWindowIndex = 0;
      maskingPeriodIndex = 0;
      maskingWindowPositionMs = 0;
    } else {
      maskingWindowIndex = getCurrentWindowIndex();
      maskingPeriodIndex = getCurrentPeriodIndex();
      maskingWindowPositionMs = getCurrentPosition();
    }
    // Also reset period-based PlaybackInfo positions if resetting the state.
    resetPosition = resetPosition || resetState;
    MediaPeriodId mediaPeriodId =
        resetPosition
            ? playbackInfo.getDummyFirstMediaPeriodId(shuffleModeEnabled, window)
            : playbackInfo.periodId;
    long startPositionUs = resetPosition ? 0 : playbackInfo.positionUs;
    long contentPositionUs = resetPosition ? C.TIME_UNSET : playbackInfo.contentPositionUs;
    return new PlaybackInfo(
        resetState ? Timeline.EMPTY : playbackInfo.timeline,
        resetState ? null : playbackInfo.manifest,
        mediaPeriodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        /* isLoading= */ false,
        resetState ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
        resetState ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
        mediaPeriodId,
        startPositionUs,
        /* totalBufferedDurationUs= */ 0,
        startPositionUs);
  }

  private void updatePlaybackInfo(
      PlaybackInfo playbackInfo,
      boolean positionDiscontinuity,
      @Player.DiscontinuityReason int positionDiscontinuityReason,
      @Player.TimelineChangeReason int timelineChangeReason,
      boolean seekProcessed) {
    boolean previousIsPlaying = isPlaying();
    // Assign playback info immediately such that all getters return the right values.
    PlaybackInfo previousPlaybackInfo = this.playbackInfo;
    this.playbackInfo = playbackInfo;
    boolean isPlaying = isPlaying();
    notifyListeners(
        new PlaybackInfoUpdate(
            playbackInfo,
            previousPlaybackInfo,
            listeners,
            trackSelector,
            positionDiscontinuity,
            positionDiscontinuityReason,
            timelineChangeReason,
            seekProcessed,
            playWhenReady,
            /* isPlayingChanged= */ previousIsPlaying != isPlaying));
  }

  private void notifyListeners(ListenerInvocation listenerInvocation) {
    CopyOnWriteArrayList<ListenerHolder> listenerSnapshot = new CopyOnWriteArrayList<>(listeners);
    notifyListeners(() -> invokeAll(listenerSnapshot, listenerInvocation));
  }

  private void notifyListeners(Runnable listenerNotificationRunnable) {
    boolean isRunningRecursiveListenerNotification = !pendingListenerNotifications.isEmpty();
    pendingListenerNotifications.addLast(listenerNotificationRunnable);
    if (isRunningRecursiveListenerNotification) {
      return;
    }
    while (!pendingListenerNotifications.isEmpty()) {
      pendingListenerNotifications.peekFirst().run();
      pendingListenerNotifications.removeFirst();
    }
  }

  private long periodPositionUsToWindowPositionMs(MediaPeriodId periodId, long positionUs) {
    long positionMs = C.usToMs(positionUs);
    playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
    positionMs += period.getPositionInWindowMs();
    return positionMs;
  }

  private boolean shouldMaskPosition() {
    return playbackInfo.timeline.isEmpty() || pendingOperationAcks > 0;
  }

  private static final class PlaybackInfoUpdate implements Runnable {

    private final PlaybackInfo playbackInfo;
    private final CopyOnWriteArrayList<ListenerHolder> listenerSnapshot;
    private final TrackSelector trackSelector;
    private final boolean positionDiscontinuity;
    private final @Player.DiscontinuityReason int positionDiscontinuityReason;
    private final @Player.TimelineChangeReason int timelineChangeReason;
    private final boolean seekProcessed;
    private final boolean playbackStateChanged;
    private final boolean timelineOrManifestChanged;
    private final boolean isLoadingChanged;
    private final boolean trackSelectorResultChanged;
    private final boolean playWhenReady;
    private final boolean isPlayingChanged;

    public PlaybackInfoUpdate(
        PlaybackInfo playbackInfo,
        PlaybackInfo previousPlaybackInfo,
        CopyOnWriteArrayList<ListenerHolder> listeners,
        TrackSelector trackSelector,
        boolean positionDiscontinuity,
        @DiscontinuityReason int positionDiscontinuityReason,
        @TimelineChangeReason int timelineChangeReason,
        boolean seekProcessed,
        boolean playWhenReady,
        boolean isPlayingChanged) {
      this.playbackInfo = playbackInfo;
      this.listenerSnapshot = new CopyOnWriteArrayList<>(listeners);
      this.trackSelector = trackSelector;
      this.positionDiscontinuity = positionDiscontinuity;
      this.positionDiscontinuityReason = positionDiscontinuityReason;
      this.timelineChangeReason = timelineChangeReason;
      this.seekProcessed = seekProcessed;
      this.playWhenReady = playWhenReady;
      this.isPlayingChanged = isPlayingChanged;
      playbackStateChanged = previousPlaybackInfo.playbackState != playbackInfo.playbackState;
      timelineOrManifestChanged =
          previousPlaybackInfo.timeline != playbackInfo.timeline
              || previousPlaybackInfo.manifest != playbackInfo.manifest;
      isLoadingChanged = previousPlaybackInfo.isLoading != playbackInfo.isLoading;
      trackSelectorResultChanged =
          previousPlaybackInfo.trackSelectorResult != playbackInfo.trackSelectorResult;
    }

    @Override
    public void run() {
      if (timelineOrManifestChanged || timelineChangeReason == TIMELINE_CHANGE_REASON_PREPARED) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onTimelineChanged(
                    playbackInfo.timeline, playbackInfo.manifest, timelineChangeReason));
      }
      if (positionDiscontinuity) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onPositionDiscontinuity(positionDiscontinuityReason));
      }
      if (trackSelectorResultChanged) {
        trackSelector.onSelectionActivated(playbackInfo.trackSelectorResult.info);
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onTracksChanged(
                    playbackInfo.trackGroups, playbackInfo.trackSelectorResult.selections));
      }
      if (isLoadingChanged) {
        invokeAll(listenerSnapshot, listener -> listener.onLoadingChanged(playbackInfo.isLoading));
      }
      if (playbackStateChanged) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onPlayerStateChanged(playWhenReady, playbackInfo.playbackState));
      }
      if (isPlayingChanged) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onIsPlayingChanged(playbackInfo.playbackState == Player.STATE_READY));
      }
      if (seekProcessed) {
        invokeAll(listenerSnapshot, EventListener::onSeekProcessed);
      }
    }
  }

  private static void invokeAll(
      CopyOnWriteArrayList<ListenerHolder> listeners, ListenerInvocation listenerInvocation) {
    for (ListenerHolder listenerHolder : listeners) {
      listenerHolder.invoke(listenerInvocation);
    }
  }
}
