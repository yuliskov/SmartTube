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
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Builder for a {@link TrackSelection.Factory} and {@link LoadControl} that implement buffer size
 * based track adaptation.
 */
public final class BufferSizeAdaptationBuilder {

  /** Dynamic filter for formats, which is applied when selecting a new track. */
  public interface DynamicFormatFilter {

    /** Filter which allows all formats. */
    DynamicFormatFilter NO_FILTER = (format, trackBitrate, isInitialSelection) -> true;

    /**
     * Called when updating the selected track to determine whether a candidate track is allowed. If
     * no format is allowed or eligible, the lowest quality format will be used.
     *
     * @param format The {@link Format} of the candidate track.
     * @param trackBitrate The estimated bitrate of the track. May differ from {@link
     *     Format#bitrate} if a more accurate estimate of the current track bitrate is available.
     * @param isInitialSelection Whether this is for the initial track selection.
     */
    boolean isFormatAllowed(Format format, int trackBitrate, boolean isInitialSelection);
  }

  /**
   * The default minimum duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 15000;

  /**
   * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 50000;

  /**
   * The default duration of media that must be buffered for playback to start or resume following a
   * user action such as a seek, in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS =
      DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;

  /**
   * The default duration of media that must be buffered for playback to resume after a rebuffer, in
   * milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user action.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS =
      DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

  /**
   * The default offset the current duration of buffered media must deviate from the ideal duration
   * of buffered media for the currently selected format, before the selected format is changed.
   */
  public static final int DEFAULT_HYSTERESIS_BUFFER_MS = 5000;

  /**
   * During start-up phase, the default fraction of the available bandwidth that the selection
   * should consider available for use. Setting to a value less than 1 is recommended to account for
   * inaccuracies in the bandwidth estimator.
   */
  public static final float DEFAULT_START_UP_BANDWIDTH_FRACTION =
      AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION;

  /**
   * During start-up phase, the default minimum duration of buffered media required for the selected
   * track to switch to one of higher quality based on measured bandwidth.
   */
  public static final int DEFAULT_START_UP_MIN_BUFFER_FOR_QUALITY_INCREASE_MS =
      AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS;

  @Nullable private DefaultAllocator allocator;
  private Clock clock;
  private int minBufferMs;
  private int maxBufferMs;
  private int bufferForPlaybackMs;
  private int bufferForPlaybackAfterRebufferMs;
  private int hysteresisBufferMs;
  private float startUpBandwidthFraction;
  private int startUpMinBufferForQualityIncreaseMs;
  private DynamicFormatFilter dynamicFormatFilter;
  private boolean buildCalled;

  /** Creates builder with default values. */
  public BufferSizeAdaptationBuilder() {
    clock = Clock.DEFAULT;
    minBufferMs = DEFAULT_MIN_BUFFER_MS;
    maxBufferMs = DEFAULT_MAX_BUFFER_MS;
    bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
    hysteresisBufferMs = DEFAULT_HYSTERESIS_BUFFER_MS;
    startUpBandwidthFraction = DEFAULT_START_UP_BANDWIDTH_FRACTION;
    startUpMinBufferForQualityIncreaseMs = DEFAULT_START_UP_MIN_BUFFER_FOR_QUALITY_INCREASE_MS;
    dynamicFormatFilter = DynamicFormatFilter.NO_FILTER;
  }

