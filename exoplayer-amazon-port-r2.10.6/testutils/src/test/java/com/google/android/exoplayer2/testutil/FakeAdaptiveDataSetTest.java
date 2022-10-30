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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData.Segment;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FakeAdaptiveDataSet}. */
@RunWith(AndroidJUnit4.class)
public final class FakeAdaptiveDataSetTest {

  private static final Format[] TEST_FORMATS = {
    Format.createVideoSampleFormat(
        null,
        MimeTypes.VIDEO_H264,
        null,
        1000000,
        Format.NO_VALUE,
        1280,
        720,
        Format.NO_VALUE,
        null,
        null),
    Format.createVideoSampleFormat(
        null,
        MimeTypes.VIDEO_H264,
        null,
        300000,
        Format.NO_VALUE,
        640,
        360,
        Format.NO_VALUE,
        null,
        null)
  };
  private static final TrackGroup TRACK_GROUP = new TrackGroup(TEST_FORMATS);

  @Test
  public void testAdaptiveDataSet() {
    long chunkDuration = 2 * C.MICROS_PER_SECOND;
    FakeAdaptiveDataSet dataSet =
        new FakeAdaptiveDataSet(
            TRACK_GROUP, 10 * C.MICROS_PER_SECOND, chunkDuration, 0.0, new Random(0));
    assertThat(dataSet.getAllData().size()).isEqualTo(TEST_FORMATS.length);
    assertThat(dataSet.getUri(0).equals(dataSet.getUri(1))).isFalse();
    assertThat(dataSet.getChunkCount()).isEqualTo(5);
    assertThat(dataSet.getChunkIndexByPosition(4 * C.MICROS_PER_SECOND)).isEqualTo(2);
    assertThat(dataSet.getChunkIndexByPosition(9 * C.MICROS_PER_SECOND)).isEqualTo(4);
    for (int i = 0; i < dataSet.getChunkCount(); i++) {
      assertThat(dataSet.getChunkDuration(i)).isEqualTo(chunkDuration);
    }
    assertChunkData(dataSet, chunkDuration);
  }

  @Test
  public void testAdaptiveDataSetTrailingSmallChunk() {
    long chunkDuration = 3 * C.MICROS_PER_SECOND;
    FakeAdaptiveDataSet dataSet =
        new FakeAdaptiveDataSet(
            TRACK_GROUP, 10 * C.MICROS_PER_SECOND, chunkDuration, 0.0, new Random(0));
    assertThat(dataSet.getAllData().size()).isEqualTo(TEST_FORMATS.length);
    assertThat(dataSet.getUri(0).equals(dataSet.getUri(1))).isFalse();
    assertThat(dataSet.getChunkCount()).isEqualTo(4);
    assertThat(dataSet.getChunkIndexByPosition(4 * C.MICROS_PER_SECOND)).isEqualTo(1);
    assertThat(dataSet.getChunkIndexByPosition(9 * C.MICROS_PER_SECOND)).isEqualTo(3);
    for (int i = 0; i < dataSet.getChunkCount() - 1; i++) {
      assertThat(dataSet.getChunkDuration(i)).isEqualTo(chunkDuration);
    }
    assertThat(dataSet.getChunkDuration(3)).isEqualTo(1 * C.MICROS_PER_SECOND);
    assertChunkData(dataSet, chunkDuration);
  }

  @Test
  public void testAdaptiveDataSetChunkSizeDistribution() {
    double expectedStdDev = 4.0;
    FakeAdaptiveDataSet dataSet =
        new FakeAdaptiveDataSet(
            TRACK_GROUP,
            100000 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            expectedStdDev,
            new Random(0));
    for (int i = 0; i < TEST_FORMATS.length; i++) {
      FakeData data = dataSet.getData(dataSet.getUri(i));
      double mean = computeSegmentSizeMean(data.getSegments());
      double stddev = computeSegmentSizeStdDev(data.getSegments(), mean);
      double relativePercentStdDev = stddev / mean * 100.0;
      assertThat(relativePercentStdDev).isWithin(0.02).of(expectedStdDev);
      assertThat(mean * 8 / TEST_FORMATS[i].bitrate).isWithin(0.01).of(1.0);
    }
  }

  private void assertChunkData(FakeAdaptiveDataSet dataSet, long chunkDuration) {
    for (int i = 0; i < dataSet.getChunkCount(); i++) {
      assertThat(dataSet.getStartTime(i)).isEqualTo(chunkDuration * i);
    }
    for (int s = 0; s < TEST_FORMATS.length; s++) {
      FakeData data = dataSet.getData(dataSet.getUri(s));
      assertThat(data.getSegments().size()).isEqualTo(dataSet.getChunkCount());
      for (int i = 0; i < data.getSegments().size(); i++) {
        long expectedLength =
            TEST_FORMATS[s].bitrate * dataSet.getChunkDuration(i) / (8 * C.MICROS_PER_SECOND);
        assertThat(data.getSegments().get(i).length).isEqualTo(expectedLength);
      }
    }
  }

  private static double computeSegmentSizeMean(List<Segment> segments) {
    double totalSize = 0.0;
    for (Segment segment : segments) {
      totalSize += segment.length;
    }
    return totalSize / segments.size();
  }

  private static double computeSegmentSizeStdDev(List<Segment> segments, double mean) {
    double totalSquaredSize = 0.0;
    for (Segment segment : segments) {
      totalSquaredSize += (double) segment.length * segment.length;
    }
    return Math.sqrt(totalSquaredSize / segments.size() - mean * mean);
  }
}
