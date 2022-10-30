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
package com.google.android.exoplayer2.ext.flac;

import com.google.android.exoplayer2.extractor.BinarySearchSeeker;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link SeekMap} implementation for FLAC stream using binary search.
 *
 * <p>This seeker performs seeking by using binary search within the stream, until it finds the
 * frame that contains the target sample.
 */
/* package */ final class FlacBinarySearchSeeker extends BinarySearchSeeker {

  private final FlacDecoderJni decoderJni;

  public FlacBinarySearchSeeker(
      FlacStreamMetadata streamMetadata,
      long firstFramePosition,
      long inputLength,
      FlacDecoderJni decoderJni) {
    super(
        new FlacSeekTimestampConverter(streamMetadata),
        new FlacTimestampSeeker(decoderJni),
        streamMetadata.durationUs(),
        /* floorTimePosition= */ 0,
        /* ceilingTimePosition= */ streamMetadata.totalSamples,
        /* floorBytePosition= */ firstFramePosition,
        /* ceilingBytePosition= */ inputLength,
        /* approxBytesPerFrame= */ streamMetadata.getApproxBytesPerFrame(),
        /* minimumSearchRange= */ Math.max(1, streamMetadata.minFrameSize));
    this.decoderJni = Assertions.checkNotNull(decoderJni);
  }

  @Override
  protected void onSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
    if (!foundTargetFrame) {
      // If we can't find the target frame (sample), we need to reset the decoder jni so that
      // it can continue from the result position.
      decoderJni.reset(resultPosition);
    }
  }

  private static final class FlacTimestampSeeker implements TimestampSeeker {

    private final FlacDecoderJni decoderJni;

    private FlacTimestampSeeker(FlacDecoderJni decoderJni) {
      this.decoderJni = decoderJni;
    }

    @Override
    public TimestampSearchResult searchForTimestamp(
        ExtractorInput input, long targetSampleIndex, OutputFrameHolder outputFrameHolder)
        throws IOException, InterruptedException {
      ByteBuffer outputBuffer = outputFrameHolder.byteBuffer;
      long searchPosition = input.getPosition();
      decoderJni.reset(searchPosition);
      try {
        decoderJni.decodeSampleWithBacktrackPosition(
            outputBuffer, /* retryPosition= */ searchPosition);
      } catch (FlacDecoderJni.FlacFrameDecodeException e) {
        // For some reasons, the extractor can't find a frame mid-stream.
        // Stop the seeking and let it re-try playing at the last search position.
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }
      if (outputBuffer.limit() == 0) {
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }

      long lastFrameSampleIndex = decoderJni.getLastFrameFirstSampleIndex();
      long nextFrameSampleIndex = decoderJni.getNextFrameFirstSampleIndex();
      long nextFrameSamplePosition = decoderJni.getDecodePosition();

      boolean targetSampleInLastFrame =
          lastFrameSampleIndex <= targetSampleIndex && nextFrameSampleIndex > targetSampleIndex;

      if (targetSampleInLastFrame) {
        // We are holding the target frame in outputFrameHolder. Set its presentation time now.
        outputFrameHolder.timeUs = decoderJni.getLastFrameTimestamp();
        return TimestampSearchResult.targetFoundResult(input.getPosition());
      } else if (nextFrameSampleIndex <= targetSampleIndex) {
        return TimestampSearchResult.underestimatedResult(
            nextFrameSampleIndex, nextFrameSamplePosition);
      } else {
        return TimestampSearchResult.overestimatedResult(lastFrameSampleIndex, searchPosition);
      }
    }
  }

  /**
   * A {@link SeekTimestampConverter} implementation that returns the frame index (sample index) as
   * the timestamp for a stream seek time position.
   */
  private static final class FlacSeekTimestampConverter implements SeekTimestampConverter {
    private final FlacStreamMetadata streamMetadata;

    public FlacSeekTimestampConverter(FlacStreamMetadata streamMetadata) {
      this.streamMetadata = streamMetadata;
    }

    @Override
    public long timeUsToTargetTime(long timeUs) {
      return Assertions.checkNotNull(streamMetadata).getSampleIndex(timeUs);
    }
  }
}
