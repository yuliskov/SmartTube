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
package com.google.android.exoplayer2.source.ads;

import android.net.Uri;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Represents ad group times relative to the start of the media and information on the state and
 * URIs of ads within each ad group.
 *
 * <p>Instances are immutable. Call the {@code with*} methods to get new instances that have the
 * required changes.
 */
public final class AdPlaybackState {

  /**
   * Represents a group of ads, with information about their states.
   *
   * <p>Instances are immutable. Call the {@code with*} methods to get new instances that have the
   * required changes.
   */
  public static final class AdGroup {

    /** The number of ads in the ad group, or {@link C#LENGTH_UNSET} if unknown. */
    public final int count;
    /** The URI of each ad in the ad group. */
    public final Uri[] uris;
    /** The state of each ad in the ad group. */
    public final @AdState int[] states;
    /** The durations of each ad in the ad group, in microseconds. */
    public final long[] durationsUs;

    /** Creates a new ad group with an unspecified number of ads. */
    public AdGroup() {
      this(
          /* count= */ C.LENGTH_UNSET,
          /* states= */ new int[0],
          /* uris= */ new Uri[0],
          /* durationsUs= */ new long[0]);
    }

    private AdGroup(int count, @AdState int[] states, Uri[] uris, long[] durationsUs) {
      Assertions.checkArgument(states.length == uris.length);
      this.count = count;
      this.states = states;
      this.uris = uris;
      this.durationsUs = durationsUs;
    }

    /**
     * Returns the index of the first ad in the ad group that should be played, or {@link #count} if
     * no ads should be played.
     */
    public int getFirstAdIndexToPlay() {
      return getNextAdIndexToPlay(-1);
    }

    /**
     * Returns the index of the next ad in the ad group that should be played after playing {@code
     * lastPlayedAdIndex}, or {@link #count} if no later ads should be played.
     */
    public int getNextAdIndexToPlay(int lastPlayedAdIndex) {
      int nextAdIndexToPlay = lastPlayedAdIndex + 1;
      while (nextAdIndexToPlay < states.length) {
        if (states[nextAdIndexToPlay] == AD_STATE_UNAVAILABLE
            || states[nextAdIndexToPlay] == AD_STATE_AVAILABLE) {
          break;
        }
        nextAdIndexToPlay++;
      }
      return nextAdIndexToPlay;
    }

    /** Returns whether the ad group has at least one ad that still needs to be played. */
    public boolean hasUnplayedAds() {
      return count == C.LENGTH_UNSET || getFirstAdIndexToPlay() < count;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AdGroup adGroup = (AdGroup) o;
      return count == adGroup.count
          && Arrays.equals(uris, adGroup.uris)
          && Arrays.equals(states, adGroup.states)
          && Arrays.equals(durationsUs, adGroup.durationsUs);
    }

    @Override
    public int hashCode() {
      int result = count;
      result = 31 * result + Arrays.hashCode(uris);
      result = 31 * result + Arrays.hashCode(states);
      result = 31 * result + Arrays.hashCode(durationsUs);
      return result;
    }

    /**
     * Returns a new instance with the ad count set to {@code count}. This method may only be called
     * if this instance's ad count has not yet been specified.
     */
    @CheckResult
    public AdGroup withAdCount(int count) {
      Assertions.checkArgument(this.count == C.LENGTH_UNSET && states.length <= count);
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, count);
      long[] durationsUs = copyDurationsUsWithSpaceForAdCount(this.durationsUs, count);
      Uri[] uris = Arrays.copyOf(this.uris, count);
      return new AdGroup(count, states, uris, durationsUs);
    }

