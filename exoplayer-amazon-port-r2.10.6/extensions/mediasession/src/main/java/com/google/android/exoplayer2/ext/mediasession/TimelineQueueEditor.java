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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Util;
import java.util.List;

/**
 * A {@link MediaSessionConnector.QueueEditor} implementation based on the {@link
 * ConcatenatingMediaSource}.
 *
 * <p>This class implements the {@link MediaSessionConnector.CommandReceiver} interface and handles
 * the {@link #COMMAND_MOVE_QUEUE_ITEM} to move a queue item instead of removing and inserting it.
 * This allows to move the currently playing window without interrupting playback.
 */
public final class TimelineQueueEditor
    implements MediaSessionConnector.QueueEditor, MediaSessionConnector.CommandReceiver {

  public static final String COMMAND_MOVE_QUEUE_ITEM = "exo_move_window";
  public static final String EXTRA_FROM_INDEX = "from_index";
  public static final String EXTRA_TO_INDEX = "to_index";

  /**
   * Factory to create {@link MediaSource}s.
   */
  public interface MediaSourceFactory {
    /**
     * Creates a {@link MediaSource} for the given {@link MediaDescriptionCompat}.
     *
     * @param description The {@link MediaDescriptionCompat} to create a media source for.
     * @return A {@link MediaSource} or {@code null} if no source can be created for the given
     *     description.
     */
    @Nullable MediaSource createMediaSource(MediaDescriptionCompat description);
  }

  /**
   * Adapter to get {@link MediaDescriptionCompat} of items in the queue and to notify the
   * application about changes in the queue to sync the data structure backing the
   * {@link MediaSessionConnector}.
   */
  public interface QueueDataAdapter {
    /**
     * Adds a {@link MediaDescriptionCompat} at the given {@code position}.
     *
     * @param position The position at which to add.
     * @param description The {@link MediaDescriptionCompat} to be added.
     */
    void add(int position, MediaDescriptionCompat description);
    /**
     * Removes the item at the given {@code position}.
     *
     * @param position The position at which to remove the item.
     */
    void remove(int position);
    /**
     * Moves a queue item from position {@code from} to position {@code to}.
     *
     * @param from The position from which to remove the item.
     * @param to The target position to which to move the item.
     */
    void move(int from, int to);
  }

  /**
   * Used to evaluate whether two {@link MediaDescriptionCompat} are considered equal.
   */
  interface MediaDescriptionEqualityChecker {
    /**
     * Returns {@code true} whether the descriptions are considered equal.
     *
     * @param d1 The first {@link MediaDescriptionCompat}.
     * @param d2 The second {@link MediaDescriptionCompat}.
     * @return {@code true} if considered equal.
     */
    boolean equals(MediaDescriptionCompat d1, MediaDescriptionCompat d2);
  }

  /**
   * Media description comparator comparing the media IDs. Media IDs are considered equals if both
   * are {@code null}.
   */
  public static final class MediaIdEqualityChecker implements MediaDescriptionEqualityChecker {

    @Override
    public boolean equals(MediaDescriptionCompat d1, MediaDescriptionCompat d2) {
      return Util.areEqual(d1.getMediaId(), d2.getMediaId());
    }

  }

  private final MediaControllerCompat mediaController;
  private final QueueDataAdapter queueDataAdapter;
  private final MediaSourceFactory sourceFactory;
  private final MediaDescriptionEqualityChecker equalityChecker;
  private final ConcatenatingMediaSource queueMediaSource;

  /**
   * Creates a new {@link TimelineQueueEditor} with a given mediaSourceFactory.
   *
   * @param mediaController A {@link MediaControllerCompat} to read the current queue.
   * @param queueMediaSource The {@link ConcatenatingMediaSource} to manipulate.
   * @param queueDataAdapter A {@link QueueDataAdapter} to change the backing data.
   * @param sourceFactory The {@link MediaSourceFactory} to build media sources.
   */
  public TimelineQueueEditor(
      @NonNull MediaControllerCompat mediaController,
      @NonNull ConcatenatingMediaSource queueMediaSource,
      @NonNull QueueDataAdapter queueDataAdapter,
      @NonNull MediaSourceFactory sourceFactory) {
    this(mediaController, queueMediaSource, queueDataAdapter, sourceFactory,
        new MediaIdEqualityChecker());
  }

  /**
   * Creates a new {@link TimelineQueueEditor} with a given mediaSourceFactory.
   *
   * @param mediaController A {@link MediaControllerCompat} to read the current queue.
   * @param queueMediaSource The {@link ConcatenatingMediaSource} to manipulate.
   * @param queueDataAdapter A {@link QueueDataAdapter} to change the backing data.
   * @param sourceFactory The {@link MediaSourceFactory} to build media sources.
   * @param equalityChecker The {@link MediaDescriptionEqualityChecker} to match queue items.
   */
  public TimelineQueueEditor(
      @NonNull MediaControllerCompat mediaController,
      @NonNull ConcatenatingMediaSource queueMediaSource,
      @NonNull QueueDataAdapter queueDataAdapter,
      @NonNull MediaSourceFactory sourceFactory,
      @NonNull MediaDescriptionEqualityChecker equalityChecker) {
    this.mediaController = mediaController;
    this.queueMediaSource = queueMediaSource;
    this.queueDataAdapter = queueDataAdapter;
    this.sourceFactory = sourceFactory;
    this.equalityChecker = equalityChecker;
  }

  @Override
  public void onAddQueueItem(Player player, MediaDescriptionCompat description) {
    onAddQueueItem(player, description, player.getCurrentTimeline().getWindowCount());
  }

  @Override
  public void onAddQueueItem(Player player, MediaDescriptionCompat description, int index) {
    MediaSource mediaSource = sourceFactory.createMediaSource(description);
    if (mediaSource != null) {
      queueDataAdapter.add(index, description);
      queueMediaSource.addMediaSource(index, mediaSource);
    }
  }

  @Override
  public void onRemoveQueueItem(Player player, MediaDescriptionCompat description) {
    List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
    for (int i = 0; i < queue.size(); i++) {
      if (equalityChecker.equals(queue.get(i).getDescription(), description)) {
        queueDataAdapter.remove(i);
        queueMediaSource.removeMediaSource(i);
        return;
      }
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
    if (!COMMAND_MOVE_QUEUE_ITEM.equals(command)) {
      return false;
    }
    int from = extras.getInt(EXTRA_FROM_INDEX, C.INDEX_UNSET);
    int to = extras.getInt(EXTRA_TO_INDEX, C.INDEX_UNSET);
    if (from != C.INDEX_UNSET && to != C.INDEX_UNSET) {
      queueDataAdapter.move(from, to);
      queueMediaSource.moveMediaSource(from, to);
    }
    return true;
  }

}
