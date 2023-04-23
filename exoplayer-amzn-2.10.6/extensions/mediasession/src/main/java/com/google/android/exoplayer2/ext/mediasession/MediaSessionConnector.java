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
package com.google.android.exoplayer2.ext.mediasession;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects a {@link MediaSessionCompat} to a {@link Player}.
 *
 * <p>This connector does <em>not</em> call {@link MediaSessionCompat#setActive(boolean)}, and so
 * application code is responsible for making the session active when desired. A session must be
 * active for transport controls to be displayed (e.g. on the lock screen) and for it to receive
 * media button events.
 *
 * <p>The connector listens for actions sent by the media session's controller and implements these
 * actions by calling appropriate player methods. The playback state of the media session is
 * automatically synced with the player. The connector can also be optionally extended by providing
 * various collaborators:
 *
 * <ul>
 *   <li>Actions to initiate media playback ({@code PlaybackStateCompat#ACTION_PREPARE_*} and {@code
 *       PlaybackStateCompat#ACTION_PLAY_*}) can be handled by a {@link PlaybackPreparer} passed to
 *       {@link #setPlaybackPreparer(PlaybackPreparer)}.
 *   <li>Custom actions can be handled by passing one or more {@link CustomActionProvider}s to
 *       {@link #setCustomActionProviders(CustomActionProvider...)}.
 *   <li>To enable a media queue and navigation within it, you can set a {@link QueueNavigator} by
 *       calling {@link #setQueueNavigator(QueueNavigator)}. Use of {@link TimelineQueueNavigator}
 *       is recommended for most use cases.
 *   <li>To enable editing of the media queue, you can set a {@link QueueEditor} by calling {@link
 *       #setQueueEditor(QueueEditor)}.
 *   <li>A {@link MediaButtonEventHandler} can be set by calling {@link
 *       #setMediaButtonEventHandler(MediaButtonEventHandler)}. By default media button events are
 *       handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
 *   <li>An {@link ErrorMessageProvider} for providing human readable error messages and
 *       corresponding error codes can be set by calling {@link
 *       #setErrorMessageProvider(ErrorMessageProvider)}.
 *   <li>A {@link MediaMetadataProvider} can be set by calling {@link
 *       #setMediaMetadataProvider(MediaMetadataProvider)}. By default the {@link
 *       DefaultMediaMetadataProvider} is used.
 * </ul>
 */
public final class MediaSessionConnector {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.mediasession");
  }

  /** Playback actions supported by the connector. */
  @LongDef(
      flag = true,
      value = {
        PlaybackStateCompat.ACTION_PLAY_PAUSE,
        PlaybackStateCompat.ACTION_PLAY,
        PlaybackStateCompat.ACTION_PAUSE,
        PlaybackStateCompat.ACTION_SEEK_TO,
        PlaybackStateCompat.ACTION_FAST_FORWARD,
        PlaybackStateCompat.ACTION_REWIND,
        PlaybackStateCompat.ACTION_STOP,
        PlaybackStateCompat.ACTION_SET_REPEAT_MODE,
        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
      })
  @Retention(RetentionPolicy.SOURCE)
  public @interface PlaybackActions {}

  @PlaybackActions
  public static final long ALL_PLAYBACK_ACTIONS =
      PlaybackStateCompat.ACTION_PLAY_PAUSE
          | PlaybackStateCompat.ACTION_PLAY
          | PlaybackStateCompat.ACTION_PAUSE
          | PlaybackStateCompat.ACTION_SEEK_TO
          | PlaybackStateCompat.ACTION_FAST_FORWARD
          | PlaybackStateCompat.ACTION_REWIND
          | PlaybackStateCompat.ACTION_STOP
          | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
          | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;

  /** The default playback actions. */
  @PlaybackActions public static final long DEFAULT_PLAYBACK_ACTIONS = ALL_PLAYBACK_ACTIONS;

  /** The default fast forward increment, in milliseconds. */
  public static final int DEFAULT_FAST_FORWARD_MS = 15000;
  /** The default rewind increment, in milliseconds. */
  public static final int DEFAULT_REWIND_MS = 5000;

  /**
   * The name of the {@link PlaybackStateCompat} float extra with the value of {@link
   * PlaybackParameters#speed}.
   */
  public static final String EXTRAS_SPEED = "EXO_SPEED";
  /**
   * The name of the {@link PlaybackStateCompat} float extra with the value of {@link
   * PlaybackParameters#pitch}.
   */
  public static final String EXTRAS_PITCH = "EXO_PITCH";

  private static final long BASE_PLAYBACK_ACTIONS =
      PlaybackStateCompat.ACTION_PLAY_PAUSE
          | PlaybackStateCompat.ACTION_PLAY
          | PlaybackStateCompat.ACTION_PAUSE
          | PlaybackStateCompat.ACTION_STOP
          | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
          | PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
  private static final int BASE_MEDIA_SESSION_FLAGS =
      MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
          | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS;
  private static final int EDITOR_MEDIA_SESSION_FLAGS =
      BASE_MEDIA_SESSION_FLAGS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;

  private static final MediaMetadataCompat METADATA_EMPTY =
      new MediaMetadataCompat.Builder().build();

  /** Receiver of media commands sent by a media controller. */
  public interface CommandReceiver {
    /**
     * See {@link MediaSessionCompat.Callback#onCommand(String, Bundle, ResultReceiver)}. The
     * receiver may handle the command, but is not required to do so. Changes to the player should
     * be made via the {@link ControlDispatcher}.
     *
     * @param player The player connected to the media session.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     * @param command The command name.
     * @param extras Optional parameters for the command, may be null.
     * @param cb A result receiver to which a result may be sent by the command, may be null.
     * @return Whether the receiver handled the command.
     */
    boolean onCommand(
        Player player,
        ControlDispatcher controlDispatcher,
        String command,
        Bundle extras,
        ResultReceiver cb);
  }

  /** Interface to which playback preparation and play actions are delegated. */
  public interface PlaybackPreparer extends CommandReceiver {

    long ACTIONS =
        PlaybackStateCompat.ACTION_PREPARE
            | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PLAY_FROM_URI;

    /**
     * Returns the actions which are supported by the preparer. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_PREPARE}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_MEDIA_ID}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_SEARCH}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_URI}, {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_MEDIA_ID}, {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_SEARCH} and {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_URI}.
     *
     * @return The bitmask of the supported media actions.
     */
    long getSupportedPrepareActions();
    /**
     * See {@link MediaSessionCompat.Callback#onPrepare()}.
     *
     * @param playWhenReady Whether playback should be started after preparation.
     */
    void onPrepare(boolean playWhenReady);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromMediaId(String, Bundle)}.
     *
     * @param mediaId The media id of the media item to be prepared.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller.
     */
    void onPrepareFromMediaId(String mediaId, boolean playWhenReady, Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromSearch(String, Bundle)}.
     *
     * @param query The search query.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller.
     */
    void onPrepareFromSearch(String query, boolean playWhenReady, Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromUri(Uri, Bundle)}.
     *
     * @param uri The {@link Uri} of the media item to be prepared.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller.
     */
    void onPrepareFromUri(Uri uri, boolean playWhenReady, Bundle extras);
  }

  /**
   * Handles queue navigation actions, and updates the media session queue by calling {@code
   * MediaSessionCompat.setQueue()}.
   */
  public interface QueueNavigator extends CommandReceiver {

    long ACTIONS =
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    /**
     * Returns the actions which are supported by the navigator. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_SKIP_TO_QUEUE_ITEM}, {@link
     * PlaybackStateCompat#ACTION_SKIP_TO_NEXT}, {@link
     * PlaybackStateCompat#ACTION_SKIP_TO_PREVIOUS}.
     *
     * @param player The player connected to the media session.
     * @return The bitmask of the supported media actions.
     */
    long getSupportedQueueNavigatorActions(Player player);
    /**
     * Called when the timeline of the player has changed.
     *
     * @param player The player connected to the media session.
     */
    void onTimelineChanged(Player player);
    /**
     * Called when the current window index changed.
     *
     * @param player The player connected to the media session.
     */
    void onCurrentWindowIndexChanged(Player player);
    /**
     * Gets the id of the currently active queue item, or {@link
     * MediaSessionCompat.QueueItem#UNKNOWN_ID} if the active item is unknown.
     *
     * <p>To let the connector publish metadata for the active queue item, the queue item with the
     * returned id must be available in the list of items returned by {@link
     * MediaControllerCompat#getQueue()}.
     *
     * @param player The player connected to the media session.
     * @return The id of the active queue item.
     */
    long getActiveQueueItemId(@Nullable Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToPrevious()}.
     *
     * @param player The player connected to the media session.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     */
    void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToQueueItem(long)}.
     *
     * @param player The player connected to the media session.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     */
    void onSkipToQueueItem(Player player, ControlDispatcher controlDispatcher, long id);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToNext()}.
     *
     * @param player The player connected to the media session.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     */
    void onSkipToNext(Player player, ControlDispatcher controlDispatcher);
  }

  /** Handles media session queue edits. */
  public interface QueueEditor extends CommandReceiver {

    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description);
    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description, int
     * index)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description, int index);
    /**
     * See {@link MediaSessionCompat.Callback#onRemoveQueueItem(MediaDescriptionCompat
     * description)}.
     */
    void onRemoveQueueItem(Player player, MediaDescriptionCompat description);
  }

  /** Callback receiving a user rating for the active media item. */
  public interface RatingCallback extends CommandReceiver {

    /** See {@link MediaSessionCompat.Callback#onSetRating(RatingCompat)}. */
    void onSetRating(Player player, RatingCompat rating);

    /** See {@link MediaSessionCompat.Callback#onSetRating(RatingCompat, Bundle)}. */
    void onSetRating(Player player, RatingCompat rating, Bundle extras);
  }

  /** Handles a media button event. */
  public interface MediaButtonEventHandler {
    /**
     * See {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
     *
     * @param player The {@link Player}.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     * @param mediaButtonEvent The {@link Intent}.
     * @return True if the event was handled, false otherwise.
     */
    boolean onMediaButtonEvent(
        Player player, ControlDispatcher controlDispatcher, Intent mediaButtonEvent);
  }

  /**
   * Provides a {@link PlaybackStateCompat.CustomAction} to be published and handles the action when
   * sent by a media controller.
   */
  public interface CustomActionProvider {
    /**
     * Called when a custom action provided by this provider is sent to the media session.
     *
     * @param player The player connected to the media session.
     * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
     *     changes to the player.
     * @param action The name of the action which was sent by a media controller.
     * @param extras Optional extras sent by a media controller.
     */
    void onCustomAction(
        Player player, ControlDispatcher controlDispatcher, String action, Bundle extras);

    /**
     * Returns a {@link PlaybackStateCompat.CustomAction} which will be published to the media
     * session by the connector or {@code null} if this action should not be published at the given
     * player state.
     *
     * @param player The player connected to the media session.
     * @return The custom action to be included in the session playback state or {@code null}.
     */
    PlaybackStateCompat.CustomAction getCustomAction(Player player);
  }

  /** Provides a {@link MediaMetadataCompat} for a given player state. */
  public interface MediaMetadataProvider {
    /**
     * Gets the {@link MediaMetadataCompat} to be published to the session.
     *
     * <p>An app may need to load metadata resources like artwork bitmaps asynchronously. In such a
     * case the app should return a {@link MediaMetadataCompat} object that does not contain these
     * resources as a placeholder. The app should start an asynchronous operation to download the
     * bitmap and put it into a cache. Finally, the app should call {@link
     * #invalidateMediaSessionMetadata()}. This causes this callback to be called again and the app
     * can now return a {@link MediaMetadataCompat} object with all the resources included.
     *
     * @param player The player connected to the media session.
     * @return The {@link MediaMetadataCompat} to be published to the session.
     */
    MediaMetadataCompat getMetadata(Player player);
  }

  /** The wrapped {@link MediaSessionCompat}. */
  public final MediaSessionCompat mediaSession;

  private final Looper looper;
  private final ComponentListener componentListener;
  private final ArrayList<CommandReceiver> commandReceivers;
  private final ArrayList<CommandReceiver> customCommandReceivers;

  private ControlDispatcher controlDispatcher;
  private CustomActionProvider[] customActionProviders;
  private Map<String, CustomActionProvider> customActionMap;
  @Nullable private MediaMetadataProvider mediaMetadataProvider;
  @Nullable private Player player;
  @Nullable private ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider;
  @Nullable private Pair<Integer, CharSequence> customError;
  @Nullable private Bundle customErrorExtras;
  @Nullable private PlaybackPreparer playbackPreparer;
  @Nullable private QueueNavigator queueNavigator;
  @Nullable private QueueEditor queueEditor;
  @Nullable private RatingCallback ratingCallback;
  @Nullable private MediaButtonEventHandler mediaButtonEventHandler;

  private long enabledPlaybackActions;
  private int rewindMs;
  private int fastForwardMs;

  /**
   * Creates an instance.
   *
   * @param mediaSession The {@link MediaSessionCompat} to connect to.
   */
  public MediaSessionConnector(MediaSessionCompat mediaSession) {
    this.mediaSession = mediaSession;
    looper = Util.getLooper();
    componentListener = new ComponentListener();
    commandReceivers = new ArrayList<>();
    customCommandReceivers = new ArrayList<>();
    controlDispatcher = new DefaultControlDispatcher();
    customActionProviders = new CustomActionProvider[0];
    customActionMap = Collections.emptyMap();
    mediaMetadataProvider =
        new DefaultMediaMetadataProvider(
            mediaSession.getController(), /* metadataExtrasPrefix= */ null);
    enabledPlaybackActions = DEFAULT_PLAYBACK_ACTIONS;
    rewindMs = DEFAULT_REWIND_MS;
    fastForwardMs = DEFAULT_FAST_FORWARD_MS;
    mediaSession.setFlags(BASE_MEDIA_SESSION_FLAGS);
    mediaSession.setCallback(componentListener, new Handler(looper));
  }

  /**
   * Sets the player to be connected to the media session. Must be called on the same thread that is
   * used to access the player.
   *
   * @param player The player to be connected to the {@code MediaSession}, or {@code null} to
   *     disconnect the current player.
   */
  public void setPlayer(@Nullable Player player) {
    Assertions.checkArgument(player == null || player.getApplicationLooper() == looper);
    if (this.player != null) {
      this.player.removeListener(componentListener);
    }
    this.player = player;
    if (player != null) {
      player.addListener(componentListener);
    }
    invalidateMediaSessionPlaybackState();
    invalidateMediaSessionMetadata();
  }

  /**
   * Sets the {@link PlaybackPreparer}.
   *
   * @param playbackPreparer The {@link PlaybackPreparer}.
   */
  public void setPlaybackPreparer(@Nullable PlaybackPreparer playbackPreparer) {
    if (this.playbackPreparer != playbackPreparer) {
      unregisterCommandReceiver(this.playbackPreparer);
      this.playbackPreparer = playbackPreparer;
      registerCommandReceiver(playbackPreparer);
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}, or null to use {@link
   *     DefaultControlDispatcher}.
   */
  public void setControlDispatcher(@Nullable ControlDispatcher controlDispatcher) {
    if (this.controlDispatcher != controlDispatcher) {
      this.controlDispatcher =
          controlDispatcher == null ? new DefaultControlDispatcher() : controlDispatcher;
    }
  }

  /**
   * Sets the {@link MediaButtonEventHandler}. Pass {@code null} if the media button event should be
   * handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
   *
   * @param mediaButtonEventHandler The {@link MediaButtonEventHandler}, or null to let the event be
   *     handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
   */
  public void setMediaButtonEventHandler(
      @Nullable MediaButtonEventHandler mediaButtonEventHandler) {
    this.mediaButtonEventHandler = mediaButtonEventHandler;
  }

  /**
   * Sets the enabled playback actions.
   *
   * @param enabledPlaybackActions The enabled playback actions.
   */
  public void setEnabledPlaybackActions(@PlaybackActions long enabledPlaybackActions) {
    enabledPlaybackActions &= ALL_PLAYBACK_ACTIONS;
    if (this.enabledPlaybackActions != enabledPlaybackActions) {
      this.enabledPlaybackActions = enabledPlaybackActions;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds. A non-positive value will cause the
   *     rewind button to be disabled.
   */
  public void setRewindIncrementMs(int rewindMs) {
    if (this.rewindMs != rewindMs) {
      this.rewindMs = rewindMs;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds. A non-positive value will
   *     cause the fast forward button to be disabled.
   */
  public void setFastForwardIncrementMs(int fastForwardMs) {
    if (this.fastForwardMs != fastForwardMs) {
      this.fastForwardMs = fastForwardMs;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the optional {@link ErrorMessageProvider}.
   *
   * @param errorMessageProvider The error message provider.
   */
  public void setErrorMessageProvider(
      @Nullable ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider) {
    if (this.errorMessageProvider != errorMessageProvider) {
      this.errorMessageProvider = errorMessageProvider;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the {@link QueueNavigator} to handle queue navigation actions {@code ACTION_SKIP_TO_NEXT},
   * {@code ACTION_SKIP_TO_PREVIOUS} and {@code ACTION_SKIP_TO_QUEUE_ITEM}.
   *
   * @param queueNavigator The queue navigator.
   */
  public void setQueueNavigator(QueueNavigator queueNavigator) {
    if (this.queueNavigator != queueNavigator) {
      unregisterCommandReceiver(this.queueNavigator);
      this.queueNavigator = queueNavigator;
      registerCommandReceiver(queueNavigator);
    }
  }

  /**
   * Sets the {@link QueueEditor} to handle queue edits sent by the media controller.
   *
   * @param queueEditor The queue editor.
   */
  public void setQueueEditor(QueueEditor queueEditor) {
    if (this.queueEditor != queueEditor) {
      unregisterCommandReceiver(this.queueEditor);
      this.queueEditor = queueEditor;
      registerCommandReceiver(queueEditor);
      mediaSession.setFlags(
          queueEditor == null ? BASE_MEDIA_SESSION_FLAGS : EDITOR_MEDIA_SESSION_FLAGS);
    }
  }

  /**
   * Sets the {@link RatingCallback} to handle user ratings.
   *
   * @param ratingCallback The rating callback.
   */
  public void setRatingCallback(RatingCallback ratingCallback) {
    if (this.ratingCallback != ratingCallback) {
      unregisterCommandReceiver(this.ratingCallback);
      this.ratingCallback = ratingCallback;
      registerCommandReceiver(this.ratingCallback);
    }
  }

  /**
   * Sets a custom error on the session.
   *
   * <p>This sets the error code via {@link PlaybackStateCompat.Builder#setErrorMessage(int,
   * CharSequence)}. By default, the error code will be set to {@link
   * PlaybackStateCompat#ERROR_CODE_APP_ERROR}.
   *
   * @param message The error string to report or {@code null} to clear the error.
   */
  public void setCustomErrorMessage(@Nullable CharSequence message) {
    int code = (message == null) ? 0 : PlaybackStateCompat.ERROR_CODE_APP_ERROR;
    setCustomErrorMessage(message, code);
  }

  /**
   * Sets a custom error on the session.
   *
   * @param message The error string to report or {@code null} to clear the error.
   * @param code The error code to report. Ignored when {@code message} is {@code null}.
   */
  public void setCustomErrorMessage(@Nullable CharSequence message, int code) {
    setCustomErrorMessage(message, code, /* extras= */ null);
  }

  /**
   * Sets a custom error on the session.
   *
   * @param message The error string to report or {@code null} to clear the error.
   * @param code The error code to report. Ignored when {@code message} is {@code null}.
   * @param extras Extras to include in reported {@link PlaybackStateCompat}.
   */
  public void setCustomErrorMessage(
      @Nullable CharSequence message, int code, @Nullable Bundle extras) {
    customError = (message == null) ? null : new Pair<>(code, message);
    customErrorExtras = (message == null) ? null : extras;
    invalidateMediaSessionPlaybackState();
  }

  /**
   * Sets custom action providers. The order of the {@link CustomActionProvider}s determines the
   * order in which the actions are published.
   *
   * @param customActionProviders The custom action providers, or null to remove all existing custom
   *     action providers.
   */
  public void setCustomActionProviders(@Nullable CustomActionProvider... customActionProviders) {
    this.customActionProviders =
        customActionProviders == null ? new CustomActionProvider[0] : customActionProviders;
    invalidateMediaSessionPlaybackState();
  }

  /**
   * Sets a provider of metadata to be published to the media session. Pass {@code null} if no
   * metadata should be published.
   *
   * @param mediaMetadataProvider The provider of metadata to publish, or {@code null} if no
   *     metadata should be published.
   */
  public void setMediaMetadataProvider(@Nullable MediaMetadataProvider mediaMetadataProvider) {
    if (this.mediaMetadataProvider != mediaMetadataProvider) {
      this.mediaMetadataProvider = mediaMetadataProvider;
      invalidateMediaSessionMetadata();
    }
  }

  /**
   * Updates the metadata of the media session.
   *
   * <p>Apps normally only need to call this method when the backing data for a given media item has
   * changed and the metadata should be updated immediately.
   *
   * <p>The {@link MediaMetadataCompat} which is published to the session is obtained by calling
   * {@link MediaMetadataProvider#getMetadata(Player)}.
   */
  public final void invalidateMediaSessionMetadata() {
    // MODIFIED: disable notifications if no provider is set
    if (mediaMetadataProvider == null) {
      return;
    }

    MediaMetadataCompat metadata =
        mediaMetadataProvider != null && player != null
            ? mediaMetadataProvider.getMetadata(player)
            : METADATA_EMPTY;
    mediaSession.setMetadata(metadata != null ? metadata : METADATA_EMPTY);
  }

  /**
   * Updates the playback state of the media session.
   *
   * <p>Apps normally only need to call this method when the custom actions provided by a {@link
   * CustomActionProvider} changed and the playback state needs to be updated immediately.
   */
  public final void invalidateMediaSessionPlaybackState() {
    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
    if (player == null) {
      builder
          .setActions(buildPrepareActions())
          .setState(
              PlaybackStateCompat.STATE_NONE,
              /* position= */ 0,
              /* playbackSpeed= */ 0,
              /* updateTime= */ SystemClock.elapsedRealtime());
      mediaSession.setPlaybackState(builder.build());
      return;
    }

    Map<String, CustomActionProvider> currentActions = new HashMap<>();
    for (CustomActionProvider customActionProvider : customActionProviders) {
      PlaybackStateCompat.CustomAction customAction = customActionProvider.getCustomAction(player);
      if (customAction != null) {
        currentActions.put(customAction.getAction(), customActionProvider);
        builder.addCustomAction(customAction);
      }
    }
    customActionMap = Collections.unmodifiableMap(currentActions);

    Bundle extras = new Bundle();
    @Nullable ExoPlaybackException playbackError = player.getPlaybackError();
    boolean reportError = playbackError != null || customError != null;
    int sessionPlaybackState =
        reportError
            ? PlaybackStateCompat.STATE_ERROR
            : getMediaSessionPlaybackState(player.getPlaybackState(), player.getPlayWhenReady());
    if (customError != null) {
      builder.setErrorMessage(customError.first, customError.second);
      if (customErrorExtras != null) {
        extras.putAll(customErrorExtras);
      }
    } else if (playbackError != null && errorMessageProvider != null) {
      Pair<Integer, String> message = errorMessageProvider.getErrorMessage(playbackError);
      builder.setErrorMessage(message.first, message.second);
    }
    long activeQueueItemId =
        queueNavigator != null
            ? queueNavigator.getActiveQueueItemId(player)
            : MediaSessionCompat.QueueItem.UNKNOWN_ID;
    PlaybackParameters playbackParameters = player.getPlaybackParameters();
    extras.putFloat(EXTRAS_SPEED, playbackParameters.speed);
    extras.putFloat(EXTRAS_PITCH, playbackParameters.pitch);
    float sessionPlaybackSpeed = player.isPlaying() ? playbackParameters.speed : 0f;
    builder
        .setActions(buildPrepareActions() | buildPlaybackActions(player))
        .setActiveQueueItemId(activeQueueItemId)
        .setBufferedPosition(player.getBufferedPosition())
        .setState(
            sessionPlaybackState,
            player.getCurrentPosition(),
            sessionPlaybackSpeed,
            /* updateTime= */ SystemClock.elapsedRealtime())
        .setExtras(extras);
    mediaSession.setPlaybackState(builder.build());
  }

  /**
   * Updates the queue of the media session by calling {@link
   * QueueNavigator#onTimelineChanged(Player)}.
   *
   * <p>Apps normally only need to call this method when the backing data for a given queue item has
   * changed and the queue should be updated immediately.
   */
  public final void invalidateMediaSessionQueue() {
    if (queueNavigator != null && player != null) {
      queueNavigator.onTimelineChanged(player);
    }
  }

  /**
   * Registers a custom command receiver for responding to commands delivered via {@link
   * MediaSessionCompat.Callback#onCommand(String, Bundle, ResultReceiver)}.
   *
   * <p>Commands are only dispatched to this receiver when a player is connected.
   *
   * @param commandReceiver The command receiver to register.
   */
  public void registerCustomCommandReceiver(CommandReceiver commandReceiver) {
    if (!customCommandReceivers.contains(commandReceiver)) {
      customCommandReceivers.add(commandReceiver);
    }
  }

  /**
   * Unregisters a previously registered custom command receiver.
   *
   * @param commandReceiver The command receiver to unregister.
   */
  public void unregisterCustomCommandReceiver(CommandReceiver commandReceiver) {
    customCommandReceivers.remove(commandReceiver);
  }

  private void registerCommandReceiver(CommandReceiver commandReceiver) {
    if (!commandReceivers.contains(commandReceiver)) {
      commandReceivers.add(commandReceiver);
    }
  }

  private void unregisterCommandReceiver(CommandReceiver commandReceiver) {
    commandReceivers.remove(commandReceiver);
  }

  private long buildPrepareActions() {
    return playbackPreparer == null
        ? 0
        : (PlaybackPreparer.ACTIONS & playbackPreparer.getSupportedPrepareActions());
  }

  private long buildPlaybackActions(Player player) {
    boolean enableSeeking = false;
    boolean enableRewind = false;
    boolean enableFastForward = false;
    boolean enableSetRating = false;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty() && !player.isPlayingAd()) {
      enableSeeking = player.isCurrentWindowSeekable();
      enableRewind = enableSeeking && rewindMs > 0;
      enableFastForward = enableSeeking && fastForwardMs > 0;
      enableSetRating = true;
    }

    long playbackActions = BASE_PLAYBACK_ACTIONS;
    if (enableSeeking) {
      playbackActions |= PlaybackStateCompat.ACTION_SEEK_TO;
    }
    if (enableFastForward) {
      playbackActions |= PlaybackStateCompat.ACTION_FAST_FORWARD;
    }
    if (enableRewind) {
      playbackActions |= PlaybackStateCompat.ACTION_REWIND;
    }
    playbackActions &= enabledPlaybackActions;

    long actions = playbackActions;
    if (queueNavigator != null) {
      actions |=
          (QueueNavigator.ACTIONS & queueNavigator.getSupportedQueueNavigatorActions(player));
    }
    if (ratingCallback != null && enableSetRating) {
      actions |= PlaybackStateCompat.ACTION_SET_RATING;
    }
    return actions;
  }

  private boolean canDispatchPlaybackAction(long action) {
    return player != null && (enabledPlaybackActions & action) != 0;
  }

  private boolean canDispatchToPlaybackPreparer(long action) {
    return playbackPreparer != null
        && (playbackPreparer.getSupportedPrepareActions() & action) != 0;
  }

  private boolean canDispatchToQueueNavigator(long action) {
    return player != null
        && queueNavigator != null
        && (queueNavigator.getSupportedQueueNavigatorActions(player) & action) != 0;
  }

  private boolean canDispatchSetRating() {
    return player != null && ratingCallback != null;
  }

  private boolean canDispatchQueueEdit() {
    return player != null && queueEditor != null;
  }

  private boolean canDispatchMediaButtonEvent() {
    return player != null && mediaButtonEventHandler != null;
  }

  private void rewind(Player player) {
    if (player.isCurrentWindowSeekable() && rewindMs > 0) {
      seekToOffset(player, /* offsetMs= */ -rewindMs);
    }
  }

  private void fastForward(Player player) {
    if (player.isCurrentWindowSeekable() && fastForwardMs > 0) {
      seekToOffset(player, /* offsetMs= */ fastForwardMs);
    }
  }

  private void seekToOffset(Player player, long offsetMs) {
    long positionMs = player.getCurrentPosition() + offsetMs;
    long durationMs = player.getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = Math.min(positionMs, durationMs);
    }
    positionMs = Math.max(positionMs, 0);
    seekTo(player, player.getCurrentWindowIndex(), positionMs);
  }

  private void seekTo(Player player, int windowIndex, long positionMs) {
    controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
  }

  private static int getMediaSessionPlaybackState(
      int exoPlayerPlaybackState, boolean playWhenReady) {
    switch (exoPlayerPlaybackState) {
      case Player.STATE_BUFFERING:
        return PlaybackStateCompat.STATE_BUFFERING;
      case Player.STATE_READY:
        return playWhenReady ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_STOPPED;
      default:
        return PlaybackStateCompat.STATE_NONE;
    }
  }

  /**
   * Provides a default {@link MediaMetadataCompat} with properties and extras taken from the {@link
   * MediaDescriptionCompat} of the {@link MediaSessionCompat.QueueItem} of the active queue item.
   */
  public static final class DefaultMediaMetadataProvider implements MediaMetadataProvider {

    private final MediaControllerCompat mediaController;
    private final String metadataExtrasPrefix;

    /**
     * Creates a new instance.
     *
     * @param mediaController The {@link MediaControllerCompat}.
     * @param metadataExtrasPrefix A string to prefix extra keys which are propagated from the
     *     active queue item to the session metadata.
     */
    public DefaultMediaMetadataProvider(
        MediaControllerCompat mediaController, @Nullable String metadataExtrasPrefix) {
      this.mediaController = mediaController;
      this.metadataExtrasPrefix = metadataExtrasPrefix != null ? metadataExtrasPrefix : "";
    }

    @Override
    public MediaMetadataCompat getMetadata(Player player) {
      if (player.getCurrentTimeline().isEmpty()) {
        return METADATA_EMPTY;
      }
      MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
      if (player.isPlayingAd()) {
        builder.putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, 1);
      }
      builder.putLong(
          MediaMetadataCompat.METADATA_KEY_DURATION,
          player.isCurrentWindowDynamic() || player.getDuration() == C.TIME_UNSET
              ? -1
              : player.getDuration());
      long activeQueueItemId = mediaController.getPlaybackState().getActiveQueueItemId();
      if (activeQueueItemId != MediaSessionCompat.QueueItem.UNKNOWN_ID) {
        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        for (int i = 0; queue != null && i < queue.size(); i++) {
          MediaSessionCompat.QueueItem queueItem = queue.get(i);
          if (queueItem.getQueueId() == activeQueueItemId) {
            MediaDescriptionCompat description = queueItem.getDescription();
            Bundle extras = description.getExtras();
            if (extras != null) {
              for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value instanceof String) {
                  builder.putString(metadataExtrasPrefix + key, (String) value);
                } else if (value instanceof CharSequence) {
                  builder.putText(metadataExtrasPrefix + key, (CharSequence) value);
                } else if (value instanceof Long) {
                  builder.putLong(metadataExtrasPrefix + key, (Long) value);
                } else if (value instanceof Integer) {
                  builder.putLong(metadataExtrasPrefix + key, (Integer) value);
                } else if (value instanceof Bitmap) {
                  builder.putBitmap(metadataExtrasPrefix + key, (Bitmap) value);
                } else if (value instanceof RatingCompat) {
                  builder.putRating(metadataExtrasPrefix + key, (RatingCompat) value);
                }
              }
            }
            if (description.getTitle() != null) {
              String title = String.valueOf(description.getTitle());
              builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
            }
            if (description.getSubtitle() != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                  String.valueOf(description.getSubtitle()));
            }
            if (description.getDescription() != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                  String.valueOf(description.getDescription()));
            }
            if (description.getIconBitmap() != null) {
              builder.putBitmap(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, description.getIconBitmap());
            }
            if (description.getIconUri() != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                  String.valueOf(description.getIconUri()));
            }
            if (description.getMediaId() != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                  String.valueOf(description.getMediaId()));
            }
            if (description.getMediaUri() != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                  String.valueOf(description.getMediaUri()));
            }
            break;
          }
        }
      }
      return builder.build();
    }
  }

  private class ComponentListener extends MediaSessionCompat.Callback
      implements Player.EventListener {

    private int currentWindowIndex;
    private int currentWindowCount;

    // Player.EventListener implementation.

    @Override
    public void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @Player.TimelineChangeReason int reason) {
      int windowCount = player.getCurrentTimeline().getWindowCount();
      int windowIndex = player.getCurrentWindowIndex();
      if (queueNavigator != null) {
        queueNavigator.onTimelineChanged(player);
        invalidateMediaSessionPlaybackState();
      } else if (currentWindowCount != windowCount || currentWindowIndex != windowIndex) {
        // active queue item and queue navigation actions may need to be updated
        invalidateMediaSessionPlaybackState();
      }
      currentWindowCount = windowCount;
      currentWindowIndex = windowIndex;
      invalidateMediaSessionMetadata();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      invalidateMediaSessionPlaybackState();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      invalidateMediaSessionPlaybackState();
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
      mediaSession.setRepeatMode(
          repeatMode == Player.REPEAT_MODE_ONE
              ? PlaybackStateCompat.REPEAT_MODE_ONE
              : repeatMode == Player.REPEAT_MODE_ALL
                  ? PlaybackStateCompat.REPEAT_MODE_ALL
                  : PlaybackStateCompat.REPEAT_MODE_NONE);
      invalidateMediaSessionPlaybackState();
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      mediaSession.setShuffleMode(
          shuffleModeEnabled
              ? PlaybackStateCompat.SHUFFLE_MODE_ALL
              : PlaybackStateCompat.SHUFFLE_MODE_NONE);
      invalidateMediaSessionPlaybackState();
      invalidateMediaSessionQueue();
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
      if (currentWindowIndex != player.getCurrentWindowIndex()) {
        if (queueNavigator != null) {
          queueNavigator.onCurrentWindowIndexChanged(player);
        }
        currentWindowIndex = player.getCurrentWindowIndex();
        // Update playback state after queueNavigator.onCurrentWindowIndexChanged has been called
        // and before updating metadata.
        invalidateMediaSessionPlaybackState();
        invalidateMediaSessionMetadata();
        return;
      }
      invalidateMediaSessionPlaybackState();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      invalidateMediaSessionPlaybackState();
    }

    // MediaSessionCompat.Callback implementation.

    @Override
    public void onPlay() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PLAY)) {
        if (player.getPlaybackState() == Player.STATE_IDLE) {
          if (playbackPreparer != null) {
            playbackPreparer.onPrepare(/* playWhenReady= */ true);
          }
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
          seekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
        }
        controlDispatcher.dispatchSetPlayWhenReady(
            Assertions.checkNotNull(player), /* playWhenReady= */ true);
      }
    }

    @Override
    public void onPause() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PAUSE)) {
        controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
      }
    }

    @Override
    public void onSeekTo(long positionMs) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SEEK_TO)) {
        seekTo(player, player.getCurrentWindowIndex(), positionMs);
      }
    }

    @Override
    public void onFastForward() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_FAST_FORWARD)) {
        fastForward(player);
      }
    }

    @Override
    public void onRewind() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_REWIND)) {
        rewind(player);
      }
    }

    @Override
    public void onStop() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_STOP)) {
        controlDispatcher.dispatchStop(player, /* reset= */ true);
      }
    }

    @Override
    public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
        boolean shuffleModeEnabled;
        switch (shuffleMode) {
          case PlaybackStateCompat.SHUFFLE_MODE_ALL:
          case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
            shuffleModeEnabled = true;
            break;
          case PlaybackStateCompat.SHUFFLE_MODE_NONE:
          case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
          default:
            shuffleModeEnabled = false;
            break;
        }
        controlDispatcher.dispatchSetShuffleModeEnabled(player, shuffleModeEnabled);
      }
    }

    @Override
    public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int mediaSessionRepeatMode) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
        @RepeatModeUtil.RepeatToggleModes int repeatMode;
        switch (mediaSessionRepeatMode) {
          case PlaybackStateCompat.REPEAT_MODE_ALL:
          case PlaybackStateCompat.REPEAT_MODE_GROUP:
            repeatMode = Player.REPEAT_MODE_ALL;
            break;
          case PlaybackStateCompat.REPEAT_MODE_ONE:
            repeatMode = Player.REPEAT_MODE_ONE;
            break;
          case PlaybackStateCompat.REPEAT_MODE_NONE:
          case PlaybackStateCompat.REPEAT_MODE_INVALID:
          default:
            repeatMode = Player.REPEAT_MODE_OFF;
            break;
        }
        controlDispatcher.dispatchSetRepeatMode(player, repeatMode);
      }
    }

    @Override
    public void onSkipToNext() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
        queueNavigator.onSkipToNext(player, controlDispatcher);
      }
    }

    @Override
    public void onSkipToPrevious() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
        queueNavigator.onSkipToPrevious(player, controlDispatcher);
      }
    }

    @Override
    public void onSkipToQueueItem(long id) {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
        queueNavigator.onSkipToQueueItem(player, controlDispatcher, id);
      }
    }

    @Override
    public void onCustomAction(@NonNull String action, @Nullable Bundle extras) {
      if (player != null && customActionMap.containsKey(action)) {
        customActionMap.get(action).onCustomAction(player, controlDispatcher, action, extras);
        invalidateMediaSessionPlaybackState();
      }
    }

    @Override
    public void onCommand(String command, Bundle extras, ResultReceiver cb) {
      if (player != null) {
        for (int i = 0; i < commandReceivers.size(); i++) {
          if (commandReceivers.get(i).onCommand(player, controlDispatcher, command, extras, cb)) {
            return;
          }
        }
        for (int i = 0; i < customCommandReceivers.size(); i++) {
          if (customCommandReceivers
              .get(i)
              .onCommand(player, controlDispatcher, command, extras, cb)) {
            return;
          }
        }
      }
    }

    @Override
    public void onPrepare() {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE)) {
        playbackPreparer.onPrepare(/* playWhenReady= */ false);
      }
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)) {
        playbackPreparer.onPrepareFromMediaId(mediaId, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPrepareFromSearch(String query, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)) {
        playbackPreparer.onPrepareFromSearch(query, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPrepareFromUri(Uri uri, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_URI)) {
        playbackPreparer.onPrepareFromUri(uri, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)) {
        playbackPreparer.onPrepareFromMediaId(mediaId, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onPlayFromSearch(String query, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)) {
        playbackPreparer.onPrepareFromSearch(query, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onPlayFromUri(Uri uri, Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_URI)) {
        playbackPreparer.onPrepareFromUri(uri, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onSetRating(RatingCompat rating) {
      if (canDispatchSetRating()) {
        ratingCallback.onSetRating(player, rating);
      }
    }

    @Override
    public void onSetRating(RatingCompat rating, Bundle extras) {
      if (canDispatchSetRating()) {
        ratingCallback.onSetRating(player, rating, extras);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description) {
      if (canDispatchQueueEdit()) {
        queueEditor.onAddQueueItem(player, description);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description, int index) {
      if (canDispatchQueueEdit()) {
        queueEditor.onAddQueueItem(player, description, index);
      }
    }

    @Override
    public void onRemoveQueueItem(MediaDescriptionCompat description) {
      if (canDispatchQueueEdit()) {
        queueEditor.onRemoveQueueItem(player, description);
      }
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
      boolean isHandled =
          canDispatchMediaButtonEvent()
              && mediaButtonEventHandler.onMediaButtonEvent(
                  player, controlDispatcher, mediaButtonEvent);
      return isHandled || super.onMediaButtonEvent(mediaButtonEvent);
    }
  }
}
