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
package com.google.android.exoplayer2.testutil;

import android.os.Handler;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.ActionSchedule.ActionNode;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerRunnable;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerTarget;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Log;

/**
 * Base class for actions to perform during playback tests.
 */
public abstract class Action {

  private final String tag;
  private final @Nullable String description;

  /**
   * @param tag A tag to use for logging.
   * @param description A description to be logged when the action is executed, or null if no
   *     logging is required.
   */
  public Action(String tag, @Nullable String description) {
    this.tag = tag;
    this.description = description;
  }

  /**
   * Executes the action and schedules the next.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   * @param handler The handler to use to pass to the next action.
   * @param nextAction The next action to schedule immediately after this action finished.
   */
  public final void doActionAndScheduleNext(
      SimpleExoPlayer player,
      DefaultTrackSelector trackSelector,
      Surface surface,
      HandlerWrapper handler,
      ActionNode nextAction) {
    if (description != null) {
      Log.i(tag, description);
    }
    doActionAndScheduleNextImpl(player, trackSelector, surface, handler, nextAction);
  }

  /**
   * Called by {@link #doActionAndScheduleNext(SimpleExoPlayer, DefaultTrackSelector, Surface,
   * HandlerWrapper, ActionNode)} to perform the action and to schedule the next action node.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   * @param handler The handler to use to pass to the next action.
   * @param nextAction The next action to schedule immediately after this action finished.
   */
  protected void doActionAndScheduleNextImpl(
      SimpleExoPlayer player,
      DefaultTrackSelector trackSelector,
      Surface surface,
      HandlerWrapper handler,
      ActionNode nextAction) {
    doActionImpl(player, trackSelector, surface);
    if (nextAction != null) {
      nextAction.schedule(player, trackSelector, surface, handler);
    }
  }

  /**
   * Called by {@link #doActionAndScheduleNextImpl(SimpleExoPlayer, DefaultTrackSelector, Surface,
   * HandlerWrapper, ActionNode)} to perform the action.
   *
   * @param player The player to which the action should be applied.
   * @param trackSelector The track selector to which the action should be applied.
   * @param surface The surface to use when applying actions.
   */
  protected abstract void doActionImpl(
      SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface);

  /**
   * Calls {@link Player#seekTo(long)} or {@link Player#seekTo(int, long)}.
   */
  public static final class Seek extends Action {

    private final Integer windowIndex;
    private final long positionMs;

    /**
     * Action calls {@link Player#seekTo(long)}.
     *
     * @param tag A tag to use for logging.
     * @param positionMs The seek position.
     */
    public Seek(String tag, long positionMs) {
      super(tag, "Seek:" + positionMs);
      this.windowIndex = null;
      this.positionMs = positionMs;
    }

    /**
     * Action calls {@link Player#seekTo(int, long)}.
     *
     * @param tag A tag to use for logging.
     * @param windowIndex The window to seek to.
     * @param positionMs The seek position.
     */
    public Seek(String tag, int windowIndex, long positionMs) {
      super(tag, "Seek:" + positionMs);
      this.windowIndex = windowIndex;
      this.positionMs = positionMs;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      if (windowIndex == null) {
        player.seekTo(positionMs);
      } else {
        player.seekTo(windowIndex, positionMs);
      }
    }

  }

  /**
   * Calls {@link Player#stop()} or {@link Player#stop(boolean)}.
   */
  public static final class Stop extends Action {

    private static final String STOP_ACTION_TAG = "Stop";

    private final Boolean reset;

    /**
     * Action will call {@link Player#stop()}.
     *
     * @param tag A tag to use for logging.
     */
    public Stop(String tag) {
      super(tag, STOP_ACTION_TAG);
      this.reset = null;
    }

    /**
     * Action will call {@link Player#stop(boolean)}.
     *
     * @param tag A tag to use for logging.
     * @param reset The value to pass to {@link Player#stop(boolean)}.
     */
    public Stop(String tag, boolean reset) {
      super(tag, STOP_ACTION_TAG);
      this.reset = reset;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      if (reset == null) {
        player.stop();
      } else {
        player.stop(reset);
      }

    }

  }

  /**
   * Calls {@link Player#setPlayWhenReady(boolean)}.
   */
  public static final class SetPlayWhenReady extends Action {

