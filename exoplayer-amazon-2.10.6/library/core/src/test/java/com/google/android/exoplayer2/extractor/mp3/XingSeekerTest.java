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
package com.google.android.exoplayer2.extractor.mp3;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.MpegAudioHeader;
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link XingSeeker}. */
@RunWith(AndroidJUnit4.class)
public final class XingSeekerTest {

  // Xing header/payload from http://storage.googleapis.com/exoplayer-test-media-0/play.mp3.
  private static final int XING_FRAME_HEADER_DATA = 0xFFFB3000;
  private static final byte[] XING_FRAME_PAYLOAD = Util.getBytesFromHexString(
      "00000007000008dd000e7919000205080a0d0f1214171a1c1e212426292c2e303336383b3d404245484a4c4f5254"
      + "575a5c5e616466696b6e707376787a7d808285878a8c8f929496999c9ea1a4a6a8abaeb0b3b5b8babdc0c2c4c7"
      + "cacccfd2d4d6d9dcdee1e3e6e8ebeef0f2f5f8fafd");
  private static final int XING_FRAME_POSITION = 157;

  /**
   * Data size, as encoded in {@link #XING_FRAME_PAYLOAD}.
   */
  private static final int DATA_SIZE_BYTES = 948505;
  /**
   * Duration of the audio stream in microseconds, encoded in {@link #XING_FRAME_PAYLOAD}.
   */
  private static final int STREAM_DURATION_US = 59271836;
  /**
   * The length of the stream in bytes.
   */
  private static final int STREAM_LENGTH = XING_FRAME_POSITION + DATA_SIZE_BYTES;

  private XingSeeker seeker;
  private XingSeeker seekerWithInputLength;
  private int xingFrameSize;

  @Before
  public void setUp() throws Exception {
    MpegAudioHeader xingFrameHeader = new MpegAudioHeader();
    MpegAudioHeader.populateHeader(XING_FRAME_HEADER_DATA, xingFrameHeader);
    seeker = XingSeeker.create(C.LENGTH_UNSET, XING_FRAME_POSITION, xingFrameHeader,
        new ParsableByteArray(XING_FRAME_PAYLOAD));
    seekerWithInputLength = XingSeeker.create(STREAM_LENGTH,
        XING_FRAME_POSITION, xingFrameHeader, new ParsableByteArray(XING_FRAME_PAYLOAD));
    xingFrameSize = xingFrameHeader.frameSize;
  }

  @Test
  public void testGetTimeUsBeforeFirstAudioFrame() {
    assertThat(seeker.getTimeUs(-1)).isEqualTo(0);
    assertThat(seekerWithInputLength.getTimeUs(-1)).isEqualTo(0);
  }

  @Test
  public void testGetTimeUsAtFirstAudioFrame() {
    assertThat(seeker.getTimeUs(XING_FRAME_POSITION + xingFrameSize)).isEqualTo(0);
    assertThat(seekerWithInputLength.getTimeUs(XING_FRAME_POSITION + xingFrameSize)).isEqualTo(0);
  }

  @Test
  public void testGetTimeUsAtEndOfStream() {
    assertThat(seeker.getTimeUs(STREAM_LENGTH))
        .isEqualTo(STREAM_DURATION_US);
    assertThat(
        seekerWithInputLength.getTimeUs(STREAM_LENGTH))
        .isEqualTo(STREAM_DURATION_US);
  }

  @Test
  public void testGetSeekPointsAtStartOfStream() {
    SeekPoints seekPoints = seeker.getSeekPoints(0);
    SeekPoint seekPoint = seekPoints.first;
    assertThat(seekPoint).isEqualTo(seekPoints.second);
    assertThat(seekPoint.timeUs).isEqualTo(0);
    assertThat(seekPoint.position).isEqualTo(XING_FRAME_POSITION + xingFrameSize);
  }

  @Test
  public void testGetSeekPointsAtEndOfStream() {
    SeekPoints seekPoints = seeker.getSeekPoints(STREAM_DURATION_US);
    SeekPoint seekPoint = seekPoints.first;
    assertThat(seekPoint).isEqualTo(seekPoints.second);
    assertThat(seekPoint.timeUs).isEqualTo(STREAM_DURATION_US);
    assertThat(seekPoint.position).isEqualTo(STREAM_LENGTH - 1);
  }

  @Test
  public void testGetTimeForAllPositions() {
    for (int offset = xingFrameSize; offset < DATA_SIZE_BYTES; offset++) {
      int position = XING_FRAME_POSITION + offset;
      // Test seeker.
      long timeUs = seeker.getTimeUs(position);
      SeekPoints seekPoints = seeker.getSeekPoints(timeUs);
      SeekPoint seekPoint = seekPoints.first;
      assertThat(seekPoint).isEqualTo(seekPoints.second);
      assertThat(seekPoint.position).isEqualTo(position);
      // Test seekerWithInputLength.
      timeUs = seekerWithInputLength.getTimeUs(position);
      seekPoints = seekerWithInputLength.getSeekPoints(timeUs);
      seekPoint = seekPoints.first;
      assertThat(seekPoint).isEqualTo(seekPoints.second);
      assertThat(seekPoint.position).isEqualTo(position);
    }
  }

}
