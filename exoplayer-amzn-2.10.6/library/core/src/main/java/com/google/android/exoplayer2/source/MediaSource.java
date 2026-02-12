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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;

/**
 * Defines and provides media to be played by an {@link com.google.android.exoplayer2.ExoPlayer}. A
 * MediaSource has two main responsibilities:
 *
 * <ul>
 *   <li>To provide the player with a {@link Timeline} defining the structure of its media, and to
 *       provide a new timeline whenever the structure of the media changes. The MediaSource
 *       provides these timelines by calling {@link SourceInfoRefreshListener#onSourceInfoRefreshed}
 *       on the {@link SourceInfoRefreshListener}s passed to {@link
 *       #prepareSource(SourceInfoRefreshListener, TransferListener)}.
 *   <li>To provide {@link MediaPeriod} instances for the periods in its timeline. MediaPeriods are
 *       obtained by calling {@link #createPeriod(MediaPeriodId, Allocator, long)}, and provide a
 *       way for the player to load and read the media.
 * </ul>
 *
 * All methods are called on the player's internal playback thread, as described in the {@link
 * com.google.android.exoplayer2.ExoPlayer} Javadoc. They should not be called directly from
 * application code. Instances can be re-used, but only for one {@link
 * com.google.android.exoplayer2.ExoPlayer} instance simultaneously.
 */
public interface MediaSource {

  /** Listener for source events. */
  interface SourceInfoRefreshListener {

    /**
     * Called when manifest and/or timeline has been refreshed.
     * <p>
     * Called on the playback thread.
     *
     * @param source The {@link MediaSource} whose info has been refreshed.
     * @param timeline The source's timeline.
     * @param manifest The loaded manifest. May be null.
     */
    void onSourceInfoRefreshed(MediaSource source, Timeline timeline, @Nullable Object manifest);

  }

  /**
   * Identifier for a {@link MediaPeriod}.
   */
  final class MediaPeriodId {

    /** The unique id of the timeline period. */
    public final Object periodUid;

    /**
     * If the media period is in an ad group, the index of the ad group in the period.
     * {@link C#INDEX_UNSET} otherwise.
     */
    public final int adGroupIndex;

    /**
     * If the media period is in an ad group, the index of the ad in its ad group in the period.
     * {@link C#INDEX_UNSET} otherwise.
     */
    public final int adIndexInAdGroup;

    /**
     * The sequence number of the window in the buffered sequence of windows this media period is
     * part of. {@link C#INDEX_UNSET} if the media period id is not part of a buffered sequence of
     * windows.
     */
    public final long windowSequenceNumber;

    /**
     * The index of the next ad group to which the media period's content is clipped, or {@link
     * C#INDEX_UNSET} if there is no following ad group or if this media period is an ad.
     */
    public final int nextAdGroupIndex;

    /**
     * Creates a media period identifier for a dummy period which is not part of a buffered sequence
     * of windows.
     *
     * @param periodUid The unique id of the timeline period.
     */
    public MediaPeriodId(Object periodUid) {
      this(periodUid, /* windowSequenceNumber= */ C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier for the specified period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber) {
      this(
          periodUid,
          /* adGroupIndex= */ C.INDEX_UNSET,
          /* adIndexInAdGroup= */ C.INDEX_UNSET,
          windowSequenceNumber,
          /* nextAdGroupIndex= */ C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier for the specified clipped period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     * @param nextAdGroupIndex The index of the next ad group to which the media period's content is
     *     clipped.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber, int nextAdGroupIndex) {
      this(
          periodUid,
          /* adGroupIndex= */ C.INDEX_UNSET,
          /* adIndexInAdGroup= */ C.INDEX_UNSET,
          windowSequenceNumber,
          nextAdGroupIndex);
    }

    /**
     * Creates a media period identifier that identifies an ad within an ad group at the specified
     * timeline period.
     *
     * @param periodUid The unique id of the timeline period that contains the ad group.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(
        Object periodUid, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber) {
      this(
          periodUid,
          adGroupIndex,
          adIndexInAdGroup,
          windowSequenceNumber,
          /* nextAdGroupIndex= */ C.INDEX_UNSET);
    }

    private MediaPeriodId(
        Object periodUid,
        int adGroupIndex,
        int adIndexInAdGroup,
        long windowSequenceNumber,
        int nextAdGroupIndex) {
      this.periodUid = periodUid;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
      this.windowSequenceNumber = windowSequenceNumber;
      this.nextAdGroupIndex = nextAdGroupIndex;
    }

    /** Returns a copy of this period identifier but with {@code newPeriodUid} as its period uid. */
    public MediaPeriodId copyWithPeriodUid(Object newPeriodUid) {
      return periodUid.equals(newPeriodUid)
          ? this
          : new MediaPeriodId(
              newPeriodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, nextAdGroupIndex);
    }

    /**
     * Returns whether this period identifier identifies an ad in an ad group in a period.
     */
    public boolean isAd() {
      return adGroupIndex != C.INDEX_UNSET;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }

      MediaPeriodId periodId = (MediaPeriodId) obj;
      return periodUid.equals(periodId.periodUid)
          && adGroupIndex == periodId.adGroupIndex
          && adIndexInAdGroup == periodId.adIndexInAdGroup
          && windowSequenceNumber == periodId.windowSequenceNumber
          && nextAdGroupIndex == periodId.nextAdGroupIndex;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + periodUid.hashCode();
      result = 31 * result + adGroupIndex;
      result = 31 * result + adIndexInAdGroup;
      result = 31 * result + (int) windowSequenceNumber;
      result = 31 * result + nextAdGroupIndex;
      return result;
    }
  }

  /**
   * Adds a {@link MediaSourceEventListener} to the list of listeners which are notified of media
   * source events.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  void addEventListener(Handler handler, MediaSourceEventListener eventListener);

  /**
   * Removes a {@link MediaSourceEventListener} from the list of listeners which are notified of
   * media source events.
   *
   * @param eventListener The listener to be removed.
   */
  void removeEventListener(MediaSourceEventListener eventListener);

  /** Returns the tag set on the media source, or null if none was set. */
  @Nullable
  default Object getTag() {
    return null;
  }

  /**
   * Starts source preparation if not yet started, and adds a listener for timeline and/or manifest
   * updates.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>The listener will be also be notified if the source already has a timeline and/or manifest.
   *
   * <p>For each call to this method, a call to {@link #releaseSource(SourceInfoRefreshListener)} is
   * needed to remove the listener and to release the source if no longer required.
   *
   * @param listener The listener to be added.
   * @param mediaTransferListener The transfer listener which should be informed of any media data
   *     transfers. May be null if no listener is available. Note that this listener should be only
   *     informed of transfers related to the media loads and not of auxiliary loads for manifests
   *     and other data.
   */
  void prepareSource(
      SourceInfoRefreshListener listener, @Nullable TransferListener mediaTransferListener);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   * <p>
   * Should not be called directly from application code.
   */
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}. This method may be called
   * multiple times without an intervening call to {@link #releasePeriod(MediaPeriod)}.
   *
   * <p>Should not be called directly from application code.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param startPositionUs The expected start position, in microseconds.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs);

  /**
   * Releases the period.
   * <p>
   * Should not be called directly from application code.
   *
   * @param mediaPeriod The period to release.
   */
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Removes a listener for timeline and/or manifest updates and releases the source if no longer
   * required.
   *
   * <p>Should not be called directly from application code.
   *
   * @param listener The listener to be removed.
   */
  void releaseSource(SourceInfoRefreshListener listener);
}
