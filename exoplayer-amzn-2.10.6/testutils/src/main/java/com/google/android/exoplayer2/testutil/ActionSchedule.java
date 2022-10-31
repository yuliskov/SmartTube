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

import android.os.Looper;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.Action.ClearVideoSurface;
import com.google.android.exoplayer2.testutil.Action.ExecuteRunnable;
import com.google.android.exoplayer2.testutil.Action.PlayUntilPosition;
import com.google.android.exoplayer2.testutil.Action.PrepareSource;
import com.google.android.exoplayer2.testutil.Action.Seek;
import com.google.android.exoplayer2.testutil.Action.SendMessages;
import com.google.android.exoplayer2.testutil.Action.SetPlayWhenReady;
import com.google.android.exoplayer2.testutil.Action.SetPlaybackParameters;
import com.google.android.exoplayer2.testutil.Action.SetRendererDisabled;
import com.google.android.exoplayer2.testutil.Action.SetRepeatMode;
import com.google.android.exoplayer2.testutil.Action.SetShuffleModeEnabled;
import com.google.android.exoplayer2.testutil.Action.SetVideoSurface;
import com.google.android.exoplayer2.testutil.Action.Stop;
import com.google.android.exoplayer2.testutil.Action.ThrowPlaybackException;
import com.google.android.exoplayer2.testutil.Action.WaitForIsLoading;
import com.google.android.exoplayer2.testutil.Action.WaitForPlaybackState;
import com.google.android.exoplayer2.testutil.Action.WaitForPositionDiscontinuity;
import com.google.android.exoplayer2.testutil.Action.WaitForSeekProcessed;
import com.google.android.exoplayer2.testutil.Action.WaitForTimelineChanged;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.HandlerWrapper;

/**
 * Schedules a sequence of {@link Action}s for execution during a test.
 */
public final class ActionSchedule {

  /**
   * Callback to notify listener that the action schedule has finished.
   */
  public interface Callback {

    /**
     * Called when action schedule finished executing all its actions.
     */
    void onActionScheduleFinished();

  }

  private final ActionNode rootNode;
  private final CallbackAction callbackAction;

  /**
   * @param rootNode The first node in the sequence.
   * @param callbackAction The final action which can be used to trigger a callback.
   */
  private ActionSchedule(ActionNode rootNode, CallbackAction callbackAction) {
    this.rootNode = rootNode;
    this.callbackAction = callbackAction;
  }

  /**
   * Starts execution of the schedule.
   *
   * @param player The player to which actions should be applied.
   * @param trackSelector The track selector to which actions should be applied.
   * @param surface The surface to use when applying actions.
   * @param mainHandler A handler associated with the main thread of the host activity.
   * @param callback A {@link Callback} to notify when the action schedule finishes, or null if no
   *     notification is needed.
   */
  /* package */ void start(
      SimpleExoPlayer player,
      DefaultTrackSelector trackSelector,
      Surface surface,
      HandlerWrapper mainHandler,
      @Nullable Callback callback) {
    callbackAction.setCallback(callback);
    rootNode.schedule(player, trackSelector, surface, mainHandler);
  }

  /**
   * A builder for {@link ActionSchedule} instances.
   */
  public static final class Builder {

    private final String tag;
    private final ActionNode rootNode;

    private long currentDelayMs;
    private ActionNode previousNode;

    /**
     * @param tag A tag to use for logging.
     */
    public Builder(String tag) {
      this.tag = tag;
      rootNode = new ActionNode(new RootAction(tag), 0);
      previousNode = rootNode;
    }

    /**
     * Schedules a delay between executing any previous actions and any subsequent ones.
     *
     * @param delayMs The delay in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder delay(long delayMs) {
      currentDelayMs += delayMs;
      return this;
    }

    /**
     * Schedules an action to be executed.
     *
     * @param action The action to schedule.
     * @return The builder, for convenience.
     */
    public Builder apply(Action action) {
      return appendActionNode(new ActionNode(action, currentDelayMs));
    }

