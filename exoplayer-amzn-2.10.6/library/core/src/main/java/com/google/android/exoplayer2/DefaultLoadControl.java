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

import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * The default {@link LoadControl} implementation.
 */
public class DefaultLoadControl implements LoadControl {

  /**
   * The default minimum duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds. This value is only applied to playbacks without video.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 15000;

  /**
   * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
   * For playbacks with video, this is also the default minimum duration of media that the player
   * will attempt to ensure is buffered.
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 50000;

  /**
   * The default duration of media that must be buffered for playback to start or resume following a
   * user action such as a seek, in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

  /**
   * The default duration of media that must be buffered for playback to resume after a rebuffer, in
   * milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user action.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;

  /**
   * The default target buffer size in bytes. The value ({@link C#LENGTH_UNSET}) means that the load
   * control will calculate the target buffer size based on the selected tracks.
   */
  public static final int DEFAULT_TARGET_BUFFER_BYTES = C.LENGTH_UNSET;

  /** The default prioritization of buffer time constraints over size constraints. */
  public static final boolean DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = true;

  /** The default back buffer duration in milliseconds. */
  public static final int DEFAULT_BACK_BUFFER_DURATION_MS = 0;

  /** The default for whether the back buffer is retained from the previous keyframe. */
  public static final boolean DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME = false;

  /** A default size in bytes for a video buffer. */
  public static final int DEFAULT_VIDEO_BUFFER_SIZE = 500 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for an audio buffer. */
  public static final int DEFAULT_AUDIO_BUFFER_SIZE = 54 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a text buffer. */
  public static final int DEFAULT_TEXT_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a metadata buffer. */
  public static final int DEFAULT_METADATA_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a camera motion buffer. */
  public static final int DEFAULT_CAMERA_MOTION_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a muxed buffer (e.g. containing video, audio and text). */
  public static final int DEFAULT_MUXED_BUFFER_SIZE =
      DEFAULT_VIDEO_BUFFER_SIZE + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;

  /** Builder for {@link DefaultLoadControl}. */
  public static final class Builder {

    private DefaultAllocator allocator;
    private int minBufferAudioMs;
    private int minBufferVideoMs;
    private int maxBufferMs;
    private int bufferForPlaybackMs;
    private int bufferForPlaybackAfterRebufferMs;
    private int targetBufferBytes;
    private boolean prioritizeTimeOverSizeThresholds;
    private int backBufferDurationMs;
    private boolean retainBackBufferFromKeyframe;
    private boolean createDefaultLoadControlCalled;

    /** Constructs a new instance. */
    public Builder() {
      minBufferAudioMs = DEFAULT_MIN_BUFFER_MS;
      minBufferVideoMs = DEFAULT_MAX_BUFFER_MS;
      maxBufferMs = DEFAULT_MAX_BUFFER_MS;
      bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
      bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
      targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES;
      prioritizeTimeOverSizeThresholds = DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS;
      backBufferDurationMs = DEFAULT_BACK_BUFFER_DURATION_MS;
      retainBackBufferFromKeyframe = DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME;
    }

