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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fake {@link Renderer} that supports any format with the matching MIME type. The renderer
 * verifies that it reads one of the given {@link Format}s.
 */
public class FakeRenderer extends BaseRenderer {

  /**
   * The amount of time ahead of the current playback position that the renderer reads from the
   * source. A real renderer will typically read ahead by a small amount due to pipelining through
   * decoders and the media output path.
   */
  private static final long SOURCE_READAHEAD_US = 250000;

  private final List<Format> expectedFormats;
  private final DecoderInputBuffer buffer;
  private final FormatHolder formatHolder;

  private long playbackPositionUs;
  private long lastSamplePositionUs;

  public boolean isEnded;
  public int positionResetCount;
  public int formatReadCount;
  public int sampleBufferReadCount;

  public FakeRenderer(Format... expectedFormats) {
    super(expectedFormats.length == 0 ? C.TRACK_TYPE_UNKNOWN
        : MimeTypes.getTrackType(expectedFormats[0].sampleMimeType));
    this.expectedFormats = Collections.unmodifiableList(Arrays.asList(expectedFormats));
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    formatHolder = new FormatHolder();
    lastSamplePositionUs = Long.MIN_VALUE;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    playbackPositionUs = positionUs;
    lastSamplePositionUs = Long.MIN_VALUE;
    positionResetCount++;
    isEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (isEnded) {
      return;
    }
    playbackPositionUs = positionUs;
    while (lastSamplePositionUs < positionUs + SOURCE_READAHEAD_US) {
      formatHolder.format = null;
      buffer.clear();
      int result = readSource(formatHolder, buffer, false);
      if (result == C.RESULT_FORMAT_READ) {
        formatReadCount++;
        assertThat(expectedFormats).contains(formatHolder.format);
        onFormatChanged(formatHolder.format);
      } else if (result == C.RESULT_BUFFER_READ) {
        if (buffer.isEndOfStream()) {
          isEnded = true;
          return;
        }
        lastSamplePositionUs = buffer.timeUs;
        sampleBufferReadCount++;
        onBufferRead();
      } else {
        Assertions.checkState(result == C.RESULT_NOTHING_READ);
        return;
      }
    }
  }

  @Override
  public boolean isReady() {
    return lastSamplePositionUs >= playbackPositionUs || isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    return getTrackType() == MimeTypes.getTrackType(format.sampleMimeType)
        ? (FORMAT_HANDLED | ADAPTIVE_SEAMLESS) : FORMAT_UNSUPPORTED_TYPE;
  }

  /** Called when the renderer reads a new format. */
  protected void onFormatChanged(Format format) {}

  /** Called when the renderer read a sample from the buffer. */
  protected void onBufferRead() {}
}
