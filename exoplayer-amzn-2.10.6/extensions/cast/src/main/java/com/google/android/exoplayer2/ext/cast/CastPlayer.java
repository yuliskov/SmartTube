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
package com.google.android.exoplayer2.ext.cast;

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BasePlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.framework.media.RemoteMediaClient.MediaChannelResult;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link Player} implementation that communicates with a Cast receiver app.
 *
 * <p>The behavior of this class depends on the underlying Cast session, which is obtained from the
 * Cast context passed to {@link #CastPlayer}. To keep track of the session, {@link
 * #isCastSessionAvailable()} can be queried and {@link SessionAvailabilityListener} can be
 * implemented and attached to the player.
 *
 * <p>If no session is available, the player state will remain unchanged and calls to methods that
 * alter it will be ignored. Querying the player state is possible even when no session is
 * available, in which case, the last observed receiver app state is reported.
 *
 * <p>Methods should be called on the application's main thread.
 */
public final class CastPlayer extends BasePlayer {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.cast");
  }

  private static final String TAG = "CastPlayer";

  private static final int RENDERER_COUNT = 3;
  private static final int RENDERER_INDEX_VIDEO = 0;
  private static final int RENDERER_INDEX_AUDIO = 1;
  private static final int RENDERER_INDEX_TEXT = 2;
  private static final long PROGRESS_REPORT_PERIOD_MS = 1000;
  private static final TrackSelectionArray EMPTY_TRACK_SELECTION_ARRAY =
      new TrackSelectionArray(null, null, null);
  private static final long[] EMPTY_TRACK_ID_ARRAY = new long[0];

  private final CastContext castContext;
  // TODO: Allow custom implementations of CastTimelineTracker.
  private final CastTimelineTracker timelineTracker;
  private final Timeline.Period period;

  // Result callbacks.
  private final StatusListener statusListener;
  private final SeekResultCallback seekResultCallback;

  // Listeners and notification.
  private final CopyOnWriteArrayList<ListenerHolder> listeners;
  private final ArrayList<ListenerNotificationTask> notificationsBatch;
  private final ArrayDeque<ListenerNotificationTask> ongoingNotificationsTasks;
  @Nullable private SessionAvailabilityListener sessionAvailabilityListener;

  // Internal state.
  @Nullable private RemoteMediaClient remoteMediaClient;
  private CastTimeline currentTimeline;
  private TrackGroupArray currentTrackGroups;
  private TrackSelectionArray currentTrackSelection;
  private int playbackState;
  private int repeatMode;
  private int currentWindowIndex;
  private boolean playWhenReady;
  private long lastReportedPositionMs;
  private int pendingSeekCount;
  private int pendingSeekWindowIndex;
  private long pendingSeekPositionMs;
  private boolean waitingForInitialTimeline;

  /**
   * @param castContext The context from which the cast session is obtained.
   */
  public CastPlayer(CastContext castContext) {
    this.castContext = castContext;
    timelineTracker = new CastTimelineTracker();
    period = new Timeline.Period();
    statusListener = new StatusListener();
    seekResultCallback = new SeekResultCallback();
    listeners = new CopyOnWriteArrayList<>();
    notificationsBatch = new ArrayList<>();
    ongoingNotificationsTasks = new ArrayDeque<>();

    SessionManager sessionManager = castContext.getSessionManager();
    sessionManager.addSessionManagerListener(statusListener, CastSession.class);
    CastSession session = sessionManager.getCurrentCastSession();
    remoteMediaClient = session != null ? session.getRemoteMediaClient() : null;

    playbackState = STATE_IDLE;
    repeatMode = REPEAT_MODE_OFF;
    currentTimeline = CastTimeline.EMPTY_CAST_TIMELINE;
    currentTrackGroups = TrackGroupArray.EMPTY;
    currentTrackSelection = EMPTY_TRACK_SELECTION_ARRAY;
    pendingSeekWindowIndex = C.INDEX_UNSET;
    pendingSeekPositionMs = C.TIME_UNSET;
    updateInternalState();
  }

  // Media Queue manipulation methods.

  /**
   * Loads a single item media queue. If no session is available, does nothing.
   *
   * @param item The item to load.
   * @param positionMs The position at which the playback should start in milliseconds relative to
   *     the start of the item at {@code startIndex}. If {@link C#TIME_UNSET} is passed, playback
   *     starts at position 0.
   * @return The Cast {@code PendingResult}, or null if no session is available.
   */
  @Nullable
  public PendingResult<MediaChannelResult> loadItem(MediaQueueItem item, long positionMs) {
    return loadItems(new MediaQueueItem[] {item}, 0, positionMs, REPEAT_MODE_OFF);
  }

  /**
   * Loads a media queue. If no session is available, does nothing.
   *
   * @param items The items to load.
   * @param startIndex The index of the item at which playback should start.
   * @param positionMs The position at which the playback should start in milliseconds relative to
   *     the start of the item at {@code startIndex}. If {@link C#TIME_UNSET} is passed, playback
   *     starts at position 0.
   * @param repeatMode The repeat mode for the created media queue.
   * @return The Cast {@code PendingResult}, or null if no session is available.
   */
  @Nullable
  public PendingResult<MediaChannelResult> loadItems(
      MediaQueueItem[] items, int startIndex, long positionMs, @RepeatMode int repeatMode) {
    if (remoteMediaClient != null) {
      positionMs = positionMs != C.TIME_UNSET ? positionMs : 0;
      waitingForInitialTimeline = true;
      return remoteMediaClient.queueLoad(items, startIndex, getCastRepeatMode(repeatMode),
          positionMs, null);
    }
    return null;
  }

  /**
   * Appends a sequence of items to the media queue. If no media queue exists, does nothing.
   *
   * @param items The items to append.
   * @return The Cast {@code PendingResult}, or null if no media queue exists.
   */
  @Nullable
  public PendingResult<MediaChannelResult> addItems(MediaQueueItem... items) {
    return addItems(MediaQueueItem.INVALID_ITEM_ID, items);
  }

  /**
   * Inserts a sequence of items into the media queue. If no media queue or period with id {@code
   * periodId} exist, does nothing.
   *
   * @param periodId The id of the period ({@link #getCurrentTimeline}) that corresponds to the item
   *     that will follow immediately after the inserted items.
   * @param items The items to insert.
   * @return The Cast {@code PendingResult}, or null if no media queue or no period with id {@code
   *     periodId} exist.
   */
  @Nullable
  public PendingResult<MediaChannelResult> addItems(int periodId, MediaQueueItem... items) {
    if (getMediaStatus() != null && (periodId == MediaQueueItem.INVALID_ITEM_ID
        || currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET)) {
      return remoteMediaClient.queueInsertItems(items, periodId, null);
    }
    return null;
  }

  /**
   * Removes an item from the media queue. If no media queue or period with id {@code periodId}
   * exist, does nothing.
   *
   * @param periodId The id of the period ({@link #getCurrentTimeline}) that corresponds to the item
   *     to remove.
   * @return The Cast {@code PendingResult}, or null if no media queue or no period with id {@code
   *     periodId} exist.
   */
  @Nullable
  public PendingResult<MediaChannelResult> removeItem(int periodId) {
    if (getMediaStatus() != null && currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET) {
      return remoteMediaClient.queueRemoveItem(periodId, null);
    }
    return null;
  }

  /**
   * Moves an existing item within the media queue. If no media queue or period with id {@code
   * periodId} exist, does nothing.
   *
   * @param periodId The id of the period ({@link #getCurrentTimeline}) that corresponds to the item
   *     to move.
   * @param newIndex The target index of the item in the media queue. Must be in the range 0 &lt;=
   *     index &lt; {@link Timeline#getPeriodCount()}, as provided by {@link #getCurrentTimeline()}.
   * @return The Cast {@code PendingResult}, or null if no media queue or no period with id {@code
   *     periodId} exist.
   */
  @Nullable
  public PendingResult<MediaChannelResult> moveItem(int periodId, int newIndex) {
    Assertions.checkArgument(newIndex >= 0 && newIndex < currentTimeline.getPeriodCount());
    if (getMediaStatus() != null && currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET) {
      return remoteMediaClient.queueMoveItemToNewIndex(periodId, newIndex, null);
    }
    return null;
  }

  /**
   * Returns the item that corresponds to the period with the given id, or null if no media queue or
   * period with id {@code periodId} exist.
   *
   * @param periodId The id of the period ({@link #getCurrentTimeline}) that corresponds to the item
   *     to get.
   * @return The item that corresponds to the period with the given id, or null if no media queue or
   *     period with id {@code periodId} exist.
   */
  @Nullable
  public MediaQueueItem getItem(int periodId) {
    MediaStatus mediaStatus = getMediaStatus();
    return mediaStatus != null && currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET
        ? mediaStatus.getItemById(periodId) : null;
  }

  // CastSession methods.

  /**
   * Returns whether a cast session is available.
   */
  public boolean isCastSessionAvailable() {
    return remoteMediaClient != null;
  }

  /**
   * Sets a listener for updates on the cast session availability.
   *
   * @param listener The {@link SessionAvailabilityListener}, or null to clear the listener.
   */
  public void setSessionAvailabilityListener(@Nullable SessionAvailabilityListener listener) {
    sessionAvailabilityListener = listener;
  }

  // Player implementation.

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
  public Looper getApplicationLooper() {
    return Looper.getMainLooper();
  }

  @Override
  public void addListener(EventListener listener) {
    listeners.addIfAbsent(new ListenerHolder(listener));
  }

  @Override
  public void removeListener(EventListener listener) {
    for (ListenerHolder listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  @Override
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    return null;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (remoteMediaClient == null) {
      return;
    }
    if (playWhenReady) {
      remoteMediaClient.play();
    } else {
      remoteMediaClient.pause();
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    MediaStatus mediaStatus = getMediaStatus();
    // We assume the default position is 0. There is no support for seeking to the default position
    // in RemoteMediaClient.
    positionMs = positionMs != C.TIME_UNSET ? positionMs : 0;
    if (mediaStatus != null) {
      if (getCurrentWindowIndex() != windowIndex) {
        remoteMediaClient.queueJumpToItem((int) currentTimeline.getPeriod(windowIndex, period).uid,
            positionMs, null).setResultCallback(seekResultCallback);
      } else {
        remoteMediaClient.seek(positionMs).setResultCallback(seekResultCallback);
      }
      pendingSeekCount++;
      pendingSeekWindowIndex = windowIndex;
      pendingSeekPositionMs = positionMs;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK)));
    } else if (pendingSeekCount == 0) {
      notificationsBatch.add(new ListenerNotificationTask(EventListener::onSeekProcessed));
    }
    flushNotifications();
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    // Unsupported by the RemoteMediaClient API. Do nothing.
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return PlaybackParameters.DEFAULT;
  }

  @Override
  public void stop(boolean reset) {
    playbackState = STATE_IDLE;
    if (remoteMediaClient != null) {
      // TODO(b/69792021): Support or emulate stop without position reset.
      remoteMediaClient.stop();
    }
  }

  @Override
  public void release() {
    SessionManager sessionManager = castContext.getSessionManager();
    sessionManager.removeSessionManagerListener(statusListener, CastSession.class);
    sessionManager.endCurrentSession(false);
  }

  @Override
  public int getRendererCount() {
    // We assume there are three renderers: video, audio, and text.
    return RENDERER_COUNT;
  }

  @Override
  public int getRendererType(int index) {
    switch (index) {
      case RENDERER_INDEX_VIDEO:
        return C.TRACK_TYPE_VIDEO;
      case RENDERER_INDEX_AUDIO:
        return C.TRACK_TYPE_AUDIO;
      case RENDERER_INDEX_TEXT:
        return C.TRACK_TYPE_TEXT;
      default:
        throw new IndexOutOfBoundsException();
    }
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (remoteMediaClient != null) {
      remoteMediaClient.queueSetRepeatMode(getCastRepeatMode(repeatMode), null);
    }
  }

  @Override
  @RepeatMode public int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    // TODO: Support shuffle mode.
  }

  @Override
  public boolean getShuffleModeEnabled() {
    // TODO: Support shuffle mode.
    return false;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return currentTrackSelection;
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return currentTrackGroups;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return currentTimeline;
  }

  @Override
  @Nullable public Object getCurrentManifest() {
    return null;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return getCurrentWindowIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return pendingSeekWindowIndex != C.INDEX_UNSET ? pendingSeekWindowIndex : currentWindowIndex;
  }

  // TODO: Fill the cast timeline information with ProgressListener's duration updates.
  // See [Internal: b/65152553].
  @Override
  public long getDuration() {
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    return pendingSeekPositionMs != C.TIME_UNSET
        ? pendingSeekPositionMs
        : remoteMediaClient != null
            ? remoteMediaClient.getApproximateStreamPosition()
            : lastReportedPositionMs;
  }

  @Override
  public long getBufferedPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    long bufferedPosition = getBufferedPosition();
    long currentPosition = getCurrentPosition();
    return bufferedPosition == C.TIME_UNSET || currentPosition == C.TIME_UNSET
        ? 0
        : bufferedPosition - currentPosition;
  }

  @Override
  public boolean isPlayingAd() {
    return false;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return C.INDEX_UNSET;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  @Override
  public long getContentPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return getBufferedPosition();
  }

  // Internal methods.

  private void updateInternalState() {
    if (remoteMediaClient == null) {
      // There is no session. We leave the state of the player as it is now.
      return;
    }

    boolean wasPlaying = playbackState == Player.STATE_READY && playWhenReady;
    int playbackState = fetchPlaybackState(remoteMediaClient);
    boolean playWhenReady = !remoteMediaClient.isPaused();
    if (this.playbackState != playbackState
        || this.playWhenReady != playWhenReady) {
      this.playbackState = playbackState;
      this.playWhenReady = playWhenReady;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onPlayerStateChanged(this.playWhenReady, this.playbackState)));
    }
    boolean isPlaying = playbackState == Player.STATE_READY && playWhenReady;
    if (wasPlaying != isPlaying) {
      notificationsBatch.add(
          new ListenerNotificationTask(listener -> listener.onIsPlayingChanged(isPlaying)));
    }
    @RepeatMode int repeatMode = fetchRepeatMode(remoteMediaClient);
    if (this.repeatMode != repeatMode) {
      this.repeatMode = repeatMode;
      notificationsBatch.add(
          new ListenerNotificationTask(listener -> listener.onRepeatModeChanged(this.repeatMode)));
    }
    maybeUpdateTimelineAndNotify();

    int currentWindowIndex = C.INDEX_UNSET;
    MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
    if (currentItem != null) {
      currentWindowIndex = currentTimeline.getIndexOfPeriod(currentItem.getItemId());
    }
    if (currentWindowIndex == C.INDEX_UNSET) {
      // The timeline is empty. Fall back to index 0, which is what ExoPlayer would do.
      currentWindowIndex = 0;
    }
    if (this.currentWindowIndex != currentWindowIndex && pendingSeekCount == 0) {
      this.currentWindowIndex = currentWindowIndex;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener ->
                  listener.onPositionDiscontinuity(DISCONTINUITY_REASON_PERIOD_TRANSITION)));
    }
    if (updateTracksAndSelections()) {
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener -> listener.onTracksChanged(currentTrackGroups, currentTrackSelection)));
    }
    flushNotifications();
  }

  private void maybeUpdateTimelineAndNotify() {
    if (updateTimeline()) {
      @Player.TimelineChangeReason int reason = waitingForInitialTimeline
          ? Player.TIMELINE_CHANGE_REASON_PREPARED : Player.TIMELINE_CHANGE_REASON_DYNAMIC;
      waitingForInitialTimeline = false;
      notificationsBatch.add(
          new ListenerNotificationTask(
              listener ->
                  listener.onTimelineChanged(currentTimeline, /* manifest= */ null, reason)));
    }
  }

  /**
   * Updates the current timeline and returns whether it has changed.
   */
  private boolean updateTimeline() {
    CastTimeline oldTimeline = currentTimeline;
    MediaStatus status = getMediaStatus();
    currentTimeline =
        status != null
            ? timelineTracker.getCastTimeline(remoteMediaClient)
            : CastTimeline.EMPTY_CAST_TIMELINE;
    return !oldTimeline.equals(currentTimeline);
  }

  /**
   * Updates the internal tracks and selection and returns whether they have changed.
   */
  private boolean updateTracksAndSelections() {
    if (remoteMediaClient == null) {
      // There is no session. We leave the state of the player as it is now.
      return false;
    }

    MediaStatus mediaStatus = getMediaStatus();
    MediaInfo mediaInfo = mediaStatus != null ? mediaStatus.getMediaInfo() : null;
    List<MediaTrack> castMediaTracks = mediaInfo != null ? mediaInfo.getMediaTracks() : null;
    if (castMediaTracks == null || castMediaTracks.isEmpty()) {
      boolean hasChanged = !currentTrackGroups.isEmpty();
      currentTrackGroups = TrackGroupArray.EMPTY;
      currentTrackSelection = EMPTY_TRACK_SELECTION_ARRAY;
      return hasChanged;
    }
    long[] activeTrackIds = mediaStatus.getActiveTrackIds();
    if (activeTrackIds == null) {
      activeTrackIds = EMPTY_TRACK_ID_ARRAY;
    }

    TrackGroup[] trackGroups = new TrackGroup[castMediaTracks.size()];
    TrackSelection[] trackSelections = new TrackSelection[RENDERER_COUNT];
    for (int i = 0; i < castMediaTracks.size(); i++) {
      MediaTrack mediaTrack = castMediaTracks.get(i);
      trackGroups[i] = new TrackGroup(CastUtils.mediaTrackToFormat(mediaTrack));

      long id = mediaTrack.getId();
      int trackType = MimeTypes.getTrackType(mediaTrack.getContentType());
      int rendererIndex = getRendererIndexForTrackType(trackType);
      if (isTrackActive(id, activeTrackIds) && rendererIndex != C.INDEX_UNSET
          && trackSelections[rendererIndex] == null) {
        trackSelections[rendererIndex] = new FixedTrackSelection(trackGroups[i], 0);
      }
    }
    TrackGroupArray newTrackGroups = new TrackGroupArray(trackGroups);
    TrackSelectionArray newTrackSelections = new TrackSelectionArray(trackSelections);

    if (!newTrackGroups.equals(currentTrackGroups)
        || !newTrackSelections.equals(currentTrackSelection)) {
      currentTrackSelection = new TrackSelectionArray(trackSelections);
      currentTrackGroups = new TrackGroupArray(trackGroups);
      return true;
    }
    return false;
  }

  private void setRemoteMediaClient(@Nullable RemoteMediaClient remoteMediaClient) {
    if (this.remoteMediaClient == remoteMediaClient) {
      // Do nothing.
      return;
    }
    if (this.remoteMediaClient != null) {
      this.remoteMediaClient.removeListener(statusListener);
      this.remoteMediaClient.removeProgressListener(statusListener);
    }
    this.remoteMediaClient = remoteMediaClient;
    if (remoteMediaClient != null) {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionAvailable();
      }
      remoteMediaClient.addListener(statusListener);
      remoteMediaClient.addProgressListener(statusListener, PROGRESS_REPORT_PERIOD_MS);
      updateInternalState();
    } else {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionUnavailable();
      }
    }
  }

  @Nullable
  private MediaStatus getMediaStatus() {
    return remoteMediaClient != null ? remoteMediaClient.getMediaStatus() : null;
  }

  /**
   * Retrieves the playback state from {@code remoteMediaClient} and maps it into a {@link Player}
   * state
   */
  private static int fetchPlaybackState(RemoteMediaClient remoteMediaClient) {
    int receiverAppStatus = remoteMediaClient.getPlayerState();
    switch (receiverAppStatus) {
      case MediaStatus.PLAYER_STATE_BUFFERING:
        return STATE_BUFFERING;
      case MediaStatus.PLAYER_STATE_PLAYING:
      case MediaStatus.PLAYER_STATE_PAUSED:
        return STATE_READY;
      case MediaStatus.PLAYER_STATE_IDLE:
      case MediaStatus.PLAYER_STATE_UNKNOWN:
      default:
        return STATE_IDLE;
    }
  }

  /**
   * Retrieves the repeat mode from {@code remoteMediaClient} and maps it into a
   * {@link Player.RepeatMode}.
   */
  @RepeatMode
  private static int fetchRepeatMode(RemoteMediaClient remoteMediaClient) {
    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
    if (mediaStatus == null) {
      // No media session active, yet.
      return REPEAT_MODE_OFF;
    }
    int castRepeatMode = mediaStatus.getQueueRepeatMode();
    switch (castRepeatMode) {
      case MediaStatus.REPEAT_MODE_REPEAT_SINGLE:
        return REPEAT_MODE_ONE;
      case MediaStatus.REPEAT_MODE_REPEAT_ALL:
      case MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE:
        return REPEAT_MODE_ALL;
      case MediaStatus.REPEAT_MODE_REPEAT_OFF:
        return REPEAT_MODE_OFF;
      default:
        throw new IllegalStateException();
    }
  }

  private static boolean isTrackActive(long id, long[] activeTrackIds) {
    for (long activeTrackId : activeTrackIds) {
      if (activeTrackId == id) {
        return true;
      }
    }
    return false;
  }

  private static int getRendererIndexForTrackType(int trackType) {
    return trackType == C.TRACK_TYPE_VIDEO
        ? RENDERER_INDEX_VIDEO
        : trackType == C.TRACK_TYPE_AUDIO
            ? RENDERER_INDEX_AUDIO
            : trackType == C.TRACK_TYPE_TEXT ? RENDERER_INDEX_TEXT : C.INDEX_UNSET;
  }

  private static int getCastRepeatMode(@RepeatMode int repeatMode) {
    switch (repeatMode) {
      case REPEAT_MODE_ONE:
        return MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
      case REPEAT_MODE_ALL:
        return MediaStatus.REPEAT_MODE_REPEAT_ALL;
      case REPEAT_MODE_OFF:
        return MediaStatus.REPEAT_MODE_REPEAT_OFF;
      default:
        throw new IllegalArgumentException();
    }
  }

  private final class StatusListener implements RemoteMediaClient.Listener,
      SessionManagerListener<CastSession>, RemoteMediaClient.ProgressListener {

    // RemoteMediaClient.ProgressListener implementation.

    @Override
    public void onProgressUpdated(long progressMs, long unusedDurationMs) {
      lastReportedPositionMs = progressMs;
    }

    // RemoteMediaClient.Listener implementation.

    @Override
    public void onStatusUpdated() {
      updateInternalState();
    }

    @Override
    public void onMetadataUpdated() {}

    @Override
    public void onQueueStatusUpdated() {
      maybeUpdateTimelineAndNotify();
    }

    @Override
    public void onPreloadStatusUpdated() {}

    @Override
    public void onSendingRemoteMediaRequest() {}

    @Override
    public void onAdBreakStatusUpdated() {}

    // SessionManagerListener implementation.

    @Override
    public void onSessionStarted(CastSession castSession, String s) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionResumed(CastSession castSession, boolean b) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionEnded(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionSuspended(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionResumeFailed(CastSession castSession, int statusCode) {
      Log.e(TAG, "Session resume failed. Error code " + statusCode + ": "
          + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionStarting(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionStartFailed(CastSession castSession, int statusCode) {
      Log.e(TAG, "Session start failed. Error code " + statusCode + ": "
          + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionEnding(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionResuming(CastSession castSession, String s) {
      // Do nothing.
    }

  }

  // Internal methods.

  private void flushNotifications() {
    boolean recursiveNotification = !ongoingNotificationsTasks.isEmpty();
    ongoingNotificationsTasks.addAll(notificationsBatch);
    notificationsBatch.clear();
    if (recursiveNotification) {
      // This will be handled once the current notification task is finished.
      return;
    }
    while (!ongoingNotificationsTasks.isEmpty()) {
      ongoingNotificationsTasks.peekFirst().execute();
      ongoingNotificationsTasks.removeFirst();
    }
  }

  // Internal classes.

  private final class SeekResultCallback implements ResultCallback<MediaChannelResult> {

    @Override
    public void onResult(@NonNull MediaChannelResult result) {
      int statusCode = result.getStatus().getStatusCode();
      if (statusCode != CastStatusCodes.SUCCESS && statusCode != CastStatusCodes.REPLACED) {
        Log.e(TAG, "Seek failed. Error code " + statusCode + ": "
            + CastUtils.getLogString(statusCode));
      }
      if (--pendingSeekCount == 0) {
        pendingSeekWindowIndex = C.INDEX_UNSET;
        pendingSeekPositionMs = C.TIME_UNSET;
        notificationsBatch.add(new ListenerNotificationTask(EventListener::onSeekProcessed));
        flushNotifications();
      }
    }
  }

  private final class ListenerNotificationTask {

    private final Iterator<ListenerHolder> listenersSnapshot;
    private final ListenerInvocation listenerInvocation;

    private ListenerNotificationTask(ListenerInvocation listenerInvocation) {
      this.listenersSnapshot = listeners.iterator();
      this.listenerInvocation = listenerInvocation;
    }

    public void execute() {
      while (listenersSnapshot.hasNext()) {
        listenersSnapshot.next().invoke(listenerInvocation);
      }
    }
  }

}