    /**
     * Returns a new instance with the specified {@code uri} set for the specified ad, and the ad
     * marked as {@link #AD_STATE_AVAILABLE}. The specified ad must currently be in {@link
     * #AD_STATE_UNAVAILABLE}, which is the default state.
     *
     * <p>This instance's ad count may be unknown, in which case {@code index} must be less than the
     * ad count specified later. Otherwise, {@code index} must be less than the current ad count.
     */
    @CheckResult
    public AdGroup withAdUri(Uri uri, int index) {
      Assertions.checkArgument(count == C.LENGTH_UNSET || index < count);
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, index + 1);
      Assertions.checkArgument(states[index] == AD_STATE_UNAVAILABLE);
      long[] durationsUs =
          this.durationsUs.length == states.length
              ? this.durationsUs
              : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
      Uri[] uris = Arrays.copyOf(this.uris, states.length);
      uris[index] = uri;
      states[index] = AD_STATE_AVAILABLE;
      return new AdGroup(count, states, uris, durationsUs);
    }

    /**
     * Returns a new instance with the specified ad set to the specified {@code state}. The ad
     * specified must currently either be in {@link #AD_STATE_UNAVAILABLE} or {@link
     * #AD_STATE_AVAILABLE}.
     *
     * <p>This instance's ad count may be unknown, in which case {@code index} must be less than the
     * ad count specified later. Otherwise, {@code index} must be less than the current ad count.
     */
    @CheckResult
    public AdGroup withAdState(@AdState int state, int index) {
      Assertions.checkArgument(count == C.LENGTH_UNSET || index < count);
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, index + 1);
      Assertions.checkArgument(
          states[index] == AD_STATE_UNAVAILABLE
              || states[index] == AD_STATE_AVAILABLE
              || states[index] == state);
      long[] durationsUs =
          this.durationsUs.length == states.length
              ? this.durationsUs
              : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
      Uri[] uris =
          this.uris.length == states.length ? this.uris : Arrays.copyOf(this.uris, states.length);
      states[index] = state;
      return new AdGroup(count, states, uris, durationsUs);
    }

    /** Returns a new instance with the specified ad durations, in microseconds. */
    @CheckResult
    public AdGroup withAdDurationsUs(long[] durationsUs) {
      Assertions.checkArgument(count == C.LENGTH_UNSET || durationsUs.length <= this.uris.length);
      if (durationsUs.length < this.uris.length) {
        durationsUs = copyDurationsUsWithSpaceForAdCount(durationsUs, uris.length);
      }
      return new AdGroup(count, states, uris, durationsUs);
    }

    /**
     * Returns an instance with all unavailable and available ads marked as skipped. If the ad count
     * hasn't been set, it will be set to zero.
     */
    @CheckResult
    public AdGroup withAllAdsSkipped() {
      if (count == C.LENGTH_UNSET) {
        return new AdGroup(
            /* count= */ 0,
            /* states= */ new int[0],
            /* uris= */ new Uri[0],
            /* durationsUs= */ new long[0]);
      }
      int count = this.states.length;
      @AdState int[] states = Arrays.copyOf(this.states, count);
      for (int i = 0; i < count; i++) {
        if (states[i] == AD_STATE_AVAILABLE || states[i] == AD_STATE_UNAVAILABLE) {
          states[i] = AD_STATE_SKIPPED;
        }
      }
      return new AdGroup(count, states, uris, durationsUs);
    }

    @CheckResult
    private static @AdState int[] copyStatesWithSpaceForAdCount(@AdState int[] states, int count) {
      int oldStateCount = states.length;
      int newStateCount = Math.max(count, oldStateCount);
      states = Arrays.copyOf(states, newStateCount);
      Arrays.fill(states, oldStateCount, newStateCount, AD_STATE_UNAVAILABLE);
      return states;
    }

    @CheckResult
    private static long[] copyDurationsUsWithSpaceForAdCount(long[] durationsUs, int count) {
      int oldDurationsUsCount = durationsUs.length;
      int newDurationsUsCount = Math.max(count, oldDurationsUsCount);
      durationsUs = Arrays.copyOf(durationsUs, newDurationsUsCount);
      Arrays.fill(durationsUs, oldDurationsUsCount, newDurationsUsCount, C.TIME_UNSET);
      return durationsUs;
    }
  }

  /**
   * Represents the state of an ad in an ad group. One of {@link #AD_STATE_UNAVAILABLE}, {@link
   * #AD_STATE_AVAILABLE}, {@link #AD_STATE_SKIPPED}, {@link #AD_STATE_PLAYED} or {@link
   * #AD_STATE_ERROR}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    AD_STATE_UNAVAILABLE,
    AD_STATE_AVAILABLE,
    AD_STATE_SKIPPED,
    AD_STATE_PLAYED,
    AD_STATE_ERROR,
  })
  public @interface AdState {}
  /** State for an ad that does not yet have a URL. */
  public static final int AD_STATE_UNAVAILABLE = 0;
  /** State for an ad that has a URL but has not yet been played. */
  public static final int AD_STATE_AVAILABLE = 1;
  /** State for an ad that was skipped. */
  public static final int AD_STATE_SKIPPED = 2;
  /** State for an ad that was played in full. */
  public static final int AD_STATE_PLAYED = 3;
  /** State for an ad that could not be loaded. */
  public static final int AD_STATE_ERROR = 4;

  /** Ad playback state with no ads. */
  public static final AdPlaybackState NONE = new AdPlaybackState();

  /** The number of ad groups. */
  public final int adGroupCount;
  /**
   * The times of ad groups, in microseconds. A final element with the value {@link
   * C#TIME_END_OF_SOURCE} indicates a postroll ad.
   */
  public final long[] adGroupTimesUs;
  /** The ad groups. */
  public final AdGroup[] adGroups;
  /** The position offset in the first unplayed ad at which to begin playback, in microseconds. */
  public final long adResumePositionUs;
  /** The content duration in microseconds, if known. {@link C#TIME_UNSET} otherwise. */
  public final long contentDurationUs;

  /**
   * Creates a new ad playback state with the specified ad group times.
   *
   * @param adGroupTimesUs The times of ad groups in microseconds. A final element with the value
   *     {@link C#TIME_END_OF_SOURCE} indicates that there is a postroll ad.
   */
  public AdPlaybackState(long... adGroupTimesUs) {
    int count = adGroupTimesUs.length;
    adGroupCount = count;
    this.adGroupTimesUs = Arrays.copyOf(adGroupTimesUs, count);
    this.adGroups = new AdGroup[count];
    for (int i = 0; i < count; i++) {
      adGroups[i] = new AdGroup();
    }
    adResumePositionUs = 0;
    contentDurationUs = C.TIME_UNSET;
  }

  private AdPlaybackState(
      long[] adGroupTimesUs, AdGroup[] adGroups, long adResumePositionUs, long contentDurationUs) {
    adGroupCount = adGroups.length;
    this.adGroupTimesUs = adGroupTimesUs;
    this.adGroups = adGroups;
    this.adResumePositionUs = adResumePositionUs;
    this.contentDurationUs = contentDurationUs;
  }

  /**
   * Returns the index of the ad group at or before {@code positionUs}, if that ad group is
   * unplayed. Returns {@link C#INDEX_UNSET} if the ad group at or before {@code positionUs} has no
   * ads remaining to be played, or if there is no such ad group.
   *
   * @param positionUs The position at or before which to find an ad group, in microseconds, or
   *     {@link C#TIME_END_OF_SOURCE} for the end of the stream (in which case the index of any
   *     unplayed postroll ad group will be returned).
   * @return The index of the ad group, or {@link C#INDEX_UNSET}.
   */
  public int getAdGroupIndexForPositionUs(long positionUs) {
    // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
    // In practice we expect there to be few ad groups so the search shouldn't be expensive.
    int index = adGroupTimesUs.length - 1;
    while (index >= 0 && isPositionBeforeAdGroup(positionUs, index)) {
      index--;
    }
    return index >= 0 && adGroups[index].hasUnplayedAds() ? index : C.INDEX_UNSET;
  }

  /**
   * Returns the index of the next ad group after {@code positionUs} that has ads remaining to be
   * played. Returns {@link C#INDEX_UNSET} if there is no such ad group.
   *
   * @param positionUs The position after which to find an ad group, in microseconds, or {@link
   *     C#TIME_END_OF_SOURCE} for the end of the stream (in which case there can be no ad group
   *     after the position).
   * @param periodDurationUs The duration of the containing period in microseconds, or {@link
   *     C#TIME_UNSET} if not known.
   * @return The index of the ad group, or {@link C#INDEX_UNSET}.
   */
  public int getAdGroupIndexAfterPositionUs(long positionUs, long periodDurationUs) {
    if (positionUs == C.TIME_END_OF_SOURCE
        || (periodDurationUs != C.TIME_UNSET && positionUs >= periodDurationUs)) {
      return C.INDEX_UNSET;
    }
    // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
    // In practice we expect there to be few ad groups so the search shouldn't be expensive.
    int index = 0;
    while (index < adGroupTimesUs.length
        && adGroupTimesUs[index] != C.TIME_END_OF_SOURCE
        && (positionUs >= adGroupTimesUs[index] || !adGroups[index].hasUnplayedAds())) {
      index++;
    }
    return index < adGroupTimesUs.length ? index : C.INDEX_UNSET;
  }

  /**
   * Returns an instance with the number of ads in {@code adGroupIndex} resolved to {@code adCount}.
   * The ad count must be greater than zero.
   */
  @CheckResult
  public AdPlaybackState withAdCount(int adGroupIndex, int adCount) {
    Assertions.checkArgument(adCount > 0);
    if (adGroups[adGroupIndex].count == adCount) {
      return this;
    }
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = this.adGroups[adGroupIndex].withAdCount(adCount);
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad URI. */
  @CheckResult
  public AdPlaybackState withAdUri(int adGroupIndex, int adIndexInAdGroup, Uri uri) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdUri(uri, adIndexInAdGroup);
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad marked as played. */
  @CheckResult
  public AdPlaybackState withPlayedAd(int adGroupIndex, int adIndexInAdGroup) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_PLAYED, adIndexInAdGroup);
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad marked as skipped. */
  @CheckResult
  public AdPlaybackState withSkippedAd(int adGroupIndex, int adIndexInAdGroup) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_SKIPPED, adIndexInAdGroup);
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad marked as having a load error. */
  @CheckResult
  public AdPlaybackState withAdLoadError(int adGroupIndex, int adIndexInAdGroup) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_ERROR, adIndexInAdGroup);
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /**
   * Returns an instance with all ads in the specified ad group skipped (except for those already
   * marked as played or in the error state).
   */
  @CheckResult
  public AdPlaybackState withSkippedAdGroup(int adGroupIndex) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    adGroups[adGroupIndex] = adGroups[adGroupIndex].withAllAdsSkipped();
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad durations, in microseconds. */
  @CheckResult
  public AdPlaybackState withAdDurationsUs(long[][] adDurationUs) {
    AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
    for (int adGroupIndex = 0; adGroupIndex < adGroupCount; adGroupIndex++) {
      adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdDurationsUs(adDurationUs[adGroupIndex]);
    }
    return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
  }

  /** Returns an instance with the specified ad resume position, in microseconds. */
  @CheckResult
  public AdPlaybackState withAdResumePositionUs(long adResumePositionUs) {
    if (this.adResumePositionUs == adResumePositionUs) {
      return this;
    } else {
      return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }
  }

  /** Returns an instance with the specified content duration, in microseconds. */
  @CheckResult
  public AdPlaybackState withContentDurationUs(long contentDurationUs) {
    if (this.contentDurationUs == contentDurationUs) {
      return this;
    } else {
      return new AdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AdPlaybackState that = (AdPlaybackState) o;
    return adGroupCount == that.adGroupCount
        && adResumePositionUs == that.adResumePositionUs
        && contentDurationUs == that.contentDurationUs
        && Arrays.equals(adGroupTimesUs, that.adGroupTimesUs)
        && Arrays.equals(adGroups, that.adGroups);
  }

  @Override
  public int hashCode() {
    int result = adGroupCount;
    result = 31 * result + (int) adResumePositionUs;
    result = 31 * result + (int) contentDurationUs;
    result = 31 * result + Arrays.hashCode(adGroupTimesUs);
    result = 31 * result + Arrays.hashCode(adGroups);
    return result;
  }

  private boolean isPositionBeforeAdGroup(long positionUs, int adGroupIndex) {
    if (positionUs == C.TIME_END_OF_SOURCE) {
      // The end of the content is at (but not before) any postroll ad, and after any other ads.
      return false;
    }
    long adGroupPositionUs = adGroupTimesUs[adGroupIndex];
    if (adGroupPositionUs == C.TIME_END_OF_SOURCE) {
      return contentDurationUs == C.TIME_UNSET || positionUs < contentDurationUs;
    } else {
      return positionUs < adGroupPositionUs;
    }
  }
}
