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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Holds a queue of media periods, from the currently playing media period at the front to the
 * loading media period at the end of the queue, with methods for controlling loading and updating
 * the queue. Also has a reference to the media period currently being read.
 */
/* package */ final class MediaPeriodQueue {

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Timeline.Period period;
  private final Timeline.Window window;

  private long nextWindowSequenceNumber;
  private Timeline timeline;
  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private @Nullable MediaPeriodHolder playing;
  private @Nullable MediaPeriodHolder reading;
  private @Nullable MediaPeriodHolder loading;
  private int length;
  private @Nullable Object oldFrontPeriodUid;
  private long oldFrontPeriodWindowSequenceNumber;

  /** Creates a new media period queue. */
  public MediaPeriodQueue() {
    period = new Timeline.Period();
    window = new Timeline.Window();
    timeline = Timeline.EMPTY;
  }

  /**
   * Sets the {@link Timeline}. Call {@link #updateQueuedPeriods(long, long)} to update the queued
   * media periods to take into account the new timeline.
   */
  public void setTimeline(Timeline timeline) {
    this.timeline = timeline;
  }

  /**
   * Sets the {@link RepeatMode} and returns whether the repeat mode change has been fully handled.
   * If not, it is necessary to seek to the current playback position.
   */
  public boolean updateRepeatMode(@RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
    return updateForPlaybackModeChange();
  }

  /**
   * Sets whether shuffling is enabled and returns whether the shuffle mode change has been fully
   * handled. If not, it is necessary to seek to the current playback position.
   */
  public boolean updateShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
    return updateForPlaybackModeChange();
  }

  /** Returns whether {@code mediaPeriod} is the current loading media period. */
  public boolean isLoading(MediaPeriod mediaPeriod) {
    return loading != null && loading.mediaPeriod == mediaPeriod;
  }

  /**
   * If there is a loading period, reevaluates its buffer.
   *
   * @param rendererPositionUs The current renderer position.
   */
  public void reevaluateBuffer(long rendererPositionUs) {
    if (loading != null) {
      loading.reevaluateBuffer(rendererPositionUs);
    }
  }

  /** Returns whether a new loading media period should be enqueued, if available. */
  public boolean shouldLoadNextMediaPeriod() {
    return loading == null
        || (!loading.info.isFinal
            && loading.isFullyBuffered()
            && loading.info.durationUs != C.TIME_UNSET
            && length < MAXIMUM_BUFFER_AHEAD_PERIODS);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the next media period to load.
   *
   * @param rendererPositionUs The current renderer position.
   * @param playbackInfo The current playback information.
   * @return The {@link MediaPeriodInfo} for the next media period to load, or {@code null} if not
   *     yet known.
   */
  public @Nullable MediaPeriodInfo getNextMediaPeriodInfo(
      long rendererPositionUs, PlaybackInfo playbackInfo) {
    return loading == null
        ? getFirstMediaPeriodInfo(playbackInfo)
        : getFollowingMediaPeriodInfo(loading, rendererPositionUs);
  }

  /**
   * Enqueues a new media period based on the specified information as the new loading media period,
   * and returns it.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSource The media source that produced the media period.
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriod enqueueNextMediaPeriod(
      RendererCapabilities[] rendererCapabilities,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSource mediaSource,
      MediaPeriodInfo info) {
    long rendererPositionOffsetUs =
        loading == null
            ? (info.id.isAd() && info.contentPositionUs != C.TIME_UNSET
                ? info.contentPositionUs
                : 0)
            : (loading.getRendererOffset() + loading.info.durationUs - info.startPositionUs);
    MediaPeriodHolder newPeriodHolder =
        new MediaPeriodHolder(
            rendererCapabilities,
            rendererPositionOffsetUs,
            trackSelector,
            allocator,
            mediaSource,
            info);
    if (loading != null) {
      Assertions.checkState(hasPlayingPeriod());
      loading.setNext(newPeriodHolder);
    }
    oldFrontPeriodUid = null;
    loading = newPeriodHolder;
    length++;
    return newPeriodHolder.mediaPeriod;
  }

  /**
   * Returns the loading period holder which is at the end of the queue, or null if the queue is
   * empty.
   */
  public MediaPeriodHolder getLoadingPeriod() {
    return loading;
  }

  /**
   * Returns the playing period holder which is at the front of the queue, or null if the queue is
   * empty or hasn't started playing.
   */
  public MediaPeriodHolder getPlayingPeriod() {
    return playing;
  }

  /**
   * Returns the reading period holder, or null if the queue is empty or the player hasn't started
   * reading.
   */
  public MediaPeriodHolder getReadingPeriod() {
    return reading;
  }

  /**
   * Returns the period holder in the front of the queue which is the playing period holder when
   * playing, or null if the queue is empty.
   */
  public MediaPeriodHolder getFrontPeriod() {
    return hasPlayingPeriod() ? playing : loading;
  }

  /** Returns whether the reading and playing period holders are set. */
  public boolean hasPlayingPeriod() {
    return playing != null;
  }

  /**
   * Continues reading from the next period holder in the queue.
   *
   * @return The updated reading period holder.
   */
  public MediaPeriodHolder advanceReadingPeriod() {
    Assertions.checkState(reading != null && reading.getNext() != null);
    reading = reading.getNext();
    return reading;
  }

  /**
   * Dequeues the playing period holder from the front of the queue and advances the playing period
   * holder to be the next item in the queue. If the playing period holder is unset, set it to the
   * item in the front of the queue.
   *
   * @return The updated playing period holder, or null if the queue is or becomes empty.
   */
  public MediaPeriodHolder advancePlayingPeriod() {
    if (playing != null) {
      if (playing == reading) {
        reading = playing.getNext();
      }
      playing.release();
      length--;
      if (length == 0) {
        loading = null;
        oldFrontPeriodUid = playing.uid;
        oldFrontPeriodWindowSequenceNumber = playing.info.id.windowSequenceNumber;
      }
      playing = playing.getNext();
    } else {
      playing = loading;
      reading = loading;
    }
    return playing;
  }

  /**
   * Removes all period holders after the given period holder. This process may also remove the
   * currently reading period holder. If that is the case, the reading period holder is set to be
   * the same as the playing period holder at the front of the queue.
   *
   * @param mediaPeriodHolder The media period holder that shall be the new end of the queue.
   * @return Whether the reading period has been removed.
   */
  public boolean removeAfter(MediaPeriodHolder mediaPeriodHolder) {
    Assertions.checkState(mediaPeriodHolder != null);
    boolean removedReading = false;
    loading = mediaPeriodHolder;
    while (mediaPeriodHolder.getNext() != null) {
      mediaPeriodHolder = mediaPeriodHolder.getNext();
      if (mediaPeriodHolder == reading) {
        reading = playing;
        removedReading = true;
      }
      mediaPeriodHolder.release();
      length--;
    }
    loading.setNext(null);
    return removedReading;
  }

  /**
   * Clears the queue.
   *
   * @param keepFrontPeriodUid Whether the queue should keep the id of the media period in the front
   *     of queue (typically the playing one) for later reuse.
   */
  public void clear(boolean keepFrontPeriodUid) {
    MediaPeriodHolder front = getFrontPeriod();
    if (front != null) {
      oldFrontPeriodUid = keepFrontPeriodUid ? front.uid : null;
      oldFrontPeriodWindowSequenceNumber = front.info.id.windowSequenceNumber;
      front.release();
      removeAfter(front);
    } else if (!keepFrontPeriodUid) {
      oldFrontPeriodUid = null;
    }
    playing = null;
    loading = null;
    reading = null;
    length = 0;
  }

  /**
   * Updates media periods in the queue to take into account the latest timeline, and returns
   * whether the timeline change has been fully handled. If not, it is necessary to seek to the
   * current playback position. The method assumes that the first media period in the queue is still
   * consistent with the new timeline.
   *
   * @param rendererPositionUs The current renderer position in microseconds.
   * @param maxRendererReadPositionUs The maximum renderer position up to which renderers have read
   *     the current reading media period in microseconds, or {@link C#TIME_END_OF_SOURCE} if they
   *     have read to the end.
   * @return Whether the timeline change has been handled completely.
   */
  public boolean updateQueuedPeriods(long rendererPositionUs, long maxRendererReadPositionUs) {
    // TODO: Merge this into setTimeline so that the queue gets updated as soon as the new timeline
    // is set, once all cases handled by ExoPlayerImplInternal.handleSourceInfoRefreshed can be
    // handled here.
    MediaPeriodHolder previousPeriodHolder = null;
    MediaPeriodHolder periodHolder = getFrontPeriod();
    while (periodHolder != null) {
      MediaPeriodInfo oldPeriodInfo = periodHolder.info;

      // Get period info based on new timeline.
      MediaPeriodInfo newPeriodInfo;
      if (previousPeriodHolder == null) {
        // The id and start position of the first period have already been verified by
        // ExoPlayerImplInternal.handleSourceInfoRefreshed. Just update duration, isLastInTimeline
        // and isLastInPeriod flags.
        newPeriodInfo = getUpdatedMediaPeriodInfo(oldPeriodInfo);
      } else {
        newPeriodInfo = getFollowingMediaPeriodInfo(previousPeriodHolder, rendererPositionUs);
        if (newPeriodInfo == null) {
          // We've loaded a next media period that is not in the new timeline.
          return !removeAfter(previousPeriodHolder);
        }
        if (!canKeepMediaPeriodHolder(oldPeriodInfo, newPeriodInfo)) {
          // The new media period has a different id or start position.
          return !removeAfter(previousPeriodHolder);
        }
      }

      // Use new period info, but keep old content position.
      periodHolder.info = newPeriodInfo.copyWithContentPositionUs(oldPeriodInfo.contentPositionUs);

      if (!areDurationsCompatible(oldPeriodInfo.durationUs, newPeriodInfo.durationUs)) {
        // The period duration changed. Remove all subsequent periods and check whether we read
        // beyond the new duration.
        long newDurationInRendererTime =
            newPeriodInfo.durationUs == C.TIME_UNSET
                ? Long.MAX_VALUE
                : periodHolder.toRendererTime(newPeriodInfo.durationUs);
        boolean isReadingAndReadBeyondNewDuration =
            periodHolder == reading
                && (maxRendererReadPositionUs == C.TIME_END_OF_SOURCE
                    || maxRendererReadPositionUs >= newDurationInRendererTime);
        boolean readingPeriodRemoved = removeAfter(periodHolder);
        return !readingPeriodRemoved && !isReadingAndReadBeyondNewDuration;
      }

      previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.getNext();
    }
    return true;
  }

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline. This method must only be called if the period is still part of
   * the current timeline.
   *
   * @param info Media period info for a media period based on an old timeline.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo info) {
    MediaPeriodId id = info.id;
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    timeline.getPeriodByUid(info.id.periodUid, period);
    long durationUs =
        id.isAd()
            ? period.getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup)
            : (info.endPositionUs == C.TIME_UNSET || info.endPositionUs == C.TIME_END_OF_SOURCE
                ? period.getDurationUs()
                : info.endPositionUs);
    return new MediaPeriodInfo(
        id,
        info.startPositionUs,
        info.contentPositionUs,
        info.endPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAds(Object periodUid, long positionUs) {
    long windowSequenceNumber = resolvePeriodIndexToWindowSequenceNumber(periodUid);
    return resolveMediaPeriodIdForAds(periodUid, positionUs, windowSequenceNumber);
  }

  // Internal methods.

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
   *     windows this period is part of.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  private MediaPeriodId resolveMediaPeriodIdForAds(
      Object periodUid, long positionUs, long windowSequenceNumber) {
    timeline.getPeriodByUid(periodUid, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(positionUs);
      return new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    } else {
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(adGroupIndex);
      return new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    }
  }

  /**
   * Resolves the specified period uid to a corresponding window sequence number. Either by reusing
   * the window sequence number of an existing matching media period or by creating a new window
   * sequence number.
   *
   * @param periodUid The uid of the timeline period.
   * @return A window sequence number for a media period created for this timeline period.
   */
  private long resolvePeriodIndexToWindowSequenceNumber(Object periodUid) {
    int windowIndex = timeline.getPeriodByUid(periodUid, period).windowIndex;
    if (oldFrontPeriodUid != null) {
      int oldFrontPeriodIndex = timeline.getIndexOfPeriod(oldFrontPeriodUid);
      if (oldFrontPeriodIndex != C.INDEX_UNSET) {
        int oldFrontWindowIndex = timeline.getPeriod(oldFrontPeriodIndex, period).windowIndex;
        if (oldFrontWindowIndex == windowIndex) {
          // Try to match old front uid after the queue has been cleared.
          return oldFrontPeriodWindowSequenceNumber;
        }
      }
    }
    MediaPeriodHolder mediaPeriodHolder = getFrontPeriod();
    while (mediaPeriodHolder != null) {
      if (mediaPeriodHolder.uid.equals(periodUid)) {
        // Reuse window sequence number of first exact period match.
        return mediaPeriodHolder.info.id.windowSequenceNumber;
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }
    mediaPeriodHolder = getFrontPeriod();
    while (mediaPeriodHolder != null) {
      int indexOfHolderInTimeline = timeline.getIndexOfPeriod(mediaPeriodHolder.uid);
      if (indexOfHolderInTimeline != C.INDEX_UNSET) {
        int holderWindowIndex = timeline.getPeriod(indexOfHolderInTimeline, period).windowIndex;
        if (holderWindowIndex == windowIndex) {
          // As an alternative, try to match other periods of the same window.
          return mediaPeriodHolder.info.id.windowSequenceNumber;
        }
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }
    // If no match is found, create new sequence number.
    return nextWindowSequenceNumber++;
  }

  /**
   * Returns whether a period described by {@code oldInfo} can be kept for playing the media period
   * described by {@code newInfo}.
   */
  private boolean canKeepMediaPeriodHolder(MediaPeriodInfo oldInfo, MediaPeriodInfo newInfo) {
    return oldInfo.startPositionUs == newInfo.startPositionUs && oldInfo.id.equals(newInfo.id);
  }

  /**
   * Returns whether a duration change of a period is compatible with keeping the following periods.
   */
  private boolean areDurationsCompatible(long previousDurationUs, long newDurationUs) {
    return previousDurationUs == C.TIME_UNSET || previousDurationUs == newDurationUs;
  }

  /**
   * Updates the queue for any playback mode change, and returns whether the change was fully
   * handled. If not, it is necessary to seek to the current playback position.
   */
  private boolean updateForPlaybackModeChange() {
    // Find the last existing period holder that matches the new period order.
    MediaPeriodHolder lastValidPeriodHolder = getFrontPeriod();
    if (lastValidPeriodHolder == null) {
      return true;
    }
    int currentPeriodIndex = timeline.getIndexOfPeriod(lastValidPeriodHolder.uid);
    while (true) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      while (lastValidPeriodHolder.getNext() != null
          && !lastValidPeriodHolder.info.isLastInTimelinePeriod) {
        lastValidPeriodHolder = lastValidPeriodHolder.getNext();
      }

      MediaPeriodHolder nextMediaPeriodHolder = lastValidPeriodHolder.getNext();
      if (nextPeriodIndex == C.INDEX_UNSET || nextMediaPeriodHolder == null) {
        break;
      }
      int nextPeriodHolderPeriodIndex = timeline.getIndexOfPeriod(nextMediaPeriodHolder.uid);
      if (nextPeriodHolderPeriodIndex != nextPeriodIndex) {
        break;
      }
      lastValidPeriodHolder = nextMediaPeriodHolder;
      currentPeriodIndex = nextPeriodIndex;
    }

    // Release any period holders that don't match the new period order.
    boolean readingPeriodRemoved = removeAfter(lastValidPeriodHolder);

    // Update the period info for the last holder, as it may now be the last period in the timeline.
    lastValidPeriodHolder.info = getUpdatedMediaPeriodInfo(lastValidPeriodHolder.info);

    // If renderers may have read from a period that's been removed, it is necessary to restart.
    return !readingPeriodRemoved || !hasPlayingPeriod();
  }

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(
        playbackInfo.periodId, playbackInfo.contentPositionUs, playbackInfo.startPositionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the media period following {@code mediaPeriodHolder}'s
   * media period.
   *
   * @param mediaPeriodHolder The media period holder.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period's info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  private @Nullable MediaPeriodInfo getFollowingMediaPeriodInfo(
      MediaPeriodHolder mediaPeriodHolder, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    // The expected delay until playback transitions to the new period is equal the duration of
    // media that's currently buffered (assuming no interruptions). This is used to project forward
    // the start position for transitions to new windows.
    long bufferedDurationUs =
        mediaPeriodHolder.getRendererOffset() + mediaPeriodInfo.durationUs - rendererPositionUs;
    if (mediaPeriodInfo.isLastInTimelinePeriod) {
      int currentPeriodIndex = timeline.getIndexOfPeriod(mediaPeriodInfo.id.periodUid);
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      if (nextPeriodIndex == C.INDEX_UNSET) {
        // We can't create a next period yet.
        return null;
      }

      long startPositionUs;
      long contentPositionUs;
      int nextWindowIndex =
          timeline.getPeriod(nextPeriodIndex, period, /* setIds= */ true).windowIndex;
      Object nextPeriodUid = period.uid;
      long windowSequenceNumber = mediaPeriodInfo.id.windowSequenceNumber;
      if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position, so project the default start position
        // forward by the duration of the buffer, and start buffering from this point.
        contentPositionUs = C.TIME_UNSET;
        Pair<Object, Long> defaultPosition =
            timeline.getPeriodPosition(
                window,
                period,
                nextWindowIndex,
                /* windowPositionUs= */ C.TIME_UNSET,
                /* defaultPositionProjectionUs= */ Math.max(0, bufferedDurationUs));
        if (defaultPosition == null) {
          return null;
        }
        nextPeriodUid = defaultPosition.first;
        startPositionUs = defaultPosition.second;
        MediaPeriodHolder nextMediaPeriodHolder = mediaPeriodHolder.getNext();
        if (nextMediaPeriodHolder != null && nextMediaPeriodHolder.uid.equals(nextPeriodUid)) {
          windowSequenceNumber = nextMediaPeriodHolder.info.id.windowSequenceNumber;
        } else {
          windowSequenceNumber = nextWindowSequenceNumber++;
        }
      } else {
        // We're starting to buffer a new period within the same window.
        startPositionUs = 0;
        contentPositionUs = 0;
      }
      MediaPeriodId periodId =
          resolveMediaPeriodIdForAds(nextPeriodUid, startPositionUs, windowSequenceNumber);
      return getMediaPeriodInfo(periodId, contentPositionUs, startPositionUs);
    }

    MediaPeriodId currentPeriodId = mediaPeriodInfo.id;
    timeline.getPeriodByUid(currentPeriodId.periodUid, period);
    if (currentPeriodId.isAd()) {
      int adGroupIndex = currentPeriodId.adGroupIndex;
      int adCountInCurrentAdGroup = period.getAdCountInAdGroup(adGroupIndex);
      if (adCountInCurrentAdGroup == C.LENGTH_UNSET) {
        return null;
      }
      int nextAdIndexInAdGroup =
          period.getNextAdIndexToPlay(adGroupIndex, currentPeriodId.adIndexInAdGroup);
      if (nextAdIndexInAdGroup < adCountInCurrentAdGroup) {
        // Play the next ad in the ad group if it's available.
        return !period.isAdAvailable(adGroupIndex, nextAdIndexInAdGroup)
            ? null
            : getMediaPeriodInfoForAd(
                currentPeriodId.periodUid,
                adGroupIndex,
                nextAdIndexInAdGroup,
                mediaPeriodInfo.contentPositionUs,
                currentPeriodId.windowSequenceNumber);
      } else {
        // Play content from the ad group position.
        long startPositionUs = mediaPeriodInfo.contentPositionUs;
        if (startPositionUs == C.TIME_UNSET) {
          // If we're transitioning from an ad group to content starting from its default position,
          // project the start position forward as if this were a transition to a new window.
          Pair<Object, Long> defaultPosition =
              timeline.getPeriodPosition(
                  window,
                  period,
                  period.windowIndex,
                  /* windowPositionUs= */ C.TIME_UNSET,
                  /* defaultPositionProjectionUs= */ Math.max(0, bufferedDurationUs));
          if (defaultPosition == null) {
            return null;
          }
          startPositionUs = defaultPosition.second;
        }
        return getMediaPeriodInfoForContent(
            currentPeriodId.periodUid, startPositionUs, currentPeriodId.windowSequenceNumber);
      }
    } else {
      // Play the next ad group if it's available.
      int nextAdGroupIndex = period.getAdGroupIndexForPositionUs(mediaPeriodInfo.endPositionUs);
      if (nextAdGroupIndex == C.INDEX_UNSET) {
        // The next ad group can't be played. Play content from the previous end position instead.
        return getMediaPeriodInfoForContent(
            currentPeriodId.periodUid,
            /* startPositionUs= */ mediaPeriodInfo.durationUs,
            currentPeriodId.windowSequenceNumber);
      }
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(nextAdGroupIndex);
      return !period.isAdAvailable(nextAdGroupIndex, adIndexInAdGroup)
          ? null
          : getMediaPeriodInfoForAd(
              currentPeriodId.periodUid,
              nextAdGroupIndex,
              adIndexInAdGroup,
              /* contentPositionUs= */ mediaPeriodInfo.durationUs,
              currentPeriodId.windowSequenceNumber);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfo(
      MediaPeriodId id, long contentPositionUs, long startPositionUs) {
    timeline.getPeriodByUid(id.periodUid, period);
    if (id.isAd()) {
      if (!period.isAdAvailable(id.adGroupIndex, id.adIndexInAdGroup)) {
        return null;
      }
      return getMediaPeriodInfoForAd(
          id.periodUid,
          id.adGroupIndex,
          id.adIndexInAdGroup,
          contentPositionUs,
          id.windowSequenceNumber);
    } else {
      return getMediaPeriodInfoForContent(id.periodUid, startPositionUs, id.windowSequenceNumber);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(
      Object periodUid,
      int adGroupIndex,
      int adIndexInAdGroup,
      long contentPositionUs,
      long windowSequenceNumber) {
    MediaPeriodId id =
        new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    long durationUs =
        timeline
            .getPeriodByUid(id.periodUid, period)
            .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs =
        adIndexInAdGroup == period.getFirstAdIndexToPlay(adGroupIndex)
            ? period.getAdResumePositionUs()
            : 0;
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        contentPositionUs,
        /* endPositionUs= */ C.TIME_UNSET,
        durationUs,
        /* isLastInTimelinePeriod= */ false,
        /* isFinal= */ false);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(
      Object periodUid, long startPositionUs, long windowSequenceNumber) {
    int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
    MediaPeriodId id = new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    long endPositionUs =
        nextAdGroupIndex != C.INDEX_UNSET
            ? period.getAdGroupTimeUs(nextAdGroupIndex)
            : C.TIME_UNSET;
    long durationUs =
        endPositionUs == C.TIME_UNSET || endPositionUs == C.TIME_END_OF_SOURCE
            ? period.durationUs
            : endPositionUs;
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        /* contentPositionUs= */ C.TIME_UNSET,
        endPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id) {
    return !id.isAd() && id.nextAdGroupIndex == C.INDEX_UNSET;
  }

  private boolean isLastInTimeline(MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int periodIndex = timeline.getIndexOfPeriod(id.periodUid);
    int windowIndex = timeline.getPeriod(periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        && isLastMediaPeriodInPeriod;
  }
}
