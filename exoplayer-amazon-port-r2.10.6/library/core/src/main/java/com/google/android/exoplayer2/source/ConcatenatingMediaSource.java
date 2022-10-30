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
package com.google.android.exoplayer2.source;

import android.os.Handler;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource.MediaSourceHolder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. It is valid for the same {@link MediaSource} instance to be present more than
 * once in the concatenation. Access to this class is thread-safe.
 */
public class ConcatenatingMediaSource extends CompositeMediaSource<MediaSourceHolder> {

  private static final int MSG_ADD = 0;
  private static final int MSG_REMOVE = 1;
  private static final int MSG_MOVE = 2;
  private static final int MSG_SET_SHUFFLE_ORDER = 3;
  private static final int MSG_UPDATE_TIMELINE = 4;
  private static final int MSG_ON_COMPLETION = 5;

  // Accessed on any thread.
  @GuardedBy("this")
  private final List<MediaSourceHolder> mediaSourcesPublic;

  @GuardedBy("this")
  private final Set<HandlerAndRunnable> pendingOnCompletionActions;

  @GuardedBy("this")
  @Nullable
  private Handler playbackThreadHandler;

  // Accessed on the playback thread only.
  private final List<MediaSourceHolder> mediaSourceHolders;
  private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final Map<Object, MediaSourceHolder> mediaSourceByUid;
  private final boolean isAtomic;
  private final boolean useLazyPreparation;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private boolean timelineUpdateScheduled;
  private Set<HandlerAndRunnable> nextTimelineUpdateOnCompletionActions;
  private ShuffleOrder shuffleOrder;
  private int windowCount;
  private int periodCount;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this(/* isAtomic= */ false, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isAtomic, MediaSource... mediaSources) {
    this(isAtomic, new DefaultShuffleOrder(0), mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(
      boolean isAtomic, ShuffleOrder shuffleOrder, MediaSource... mediaSources) {
    this(isAtomic, /* useLazyPreparation= */ false, shuffleOrder, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  @SuppressWarnings("initialization")
  public ConcatenatingMediaSource(
      boolean isAtomic,
      boolean useLazyPreparation,
      ShuffleOrder shuffleOrder,
      MediaSource... mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    this.shuffleOrder = shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
    this.mediaSourceByMediaPeriod = new IdentityHashMap<>();
    this.mediaSourceByUid = new HashMap<>();
    this.mediaSourcesPublic = new ArrayList<>();
    this.mediaSourceHolders = new ArrayList<>();
    this.nextTimelineUpdateOnCompletionActions = new HashSet<>();
    this.pendingOnCompletionActions = new HashSet<>();
    this.isAtomic = isAtomic;
    this.useLazyPreparation = useLazyPreparation;
    window = new Timeline.Window();
    period = new Timeline.Period();
    addMediaSources(Arrays.asList(mediaSources));
  }

  /**
   * Appends a {@link MediaSource} to the playlist.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public final synchronized void addMediaSource(MediaSource mediaSource) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource);
  }

  /**
   * Appends a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public final synchronized void addMediaSource(
      MediaSource mediaSource, Handler handler, Runnable onCompletionAction) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, handler, onCompletionAction);
  }

  /**
   * Adds a {@link MediaSource} to the playlist.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public final synchronized void addMediaSource(int index, MediaSource mediaSource) {
    addPublicMediaSources(
        index,
        Collections.singletonList(mediaSource),
        /* handler= */ null,
        /* onCompletionAction= */ null);
  }

  /**
   * Adds a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public final synchronized void addMediaSource(
      int index, MediaSource mediaSource, Handler handler, Runnable onCompletionAction) {
    addPublicMediaSources(
        index, Collections.singletonList(mediaSource), handler, onCompletionAction);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public final synchronized void addMediaSources(Collection<MediaSource> mediaSources) {
    addPublicMediaSources(
        mediaSourcesPublic.size(),
        mediaSources,
        /* handler= */ null,
        /* onCompletionAction= */ null);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist and executes a custom action on
   * completion.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public final synchronized void addMediaSources(
      Collection<MediaSource> mediaSources, Handler handler, Runnable onCompletionAction) {
    addPublicMediaSources(mediaSourcesPublic.size(), mediaSources, handler, onCompletionAction);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public final synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources) {
    addPublicMediaSources(index, mediaSources, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public final synchronized void addMediaSources(
      int index,
      Collection<MediaSource> mediaSources,
      Handler handler,
      Runnable onCompletionAction) {
    addPublicMediaSources(index, mediaSources, handler, onCompletionAction);
  }

  /**
   * Removes a {@link MediaSource} from the playlist.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public final synchronized void removeMediaSource(int index) {
    removePublicMediaSources(index, index + 1, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Removes a {@link MediaSource} from the playlist and executes a custom action on completion.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int, Handler, Runnable)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int, Handler, Runnable)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been removed from the playlist.
   */
  public final synchronized void removeMediaSource(
      int index, Handler handler, Runnable onCompletionAction) {
    removePublicMediaSources(index, index + 1, handler, onCompletionAction);
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded).
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @throws IndexOutOfBoundsException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public final synchronized void removeMediaSourceRange(int fromIndex, int toIndex) {
    removePublicMediaSources(
        fromIndex, toIndex, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded), and executes a custom action on completion.
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source range has been removed from the playlist.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public final synchronized void removeMediaSourceRange(
      int fromIndex, int toIndex, Handler handler, Runnable onCompletionAction) {
    removePublicMediaSources(fromIndex, toIndex, handler, onCompletionAction);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public final synchronized void moveMediaSource(int currentIndex, int newIndex) {
    movePublicMediaSource(
        currentIndex, newIndex, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist and executes a custom action on
   * completion.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been moved.
   */
  public final synchronized void moveMediaSource(
      int currentIndex, int newIndex, Handler handler, Runnable onCompletionAction) {
    movePublicMediaSource(currentIndex, newIndex, handler, onCompletionAction);
  }

  /** Clears the playlist. */
  public final synchronized void clear() {
    removeMediaSourceRange(0, getSize());
  }

  /**
   * Clears the playlist and executes a custom action on completion.
   *
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the playlist
   *     has been cleared.
   */
  public final synchronized void clear(Handler handler, Runnable onCompletionAction) {
    removeMediaSourceRange(0, getSize(), handler, onCompletionAction);
  }

  /** Returns the number of media sources in the playlist. */
  public final synchronized int getSize() {
    return mediaSourcesPublic.size();
  }

  /**
   * Returns the {@link MediaSource} at a specified index.
   *
   * @param index An index in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @return The {@link MediaSource} at this index.
   */
  public final synchronized MediaSource getMediaSource(int index) {
    return mediaSourcesPublic.get(index).mediaSource;
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   */
  public final synchronized void setShuffleOrder(ShuffleOrder shuffleOrder) {
    setPublicShuffleOrder(shuffleOrder, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the shuffle
   *     order has been changed.
   */
  public final synchronized void setShuffleOrder(
      ShuffleOrder shuffleOrder, Handler handler, Runnable onCompletionAction) {
    setPublicShuffleOrder(shuffleOrder, handler, onCompletionAction);
  }

  // CompositeMediaSource implementation.

  @Override
  @Nullable
  public Object getTag() {
    return null;
  }

  @Override
  public final synchronized void prepareSourceInternal(
      @Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    playbackThreadHandler = new Handler(/* callback= */ this::handleMessage);
    if (mediaSourcesPublic.isEmpty()) {
      updateTimelineAndScheduleOnCompletionActions();
    } else {
      shuffleOrder = shuffleOrder.cloneAndInsert(0, mediaSourcesPublic.size());
      addMediaSourcesInternal(0, mediaSourcesPublic);
      scheduleTimelineUpdate();
    }
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing. Source info refresh errors of the individual sources will be thrown when calling
    // DeferredMediaPeriod.maybeThrowPrepareError.
  }

  @Override
  public final MediaPeriod createPeriod(
      MediaPeriodId id, Allocator allocator, long startPositionUs) {
    Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
    MediaSourceHolder holder = mediaSourceByUid.get(mediaSourceHolderUid);
    if (holder == null) {
      // Stale event. The media source has already been removed.
      holder = new MediaSourceHolder(new DummyMediaSource());
      holder.hasStartedPreparing = true;
    }
    DeferredMediaPeriod mediaPeriod =
        new DeferredMediaPeriod(holder.mediaSource, id, allocator, startPositionUs);
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    holder.activeMediaPeriods.add(mediaPeriod);
    if (!holder.hasStartedPreparing) {
      holder.hasStartedPreparing = true;
      prepareChildSource(holder, holder.mediaSource);
    } else if (holder.isPrepared) {
      MediaPeriodId idInSource = id.copyWithPeriodUid(getChildPeriodUid(holder, id.periodUid));
      mediaPeriod.createPeriod(idInSource);
    }
    return mediaPeriod;
  }

  @Override
  public final void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder =
        Assertions.checkNotNull(mediaSourceByMediaPeriod.remove(mediaPeriod));
    ((DeferredMediaPeriod) mediaPeriod).releasePeriod();
    holder.activeMediaPeriods.remove(mediaPeriod);
    maybeReleaseChildSource(holder);
  }

  @Override
  public final synchronized void releaseSourceInternal() {
    super.releaseSourceInternal();
    mediaSourceHolders.clear();
    mediaSourceByUid.clear();
    shuffleOrder = shuffleOrder.cloneAndClear();
    windowCount = 0;
    periodCount = 0;
    if (playbackThreadHandler != null) {
      playbackThreadHandler.removeCallbacksAndMessages(null);
      playbackThreadHandler = null;
    }
    timelineUpdateScheduled = false;
    nextTimelineUpdateOnCompletionActions.clear();
    dispatchOnCompletionActions(pendingOnCompletionActions);
  }

  @Override
  protected final void onChildSourceInfoRefreshed(
      MediaSourceHolder mediaSourceHolder,
      MediaSource mediaSource,
      Timeline timeline,
      @Nullable Object manifest) {
    updateMediaSourceInternal(mediaSourceHolder, timeline);
  }

  @Override
  protected @Nullable MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaSourceHolder mediaSourceHolder, MediaPeriodId mediaPeriodId) {
    for (int i = 0; i < mediaSourceHolder.activeMediaPeriods.size(); i++) {
      // Ensure the reported media period id has the same window sequence number as the one created
      // by this media source. Otherwise it does not belong to this child source.
      if (mediaSourceHolder.activeMediaPeriods.get(i).id.windowSequenceNumber
          == mediaPeriodId.windowSequenceNumber) {
        Object periodUid = getPeriodUid(mediaSourceHolder, mediaPeriodId.periodUid);
        return mediaPeriodId.copyWithPeriodUid(periodUid);
      }
    }
    return null;
  }

  @Override
  protected int getWindowIndexForChildWindowIndex(
      MediaSourceHolder mediaSourceHolder, int windowIndex) {
    return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
  }

  // Internal methods. Called from any thread.

  @GuardedBy("this")
  private void addPublicMediaSources(
      int index,
      Collection<MediaSource> mediaSources,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    List<MediaSourceHolder> mediaSourceHolders = new ArrayList<>(mediaSources.size());
    for (MediaSource mediaSource : mediaSources) {
      mediaSourceHolders.add(new MediaSourceHolder(mediaSource));
    }
    mediaSourcesPublic.addAll(index, mediaSourceHolders);
    if (playbackThreadHandler != null && !mediaSources.isEmpty()) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_ADD, new MessageData<>(index, mediaSourceHolders, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void removePublicMediaSources(
      int fromIndex,
      int toIndex,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    Util.removeRange(mediaSourcesPublic, fromIndex, toIndex);
    if (playbackThreadHandler != null) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_REMOVE, new MessageData<>(fromIndex, toIndex, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void movePublicMediaSource(
      int currentIndex,
      int newIndex,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    mediaSourcesPublic.add(newIndex, mediaSourcesPublic.remove(currentIndex));
    if (playbackThreadHandler != null) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_MOVE, new MessageData<>(currentIndex, newIndex, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void setPublicShuffleOrder(
      ShuffleOrder shuffleOrder, @Nullable Handler handler, @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    if (playbackThreadHandler != null) {
      int size = getSize();
      if (shuffleOrder.getLength() != size) {
        shuffleOrder =
            shuffleOrder
                .cloneAndClear()
                .cloneAndInsert(/* insertionIndex= */ 0, /* insertionCount= */ size);
      }
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(
              MSG_SET_SHUFFLE_ORDER,
              new MessageData<>(/* index= */ 0, shuffleOrder, callbackAction))
          .sendToTarget();
    } else {
      this.shuffleOrder =
          shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
      if (onCompletionAction != null && handler != null) {
        handler.post(onCompletionAction);
      }
    }
  }

  @GuardedBy("this")
  @Nullable
  private HandlerAndRunnable createOnCompletionAction(
      @Nullable Handler handler, @Nullable Runnable runnable) {
    if (handler == null || runnable == null) {
      return null;
    }
    HandlerAndRunnable handlerAndRunnable = new HandlerAndRunnable(handler, runnable);
    pendingOnCompletionActions.add(handlerAndRunnable);
    return handlerAndRunnable;
  }

  // Internal methods. Called on the playback thread.

  @SuppressWarnings("unchecked")
  private boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_ADD:
        MessageData<Collection<MediaSourceHolder>> addMessage =
            (MessageData<Collection<MediaSourceHolder>>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrder.cloneAndInsert(addMessage.index, addMessage.customData.size());
        addMediaSourcesInternal(addMessage.index, addMessage.customData);
        scheduleTimelineUpdate(addMessage.onCompletionAction);
        break;
      case MSG_REMOVE:
        MessageData<Integer> removeMessage = (MessageData<Integer>) Util.castNonNull(msg.obj);
        int fromIndex = removeMessage.index;
        int toIndex = removeMessage.customData;
        if (fromIndex == 0 && toIndex == shuffleOrder.getLength()) {
          shuffleOrder = shuffleOrder.cloneAndClear();
        } else {
          shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndex);
        }
        for (int index = toIndex - 1; index >= fromIndex; index--) {
          removeMediaSourceInternal(index);
        }
        scheduleTimelineUpdate(removeMessage.onCompletionAction);
        break;
      case MSG_MOVE:
        MessageData<Integer> moveMessage = (MessageData<Integer>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrder.cloneAndRemove(moveMessage.index, moveMessage.index + 1);
        shuffleOrder = shuffleOrder.cloneAndInsert(moveMessage.customData, 1);
        moveMediaSourceInternal(moveMessage.index, moveMessage.customData);
        scheduleTimelineUpdate(moveMessage.onCompletionAction);
        break;
      case MSG_SET_SHUFFLE_ORDER:
        MessageData<ShuffleOrder> shuffleOrderMessage =
            (MessageData<ShuffleOrder>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrderMessage.customData;
        scheduleTimelineUpdate(shuffleOrderMessage.onCompletionAction);
        break;
      case MSG_UPDATE_TIMELINE:
        updateTimelineAndScheduleOnCompletionActions();
        break;
      case MSG_ON_COMPLETION:
        Set<HandlerAndRunnable> actions = (Set<HandlerAndRunnable>) Util.castNonNull(msg.obj);
        dispatchOnCompletionActions(actions);
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }

  private void scheduleTimelineUpdate() {
    scheduleTimelineUpdate(/* onCompletionAction= */ null);
  }

  private void scheduleTimelineUpdate(@Nullable HandlerAndRunnable onCompletionAction) {
    if (!timelineUpdateScheduled) {
      getPlaybackThreadHandlerOnPlaybackThread().obtainMessage(MSG_UPDATE_TIMELINE).sendToTarget();
      timelineUpdateScheduled = true;
    }
    if (onCompletionAction != null) {
      nextTimelineUpdateOnCompletionActions.add(onCompletionAction);
    }
  }

  private void updateTimelineAndScheduleOnCompletionActions() {
    timelineUpdateScheduled = false;
    Set<HandlerAndRunnable> onCompletionActions = nextTimelineUpdateOnCompletionActions;
    nextTimelineUpdateOnCompletionActions = new HashSet<>();
    refreshSourceInfo(
        new ConcatenatedTimeline(
            mediaSourceHolders, windowCount, periodCount, shuffleOrder, isAtomic),
        /* manifest= */ null);
    getPlaybackThreadHandlerOnPlaybackThread()
        .obtainMessage(MSG_ON_COMPLETION, onCompletionActions)
        .sendToTarget();
  }

  @SuppressWarnings("GuardedBy")
  private Handler getPlaybackThreadHandlerOnPlaybackThread() {
    // Write access to this value happens on the playback thread only, so playback thread reads
    // don't need to be synchronized.
    return Assertions.checkNotNull(playbackThreadHandler);
  }

  private synchronized void dispatchOnCompletionActions(
      Set<HandlerAndRunnable> onCompletionActions) {
    for (HandlerAndRunnable pendingAction : onCompletionActions) {
      pendingAction.dispatch();
    }
    pendingOnCompletionActions.removeAll(onCompletionActions);
  }

  private void addMediaSourcesInternal(
      int index, Collection<MediaSourceHolder> mediaSourceHolders) {
    for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
      addMediaSourceInternal(index++, mediaSourceHolder);
    }
  }

  private void addMediaSourceInternal(int newIndex, MediaSourceHolder newMediaSourceHolder) {
    if (newIndex > 0) {
      MediaSourceHolder previousHolder = mediaSourceHolders.get(newIndex - 1);
      newMediaSourceHolder.reset(
          newIndex,
          previousHolder.firstWindowIndexInChild + previousHolder.timeline.getWindowCount(),
          previousHolder.firstPeriodIndexInChild + previousHolder.timeline.getPeriodCount());
    } else {
      newMediaSourceHolder.reset(
          newIndex, /* firstWindowIndexInChild= */ 0, /* firstPeriodIndexInChild= */ 0);
    }
    correctOffsets(
        newIndex,
        /* childIndexUpdate= */ 1,
        newMediaSourceHolder.timeline.getWindowCount(),
        newMediaSourceHolder.timeline.getPeriodCount());
    mediaSourceHolders.add(newIndex, newMediaSourceHolder);
    mediaSourceByUid.put(newMediaSourceHolder.uid, newMediaSourceHolder);
    if (!useLazyPreparation) {
      newMediaSourceHolder.hasStartedPreparing = true;
      prepareChildSource(newMediaSourceHolder, newMediaSourceHolder.mediaSource);
    }
  }

  private void updateMediaSourceInternal(MediaSourceHolder mediaSourceHolder, Timeline timeline) {
    if (mediaSourceHolder == null) {
      throw new IllegalArgumentException();
    }
    DeferredTimeline deferredTimeline = mediaSourceHolder.timeline;
    if (deferredTimeline.getTimeline() == timeline) {
      return;
    }
    int windowOffsetUpdate = timeline.getWindowCount() - deferredTimeline.getWindowCount();
    int periodOffsetUpdate = timeline.getPeriodCount() - deferredTimeline.getPeriodCount();
    if (windowOffsetUpdate != 0 || periodOffsetUpdate != 0) {
      correctOffsets(
          mediaSourceHolder.childIndex + 1,
          /* childIndexUpdate= */ 0,
          windowOffsetUpdate,
          periodOffsetUpdate);
    }
    if (mediaSourceHolder.isPrepared) {
      mediaSourceHolder.timeline = deferredTimeline.cloneWithUpdatedTimeline(timeline);
    } else if (timeline.isEmpty()) {
      mediaSourceHolder.timeline =
          DeferredTimeline.createWithRealTimeline(timeline, DeferredTimeline.DUMMY_ID);
    } else {
      // We should have at most one deferred media period for the DummyTimeline because the duration
      // is unset and we don't load beyond periods with unset duration. We need to figure out how to
      // handle the prepare positions of multiple deferred media periods, should that ever change.
      Assertions.checkState(mediaSourceHolder.activeMediaPeriods.size() <= 1);
      DeferredMediaPeriod deferredMediaPeriod =
          mediaSourceHolder.activeMediaPeriods.isEmpty()
              ? null
              : mediaSourceHolder.activeMediaPeriods.get(0);
      // Determine first period and the start position.
      // This will be:
      //  1. The default window start position if no deferred period has been created yet.
      //  2. The non-zero prepare position of the deferred period under the assumption that this is
      //     a non-zero initial seek position in the window.
      //  3. The default window start position if the deferred period has a prepare position of zero
      //     under the assumption that the prepare position of zero was used because it's the
      //     default position of the DummyTimeline window. Note that this will override an
      //     intentional seek to zero for a window with a non-zero default position. This is
      //     unlikely to be a problem as a non-zero default position usually only occurs for live
      //     playbacks and seeking to zero in a live window would cause BehindLiveWindowExceptions
      //     anyway.
      timeline.getWindow(/* windowIndex= */ 0, window);
      long windowStartPositionUs = window.getDefaultPositionUs();
      if (deferredMediaPeriod != null) {
        long periodPreparePositionUs = deferredMediaPeriod.getPreparePositionUs();
        if (periodPreparePositionUs != 0) {
          windowStartPositionUs = periodPreparePositionUs;
        }
      }
      Pair<Object, Long> periodPosition =
          timeline.getPeriodPosition(window, period, /* windowIndex= */ 0, windowStartPositionUs);
      Object periodUid = periodPosition.first;
      long periodPositionUs = periodPosition.second;
      mediaSourceHolder.timeline = DeferredTimeline.createWithRealTimeline(timeline, periodUid);
      if (deferredMediaPeriod != null) {
        deferredMediaPeriod.overridePreparePositionUs(periodPositionUs);
        MediaPeriodId idInSource =
            deferredMediaPeriod.id.copyWithPeriodUid(
                getChildPeriodUid(mediaSourceHolder, deferredMediaPeriod.id.periodUid));
        deferredMediaPeriod.createPeriod(idInSource);
      }
    }
    mediaSourceHolder.isPrepared = true;
    scheduleTimelineUpdate();
  }

  private void removeMediaSourceInternal(int index) {
    MediaSourceHolder holder = mediaSourceHolders.remove(index);
    mediaSourceByUid.remove(holder.uid);
    Timeline oldTimeline = holder.timeline;
    correctOffsets(
        index,
        /* childIndexUpdate= */ -1,
        -oldTimeline.getWindowCount(),
        -oldTimeline.getPeriodCount());
    holder.isRemoved = true;
    maybeReleaseChildSource(holder);
  }

  private void moveMediaSourceInternal(int currentIndex, int newIndex) {
    int startIndex = Math.min(currentIndex, newIndex);
    int endIndex = Math.max(currentIndex, newIndex);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    int periodOffset = mediaSourceHolders.get(startIndex).firstPeriodIndexInChild;
    mediaSourceHolders.add(newIndex, mediaSourceHolders.remove(currentIndex));
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.firstWindowIndexInChild = windowOffset;
      holder.firstPeriodIndexInChild = periodOffset;
      windowOffset += holder.timeline.getWindowCount();
      periodOffset += holder.timeline.getPeriodCount();
    }
  }

  private void correctOffsets(
      int startIndex, int childIndexUpdate, int windowOffsetUpdate, int periodOffsetUpdate) {
    windowCount += windowOffsetUpdate;
    periodCount += periodOffsetUpdate;
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      mediaSourceHolders.get(i).childIndex += childIndexUpdate;
      mediaSourceHolders.get(i).firstWindowIndexInChild += windowOffsetUpdate;
      mediaSourceHolders.get(i).firstPeriodIndexInChild += periodOffsetUpdate;
    }
  }

  private void maybeReleaseChildSource(MediaSourceHolder mediaSourceHolder) {
    // Release if the source has been removed from the playlist, but only if it has been previously
    // prepared and only if we are not waiting for an existing media period to be released.
    if (mediaSourceHolder.isRemoved
        && mediaSourceHolder.hasStartedPreparing
        && mediaSourceHolder.activeMediaPeriods.isEmpty()) {
      releaseChildSource(mediaSourceHolder);
    }
  }

  /** Return uid of media source holder from period uid of concatenated source. */
  private static Object getMediaSourceHolderUid(Object periodUid) {
    return ConcatenatedTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
  }

  /** Return uid of child period from period uid of concatenated source. */
  private static Object getChildPeriodUid(MediaSourceHolder holder, Object periodUid) {
    Object childUid = ConcatenatedTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
    return childUid.equals(DeferredTimeline.DUMMY_ID) ? holder.timeline.replacedId : childUid;
  }

  private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
    if (holder.timeline.replacedId.equals(childPeriodUid)) {
      childPeriodUid = DeferredTimeline.DUMMY_ID;
    }
    return ConcatenatedTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder implements Comparable<MediaSourceHolder> {

    public final MediaSource mediaSource;
    public final Object uid;
    public final List<DeferredMediaPeriod> activeMediaPeriods;

    public DeferredTimeline timeline;
    public int childIndex;
    public int firstWindowIndexInChild;
    public int firstPeriodIndexInChild;
    public boolean hasStartedPreparing;
    public boolean isPrepared;
    public boolean isRemoved;

    public MediaSourceHolder(MediaSource mediaSource) {
      this.mediaSource = mediaSource;
      this.timeline = DeferredTimeline.createWithDummyTimeline(mediaSource.getTag());
      this.activeMediaPeriods = new ArrayList<>();
      this.uid = new Object();
    }

    public void reset(int childIndex, int firstWindowIndexInChild, int firstPeriodIndexInChild) {
      this.childIndex = childIndex;
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.firstPeriodIndexInChild = firstPeriodIndexInChild;
      this.hasStartedPreparing = false;
      this.isPrepared = false;
      this.isRemoved = false;
      this.activeMediaPeriods.clear();
    }

    @Override
    public int compareTo(@NonNull MediaSourceHolder other) {
      return this.firstPeriodIndexInChild - other.firstPeriodIndexInChild;
    }
  }

  /** Message used to post actions from app thread to playback thread. */
  private static final class MessageData<T> {

    public final int index;
    public final T customData;
    @Nullable public final HandlerAndRunnable onCompletionAction;

    public MessageData(int index, T customData, @Nullable HandlerAndRunnable onCompletionAction) {
      this.index = index;
      this.customData = customData;
      this.onCompletionAction = onCompletionAction;
    }
  }

  /** Timeline exposing concatenated timelines of playlist media sources. */
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final int windowCount;
    private final int periodCount;
    private final int[] firstPeriodInChildIndices;
    private final int[] firstWindowInChildIndices;
    private final Timeline[] timelines;
    private final Object[] uids;
    private final HashMap<Object, Integer> childIndexByUid;

    public ConcatenatedTimeline(
        Collection<MediaSourceHolder> mediaSourceHolders,
        int windowCount,
        int periodCount,
        ShuffleOrder shuffleOrder,
        boolean isAtomic) {
      super(isAtomic, shuffleOrder);
      this.windowCount = windowCount;
      this.periodCount = periodCount;
      int childCount = mediaSourceHolders.size();
      firstPeriodInChildIndices = new int[childCount];
      firstWindowInChildIndices = new int[childCount];
      timelines = new Timeline[childCount];
      uids = new Object[childCount];
      childIndexByUid = new HashMap<>();
      int index = 0;
      for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
        timelines[index] = mediaSourceHolder.timeline;
        firstPeriodInChildIndices[index] = mediaSourceHolder.firstPeriodIndexInChild;
        firstWindowInChildIndices[index] = mediaSourceHolder.firstWindowIndexInChild;
        uids[index] = mediaSourceHolder.uid;
        childIndexByUid.put(uids[index], index++);
      }
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(firstPeriodInChildIndices, periodIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(firstWindowInChildIndices, windowIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      Integer index = childIndexByUid.get(childUid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return firstPeriodInChildIndices[childIndex];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return firstWindowInChildIndices[childIndex];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return uids[childIndex];
    }

    @Override
    public int getWindowCount() {
      return windowCount;
    }

    @Override
    public int getPeriodCount() {
      return periodCount;
    }
  }

  /**
   * Timeline used as placeholder for an unprepared media source. After preparation, a
   * DeferredTimeline is used to keep the originally assigned dummy period ID.
   */
  private static final class DeferredTimeline extends ForwardingTimeline {

    private static final Object DUMMY_ID = new Object();

    private final Object replacedId;

    /**
     * Returns an instance with a dummy timeline using the provided window tag.
     *
     * @param windowTag A window tag.
     */
    public static DeferredTimeline createWithDummyTimeline(@Nullable Object windowTag) {
      return new DeferredTimeline(new DummyTimeline(windowTag), DUMMY_ID);
    }

    /**
     * Returns an instance with a real timeline, replacing the provided period ID with the already
     * assigned dummy period ID.
     *
     * @param timeline The real timeline.
     * @param firstPeriodUid The period UID in the timeline which will be replaced by the already
     *     assigned dummy period UID.
     */
    public static DeferredTimeline createWithRealTimeline(
        Timeline timeline, Object firstPeriodUid) {
      return new DeferredTimeline(timeline, firstPeriodUid);
    }

    private DeferredTimeline(Timeline timeline, Object replacedId) {
      super(timeline);
      this.replacedId = replacedId;
    }

    /**
     * Returns a copy with an updated timeline. This keeps the existing period replacement.
     *
     * @param timeline The new timeline.
     */
    public DeferredTimeline cloneWithUpdatedTimeline(Timeline timeline) {
      return new DeferredTimeline(timeline, replacedId);
    }

    /** Returns wrapped timeline. */
    public Timeline getTimeline() {
      return timeline;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(periodIndex, period, setIds);
      if (Util.areEqual(period.uid, replacedId)) {
        period.uid = DUMMY_ID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(DUMMY_ID.equals(uid) ? replacedId : uid);
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Object uid = timeline.getUidOfPeriod(periodIndex);
      return Util.areEqual(uid, replacedId) ? DUMMY_ID : uid;
    }
  }

  /** Dummy placeholder timeline with one dynamic window with a period of indeterminate duration. */
  private static final class DummyTimeline extends Timeline {

    @Nullable private final Object tag;

    public DummyTimeline(@Nullable Object tag) {
      this.tag = tag;
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(
        int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
      return window.set(
          tag,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* isSeekable= */ false,
          // Dynamic window to indicate pending timeline updates.
          /* isDynamic= */ true,
          /* defaultPositionUs= */ 0,
          /* durationUs= */ C.TIME_UNSET,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return period.set(
          /* id= */ 0,
          /* uid= */ DeferredTimeline.DUMMY_ID,
          /* windowIndex= */ 0,
          /* durationUs = */ C.TIME_UNSET,
          /* positionInWindowUs= */ 0);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return uid == DeferredTimeline.DUMMY_ID ? 0 : C.INDEX_UNSET;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      return DeferredTimeline.DUMMY_ID;
    }
  }

  /** Dummy media source which does nothing and does not support creating periods. */
  private static final class DummyMediaSource extends BaseMediaSource {

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
      // Do nothing.
    }

    @Override
    @Nullable
    public Object getTag() {
      return null;
    }

    @Override
    protected void releaseSourceInternal() {
      // Do nothing.
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
      // Do nothing.
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      // Do nothing.
    }
  }

  private static final class HandlerAndRunnable {

    private final Handler handler;
    private final Runnable runnable;

    public HandlerAndRunnable(Handler handler, Runnable runnable) {
      this.handler = handler;
      this.runnable = runnable;
    }

    public void dispatch() {
      handler.post(runnable);
    }
  }
}

