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

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A flexible representation of the structure of media. A timeline is able to represent the
 * structure of a wide variety of media, from simple cases like a single media file through to
 * complex compositions of media such as playlists and streams with inserted ads. Instances are
 * immutable. For cases where media is changing dynamically (e.g. live streams), a timeline provides
 * a snapshot of the current state.
 * <p>
 * A timeline consists of related {@link Period}s and {@link Window}s. A period defines a single
 * logical piece of media, for example a media file. It may also define groups of ads inserted into
 * the media, along with information about whether those ads have been loaded and played. A window
 * spans one or more periods, defining the region within those periods that's currently available
 * for playback along with additional information such as whether seeking is supported within the
 * window. Each window defines a default position, which is the position from which playback will
 * start when the player starts playing the window. The following examples illustrate timelines for
 * various use cases.
 *
 * <h3 id="single-file">Single media file or on-demand stream</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-single-file.svg" alt="Example timeline for a single file">
 * </p>
 * A timeline for a single media file or on-demand stream consists of a single period and window.
 * The window spans the whole period, indicating that all parts of the media are available for
 * playback. The window's default position is typically at the start of the period (indicated by the
 * black dot in the figure above).
 *
 * <h3>Playlist of media files or on-demand streams</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-playlist.svg" alt="Example timeline for a playlist of files">
 * </p>
 * A timeline for a playlist of media files or on-demand streams consists of multiple periods, each
 * with its own window. Each window spans the whole of the corresponding period, and typically has a
 * default position at the start of the period. The properties of the periods and windows (e.g.
 * their durations and whether the window is seekable) will often only become known when the player
 * starts buffering the corresponding file or stream.
 *
 * <h3 id="live-limited">Live stream with limited availability</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-limited.svg" alt="Example timeline for a live stream with
 *       limited availability">
 * </p>
 * A timeline for a live stream consists of a period whose duration is unknown, since it's
 * continually extending as more content is broadcast. If content only remains available for a
 * limited period of time then the window may start at a non-zero position, defining the region of
 * content that can still be played. The window will have {@link Window#isDynamic} set to true if
 * the stream is still live. Its default position is typically near to the live edge (indicated by
 * the black dot in the figure above).
 *
 * <h3>Live stream with indefinite availability</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-indefinite.svg" alt="Example timeline for a live stream with
 *       indefinite availability">
 * </p>
 * A timeline for a live stream with indefinite availability is similar to the
 * <a href="#live-limited">Live stream with limited availability</a> case, except that the window
 * starts at the beginning of the period to indicate that all of the previously broadcast content
 * can still be played.
 *
 * <h3 id="live-multi-period">Live stream with multiple periods</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-multi-period.svg" alt="Example timeline for a live stream
 *       with multiple periods">
 * </p>
 * This case arises when a live stream is explicitly divided into separate periods, for example at
 * content boundaries. This case is similar to the <a href="#live-limited">Live stream with limited
 * availability</a> case, except that the window may span more than one period. Multiple periods are
 * also possible in the indefinite availability case.
 *
 * <h3>On-demand stream followed by live stream</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-advanced.svg" alt="Example timeline for an on-demand stream
 *       followed by a live stream">
 * </p>
 * This case is the concatenation of the <a href="#single-file">Single media file or on-demand
 * stream</a> and <a href="#multi-period">Live stream with multiple periods</a> cases. When playback
 * of the on-demand stream ends, playback of the live stream will start from its default position
 * near the live edge.
 *
 * <h3 id="single-file-midrolls">On-demand stream with mid-roll ads</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-single-file-midrolls.svg" alt="Example timeline for an on-demand
 *       stream with mid-roll ad groups">
 * </p>
 * This case includes mid-roll ad groups, which are defined as part of the timeline's single period.
 * The period can be queried for information about the ad groups and the ads they contain.
 */
public abstract class Timeline {

  /**
   * Holds information about a window in a {@link Timeline}. A window defines a region of media
   * currently available for playback along with additional information such as whether seeking is
   * supported within the window. The figure below shows some of the information defined by a
   * window, as well as how this information relates to corresponding {@link Period}s in the
   * timeline.
   * <p align="center">
   *   <img src="doc-files/timeline-window.svg" alt="Information defined by a timeline window">
   * </p>
   */
  public static final class Window {

    /** A tag for the window. Not necessarily unique. */
    @Nullable public Object tag;

    /**
     * The start time of the presentation to which this window belongs in milliseconds since the
     * epoch, or {@link C#TIME_UNSET} if unknown or not applicable. For informational purposes only.
     */
    public long presentationStartTimeMs;

    /**
     * The window's start time in milliseconds since the epoch, or {@link C#TIME_UNSET} if unknown
     * or not applicable. For informational purposes only.
     */
    public long windowStartTimeMs;

    /**
     * Whether it's possible to seek within this window.
     */
    public boolean isSeekable;

    // TODO: Split this to better describe which parts of the window might change. For example it
    // should be possible to individually determine whether the start and end positions of the
    // window may change relative to the underlying periods. For an example of where it's useful to
    // know that the end position is fixed whilst the start position may still change, see:
    // https://github.com/google/ExoPlayer/issues/4780.
    /** Whether this window may change when the timeline is updated. */
    public boolean isDynamic;

    /**
     * The index of the first period that belongs to this window.
     */
    public int firstPeriodIndex;

    /**
     * The index of the last period that belongs to this window.
     */
    public int lastPeriodIndex;

    /**
     * The default position relative to the start of the window at which to begin playback, in
     * microseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long defaultPositionUs;

    /**
     * The duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long durationUs;

    /**
     * The position of the start of this window relative to the start of the first period belonging
     * to it, in microseconds.
     */
    public long positionInFirstPeriodUs;

    /** Sets the data held by this window. */
    public Window set(
        @Nullable Object tag,
        long presentationStartTimeMs,
        long windowStartTimeMs,
        boolean isSeekable,
        boolean isDynamic,
        long defaultPositionUs,
        long durationUs,
        int firstPeriodIndex,
        int lastPeriodIndex,
        long positionInFirstPeriodUs) {
      this.tag = tag;
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.defaultPositionUs = defaultPositionUs;
      this.durationUs = durationUs;
      this.firstPeriodIndex = firstPeriodIndex;
      this.lastPeriodIndex = lastPeriodIndex;
      this.positionInFirstPeriodUs = positionInFirstPeriodUs;
      return this;
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in milliseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long getDefaultPositionMs() {
      return C.usToMs(defaultPositionUs);
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in microseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long getDefaultPositionUs() {
      return defaultPositionUs;
    }

    /**
     * Returns the duration of the window in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in milliseconds.
     */
    public long getPositionInFirstPeriodMs() {
      return C.usToMs(positionInFirstPeriodUs);
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public long getPositionInFirstPeriodUs() {
      return positionInFirstPeriodUs;
    }

  }

  /**
   * Holds information about a period in a {@link Timeline}. A period defines a single logical piece
   * of media, for example a media file. It may also define groups of ads inserted into the media,
   * along with information about whether those ads have been loaded and played.
   * <p>
   * The figure below shows some of the information defined by a period, as well as how this
   * information relates to a corresponding {@link Window} in the timeline.
   * <p align="center">
   *   <img src="doc-files/timeline-period.svg" alt="Information defined by a period">
   * </p>
   */
  public static final class Period {

    /**
     * An identifier for the period. Not necessarily unique. May be null if the ids of the period
     * are not required.
     */
    @Nullable public Object id;

    /**
     * A unique identifier for the period. May be null if the ids of the period are not required.
     */
    @Nullable public Object uid;

    /**
     * The index of the window to which this period belongs.
     */
    public int windowIndex;

    /**
     * The duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long durationUs;

    private long positionInWindowUs;
    private AdPlaybackState adPlaybackState;

    /** Creates a new instance with no ad playback state. */
    public Period() {
      adPlaybackState = AdPlaybackState.NONE;
    }

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
     *     period are not required.
     * @param uid A unique identifier for the period. May be null if the ids of the period are not
     *     required.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @return This period, for convenience.
     */
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs) {
      return set(id, uid, windowIndex, durationUs, positionInWindowUs, AdPlaybackState.NONE);
    }

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
     *     period are not required.
     * @param uid A unique identifier for the period. May be null if the ids of the period are not
     *     required.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @param adPlaybackState The state of the period's ads, or {@link AdPlaybackState#NONE} if
     *     there are no ads.
     * @return This period, for convenience.
     */
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs,
        AdPlaybackState adPlaybackState) {
      this.id = id;
      this.uid = uid;
      this.windowIndex = windowIndex;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      this.adPlaybackState = adPlaybackState;
      return this;
    }

    /**
     * Returns the duration of the period in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in milliseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowMs() {
      return C.usToMs(positionInWindowUs);
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in microseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowUs() {
      return positionInWindowUs;
    }

    /**
     * Returns the number of ad groups in the period.
     */
    public int getAdGroupCount() {
      return adPlaybackState.adGroupCount;
    }

    /**
     * Returns the time of the ad group at index {@code adGroupIndex} in the period, in
     * microseconds.
     *
     * @param adGroupIndex The ad group index.
     * @return The time of the ad group at the index, in microseconds.
     */
    public long getAdGroupTimeUs(int adGroupIndex) {
      return adPlaybackState.adGroupTimesUs[adGroupIndex];
    }

    /**
     * Returns the index of the first ad in the specified ad group that should be played, or the
     * number of ads in the ad group if no ads should be played.
     *
     * @param adGroupIndex The ad group index.
     * @return The index of the first ad that should be played, or the number of ads in the ad group
     *     if no ads should be played.
     */
    public int getFirstAdIndexToPlay(int adGroupIndex) {
      return adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
    }

    /**
     * Returns the index of the next ad in the specified ad group that should be played after
     * playing {@code adIndexInAdGroup}, or the number of ads in the ad group if no later ads should
     * be played.
     *
     * @param adGroupIndex The ad group index.
     * @param lastPlayedAdIndex The last played ad index in the ad group.
     * @return The index of the next ad that should be played, or the number of ads in the ad group
     *     if the ad group does not have any ads remaining to play.
     */
    public int getNextAdIndexToPlay(int adGroupIndex, int lastPlayedAdIndex) {
      return adPlaybackState.adGroups[adGroupIndex].getNextAdIndexToPlay(lastPlayedAdIndex);
    }

    /**
     * Returns whether the ad group at index {@code adGroupIndex} has been played.
     *
     * @param adGroupIndex The ad group index.
     * @return Whether the ad group at index {@code adGroupIndex} has been played.
     */
    public boolean hasPlayedAdGroup(int adGroupIndex) {
      return !adPlaybackState.adGroups[adGroupIndex].hasUnplayedAds();
    }

    /**
     * Returns the index of the ad group at or before {@code positionUs}, if that ad group is
     * unplayed. Returns {@link C#INDEX_UNSET} if the ad group at or before {@code positionUs} has
     * no ads remaining to be played, or if there is no such ad group.
     *
     * @param positionUs The position at or before which to find an ad group, in microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexForPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexForPositionUs(positionUs);
    }

    /**
     * Returns the index of the next ad group after {@code positionUs} that has ads remaining to be
     * played. Returns {@link C#INDEX_UNSET} if there is no such ad group.
     *
     * @param positionUs The position after which to find an ad group, in microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexAfterPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, durationUs);
    }

    /**
     * Returns the number of ads in the ad group at index {@code adGroupIndex}, or
     * {@link C#LENGTH_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @return The number of ads in the ad group, or {@link C#LENGTH_UNSET} if not yet known.
     */
    public int getAdCountInAdGroup(int adGroupIndex) {
      return adPlaybackState.adGroups[adGroupIndex].count;
    }

    /**
     * Returns whether the URL for the specified ad is known.
     *
     * @param adGroupIndex The ad group index.
     * @param adIndexInAdGroup The ad index in the ad group.
     * @return Whether the URL for the specified ad is known.
     */
    public boolean isAdAvailable(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
      return adGroup.count != C.LENGTH_UNSET
          && adGroup.states[adIndexInAdGroup] != AdPlaybackState.AD_STATE_UNAVAILABLE;
    }

    /**
     * Returns the duration of the ad at index {@code adIndexInAdGroup} in the ad group at
     * {@code adGroupIndex}, in microseconds, or {@link C#TIME_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @param adIndexInAdGroup The ad index in the ad group.
     * @return The duration of the ad, or {@link C#TIME_UNSET} if not yet known.
     */
    public long getAdDurationUs(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
      return adGroup.count != C.LENGTH_UNSET ? adGroup.durationsUs[adIndexInAdGroup] : C.TIME_UNSET;
    }

    /**
     * Returns the position offset in the first unplayed ad at which to begin playback, in
     * microseconds.
     */
    public long getAdResumePositionUs() {
      return adPlaybackState.adResumePositionUs;
    }

  }

  /** An empty timeline. */
  public static final Timeline EMPTY =
      new Timeline() {

        @Override
        public int getWindowCount() {
          return 0;
        }

        @Override
        public Window getWindow(
            int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getPeriodCount() {
          return 0;
        }

        @Override
        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
          return C.INDEX_UNSET;
        }

        @Override
        public Object getUidOfPeriod(int periodIndex) {
          throw new IndexOutOfBoundsException();
        }
      };

  /**
   * Returns whether the timeline is empty.
   */
  public final boolean isEmpty() {
    return getWindowCount() == 0;
  }

  /**
   * Returns the number of windows in the timeline.
   */
  public abstract int getWindowCount();

  /**
   * Returns the index of the window after the window at index {@code windowIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the next window, or {@link C#INDEX_UNSET} if this is the last window.
   */
  public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled) ? C.INDEX_UNSET
            : windowIndex + 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled)
            ? getFirstWindowIndex(shuffleModeEnabled) : windowIndex + 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the window before the window at index {@code windowIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the previous window, or {@link C#INDEX_UNSET} if this is the first window.
   */
  public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled) ? C.INDEX_UNSET
            : windowIndex - 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled)
            ? getLastWindowIndex(shuffleModeEnabled) : windowIndex - 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the last window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the last window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : getWindowCount() - 1;
  }

  /**
   * Returns the index of the first window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the first window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : 0;
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index. Does not populate
   * {@link Window#tag}.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window) {
    return getWindow(windowIndex, window, false);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param setTag Whether {@link Window#tag} should be populated. If false, the field will be set
   *     to null. The caller should pass false for efficiency reasons unless the field is required.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window, boolean setTag) {
    return getWindow(windowIndex, window, setTag, 0);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param setTag Whether {@link Window#tag} should be populated. If false, the field will be set
   *     to null. The caller should pass false for efficiency reasons unless the field is required.
   * @param defaultPositionProjectionUs A duration into the future that the populated window's
   *     default start position should be projected.
   * @return The populated {@link Window}, for convenience.
   */
  public abstract Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs);

  /**
   * Returns the number of periods in the timeline.
   */
  public abstract int getPeriodCount();

  /**
   * Returns the index of the period after the period at index {@code periodIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex Index of a period in the timeline.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the next period, or {@link C#INDEX_UNSET} if this is the last period.
   */
  public final int getNextPeriodIndex(int periodIndex, Period period, Window window,
      @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    int windowIndex = getPeriod(periodIndex, period).windowIndex;
    if (getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
      int nextWindowIndex = getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
      if (nextWindowIndex == C.INDEX_UNSET) {
        return C.INDEX_UNSET;
      }
      return getWindow(nextWindowIndex, window).firstPeriodIndex;
    }
    return periodIndex + 1;
  }

  /**
   * Returns whether the given period is the last period of the timeline depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex A period index.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return Whether the period of the given index is the last period of the timeline.
   */
  public final boolean isLastPeriod(int periodIndex, Period period, Window window,
      @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    return getNextPeriodIndex(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        == C.INDEX_UNSET;
  }

  /**
   * Calls {@link #getPeriodPosition(Window, Period, int, long, long)} with a zero default position
   * projection.
   */
  public final Pair<Object, Long> getPeriodPosition(
      Window window, Period period, int windowIndex, long windowPositionUs) {
    return Assertions.checkNotNull(
        getPeriodPosition(
            window, period, windowIndex, windowPositionUs, /* defaultPositionProjectionUs= */ 0));
  }

  /**
   * Converts (windowIndex, windowPositionUs) to the corresponding (periodUid, periodPositionUs).
   *
   * @param window A {@link Window} that may be overwritten.
   * @param period A {@link Period} that may be overwritten.
   * @param windowIndex The window index.
   * @param windowPositionUs The window time, or {@link C#TIME_UNSET} to use the window's default
   *     start position.
   * @param defaultPositionProjectionUs If {@code windowPositionUs} is {@link C#TIME_UNSET}, the
   *     duration into the future by which the window's position should be projected.
   * @return The corresponding (periodUid, periodPositionUs), or null if {@code #windowPositionUs}
   *     is {@link C#TIME_UNSET}, {@code defaultPositionProjectionUs} is non-zero, and the window's
   *     position could not be projected by {@code defaultPositionProjectionUs}.
   */
  @Nullable
  public final Pair<Object, Long> getPeriodPosition(
      Window window,
      Period period,
      int windowIndex,
      long windowPositionUs,
      long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, getWindowCount());
    getWindow(windowIndex, window, false, defaultPositionProjectionUs);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    long periodPositionUs = window.getPositionInFirstPeriodUs() + windowPositionUs;
    long periodDurationUs = getPeriod(periodIndex, period, /* setIds= */ true).getDurationUs();
    while (periodDurationUs != C.TIME_UNSET && periodPositionUs >= periodDurationUs
        && periodIndex < window.lastPeriodIndex) {
      periodPositionUs -= periodDurationUs;
      periodDurationUs = getPeriod(++periodIndex, period, /* setIds= */ true).getDurationUs();
    }
    return Pair.create(Assertions.checkNotNull(period.uid), periodPositionUs);
  }

  /**
   * Populates a {@link Period} with data for the period with the specified unique identifier.
   *
   * @param periodUid The unique identifier of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public Period getPeriodByUid(Object periodUid, Period period) {
    return getPeriod(getIndexOfPeriod(periodUid), period, /* setIds= */ true);
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index. {@link Period#id}
   * and {@link Period#uid} will be set to null.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public final Period getPeriod(int periodIndex, Period period) {
    return getPeriod(periodIndex, period, false);
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @param setIds Whether {@link Period#id} and {@link Period#uid} should be populated. If false,
   *     the fields will be set to null. The caller should pass false for efficiency reasons unless
   *     the fields are required.
   * @return The populated {@link Period}, for convenience.
   */
  public abstract Period getPeriod(int periodIndex, Period period, boolean setIds);

  /**
   * Returns the index of the period identified by its unique {@code id}, or {@link C#INDEX_UNSET}
   * if the period is not in the timeline.
   *
   * @param uid A unique identifier for a period.
   * @return The index of the period, or {@link C#INDEX_UNSET} if the period was not found.
   */
  public abstract int getIndexOfPeriod(Object uid);

  /**
   * Returns the unique id of the period identified by its index in the timeline.
   *
   * @param periodIndex The index of the period.
   * @return The unique id of the period.
   */
  public abstract Object getUidOfPeriod(int periodIndex);
}