  /**
   * Set the clock to use. Should only be set for testing purposes.
   *
   * @param clock The {@link Clock}.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setClock(Clock clock) {
    Assertions.checkState(!buildCalled);
    this.clock = clock;
    return this;
  }

  /**
   * Sets the {@link DefaultAllocator} used by the loader.
   *
   * @param allocator The {@link DefaultAllocator}.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setAllocator(DefaultAllocator allocator) {
    Assertions.checkState(!buildCalled);
    this.allocator = allocator;
    return this;
  }

  /**
   * Sets the buffer duration parameters.
   *
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt to buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setBufferDurationsMs(
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs) {
    Assertions.checkState(!buildCalled);
    this.minBufferMs = minBufferMs;
    this.maxBufferMs = maxBufferMs;
    this.bufferForPlaybackMs = bufferForPlaybackMs;
    this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
    return this;
  }

  /**
   * Sets the hysteresis buffer used to prevent repeated format switching.
   *
   * @param hysteresisBufferMs The offset the current duration of buffered media must deviate from
   *     the ideal duration of buffered media for the currently selected format, before the selected
   *     format is changed. This value must be smaller than {@code maxBufferMs - minBufferMs}.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setHysteresisBufferMs(int hysteresisBufferMs) {
    Assertions.checkState(!buildCalled);
    this.hysteresisBufferMs = hysteresisBufferMs;
    return this;
  }

  /**
   * Sets track selection parameters used during the start-up phase before the selection can be made
   * purely on based on buffer size. During the start-up phase the selection is based on the current
   * bandwidth estimate.
   *
   * @param bandwidthFraction The fraction of the available bandwidth that the selection should
   *     consider available for use. Setting to a value less than 1 is recommended to account for
   *     inaccuracies in the bandwidth estimator.
   * @param minBufferForQualityIncreaseMs The minimum duration of buffered media required for the
   *     selected track to switch to one of higher quality.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setStartUpTrackSelectionParameters(
      float bandwidthFraction, int minBufferForQualityIncreaseMs) {
    Assertions.checkState(!buildCalled);
    this.startUpBandwidthFraction = bandwidthFraction;
    this.startUpMinBufferForQualityIncreaseMs = minBufferForQualityIncreaseMs;
    return this;
  }

  /**
   * Sets the {@link DynamicFormatFilter} to use when updating the selected track.
   *
   * @param dynamicFormatFilter The {@link DynamicFormatFilter}.
   * @return This builder, for convenience.
   * @throws IllegalStateException If {@link #buildPlayerComponents()} has already been called.
   */
  public BufferSizeAdaptationBuilder setDynamicFormatFilter(
      DynamicFormatFilter dynamicFormatFilter) {
    Assertions.checkState(!buildCalled);
    this.dynamicFormatFilter = dynamicFormatFilter;
    return this;
  }

