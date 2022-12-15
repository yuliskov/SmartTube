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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ConstantBitrateSeekMap}. */
@RunWith(AndroidJUnit4.class)
public final class ConstantBitrateSeekMapTest {

  private ConstantBitrateSeekMap constantBitrateSeekMap;

  @Test
  public void testIsSeekable_forKnownInputLength_returnSeekable() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 1000,
            /* firstFrameBytePosition= */ 0,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    assertThat(constantBitrateSeekMap.isSeekable()).isTrue();
  }

  @Test
  public void testIsSeekable_forUnknownInputLength_returnUnseekable() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ C.LENGTH_UNSET,
            /* firstFrameBytePosition= */ 0,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    assertThat(constantBitrateSeekMap.isSeekable()).isFalse();
  }

  @Test
  public void testGetSeekPoints_forUnseekableInput_returnSeekPoint0() {
    int firstBytePosition = 100;
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ C.LENGTH_UNSET,
            /* firstFrameBytePosition= */ firstBytePosition,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 123);
    assertThat(seekPoints.first.timeUs).isEqualTo(0);
    assertThat(seekPoints.first.position).isEqualTo(firstBytePosition);
    assertThat(seekPoints.second).isEqualTo(seekPoints.first);
  }

  @Test
  public void testGetDurationUs_forKnownInputLength_returnCorrectDuration() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    // Bitrate = 8000 (bits/s) = 1000 (bytes/s)
    // FrameSize = 100 (bytes), so 1 frame = 1s = 100_000 us
    // Input length = 2300 (bytes), first frame = 100, so duration = 2_200_000 us.
    assertThat(constantBitrateSeekMap.getDurationUs()).isEqualTo(2_200_000);
  }

  @Test
  public void testGetDurationUs_forUnnnownInputLength_returnUnknownDuration() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ C.LENGTH_UNSET,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    assertThat(constantBitrateSeekMap.getDurationUs()).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void testGetSeekPoints_forSeekableInput_forSyncPosition0_return1SeekPoint() {
    int firstBytePosition = 100;
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ firstBytePosition,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 0);
    assertThat(seekPoints.first.timeUs).isEqualTo(0);
    assertThat(seekPoints.first.position).isEqualTo(firstBytePosition);
    assertThat(seekPoints.second).isEqualTo(seekPoints.first);
  }

  @Test
  public void testGetSeekPoints_forSeekableInput_forSeekPointAtSyncPosition_return1SeekPoint() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 1_200_000);
    // Bitrate = 8000 (bits/s) = 1000 (bytes/s)
    // FrameSize = 100 (bytes), so 1 frame = 1s = 100_000 us
    assertThat(seekPoints.first.timeUs).isEqualTo(1_200_000);
    assertThat(seekPoints.first.position).isEqualTo(1300);
    assertThat(seekPoints.second).isEqualTo(seekPoints.first);
  }

  @Test
  public void testGetSeekPoints_forSeekableInput_forNonSyncSeekPosition_return2SeekPoints() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 345_678);
    // Bitrate = 8000 (bits/s) = 1000 (bytes/s)
    // FrameSize = 100 (bytes), so 1 frame = 1s = 100_000 us
    assertThat(seekPoints.first.timeUs).isEqualTo(300_000);
    assertThat(seekPoints.first.position).isEqualTo(400);
    assertThat(seekPoints.second.timeUs).isEqualTo(400_000);
    assertThat(seekPoints.second.position).isEqualTo(500);
  }

  @Test
  public void testGetSeekPoints_forSeekableInput_forSeekPointWithinLastFrame_return1SeekPoint() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 2_123_456);
    assertThat(seekPoints.first.timeUs).isEqualTo(2_100_000);
    assertThat(seekPoints.first.position).isEqualTo(2_200);
    assertThat(seekPoints.second).isEqualTo(seekPoints.first);
  }

  @Test
  public void testGetSeekPoints_forSeekableInput_forSeekPointAtEndOfStream_return1SeekPoint() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    SeekMap.SeekPoints seekPoints = constantBitrateSeekMap.getSeekPoints(/* timeUs= */ 2_200_000);
    assertThat(seekPoints.first.timeUs).isEqualTo(2_100_000);
    assertThat(seekPoints.first.position).isEqualTo(2_200);
    assertThat(seekPoints.second).isEqualTo(seekPoints.first);
  }

  @Test
  public void testGetTimeUsAtPosition_forPosition0_return0() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    long timeUs = constantBitrateSeekMap.getTimeUsAtPosition(0);
    assertThat(timeUs).isEqualTo(0);
  }

  @Test
  public void testGetTimeUsAtPosition_forPositionWithinStream_returnCorrectTime() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    long timeUs = constantBitrateSeekMap.getTimeUsAtPosition(1234);
    assertThat(timeUs).isEqualTo(1_134_000);
  }

  @Test
  public void testGetTimeUsAtPosition_forPositionAtEndOfStream_returnStreamDuration() {
    constantBitrateSeekMap =
        new ConstantBitrateSeekMap(
            /* inputLength= */ 2_300,
            /* firstFrameBytePosition= */ 100,
            /* bitrate= */ 8_000,
            /* frameSize= */ 100);
    long timeUs = constantBitrateSeekMap.getTimeUsAtPosition(2300);
    assertThat(timeUs).isEqualTo(constantBitrateSeekMap.getDurationUs());
  }
}