    /**
     * Schedules an action to be executed repeatedly.
     *
     * @param action The action to schedule.
     * @param intervalMs The interval between each repetition in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder repeat(Action action, long intervalMs) {
      return appendActionNode(new ActionNode(action, currentDelayMs, intervalMs));
    }

    /**
     * Schedules a seek action to be executed.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seek(long positionMs) {
      return apply(new Seek(tag, positionMs));
    }

    /**
     * Schedules a seek action to be executed.
     *
     * @param windowIndex The window to seek to.
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seek(int windowIndex, long positionMs) {
      return apply(new Seek(tag, windowIndex, positionMs));
    }

    /**
     * Schedules a seek action to be executed and waits until playback resumes after the seek.
     *
     * @param positionMs The seek position.
     * @return The builder, for convenience.
     */
    public Builder seekAndWait(long positionMs) {
      return apply(new Seek(tag, positionMs))
          .apply(new WaitForSeekProcessed(tag))
          .apply(new WaitForPlaybackState(tag, Player.STATE_READY));
    }

    /**
     * Schedules a delay until the player indicates that a seek has been processed.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForSeekProcessed() {
      return apply(new WaitForSeekProcessed(tag));
    }

    /**
     * Schedules a playback parameters setting action to be executed.
     *
     * @param playbackParameters The playback parameters to set.
     * @return The builder, for convenience.
     * @see Player#setPlaybackParameters(PlaybackParameters)
     */
    public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
      return apply(new SetPlaybackParameters(tag, playbackParameters));
    }

    /**
     * Schedules a stop action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder stop() {
      return apply(new Stop(tag));
    }

    /**
     * Schedules a stop action to be executed.
     *
     * @param reset Whether the player should be reset.
     * @return The builder, for convenience.
     */
    public Builder stop(boolean reset) {
      return apply(new Stop(tag, reset));
    }

    /**
     * Schedules a play action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder play() {
      return apply(new SetPlayWhenReady(tag, true));
    }

    /**
     * Schedules a play action to be executed, waits until the player reaches the specified
     * position, and pauses the player again.
     *
     * @param windowIndex The window index at which the player should be paused again.
     * @param positionMs The position in that window at which the player should be paused again.
     * @return The builder, for convenience.
     */
    public Builder playUntilPosition(int windowIndex, long positionMs) {
      return apply(new PlayUntilPosition(tag, windowIndex, positionMs));
    }

    /**
     * Schedules a play action to be executed, waits until the player reaches the start of the
     * specified window, and pauses the player again.
     *
     * @param windowIndex The window index at which the player should be paused again.
     * @return The builder, for convenience.
     */
    public Builder playUntilStartOfWindow(int windowIndex) {
      return apply(new PlayUntilPosition(tag, windowIndex, /* positionMs= */ 0));
    }

    /**
     * Schedules a pause action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder pause() {
      return apply(new SetPlayWhenReady(tag, false));
    }

    /**
     * Schedules a renderer enable action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder enableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, false));
    }

    /**
     * Schedules a renderer disable action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder disableRenderer(int index) {
      return apply(new SetRendererDisabled(tag, index, true));
    }

    /**
     * Schedules a clear video surface action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder clearVideoSurface() {
      return apply(new ClearVideoSurface(tag));
    }

    /**
     * Schedules a set video surface action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setVideoSurface() {
      return apply(new SetVideoSurface(tag));
    }

    /**
     * Schedules a new source preparation action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder prepareSource(MediaSource mediaSource) {
      return apply(new PrepareSource(tag, mediaSource));
    }

    /**
     * Schedules a new source preparation action to be executed.
     * @see com.google.android.exoplayer2.ExoPlayer#prepare(MediaSource, boolean, boolean).
     *
     * @return The builder, for convenience.
     */
    public Builder prepareSource(MediaSource mediaSource, boolean resetPosition,
        boolean resetState) {
      return apply(new PrepareSource(tag, mediaSource, resetPosition, resetState));
    }