  /**
   * Builds player components for buffer size based track adaptation.
   *
   * @return A pair of a {@link TrackSelection.Factory} and a {@link LoadControl}, which should be
   *     used to construct the player.
   */
  public Pair<TrackSelection.Factory, LoadControl> buildPlayerComponents() {
    Assertions.checkArgument(hysteresisBufferMs < maxBufferMs - minBufferMs);
    Assertions.checkState(!buildCalled);
    buildCalled = true;

    DefaultLoadControl.Builder loadControlBuilder =
        new DefaultLoadControl.Builder()
            .setTargetBufferBytes(/* targetBufferBytes = */ Integer.MAX_VALUE)
            .setBufferDurationsMs(
                /* minBufferMs= */ maxBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs);
    if (allocator != null) {
      loadControlBuilder.setAllocator(allocator);
    }

    TrackSelection.Factory trackSelectionFactory =
        new TrackSelection.Factory() {
          @Override
          public @NullableType TrackSelection[] createTrackSelections(
              @NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {
            return TrackSelectionUtil.createTrackSelectionsForDefinitions(
                definitions,
                definition ->
                    new BufferSizeAdaptiveTrackSelection(
                        definition.group,
                        definition.tracks,
                        bandwidthMeter,
                        minBufferMs,
                        maxBufferMs,
                        hysteresisBufferMs,
                        startUpBandwidthFraction,
                        startUpMinBufferForQualityIncreaseMs,
                        dynamicFormatFilter,
                        clock));
          }
        };

    return Pair.create(trackSelectionFactory, loadControlBuilder.createDefaultLoadControl());
  }

  private static final class BufferSizeAdaptiveTrackSelection extends BaseTrackSelection {

    private static final int BITRATE_BLACKLISTED = Format.NO_VALUE;

    private final BandwidthMeter bandwidthMeter;
    private final Clock clock;
    private final DynamicFormatFilter dynamicFormatFilter;
    private final int[] formatBitrates;
    private final long minBufferUs;
    private final long maxBufferUs;
    private final long hysteresisBufferUs;
    private final float startUpBandwidthFraction;
    private final long startUpMinBufferForQualityIncreaseUs;
    private final int minBitrate;
    private final int maxBitrate;
    private final double bitrateToBufferFunctionSlope;
    private final double bitrateToBufferFunctionIntercept;

    private boolean isInSteadyState;
    private int selectedIndex;
    private int selectionReason;
    private float playbackSpeed;

    private BufferSizeAdaptiveTrackSelection(
        TrackGroup trackGroup,
        int[] tracks,
        BandwidthMeter bandwidthMeter,
        int minBufferMs,
        int maxBufferMs,
        int hysteresisBufferMs,
        float startUpBandwidthFraction,
        int startUpMinBufferForQualityIncreaseMs,
        DynamicFormatFilter dynamicFormatFilter,
        Clock clock) {
      super(trackGroup, tracks);
      this.bandwidthMeter = bandwidthMeter;
      this.minBufferUs = C.msToUs(minBufferMs);
      this.maxBufferUs = C.msToUs(maxBufferMs);
      this.hysteresisBufferUs = C.msToUs(hysteresisBufferMs);
      this.startUpBandwidthFraction = startUpBandwidthFraction;
      this.startUpMinBufferForQualityIncreaseUs = C.msToUs(startUpMinBufferForQualityIncreaseMs);
      this.dynamicFormatFilter = dynamicFormatFilter;
      this.clock = clock;

      formatBitrates = new int[length];
      maxBitrate = getFormat(/* index= */ 0).bitrate;
      minBitrate = getFormat(/* index= */ length - 1).bitrate;
      selectionReason = C.SELECTION_REASON_UNKNOWN;
      playbackSpeed = 1.0f;

      // We use a log-linear function to map from bitrate to buffer size:
      // buffer = slope * ln(bitrate) + intercept,
      // with buffer(minBitrate) = minBuffer and buffer(maxBitrate) = maxBuffer - hysteresisBuffer.
      bitrateToBufferFunctionSlope =
          (maxBufferUs - hysteresisBufferUs - minBufferUs)
              / Math.log((double) maxBitrate / minBitrate);
      bitrateToBufferFunctionIntercept =
          minBufferUs - bitrateToBufferFunctionSlope * Math.log(minBitrate);
    }

    @Override
    public void onPlaybackSpeed(float playbackSpeed) {
      this.playbackSpeed = playbackSpeed;
    }

    @Override
    public void onDiscontinuity() {
      isInSteadyState = false;
    }

    @Override
    public int getSelectedIndex() {
      return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
      return selectionReason;
    }

    @Override
    @Nullable
    public Object getSelectionData() {
      return null;
    }

    @Override
    public void updateSelectedTrack(
        long playbackPositionUs,
        long bufferedDurationUs,
        long availableDurationUs,
        List<? extends MediaChunk> queue,
        MediaChunkIterator[] mediaChunkIterators) {
      updateFormatBitrates(/* nowMs= */ clock.elapsedRealtime());

      // Make initial selection
      if (selectionReason == C.SELECTION_REASON_UNKNOWN) {
        selectionReason = C.SELECTION_REASON_INITIAL;
        selectedIndex = selectIdealIndexUsingBandwidth(/* isInitialSelection= */ true);
        return;
      }

      long bufferUs = getCurrentPeriodBufferedDurationUs(playbackPositionUs, bufferedDurationUs);
      int oldSelectedIndex = selectedIndex;
      if (isInSteadyState) {
        selectIndexSteadyState(bufferUs);
      } else {
        selectIndexStartUpPhase(bufferUs);
      }
      if (selectedIndex != oldSelectedIndex) {
        selectionReason = C.SELECTION_REASON_ADAPTIVE;
      }
    }

    // Steady state.

    private void selectIndexSteadyState(long bufferUs) {
      if (isOutsideHysteresis(bufferUs)) {
        selectedIndex = selectIdealIndexUsingBufferSize(bufferUs);
      }
    }

    private boolean isOutsideHysteresis(long bufferUs) {
      if (formatBitrates[selectedIndex] == BITRATE_BLACKLISTED) {
        return true;
      }
      long targetBufferForCurrentBitrateUs =
          getTargetBufferForBitrateUs(formatBitrates[selectedIndex]);
      long bufferDiffUs = bufferUs - targetBufferForCurrentBitrateUs;
      return Math.abs(bufferDiffUs) > hysteresisBufferUs;
    }

    private int selectIdealIndexUsingBufferSize(long bufferUs) {
      int lowestBitrateNonBlacklistedIndex = 0;
      for (int i = 0; i < formatBitrates.length; i++) {
        if (formatBitrates[i] != BITRATE_BLACKLISTED) {
          if (getTargetBufferForBitrateUs(formatBitrates[i]) <= bufferUs
              && dynamicFormatFilter.isFormatAllowed(
                  getFormat(i), formatBitrates[i], /* isInitialSelection= */ false)) {
            return i;
          }
          lowestBitrateNonBlacklistedIndex = i;
        }
      }
      return lowestBitrateNonBlacklistedIndex;
    }

    // Startup.

    private void selectIndexStartUpPhase(long bufferUs) {
      int startUpSelectedIndex = selectIdealIndexUsingBandwidth(/* isInitialSelection= */ false);
      int steadyStateSelectedIndex = selectIdealIndexUsingBufferSize(bufferUs);
      if (steadyStateSelectedIndex <= selectedIndex) {
        // Switch to steady state if we have enough buffer to maintain current selection.
        selectedIndex = steadyStateSelectedIndex;
        isInSteadyState = true;
      } else {
        if (bufferUs < startUpMinBufferForQualityIncreaseUs
            && startUpSelectedIndex < selectedIndex
            && formatBitrates[selectedIndex] != BITRATE_BLACKLISTED) {
          // Switching up from a non-blacklisted track is only allowed if we have enough buffer.
          return;
        }
        selectedIndex = startUpSelectedIndex;
      }
    }

    private int selectIdealIndexUsingBandwidth(boolean isInitialSelection) {
      long effectiveBitrate =
          (long) (bandwidthMeter.getBitrateEstimate() * startUpBandwidthFraction);
      int lowestBitrateNonBlacklistedIndex = 0;
      for (int i = 0; i < formatBitrates.length; i++) {
        if (formatBitrates[i] != BITRATE_BLACKLISTED) {
          if (Math.round(formatBitrates[i] * playbackSpeed) <= effectiveBitrate
              && dynamicFormatFilter.isFormatAllowed(
                  getFormat(i), formatBitrates[i], isInitialSelection)) {
            return i;
          }
          lowestBitrateNonBlacklistedIndex = i;
        }
      }
      return lowestBitrateNonBlacklistedIndex;
    }

    // Utility methods.

    private void updateFormatBitrates(long nowMs) {
      for (int i = 0; i < length; i++) {
        if (nowMs == Long.MIN_VALUE || !isBlacklisted(i, nowMs)) {
          formatBitrates[i] = getFormat(i).bitrate;
        } else {
          formatBitrates[i] = BITRATE_BLACKLISTED;
        }
      }
    }

    private long getTargetBufferForBitrateUs(int bitrate) {
      if (bitrate <= minBitrate) {
        return minBufferUs;
      }
      if (bitrate >= maxBitrate) {
        return maxBufferUs - hysteresisBufferUs;
      }
      return (int)
          (bitrateToBufferFunctionSlope * Math.log(bitrate) + bitrateToBufferFunctionIntercept);
    }

    private static long getCurrentPeriodBufferedDurationUs(
        long playbackPositionUs, long bufferedDurationUs) {
      return playbackPositionUs >= 0 ? bufferedDurationUs : playbackPositionUs + bufferedDurationUs;
    }
  }
}