    private final boolean playWhenReady;

    /**
     * @param tag A tag to use for logging.
     * @param playWhenReady The value to pass.
     */
    public SetPlayWhenReady(String tag, boolean playWhenReady) {
      super(tag, playWhenReady ? "Play" : "Pause");
      this.playWhenReady = playWhenReady;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.setPlayWhenReady(playWhenReady);
    }

  }

  /**
   * Updates the {@link Parameters} of a {@link DefaultTrackSelector} to specify whether the
   * renderer at a given index should be disabled.
   */
  public static final class SetRendererDisabled extends Action {

    private final int rendererIndex;
    private final boolean disabled;

    /**
     * @param tag A tag to use for logging.
     * @param rendererIndex The index of the renderer.
     * @param disabled Whether the renderer should be disabled.
     */
    public SetRendererDisabled(String tag, int rendererIndex, boolean disabled) {
      super(tag, "SetRendererDisabled:" + rendererIndex + ":" + disabled);
      this.rendererIndex = rendererIndex;
      this.disabled = disabled;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      trackSelector.setParameters(
          trackSelector.buildUponParameters().setRendererDisabled(rendererIndex, disabled));
    }

  }

  /**
   * Calls {@link SimpleExoPlayer#clearVideoSurface()}.
   */
  public static final class ClearVideoSurface extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public ClearVideoSurface(String tag) {
      super(tag, "ClearVideoSurface");
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.clearVideoSurface();
    }

  }

  /**
   * Calls {@link SimpleExoPlayer#setVideoSurface(Surface)}.
   */
  public static final class SetVideoSurface extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public SetVideoSurface(String tag) {
      super(tag, "SetVideoSurface");
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.setVideoSurface(surface);
    }

  }

  /**
   * Calls {@link ExoPlayer#prepare(MediaSource)}.
   */
  public static final class PrepareSource extends Action {

    private final MediaSource mediaSource;
    private final boolean resetPosition;
    private final boolean resetState;

    /**
     * @param tag A tag to use for logging.
     */
    public PrepareSource(String tag, MediaSource mediaSource) {
      this(tag, mediaSource, true, true);
    }

    /**
     * @param tag A tag to use for logging.
     */
    public PrepareSource(String tag, MediaSource mediaSource, boolean resetPosition,
        boolean resetState) {
      super(tag, "PrepareSource");
      this.mediaSource = mediaSource;
      this.resetPosition = resetPosition;
      this.resetState = resetState;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.prepare(mediaSource, resetPosition, resetState);
    }

  }

  /**
   * Calls {@link Player#setRepeatMode(int)}.
   */
  public static final class SetRepeatMode extends Action {

    private final @Player.RepeatMode int repeatMode;

    /**
     * @param tag A tag to use for logging.
     */
    public SetRepeatMode(String tag, @Player.RepeatMode int repeatMode) {
      super(tag, "SetRepeatMode:" + repeatMode);
      this.repeatMode = repeatMode;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.setRepeatMode(repeatMode);
    }

  }

  /**
   * Calls {@link Player#setShuffleModeEnabled(boolean)}.
   */
  public static final class SetShuffleModeEnabled extends Action {

    private final boolean shuffleModeEnabled;

    /**
     * @param tag A tag to use for logging.
     */
    public SetShuffleModeEnabled(String tag, boolean shuffleModeEnabled) {
      super(tag, "SetShuffleModeEnabled:" + shuffleModeEnabled);
      this.shuffleModeEnabled = shuffleModeEnabled;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.setShuffleModeEnabled(shuffleModeEnabled);
    }
  }

  /** Calls {@link ExoPlayer#createMessage(Target)} and {@link PlayerMessage#send()}. */
  public static final class SendMessages extends Action {

    private final Target target;
    private final int windowIndex;
    private final long positionMs;
    private final boolean deleteAfterDelivery;

    /**
     * @param tag A tag to use for logging.
     * @param target A message target.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     */
    public SendMessages(String tag, Target target, long positionMs) {
      this(
          tag,
          target,
          /* windowIndex= */ C.INDEX_UNSET,
          positionMs,
          /* deleteAfterDelivery= */ true);
    }

    /**
     * @param tag A tag to use for logging.
     * @param target A message target.
     * @param windowIndex The window index at which the message should be sent, or {@link
     *     C#INDEX_UNSET} for the current window.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     * @param deleteAfterDelivery Whether the message will be deleted after delivery.
     */
    public SendMessages(
        String tag, Target target, int windowIndex, long positionMs, boolean deleteAfterDelivery) {
      super(tag, "SendMessages");
      this.target = target;
      this.windowIndex = windowIndex;
      this.positionMs = positionMs;
      this.deleteAfterDelivery = deleteAfterDelivery;
    }

    @Override
    protected void doActionImpl(
        final SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      if (target instanceof PlayerTarget) {
        ((PlayerTarget) target).setPlayer(player);
      }
      PlayerMessage message = player.createMessage(target);
      if (windowIndex != C.INDEX_UNSET) {
        message.setPosition(windowIndex, positionMs);
      } else {
        message.setPosition(positionMs);
      }
      message.setHandler(new Handler());
      message.setDeleteAfterDelivery(deleteAfterDelivery);
      message.send();
    }
  }

  /**
   * Calls {@link Player#setPlaybackParameters(PlaybackParameters)}.
   */
  public static final class SetPlaybackParameters extends Action {

    private final PlaybackParameters playbackParameters;

    /**
     * @param tag A tag to use for logging.
     * @param playbackParameters The playback parameters.
     */
    public SetPlaybackParameters(String tag, PlaybackParameters playbackParameters) {
      super(tag, "SetPlaybackParameters:" + playbackParameters);
      this.playbackParameters = playbackParameters;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player.setPlaybackParameters(playbackParameters);
    }

  }

  /** Throws a playback exception on the playback thread. */
  public static final class ThrowPlaybackException extends Action {

    private final ExoPlaybackException exception;

    /**
     * @param tag A tag to use for logging.
     * @param exception The exception to throw.
     */
    public ThrowPlaybackException(String tag, ExoPlaybackException exception) {
      super(tag, "ThrowPlaybackException:" + exception);
      this.exception = exception;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      player
          .createMessage(
              (messageType, payload) -> {
                throw exception;
              })
          .send();
    }
  }

  /**
   * Schedules a play action to be executed, waits until the player reaches the specified position,
   * and pauses the player again.
   */
  public static final class PlayUntilPosition extends Action {

    private final int windowIndex;
    private final long positionMs;

    /**
     * @param tag A tag to use for logging.
     * @param windowIndex The window index at which the player should be paused again.
     * @param positionMs The position in that window at which the player should be paused again.
     */
    public PlayUntilPosition(String tag, int windowIndex, long positionMs) {
      super(tag, "PlayUntilPosition:" + windowIndex + "," + positionMs);
      this.windowIndex = windowIndex;
      this.positionMs = positionMs;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      Handler testThreadHandler = new Handler();
      // Schedule a message on the playback thread to ensure the player is paused immediately.
      player
          .createMessage(
              (messageType, payload) -> {
                // Block playback thread until pause command has been sent from test thread.
                ConditionVariable blockPlaybackThreadCondition = new ConditionVariable();
                testThreadHandler.post(
                    () -> {
                      player.setPlayWhenReady(/* playWhenReady= */ false);
                      blockPlaybackThreadCondition.open();
                    });
                try {
                  blockPlaybackThreadCondition.block();
                } catch (InterruptedException e) {
                  // Ignore.
                }
              })
          .setPosition(windowIndex, positionMs)
          .send();
      // Schedule another message on this test thread to continue action schedule.
      player
          .createMessage(
              (messageType, payload) ->
                  nextAction.schedule(player, trackSelector, surface, handler))
          .setPosition(windowIndex, positionMs)
          .setHandler(testThreadHandler)
          .send();
      player.setPlayWhenReady(true);
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Waits for {@link Player.EventListener#onTimelineChanged(Timeline, Object, int)}.
   */
  public static final class WaitForTimelineChanged extends Action {

    private final @Nullable Timeline expectedTimeline;

    /**
     * Creates action waiting for a timeline change.
     *
     * @param tag A tag to use for logging.
     * @param expectedTimeline The expected timeline to wait for. If null, wait for any timeline
     *     change.
     */
    public WaitForTimelineChanged(String tag, @Nullable Timeline expectedTimeline) {
      super(tag, "WaitForTimelineChanged");
      this.expectedTimeline = expectedTimeline;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      Player.EventListener listener =
          new Player.EventListener() {
            @Override
            public void onTimelineChanged(
                Timeline timeline,
                @Nullable Object manifest,
                @Player.TimelineChangeReason int reason) {
              if (expectedTimeline == null || timeline.equals(expectedTimeline)) {
                player.removeListener(this);
                nextAction.schedule(player, trackSelector, surface, handler);
              }
            }
          };
      player.addListener(listener);
      if (expectedTimeline != null && player.getCurrentTimeline().equals(expectedTimeline)) {
        player.removeListener(listener);
        nextAction.schedule(player, trackSelector, surface, handler);
      }
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Waits for {@link Player.EventListener#onPositionDiscontinuity(int)}.
   */
  public static final class WaitForPositionDiscontinuity extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForPositionDiscontinuity(String tag) {
      super(tag, "WaitForPositionDiscontinuity");
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      player.addListener(
          new Player.EventListener() {
            @Override
            public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
              player.removeListener(this);
              nextAction.schedule(player, trackSelector, surface, handler);
            }
          });
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Waits for a specified playback state, returning either immediately or after a call to
   * {@link Player.EventListener#onPlayerStateChanged(boolean, int)}.
   */
  public static final class WaitForPlaybackState extends Action {

    private final int targetPlaybackState;

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForPlaybackState(String tag, int targetPlaybackState) {
      super(tag, "WaitForPlaybackState");
      this.targetPlaybackState = targetPlaybackState;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      if (targetPlaybackState == player.getPlaybackState()) {
        nextAction.schedule(player, trackSelector, surface, handler);
      } else {
        player.addListener(
            new Player.EventListener() {
              @Override
              public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (targetPlaybackState == playbackState) {
                  player.removeListener(this);
                  nextAction.schedule(player, trackSelector, surface, handler);
                }
              }
            });
      }
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Waits for a specified loading state, returning either immediately or after a call to {@link
   * Player.EventListener#onLoadingChanged(boolean)}.
   */
  public static final class WaitForIsLoading extends Action {

    private final boolean targetIsLoading;

    /**
     * @param tag A tag to use for logging.
     * @param targetIsLoading The loading state to wait for.
     */
    public WaitForIsLoading(String tag, boolean targetIsLoading) {
      super(tag, "WaitForIsLoading");
      this.targetIsLoading = targetIsLoading;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      if (targetIsLoading == player.isLoading()) {
        nextAction.schedule(player, trackSelector, surface, handler);
      } else {
        player.addListener(
            new Player.EventListener() {
              @Override
              public void onLoadingChanged(boolean isLoading) {
                if (targetIsLoading == isLoading) {
                  player.removeListener(this);
                  nextAction.schedule(player, trackSelector, surface, handler);
                }
              }
            });
      }
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Waits for {@link Player.EventListener#onSeekProcessed()}.
   */
  public static final class WaitForSeekProcessed extends Action {

    /**
     * @param tag A tag to use for logging.
     */
    public WaitForSeekProcessed(String tag) {
      super(tag, "WaitForSeekProcessed");
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        final SimpleExoPlayer player,
        final DefaultTrackSelector trackSelector,
        final Surface surface,
        final HandlerWrapper handler,
        final ActionNode nextAction) {
      if (nextAction == null) {
        return;
      }
      player.addListener(
          new Player.EventListener() {
            @Override
            public void onSeekProcessed() {
              player.removeListener(this);
              nextAction.schedule(player, trackSelector, surface, handler);
            }
          });
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

  /**
   * Calls {@link Runnable#run()}.
   */
  public static final class ExecuteRunnable extends Action {

    private final Runnable runnable;

    /**
     * @param tag A tag to use for logging.
     */
    public ExecuteRunnable(String tag, Runnable runnable) {
      super(tag, "ExecuteRunnable");
      this.runnable = runnable;
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      if (runnable instanceof PlayerRunnable) {
        ((PlayerRunnable) runnable).setPlayer(player);
      }
      runnable.run();
    }

  }

}
