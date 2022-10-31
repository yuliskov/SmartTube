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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * A seeker that supports seeking within a stream by searching for the target frame using binary
 * search.
 *
 * <p>This seeker operates on a stream that contains multiple frames (or samples). Each frame is
 * associated with some kind of timestamps, such as stream time, or frame indices. Given a target
 * seek time, the seeker will find the corresponding target timestamp, and perform a search
 * operation within the stream to identify the target frame and return the byte position in the
 * stream of the target frame.
 */
public abstract class BinarySearchSeeker {

  /** A seeker that looks for a given timestamp from an input. */
  protected interface TimestampSeeker {

    /**
     * Searches a limited window of the provided input for a target timestamp. The size of the
     * window is implementation specific, but should be small enough such that it's reasonable for
     * multiple such reads to occur during a seek operation.
     *
     * @param input The {@link ExtractorInput} from which data should be peeked.
     * @param targetTimestamp The target timestamp.
     * @param outputFrameHolder If {@link TimestampSearchResult#TYPE_TARGET_TIMESTAMP_FOUND} is
     *     returned, this holder may be updated to hold the extracted frame that contains the target
     *     frame/sample associated with the target timestamp.
     * @return A {@link TimestampSearchResult} that describes the result of the search.
     * @throws IOException If an error occurred reading from the input.
     * @throws InterruptedException If the thread was interrupted.
     */
    TimestampSearchResult searchForTimestamp(
        ExtractorInput input, long targetTimestamp, OutputFrameHolder outputFrameHolder)
        throws IOException, InterruptedException;

    /** Called when a seek operation finishes. */
    default void onSeekFinished() {}
  }

  /**
   * Holds a frame extracted from a stream, together with the time stamp of the frame in
   * microseconds.
   */
  public static final class OutputFrameHolder {

    public final ByteBuffer byteBuffer;

    public long timeUs;

    /** Constructs an instance, wrapping the given byte buffer. */
    public OutputFrameHolder(ByteBuffer outputByteBuffer) {
      this.timeUs = 0;
      this.byteBuffer = outputByteBuffer;
    }
  }

  /**
   * A {@link SeekTimestampConverter} implementation that returns the seek time itself as the
   * timestamp for a seek time position.
   */
  public static final class DefaultSeekTimestampConverter implements SeekTimestampConverter {

    @Override
    public long timeUsToTargetTime(long timeUs) {
      return timeUs;
    }
  }

  /**
   * A converter that converts seek time in stream time into target timestamp for the {@link
   * BinarySearchSeeker}.
   */
  protected interface SeekTimestampConverter {
    /**
     * Converts a seek time in microseconds into target timestamp for the {@link
     * BinarySearchSeeker}.
     */
    long timeUsToTargetTime(long timeUs);
  }

  /**
   * When seeking within the source, if the offset is smaller than or equal to this value, the seek
   * operation will be performed using a skip operation. Otherwise, the source will be reloaded at
   * the new seek position.
   */
  private static final long MAX_SKIP_BYTES = 256 * 1024;

  protected final BinarySearchSeekMap seekMap;
  protected final TimestampSeeker timestampSeeker;
  protected @Nullable SeekOperationParams seekOperationParams;

  private final int minimumSearchRange;

  /**
   * Constructs an instance.
   *
   * @param seekTimestampConverter The {@link SeekTimestampConverter} that converts seek time in
   *     stream time into target timestamp.
   * @param timestampSeeker A {@link TimestampSeeker} that will be used to search for timestamps
   *     within the stream.
   * @param durationUs The duration of the stream in microseconds.
   * @param floorTimePosition The minimum timestamp value (inclusive) in the stream.
   * @param ceilingTimePosition The minimum timestamp value (exclusive) in the stream.
   * @param floorBytePosition The starting position of the frame with minimum timestamp value
   *     (inclusive) in the stream.
   * @param ceilingBytePosition The position after the frame with maximum timestamp value in the
   *     stream.
   * @param approxBytesPerFrame Approximated bytes per frame.
   * @param minimumSearchRange The minimum byte range that this binary seeker will operate on. If
   *     the remaining search range is smaller than this value, the search will stop, and the seeker
   *     will return the position at the floor of the range as the result.
   */
  @SuppressWarnings("initialization")
  protected BinarySearchSeeker(
      SeekTimestampConverter seekTimestampConverter,
      TimestampSeeker timestampSeeker,
      long durationUs,
      long floorTimePosition,
      long ceilingTimePosition,
      long floorBytePosition,
      long ceilingBytePosition,
      long approxBytesPerFrame,
      int minimumSearchRange) {
    this.timestampSeeker = timestampSeeker;
    this.minimumSearchRange = minimumSearchRange;
    this.seekMap =
        new BinarySearchSeekMap(
            seekTimestampConverter,
            durationUs,
            floorTimePosition,
            ceilingTimePosition,
            floorBytePosition,
            ceilingBytePosition,
            approxBytesPerFrame);
  }

