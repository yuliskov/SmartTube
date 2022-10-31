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

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData.Segment;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.Random;

/**
 * Fake data set emulating the data of an adaptive media source.
 * It provides chunk data for all {@link Format}s in the given {@link TrackGroup}.
 */
public final class FakeAdaptiveDataSet extends FakeDataSet {

  /**
   * Factory for {@link FakeAdaptiveDataSet}s.
   */
  public static final class Factory {

    private static final Random random = new Random();

    private final long chunkDurationUs;
    private final double bitratePercentStdDev;

    /**
     * Set up factory for {@link FakeAdaptiveDataSet}s with a chunk duration and the standard
     * deviation of the chunk size.
     *
     * @param chunkDurationUs The chunk duration to use in microseconds.
     * @param bitratePercentStdDev The standard deviation used to generate the chunk sizes centered
     *     around the average bitrate of the {@link Format}s. The standard deviation is given in
     *     percent (of the average size).
     */
    public Factory(long chunkDurationUs, double bitratePercentStdDev) {
      this.chunkDurationUs = chunkDurationUs;
      this.bitratePercentStdDev = bitratePercentStdDev;
    }

    /**
     * Returns a new {@link FakeAdaptiveDataSet} for the given {@link TrackGroup}.
     *
     * @param trackGroup The {@link TrackGroup} for which the data set is to be created.
     * @param mediaDurationUs The total duration of the fake data set in microseconds.
     */
    public FakeAdaptiveDataSet createDataSet(TrackGroup trackGroup, long mediaDurationUs) {
      return new FakeAdaptiveDataSet(trackGroup, mediaDurationUs, chunkDurationUs,
          bitratePercentStdDev, random);
    }

  }

  /** {@link MediaChunkIterator} for the chunks defined by a fake adaptive data set. */
  public static final class Iterator extends BaseMediaChunkIterator {

    private final FakeAdaptiveDataSet dataSet;
    private final int trackGroupIndex;

    /**
     * Create iterator.
     *
     * @param dataSet The data set to iterate over.
     * @param trackGroupIndex The index of the track group to iterate over.
     * @param chunkIndex The chunk index to which the iterator points initially.
     */
    public Iterator(FakeAdaptiveDataSet dataSet, int trackGroupIndex, int chunkIndex) {
      super(/* fromIndex= */ chunkIndex, /* toIndex= */ dataSet.getChunkCount() - 1);
      this.dataSet = dataSet;
      this.trackGroupIndex = trackGroupIndex;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      String uri = dataSet.getUri(trackGroupIndex);
      int chunkIndex = (int) getCurrentIndex();
      Segment fakeDataChunk = dataSet.getData(uri).getSegments().get(chunkIndex);
      return new DataSpec(
          Uri.parse(uri), fakeDataChunk.byteOffset, fakeDataChunk.length, /* key= */ null);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return dataSet.getStartTime((int) getCurrentIndex());
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      int chunkIndex = (int) getCurrentIndex();
      return dataSet.getStartTime(chunkIndex) + dataSet.getChunkDuration(chunkIndex);
    }
  }

  private final int chunkCount;
  private final long chunkDurationUs;
  private final long lastChunkDurationUs;

  /**
   * Create {@link FakeAdaptiveDataSet} using a {@link TrackGroup} and meta data about the media.
   *
   * @param trackGroup The {@link TrackGroup} for which the data set is to be created.
   * @param mediaDurationUs The total duration of the fake data set in microseconds.
   * @param chunkDurationUs The chunk duration to use in microseconds.
   * @param bitratePercentStdDev  The standard deviation used to generate the chunk sizes centered
   *     around the average bitrate of the {@link Format}s in the {@link TrackGroup}. The standard
   *     deviation is given in percent (of the average size).
   * @param random A {@link Random} instance used to generate random chunk sizes.
   */
  /* package */ FakeAdaptiveDataSet(TrackGroup trackGroup, long mediaDurationUs,
      long chunkDurationUs, double bitratePercentStdDev, Random random) {
    this.chunkDurationUs = chunkDurationUs;
    long lastChunkDurationUs = mediaDurationUs % chunkDurationUs;
    int fullChunks = (int) (mediaDurationUs / chunkDurationUs);
    this.lastChunkDurationUs = lastChunkDurationUs == 0 ? chunkDurationUs : lastChunkDurationUs;
    this.chunkCount = lastChunkDurationUs == 0 ? fullChunks : fullChunks + 1;
    double[] bitrateFactors = new double[chunkCount];
    for (int i = 0; i < chunkCount; i++) {
      bitrateFactors[i] = 1.0 + random.nextGaussian() * bitratePercentStdDev / 100.0;
    }
    for (int i = 0; i < trackGroup.length; i++) {
      String uri = getUri(i);
      Format format = trackGroup.getFormat(i);
      double avgChunkLength = format.bitrate * chunkDurationUs / (8 * C.MICROS_PER_SECOND);
      FakeData newData = this.newData(uri);
      for (int j = 0; j < fullChunks; j++) {
        newData.appendReadData((int) (avgChunkLength * bitrateFactors[j]));
      }
      if (lastChunkDurationUs > 0) {
        int lastChunkLength = (int) (format.bitrate * bitrateFactors[bitrateFactors.length - 1]
            * (mediaDurationUs % chunkDurationUs) / (8 * C.MICROS_PER_SECOND));
        newData.appendReadData(lastChunkLength);
      }
    }
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public String getUri(int trackIndex) {
    return "fake://adaptive.media/" + trackIndex;
  }

  public long getChunkDuration(int chunkIndex) {
    return chunkIndex == getChunkCount() - 1 ? lastChunkDurationUs : chunkDurationUs;
  }

  public long getStartTime(int chunkIndex) {
    return chunkIndex * chunkDurationUs;
  }

  public int getChunkIndexByPosition(long positionUs) {
    return (int) (positionUs / chunkDurationUs);
  }
}
