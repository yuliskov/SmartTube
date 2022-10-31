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

import android.os.Bundle;
import android.os.ResultReceiver;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * An abstract implementation of the {@link MediaSessionConnector.QueueNavigator} that maps the
 * windows of a {@link Player}'s {@link Timeline} to the media session queue.
 */
public abstract class TimelineQueueNavigator implements MediaSessionConnector.QueueNavigator {

  public static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;
  public static final int DEFAULT_MAX_QUEUE_SIZE = 10;

  private final MediaSessionCompat mediaSession;
  private final Timeline.Window window;
  private final int maxQueueSize;

  private long activeQueueItemId;

  /**
   * Creates an instance for a given {@link MediaSessionCompat}.
   * <p>
   * Equivalent to {@code TimelineQueueNavigator(mediaSession, DEFAULT_MAX_QUEUE_SIZE)}.
   *
   * @param mediaSession The {@link MediaSessionCompat}.
   */
  public TimelineQueueNavigator(MediaSessionCompat mediaSession) {
    this(mediaSession, DEFAULT_MAX_QUEUE_SIZE);
  }

  /**
   * Creates an instance for a given {@link MediaSessionCompat} and maximum queue size.
   * <p>
   * If the number of windows in the {@link Player}'s {@link Timeline} exceeds {@code maxQueueSize},
   * the media session queue will correspond to {@code maxQueueSize} windows centered on the one
   * currently being played.
   *
   * @param mediaSession The {@link MediaSessionCompat}.
   * @param maxQueueSize The maximum queue size.
   */
  public TimelineQueueNavigator(MediaSessionCompat mediaSession, int maxQueueSize) {
    Assertions.checkState(maxQueueSize > 0);
    this.mediaSession = mediaSession;
    this.maxQueueSize = maxQueueSize;
    activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
    window = new Timeline.Window();
  }

  /**
   * Gets the {@link MediaDescriptionCompat} for a given timeline window index.
   *
   * <p>Often artworks and icons need to be loaded asynchronously. In such a case, return a {@link
   * MediaDescriptionCompat} without the images, load your images asynchronously off the main thread
   * and then call {@link MediaSessionConnector#invalidateMediaSessionQueue()} to make the connector
   * update the queue by calling {@link #getMediaDescription(Player, int)} again.
   *
   * @param player The current player.
   * @param windowIndex The timeline window index for which to provide a description.
   * @return A {@link MediaDescriptionCompat}.
   */
  public abstract MediaDescriptionCompat getMediaDescription(Player player, int windowIndex);

  @Override
  public long getSupportedQueueNavigatorActions(Player player) {
    boolean enableSkipTo = false;
    boolean enablePrevious = false;
    boolean enableNext = false;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty() && !player.isPlayingAd()) {
      timeline.getWindow(player.getCurrentWindowIndex(), window);
      enableSkipTo = timeline.getWindowCount() > 1;
      enablePrevious = window.isSeekable || !window.isDynamic || player.hasPrevious();
      enableNext = window.isDynamic || player.hasNext();
    }

    long actions = 0;
    if (enableSkipTo) {
      actions |= PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
    }
    if (enablePrevious) {
      actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
    }
    if (enableNext) {
      actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
    }
    return actions;
  }

  @Override
  public final void onTimelineChanged(Player player) {
    publishFloatingQueueWindow(player);
  }

  @Override
  public final void onCurrentWindowIndexChanged(Player player) {
    if (activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID
        || player.getCurrentTimeline().getWindowCount() > maxQueueSize) {
      publishFloatingQueueWindow(player);
    } else if (!player.getCurrentTimeline().isEmpty()) {
      activeQueueItemId = player.getCurrentWindowIndex();
    }
  }

  @Override
  public final long getActiveQueueItemId(@Nullable Player player) {
    return activeQueueItemId;
  }

  @Override
  public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    timeline.getWindow(windowIndex, window);
    int previousWindowIndex = player.getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET
        && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
            || (window.isDynamic && !window.isSeekable))) {
      controlDispatcher.dispatchSeekTo(player, previousWindowIndex, C.TIME_UNSET);
    } else {
      controlDispatcher.dispatchSeekTo(player, windowIndex, 0);
    }
  }

  @Override
  public void onSkipToQueueItem(Player player, ControlDispatcher controlDispatcher, long id) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return;
    }
    int windowIndex = (int) id;
    if (0 <= windowIndex && windowIndex < timeline.getWindowCount()) {
      controlDispatcher.dispatchSeekTo(player, windowIndex, C.TIME_UNSET);
    }
  }

  @Override
  public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty() || player.isPlayingAd()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    int nextWindowIndex = player.getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      controlDispatcher.dispatchSeekTo(player, nextWindowIndex, C.TIME_UNSET);
    } else if (timeline.getWindow(windowIndex, window).isDynamic) {
      controlDispatcher.dispatchSeekTo(player, windowIndex, C.TIME_UNSET);
    }
  }

  // CommandReceiver implementation.

  @Override
  public boolean onCommand(
      Player player,
      ControlDispatcher controlDispatcher,
      String command,
      Bundle extras,
      ResultReceiver cb) {
    return false;
  }

  // Helper methods.

  private void publishFloatingQueueWindow(Player player) {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      mediaSession.setQueue(Collections.emptyList());
      activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
      return;
    }
    ArrayDeque<MediaSessionCompat.QueueItem> queue = new ArrayDeque<>();
    int queueSize = Math.min(maxQueueSize, timeline.getWindowCount());

    // Add the active queue item.
    int currentWindowIndex = player.getCurrentWindowIndex();
    queue.add(
        new MediaSessionCompat.QueueItem(
            getMediaDescription(player, currentWindowIndex), currentWindowIndex));

    // Fill queue alternating with next and/or previous queue items.
    int firstWindowIndex = currentWindowIndex;
    int lastWindowIndex = currentWindowIndex;
    boolean shuffleModeEnabled = player.getShuffleModeEnabled();
    while ((firstWindowIndex != C.INDEX_UNSET || lastWindowIndex != C.INDEX_UNSET)
        && queue.size() < queueSize) {
      // Begin with next to have a longer tail than head if an even sized queue needs to be trimmed.
      if (lastWindowIndex != C.INDEX_UNSET) {
        lastWindowIndex =
            timeline.getNextWindowIndex(
                lastWindowIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled);
        if (lastWindowIndex != C.INDEX_UNSET) {
          queue.add(
              new MediaSessionCompat.QueueItem(
                  getMediaDescription(player, lastWindowIndex), lastWindowIndex));
        }
      }
      if (firstWindowIndex != C.INDEX_UNSET && queue.size() < queueSize) {
        firstWindowIndex =
            timeline.getPreviousWindowIndex(
                firstWindowIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled);
        if (firstWindowIndex != C.INDEX_UNSET) {
          queue.addFirst(
              new MediaSessionCompat.QueueItem(
                  getMediaDescription(player, firstWindowIndex), firstWindowIndex));
        }
      }
    }
    mediaSession.setQueue(new ArrayList<>(queue));
    activeQueueItemId = currentWindowIndex;
  }
}