  /** Returns the seek map for the stream. */
  public final SeekMap getSeekMap() {
    return seekMap;
  }

  /**
   * Sets the target time in microseconds within the stream to seek to.
   *
   * @param timeUs The target time in microseconds within the stream.
   */
  public final void setSeekTargetUs(long timeUs) {
    if (seekOperationParams != null && seekOperationParams.getSeekTimeUs() == timeUs) {
      return;
    }
    seekOperationParams = createSeekParamsForTargetTimeUs(timeUs);
  }

  /** Returns whether the last operation set by {@link #setSeekTargetUs(long)} is still pending. */
  public final boolean isSeeking() {
    return seekOperationParams != null;
  }

  /**
   * Continues to handle the pending seek operation. Returns one of the {@code RESULT_} values from
   * {@link Extractor}.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPositionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated
   *     to hold the position of the required seek.
   * @param outputFrameHolder If {@link Extractor#RESULT_CONTINUE} is returned, this holder may be
   *     updated to hold the extracted frame that contains the target sample. The caller needs to
   *     check the byte buffer limit to see if an extracted frame is available.
   * @return One of the {@code RESULT_} values defined in {@link Extractor}.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int handlePendingSeek(
      ExtractorInput input, PositionHolder seekPositionHolder, OutputFrameHolder outputFrameHolder)
      throws InterruptedException, IOException {
    TimestampSeeker timestampSeeker = Assertions.checkNotNull(this.timestampSeeker);
    while (true) {
      SeekOperationParams seekOperationParams = Assertions.checkNotNull(this.seekOperationParams);
      long floorPosition = seekOperationParams.getFloorBytePosition();
      long ceilingPosition = seekOperationParams.getCeilingBytePosition();
      long searchPosition = seekOperationParams.getNextSearchBytePosition();

      if (ceilingPosition - floorPosition <= minimumSearchRange) {
        // The seeking range is too small, so we can just continue from the floor position.
        markSeekOperationFinished(/* foundTargetFrame= */ false, floorPosition);
        return seekToPosition(input, floorPosition, seekPositionHolder);
      }
      if (!skipInputUntilPosition(input, searchPosition)) {
        return seekToPosition(input, searchPosition, seekPositionHolder);
      }

      input.resetPeekPosition();
      TimestampSearchResult timestampSearchResult =
          timestampSeeker.searchForTimestamp(
              input, seekOperationParams.getTargetTimePosition(), outputFrameHolder);

      switch (timestampSearchResult.type) {
        case TimestampSearchResult.TYPE_POSITION_OVERESTIMATED:
          seekOperationParams.updateSeekCeiling(
              timestampSearchResult.timestampToUpdate, timestampSearchResult.bytePositionToUpdate);
          break;
        case TimestampSearchResult.TYPE_POSITION_UNDERESTIMATED:
          seekOperationParams.updateSeekFloor(
              timestampSearchResult.timestampToUpdate, timestampSearchResult.bytePositionToUpdate);
          break;
        case TimestampSearchResult.TYPE_TARGET_TIMESTAMP_FOUND:
          markSeekOperationFinished(
              /* foundTargetFrame= */ true, timestampSearchResult.bytePositionToUpdate);
          skipInputUntilPosition(input, timestampSearchResult.bytePositionToUpdate);
          return seekToPosition(
              input, timestampSearchResult.bytePositionToUpdate, seekPositionHolder);
        case TimestampSearchResult.TYPE_NO_TIMESTAMP:
          // We can't find any timestamp in the search range from the search position.
          // Give up, and just continue reading from the last search position in this case.
          markSeekOperationFinished(/* foundTargetFrame= */ false, searchPosition);
          return seekToPosition(input, searchPosition, seekPositionHolder);
        default:
          throw new IllegalStateException("Invalid case");
      }
    }
  }

  protected SeekOperationParams createSeekParamsForTargetTimeUs(long timeUs) {
    return new SeekOperationParams(
        timeUs,
        seekMap.timeUsToTargetTime(timeUs),
        seekMap.floorTimePosition,
        seekMap.ceilingTimePosition,
        seekMap.floorBytePosition,
        seekMap.ceilingBytePosition,
        seekMap.approxBytesPerFrame);
  }

  protected final void markSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
    seekOperationParams = null;
    timestampSeeker.onSeekFinished();
    onSeekOperationFinished(foundTargetFrame, resultPosition);
  }

  protected void onSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
    // Do nothing.
  }

  protected final boolean skipInputUntilPosition(ExtractorInput input, long position)
      throws IOException, InterruptedException {
    long bytesToSkip = position - input.getPosition();
    if (bytesToSkip >= 0 && bytesToSkip <= MAX_SKIP_BYTES) {
      input.skipFully((int) bytesToSkip);
      return true;
    }
    return false;
  }

  protected final int seekToPosition(
      ExtractorInput input, long position, PositionHolder seekPositionHolder) {
    if (position == input.getPosition()) {
      return Extractor.RESULT_CONTINUE;
    } else {
      seekPositionHolder.position = position;
      return Extractor.RESULT_SEEK;
    }
  }

  /**
   * Contains parameters for a pending seek operation by {@link BinarySearchSeeker}.
   *
   * <p>This class holds parameters for a binary-search for the {@code targetTimePosition} in the
   * range [floorPosition, ceilingPosition).
   */
  protected static class SeekOperationParams {
    private final long seekTimeUs;
    private final long targetTimePosition;
    private final long approxBytesPerFrame;

    private long floorTimePosition;
    private long ceilingTimePosition;
    private long floorBytePosition;
    private long ceilingBytePosition;
    private long nextSearchBytePosition;

    /**
     * Returns the next position in the stream to search for target frame, given [floorBytePosition,
     * ceilingBytePosition), with corresponding [floorTimePosition, ceilingTimePosition).
     */
    protected static long calculateNextSearchBytePosition(
        long targetTimePosition,
        long floorTimePosition,
        long ceilingTimePosition,
        long floorBytePosition,
        long ceilingBytePosition,
        long approxBytesPerFrame) {
      if (floorBytePosition + 1 >= ceilingBytePosition
          || floorTimePosition + 1 >= ceilingTimePosition) {
        return floorBytePosition;
      }
      long seekTimeDuration = targetTimePosition - floorTimePosition;
      float estimatedBytesPerTimeUnit =
          (float) (ceilingBytePosition - floorBytePosition)
              / (ceilingTimePosition - floorTimePosition);
      // It's better to under-estimate rather than over-estimate, because the extractor
      // input can skip forward easily, but cannot rewind easily (it may require a new connection
      // to be made).
      // Therefore, we should reduce the estimated position by some amount, so it will converge to
      // the correct frame earlier.
      long bytesToSkip = (long) (seekTimeDuration * estimatedBytesPerTimeUnit);
      long confidenceInterval = bytesToSkip / 20;
      long estimatedFramePosition = floorBytePosition + bytesToSkip - approxBytesPerFrame;
      long estimatedPosition = estimatedFramePosition - confidenceInterval;
      return Util.constrainValue(estimatedPosition, floorBytePosition, ceilingBytePosition - 1);
    }

    protected SeekOperationParams(
        long seekTimeUs,
        long targetTimePosition,
        long floorTimePosition,
        long ceilingTimePosition,
        long floorBytePosition,
        long ceilingBytePosition,
        long approxBytesPerFrame) {
      this.seekTimeUs = seekTimeUs;
      this.targetTimePosition = targetTimePosition;
      this.floorTimePosition = floorTimePosition;
      this.ceilingTimePosition = ceilingTimePosition;
      this.floorBytePosition = floorBytePosition;
      this.ceilingBytePosition = ceilingBytePosition;
      this.approxBytesPerFrame = approxBytesPerFrame;
      this.nextSearchBytePosition =
          calculateNextSearchBytePosition(
              targetTimePosition,
              floorTimePosition,
              ceilingTimePosition,
              floorBytePosition,
              ceilingBytePosition,
              approxBytesPerFrame);
    }

    /**
     * Returns the floor byte position of the range [floorPosition, ceilingPosition) for this seek
     * operation.
     */
    private long getFloorBytePosition() {
      return floorBytePosition;
    }

    /**
     * Returns the ceiling byte position of the range [floorPosition, ceilingPosition) for this seek
     * operation.
     */
    private long getCeilingBytePosition() {
      return ceilingBytePosition;
    }

    /** Returns the target timestamp as translated from the seek time. */
    private long getTargetTimePosition() {
      return targetTimePosition;
    }

    /** Returns the target seek time in microseconds. */
    private long getSeekTimeUs() {
      return seekTimeUs;
    }

    /** Updates the floor constraints (inclusive) of the seek operation. */
    private void updateSeekFloor(long floorTimePosition, long floorBytePosition) {
      this.floorTimePosition = floorTimePosition;
      this.floorBytePosition = floorBytePosition;
      updateNextSearchBytePosition();
    }

    /** Updates the ceiling constraints (exclusive) of the seek operation. */
    private void updateSeekCeiling(long ceilingTimePosition, long ceilingBytePosition) {
      this.ceilingTimePosition = ceilingTimePosition;
      this.ceilingBytePosition = ceilingBytePosition;
      updateNextSearchBytePosition();
    }

    /** Returns the next position in the stream to search. */
    private long getNextSearchBytePosition() {
      return nextSearchBytePosition;
    }

    private void updateNextSearchBytePosition() {
      this.nextSearchBytePosition =
          calculateNextSearchBytePosition(
              targetTimePosition,
              floorTimePosition,
              ceilingTimePosition,
              floorBytePosition,
              ceilingBytePosition,
              approxBytesPerFrame);
    }
  }

  /**
   * Represents possible search results for {@link
   * TimestampSeeker#searchForTimestamp(ExtractorInput, long, OutputFrameHolder)}.
   */
  public static final class TimestampSearchResult {

    /** The search found a timestamp that it deems close enough to the given target. */
    public static final int TYPE_TARGET_TIMESTAMP_FOUND = 0;
    /** The search found only timestamps larger than the target timestamp. */
    public static final int TYPE_POSITION_OVERESTIMATED = -1;
    /** The search found only timestamps smaller than the target timestamp. */
    public static final int TYPE_POSITION_UNDERESTIMATED = -2;
    /** The search didn't find any timestamps. */
    public static final int TYPE_NO_TIMESTAMP = -3;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      TYPE_TARGET_TIMESTAMP_FOUND,
      TYPE_POSITION_OVERESTIMATED,
      TYPE_POSITION_UNDERESTIMATED,
      TYPE_NO_TIMESTAMP
    })
    @interface Type {}

    public static final TimestampSearchResult NO_TIMESTAMP_IN_RANGE_RESULT =
        new TimestampSearchResult(TYPE_NO_TIMESTAMP, C.TIME_UNSET, C.POSITION_UNSET);

    /** The type of the result. */
    @Type private final int type;

    /**
     * When {@link #type} is {@link #TYPE_POSITION_OVERESTIMATED}, the {@link
     * SeekOperationParams#ceilingTimePosition} should be updated with this value. When {@link
     * #type} is {@link #TYPE_POSITION_UNDERESTIMATED}, the {@link
     * SeekOperationParams#floorTimePosition} should be updated with this value.
     */
    private final long timestampToUpdate;
    /**
     * When {@link #type} is {@link #TYPE_POSITION_OVERESTIMATED}, the {@link
     * SeekOperationParams#ceilingBytePosition} should be updated with this value. When {@link
     * #type} is {@link #TYPE_POSITION_UNDERESTIMATED}, the {@link
     * SeekOperationParams#floorBytePosition} should be updated with this value.
     */
    private final long bytePositionToUpdate;

    private TimestampSearchResult(
        @Type int type, long timestampToUpdate, long bytePositionToUpdate) {
      this.type = type;
      this.timestampToUpdate = timestampToUpdate;
      this.bytePositionToUpdate = bytePositionToUpdate;
    }

    /**
     * Returns a result to signal that the current position in the input stream overestimates the
     * true position of the target frame, and the {@link BinarySearchSeeker} should modify its
     * {@link SeekOperationParams}'s ceiling timestamp and byte position using the given values.
     */
    public static TimestampSearchResult overestimatedResult(
        long newCeilingTimestamp, long newCeilingBytePosition) {
      return new TimestampSearchResult(
          TYPE_POSITION_OVERESTIMATED, newCeilingTimestamp, newCeilingBytePosition);
    }

    /**
     * Returns a result to signal that the current position in the input stream underestimates the
     * true position of the target frame, and the {@link BinarySearchSeeker} should modify its
     * {@link SeekOperationParams}'s floor timestamp and byte position using the given values.
     */
    public static TimestampSearchResult underestimatedResult(
        long newFloorTimestamp, long newCeilingBytePosition) {
      return new TimestampSearchResult(
          TYPE_POSITION_UNDERESTIMATED, newFloorTimestamp, newCeilingBytePosition);
    }

    /**
     * Returns a result to signal that the target timestamp has been found at {@code
     * resultBytePosition}, and the seek operation can stop.
     *
     * <p>Note that when this value is returned from {@link
     * TimestampSeeker#searchForTimestamp(ExtractorInput, long, OutputFrameHolder)}, the {@link
     * OutputFrameHolder} may be updated to hold the target frame as an optimization.
     */
    public static TimestampSearchResult targetFoundResult(long resultBytePosition) {
      return new TimestampSearchResult(
          TYPE_TARGET_TIMESTAMP_FOUND, C.TIME_UNSET, resultBytePosition);
    }
  }

  /**
   * A {@link SeekMap} implementation that returns the estimated byte location from {@link
   * SeekOperationParams#calculateNextSearchBytePosition(long, long, long, long, long, long)} for
   * each {@link #getSeekPoints(long)} query.
   */
  public static class BinarySearchSeekMap implements SeekMap {
    private final SeekTimestampConverter seekTimestampConverter;
    private final long durationUs;
    private final long floorTimePosition;
    private final long ceilingTimePosition;
    private final long floorBytePosition;
    private final long ceilingBytePosition;
    private final long approxBytesPerFrame;

    /** Constructs a new instance of this seek map. */
    public BinarySearchSeekMap(
        SeekTimestampConverter seekTimestampConverter,
        long durationUs,
        long floorTimePosition,
        long ceilingTimePosition,
        long floorBytePosition,
        long ceilingBytePosition,
        long approxBytesPerFrame) {
      this.seekTimestampConverter = seekTimestampConverter;
      this.durationUs = durationUs;
      this.floorTimePosition = floorTimePosition;
      this.ceilingTimePosition = ceilingTimePosition;
      this.floorBytePosition = floorBytePosition;
      this.ceilingBytePosition = ceilingBytePosition;
      this.approxBytesPerFrame = approxBytesPerFrame;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      long nextSearchPosition =
          SeekOperationParams.calculateNextSearchBytePosition(
              /* targetTimePosition= */ seekTimestampConverter.timeUsToTargetTime(timeUs),
              /* floorTimePosition= */ floorTimePosition,
              /* ceilingTimePosition= */ ceilingTimePosition,
              /* floorBytePosition= */ floorBytePosition,
              /* ceilingBytePosition= */ ceilingBytePosition,
              /* approxBytesPerFrame= */ approxBytesPerFrame);
      return new SeekPoints(new SeekPoint(timeUs, nextSearchPosition));
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }

    /** @see SeekTimestampConverter#timeUsToTargetTime(long) */
    public long timeUsToTargetTime(long timeUs) {
      return seekTimestampConverter.timeUsToTargetTime(timeUs);
    }
  }
}