    /**
     * Sets the {@link DefaultAllocator} used by the loader.
     *
     * @param allocator The {@link DefaultAllocator}.
     * @return This builder, for convenience.
     * @throws IllegalStateException If {@link #createDefaultLoadControl()} has already been called.
     */
    public Builder setAllocator(DefaultAllocator allocator) {
      Assertions.checkState(!createDefaultLoadControlCalled);
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
     * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start
     *     or resume following a user action such as a seek, in milliseconds.
     * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered
     *     for playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be
     *     caused by buffer depletion rather than a user action.
     * @return This builder, for convenience.
     * @throws IllegalStateException If {@link #createDefaultLoadControl()} has already been called.
     */
    public Builder setBufferDurationsMs(
        int minBufferMs,
        int maxBufferMs,
        int bufferForPlaybackMs,
        int bufferForPlaybackAfterRebufferMs) {
      Assertions.checkState(!createDefaultLoadControlCalled);
      assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0");
      assertGreaterOrEqual(
          bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0");
      assertGreaterOrEqual(minBufferMs, bufferForPlaybackMs, "minBufferMs", "bufferForPlaybackMs");
      assertGreaterOrEqual(
          minBufferMs,
          bufferForPlaybackAfterRebufferMs,
          "minBufferMs",
          "bufferForPlaybackAfterRebufferMs");
      assertGreaterOrEqual(maxBufferMs, minBufferMs, "maxBufferMs", "minBufferMs");
      this.minBufferAudioMs = minBufferMs;
      this.minBufferVideoMs = minBufferMs;
      this.maxBufferMs = maxBufferMs;
      this.bufferForPlaybackMs = bufferForPlaybackMs;
      this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
      return this;
    }

    /**
     * Sets the target buffer size in bytes. If set to {@link C#LENGTH_UNSET}, the target buffer
     * size will be calculated based on the selected tracks.
     *
     * @param targetBufferBytes The target buffer size in bytes.
     * @return This builder, for convenience.
     * @throws IllegalStateException If {@link #createDefaultLoadControl()} has already been called.
     */
    public Builder setTargetBufferBytes(int targetBufferBytes) {
      Assertions.checkState(!createDefaultLoadControlCalled);
      this.targetBufferBytes = targetBufferBytes;
      return this;
    }

    /**
     * Sets whether the load control prioritizes buffer time constraints over buffer size
     * constraints.
     *
     * @param prioritizeTimeOverSizeThresholds Whether the load control prioritizes buffer time
     *     constraints over buffer size constraints.
     * @return This builder, for convenience.
     * @throws IllegalStateException If {@link #createDefaultLoadControl()} has already been called.
     */
    public Builder setPrioritizeTimeOverSizeThresholds(boolean prioritizeTimeOverSizeThresholds) {
      Assertions.checkState(!createDefaultLoadControlCalled);
      this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
      return this;
    }

    /**
     * Sets the back buffer duration, and whether the back buffer is retained from the previous
     * keyframe.
     *
     * @param backBufferDurationMs The back buffer duration in milliseconds.
     * @param retainBackBufferFromKeyframe Whether the back buffer is retained from the previous
     *     keyframe.
     * @return This builder, for convenience.
     * @throws IllegalStateException If {@link #createDefaultLoadControl()} has already been called.
     */
    public Builder setBackBuffer(int backBufferDurationMs, boolean retainBackBufferFromKeyframe) {
      Assertions.checkState(!createDefaultLoadControlCalled);
      assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0");
      this.backBufferDurationMs = backBufferDurationMs;
      this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
      return this;
    }

    /** Creates a {@link DefaultLoadControl}. */
    public DefaultLoadControl createDefaultLoadControl() {
      Assertions.checkState(!createDefaultLoadControlCalled);
      createDefaultLoadControlCalled = true;
      if (allocator == null) {
        allocator = new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
      }
      return new DefaultLoadControl(
          allocator,
          minBufferAudioMs,
          minBufferVideoMs,
          maxBufferMs,
          bufferForPlaybackMs,
          bufferForPlaybackAfterRebufferMs,
          targetBufferBytes,
          prioritizeTimeOverSizeThresholds,
          backBufferDurationMs,
          retainBackBufferFromKeyframe);
    }
  }

  private final DefaultAllocator allocator;

  private final long minBufferAudioUs;
  private final long minBufferVideoUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private final long bufferForPlaybackAfterRebufferUs;
  private final int targetBufferBytesOverwrite;
  private final boolean prioritizeTimeOverSizeThresholds;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;

  private int targetBufferSize;
  private boolean isBuffering;
  private boolean hasVideo;

  /** Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class. */
  @SuppressWarnings("deprecation")
  public DefaultLoadControl() {
    this(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
  }

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultLoadControl(DefaultAllocator allocator) {
    this(
        allocator,
        /* minBufferAudioMs= */ DEFAULT_MIN_BUFFER_MS,
        /* minBufferVideoMs= */ DEFAULT_MAX_BUFFER_MS,
        DEFAULT_MAX_BUFFER_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        DEFAULT_TARGET_BUFFER_BYTES,
        DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
        DEFAULT_BACK_BUFFER_DURATION_MS,
        DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
  }

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultLoadControl(
      DefaultAllocator allocator,
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int targetBufferBytes,
      boolean prioritizeTimeOverSizeThresholds) {
    this(
        allocator,
        /* minBufferAudioMs= */ minBufferMs,
        /* minBufferVideoMs= */ minBufferMs,
        maxBufferMs,
        bufferForPlaybackMs,
        bufferForPlaybackAfterRebufferMs,
        targetBufferBytes,
        prioritizeTimeOverSizeThresholds,
        DEFAULT_BACK_BUFFER_DURATION_MS,
        DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
  }

  protected DefaultLoadControl(
      DefaultAllocator allocator,
      int minBufferAudioMs,
      int minBufferVideoMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int targetBufferBytes,
      boolean prioritizeTimeOverSizeThresholds,
      int backBufferDurationMs,
      boolean retainBackBufferFromKeyframe) {
    assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0");
    assertGreaterOrEqual(
        bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0");
    assertGreaterOrEqual(
        minBufferAudioMs, bufferForPlaybackMs, "minBufferAudioMs", "bufferForPlaybackMs");
    assertGreaterOrEqual(
        minBufferVideoMs, bufferForPlaybackMs, "minBufferVideoMs", "bufferForPlaybackMs");
    assertGreaterOrEqual(
        minBufferAudioMs,
        bufferForPlaybackAfterRebufferMs,
        "minBufferAudioMs",
        "bufferForPlaybackAfterRebufferMs");
    assertGreaterOrEqual(
        minBufferVideoMs,
        bufferForPlaybackAfterRebufferMs,
        "minBufferVideoMs",
        "bufferForPlaybackAfterRebufferMs");
    assertGreaterOrEqual(maxBufferMs, minBufferAudioMs, "maxBufferMs", "minBufferAudioMs");
    assertGreaterOrEqual(maxBufferMs, minBufferVideoMs, "maxBufferMs", "minBufferVideoMs");
    assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0");

    this.allocator = allocator;
    this.minBufferAudioUs = C.msToUs(minBufferAudioMs);
    this.minBufferVideoUs = C.msToUs(minBufferVideoMs);
    this.maxBufferUs = C.msToUs(maxBufferMs);
    this.bufferForPlaybackUs = C.msToUs(bufferForPlaybackMs);
    this.bufferForPlaybackAfterRebufferUs = C.msToUs(bufferForPlaybackAfterRebufferMs);
    this.targetBufferBytesOverwrite = targetBufferBytes;
    this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
    this.backBufferDurationUs = C.msToUs(backBufferDurationMs);
    this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
  }

  @Override
  public void onPrepared() {
    reset(false);
  }

  @Override
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections) {
    hasVideo = hasVideo(renderers, trackSelections);
    targetBufferSize =
        targetBufferBytesOverwrite == C.LENGTH_UNSET
            ? calculateTargetBufferSize(renderers, trackSelections)
            : targetBufferBytesOverwrite;
    allocator.setTargetBufferSize(targetBufferSize);
  }

  @Override
  public void onStopped() {
    reset(true);
  }

  @Override
  public void onReleased() {
    reset(true);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public long getBackBufferDurationUs() {
    return backBufferDurationUs;
  }

  @Override
  public boolean retainBackBufferFromKeyframe() {
    return retainBackBufferFromKeyframe;
  }

  @Override
  public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
    boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    long minBufferUs = hasVideo ? minBufferVideoUs : minBufferAudioUs;
    if (playbackSpeed > 1) {
      // The playback speed is faster than real time, so scale up the minimum required media
      // duration to keep enough media buffered for a playout duration of minBufferUs.
      long mediaDurationMinBufferUs =
          Util.getMediaDurationForPlayoutDuration(minBufferUs, playbackSpeed);
      minBufferUs = Math.min(mediaDurationMinBufferUs, maxBufferUs);
    }
    if (bufferedDurationUs < minBufferUs) {
      isBuffering = prioritizeTimeOverSizeThresholds || !targetBufferSizeReached;
    } else if (bufferedDurationUs >= maxBufferUs || targetBufferSizeReached) {
      isBuffering = false;
    } // Else don't change the buffering state
    return isBuffering;
  }

  @Override
  public boolean shouldStartPlayback(
      long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
    bufferedDurationUs = Util.getPlayoutDurationForMediaDuration(bufferedDurationUs, playbackSpeed);
    long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    return minBufferDurationUs <= 0
        || bufferedDurationUs >= minBufferDurationUs
        || (!prioritizeTimeOverSizeThresholds
            && allocator.getTotalBytesAllocated() >= targetBufferSize);
  }

  /**
   * Calculate target buffer size in bytes based on the selected tracks. The player will try not to
   * exceed this target buffer. Only used when {@code targetBufferBytes} is {@link C#LENGTH_UNSET}.
   *
   * @param renderers The renderers for which the track were selected.
   * @param trackSelectionArray The selected tracks.
   * @return The target buffer size in bytes.
   */
  protected int calculateTargetBufferSize(
      Renderer[] renderers, TrackSelectionArray trackSelectionArray) {
    int targetBufferSize = 0;
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectionArray.get(i) != null) {
        targetBufferSize += getDefaultBufferSize(renderers[i].getTrackType());
      }
    }
    return targetBufferSize;
  }

  private void reset(boolean resetAllocator) {
    targetBufferSize = 0;
    isBuffering = false;
    if (resetAllocator) {
      allocator.reset();
    }
  }

  private static int getDefaultBufferSize(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_DEFAULT:
        return DEFAULT_MUXED_BUFFER_SIZE;
      case C.TRACK_TYPE_AUDIO:
        return DEFAULT_AUDIO_BUFFER_SIZE;
      case C.TRACK_TYPE_VIDEO:
        return DEFAULT_VIDEO_BUFFER_SIZE;
      case C.TRACK_TYPE_TEXT:
        return DEFAULT_TEXT_BUFFER_SIZE;
      case C.TRACK_TYPE_METADATA:
        return DEFAULT_METADATA_BUFFER_SIZE;
      case C.TRACK_TYPE_CAMERA_MOTION:
        return DEFAULT_CAMERA_MOTION_BUFFER_SIZE;
      case C.TRACK_TYPE_NONE:
        return 0;
      default:
        throw new IllegalArgumentException();
    }
  }

  private static boolean hasVideo(Renderer[] renderers, TrackSelectionArray trackSelectionArray) {
    for (int i = 0; i < renderers.length; i++) {
      if (renderers[i].getTrackType() == C.TRACK_TYPE_VIDEO && trackSelectionArray.get(i) != null) {
        return true;
      }
    }
    return false;
  }

  private static void assertGreaterOrEqual(int value1, int value2, String name1, String name2) {
    Assertions.checkArgument(value1 >= value2, name1 + " cannot be less than " + name2);
  }
}