    /**
     * Schedules a repeat mode setting action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
      return apply(new SetRepeatMode(tag, repeatMode));
    }

    /**
     * Schedules a shuffle setting action to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
      return apply(new SetShuffleModeEnabled(tag, shuffleModeEnabled));
    }

    /**
     * Schedules sending a {@link PlayerMessage}.
     *
     * @param positionMs The position in the current window at which the message should be sent, in
     *     milliseconds.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(Target target, long positionMs) {
      return apply(new SendMessages(tag, target, positionMs));
    }

    /**
     * Schedules sending a {@link PlayerMessage}.
     *
     * @param target A message target.
     * @param windowIndex The window index at which the message should be sent.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(Target target, int windowIndex, long positionMs) {
      return apply(
          new SendMessages(tag, target, windowIndex, positionMs, /* deleteAfterDelivery= */ true));
    }

    /**
     * Schedules to send a {@link PlayerMessage}.
     *
     * @param target A message target.
     * @param windowIndex The window index at which the message should be sent.
     * @param positionMs The position at which the message should be sent, in milliseconds.
     * @param deleteAfterDelivery Whether the message will be deleted after delivery.
     * @return The builder, for convenience.
     */
    public Builder sendMessage(
        Target target, int windowIndex, long positionMs, boolean deleteAfterDelivery) {
      return apply(new SendMessages(tag, target, windowIndex, positionMs, deleteAfterDelivery));
    }

    /**
     * Schedules a delay until any timeline change.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForTimelineChanged() {
      return apply(new WaitForTimelineChanged(tag, /* expectedTimeline= */ null));
    }

    /**
     * Schedules a delay until the timeline changed to a specified expected timeline.
     *
     * @param expectedTimeline The expected timeline to wait for. If null, wait for any timeline
     *     change.
     * @return The builder, for convenience.
     */
    public Builder waitForTimelineChanged(Timeline expectedTimeline) {
      return apply(new WaitForTimelineChanged(tag, expectedTimeline));
    }

    /**
     * Schedules a delay until the next position discontinuity.
     *
     * @return The builder, for convenience.
     */
    public Builder waitForPositionDiscontinuity() {
      return apply(new WaitForPositionDiscontinuity(tag));
    }

    /**
     * Schedules a delay until the playback state changed to the specified state.
     *
     * @param targetPlaybackState The target playback state.
     * @return The builder, for convenience.
     */
    public Builder waitForPlaybackState(int targetPlaybackState) {
      return apply(new WaitForPlaybackState(tag, targetPlaybackState));
    }

    /**
     * Schedules a delay until {@code player.isLoading()} changes to the specified value.
     *
     * @param targetIsLoading The target value of {@code player.isLoading()}.
     * @return The builder, for convenience.
     */
    public Builder waitForIsLoading(boolean targetIsLoading) {
      return apply(new WaitForIsLoading(tag, targetIsLoading));
    }

    /**
     * Schedules a {@link Runnable} to be executed.
     *
     * @return The builder, for convenience.
     */
    public Builder executeRunnable(Runnable runnable) {
      return apply(new ExecuteRunnable(tag, runnable));
    }

    /**
     * Schedules to throw a playback exception on the playback thread.
     *
     * @param exception The exception to throw.
     * @return The builder, for convenience.
     */
    public Builder throwPlaybackException(ExoPlaybackException exception) {
      return apply(new ThrowPlaybackException(tag, exception));
    }

    public ActionSchedule build() {
      CallbackAction callbackAction = new CallbackAction(tag);
      apply(callbackAction);
      return new ActionSchedule(rootNode, callbackAction);
    }

    private Builder appendActionNode(ActionNode actionNode) {
      previousNode.setNext(actionNode);
      previousNode = actionNode;
      currentDelayMs = 0;
      return this;
    }
  }

  /**
   * Provides a wrapper for a {@link Target} which has access to the player when handling messages.
   * Can be used with {@link Builder#sendMessage(Target, long)}.
   */
  public abstract static class PlayerTarget implements Target {

    private SimpleExoPlayer player;

    /** Handles the message send to the component and additionally provides access to the player. */
    public abstract void handleMessage(
        SimpleExoPlayer player, int messageType, @Nullable Object message);

    /** Sets the player to be passed to {@link #handleMessage(SimpleExoPlayer, int, Object)}. */
    /* package */ void setPlayer(SimpleExoPlayer player) {
      this.player = player;
    }

    @Override
    public final void handleMessage(int messageType, @Nullable Object message)
        throws ExoPlaybackException {
      handleMessage(player, messageType, message);
    }
  }

  /**
   * Provides a wrapper for a {@link Runnable} which has access to the player. Can be used with
   * {@link Builder#executeRunnable(Runnable)}.
   */
  public abstract static class PlayerRunnable implements Runnable {

    private SimpleExoPlayer player;

    /** Executes Runnable with reference to player. */
    public abstract void run(SimpleExoPlayer player);

    /** Sets the player to be passed to {@link #run(SimpleExoPlayer)} . */
    /* package */ void setPlayer(SimpleExoPlayer player) {
      this.player = player;
    }

    @Override
    public final void run() {
      run(player);
    }
  }

  /**
   * Wraps an {@link Action}, allowing a delay and a next {@link Action} to be specified.
   */
  /* package */ static final class ActionNode implements Runnable {

    private final Action action;
    private final long delayMs;
    private final long repeatIntervalMs;

    private ActionNode next;

    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private Surface surface;
    private HandlerWrapper mainHandler;

    /**
     * @param action The wrapped action.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     */
    public ActionNode(Action action, long delayMs) {
      this(action, delayMs, C.TIME_UNSET);
    }

    /**
     * @param action The wrapped action.
     * @param delayMs The delay between the node being scheduled and the action being executed.
     * @param repeatIntervalMs The interval between one execution and the next repetition. If set to
     *     {@link C#TIME_UNSET}, the action is executed once only.
     */
    public ActionNode(Action action, long delayMs, long repeatIntervalMs) {
      this.action = action;
      this.delayMs = delayMs;
      this.repeatIntervalMs = repeatIntervalMs;
    }

    /**
     * Sets the next action.
     *
     * @param next The next {@link Action}.
     */
    public void setNext(ActionNode next) {
      this.next = next;
    }

    /**
     * Schedules {@link #action} to be executed after {@link #delayMs}. The {@link #next} node will
     * be scheduled immediately after {@link #action} is executed.
     *
     * @param player The player to which actions should be applied.
     * @param trackSelector The track selector to which actions should be applied.
     * @param surface The surface to use when applying actions.
     * @param mainHandler A handler associated with the main thread of the host activity.
     */
    public void schedule(
        SimpleExoPlayer player,
        DefaultTrackSelector trackSelector,
        Surface surface,
        HandlerWrapper mainHandler) {
      this.player = player;
      this.trackSelector = trackSelector;
      this.surface = surface;
      this.mainHandler = mainHandler;
      if (delayMs == 0 && Looper.myLooper() == mainHandler.getLooper()) {
        run();
      } else {
        mainHandler.postDelayed(this, delayMs);
      }
    }

    @Override
    public void run() {
      action.doActionAndScheduleNext(player, trackSelector, surface, mainHandler, next);
      if (repeatIntervalMs != C.TIME_UNSET) {
        mainHandler.postDelayed(
            new Runnable() {
              @Override
              public void run() {
                action.doActionAndScheduleNext(player, trackSelector, surface, mainHandler, null);
                mainHandler.postDelayed(this, repeatIntervalMs);
              }
            },
            repeatIntervalMs);
      }
    }

  }

  /**
   * A no-op root action.
   */
  private static final class RootAction extends Action {

    public RootAction(String tag) {
      super(tag, "Root");
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Do nothing.
    }
  }

  /**
   * An action calling a specified {@link ActionSchedule.Callback}.
   */
  private static final class CallbackAction extends Action {

    private @Nullable Callback callback;

    public CallbackAction(String tag) {
      super(tag, "FinishedCallback");
    }

    public void setCallback(@Nullable Callback callback) {
      this.callback = callback;
    }

    @Override
    protected void doActionAndScheduleNextImpl(
        SimpleExoPlayer player,
        DefaultTrackSelector trackSelector,
        Surface surface,
        HandlerWrapper handler,
        ActionNode nextAction) {
      Assertions.checkArgument(nextAction == null);
      if (callback != null) {
        handler.post(() -> callback.onActionScheduleFinished());
      }
    }

    @Override
    protected void doActionImpl(
        SimpleExoPlayer player, DefaultTrackSelector trackSelector, Surface surface) {
      // Not triggered.
    }
  }

}
