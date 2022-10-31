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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link CompositeSequenceableLoader}. */
@RunWith(AndroidJUnit4.class)
public final class CompositeSequenceableLoaderTest {

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns minimum buffered
   * position among all sub-loaders.
   */
  @Test
  public void testGetBufferedPositionUsReturnsMinimumLoaderBufferedPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns minimum buffered
   * position that is not {@link C#TIME_END_OF_SOURCE} among all sub-loaders.
   */
  @Test
  public void testGetBufferedPositionUsReturnsMinimumNonEndOfSourceLoaderBufferedPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2, loader3});
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns
   * {@link C#TIME_END_OF_SOURCE} when all sub-loaders have buffered till end-of-source.
   */
  @Test
  public void testGetBufferedPositionUsReturnsEndOfSourceWhenAllLoaderBufferedTillEndOfSource() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns minimum next
   * load position among all sub-loaders.
   */
  @Test
  public void testGetNextLoadPositionUsReturnMinimumLoaderNextLoadPositionUs() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2000);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(2000);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns minimum next
   * load position that is not {@link C#TIME_END_OF_SOURCE} among all sub-loaders.
   */
  @Test
  public void testGetNextLoadPositionUsReturnMinimumNonEndOfSourceLoaderNextLoadPositionUs() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2, loader3});
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(2000);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns
   * {@link C#TIME_END_OF_SOURCE} when all sub-loaders have next load position at end-of-source.
   */
  @Test
  public void testGetNextLoadPositionUsReturnsEndOfSourceWhenAllLoaderLoadingLastChunk() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(long)} only allows the loader
   * with minimum next load position to continue loading if next load positions are not behind
   * current playback position.
   */
  @Test
  public void testContinueLoadingOnlyAllowFurthestBehindLoaderToLoadIfNotBehindPlaybackPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    compositeSequenceableLoader.continueLoading(100);

    assertThat(loader1.numInvocations).isEqualTo(1);
    assertThat(loader2.numInvocations).isEqualTo(0);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(long)} allows all loaders
   * with next load position behind current playback position to continue loading.
   */
  @Test
  public void testContinueLoadingReturnAllowAllLoadersBehindPlaybackPositionToLoad() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1002, /* nextLoadPositionUs */ 2002);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2, loader3});
    compositeSequenceableLoader.continueLoading(3000);

    assertThat(loader1.numInvocations).isEqualTo(1);
    assertThat(loader2.numInvocations).isEqualTo(1);
    assertThat(loader3.numInvocations).isEqualTo(1);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(long)} does not allow loader
   * with next load position at end-of-source to continue loading.
   */
  @Test
  public void testContinueLoadingOnlyNotAllowEndOfSourceLoaderToLoad() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});
    compositeSequenceableLoader.continueLoading(3000);

    assertThat(loader1.numInvocations).isEqualTo(0);
    assertThat(loader2.numInvocations).isEqualTo(0);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(long)} returns true if the loader
   * with minimum next load position can make progress if next load positions are not behind
   * current playback position.
   */
  @Test
  public void testContinueLoadingReturnTrueIfFurthestBehindLoaderCanMakeProgress() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    loader1.setNextChunkDurationUs(1000);

    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});

    assertThat(compositeSequenceableLoader.continueLoading(100)).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(long)} returns true if any loader
   * that are behind current playback position can make progress, even if it is not the one with
   * minimum next load position.
   */
  @Test
  public void testContinueLoadingReturnTrueIfLoaderBehindPlaybackPositionCanMakeProgress() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    // loader2 is not the furthest behind, but it can make progress if allowed.
    loader2.setNextChunkDurationUs(1000);

    CompositeSequenceableLoader compositeSequenceableLoader = new CompositeSequenceableLoader(
        new SequenceableLoader[] {loader1, loader2});

    assertThat(compositeSequenceableLoader.continueLoading(3000)).isTrue();
  }

  private static class FakeSequenceableLoader implements SequenceableLoader {

    private long bufferedPositionUs;
    private long nextLoadPositionUs;
    private int numInvocations;
    private int nextChunkDurationUs;

    private FakeSequenceableLoader(long bufferedPositionUs, long nextLoadPositionUs) {
      this.bufferedPositionUs = bufferedPositionUs;
      this.nextLoadPositionUs = nextLoadPositionUs;
    }

    @Override
    public long getBufferedPositionUs() {
      return bufferedPositionUs;
    }

    @Override
    public long getNextLoadPositionUs() {
      return nextLoadPositionUs;
    }

    @Override
    public boolean continueLoading(long positionUs) {
      numInvocations++;
      boolean loaded = nextChunkDurationUs != 0;
      // The current chunk has been loaded, advance to next chunk.
      bufferedPositionUs = nextLoadPositionUs;
      nextLoadPositionUs += nextChunkDurationUs;
      nextChunkDurationUs = 0;
      return loaded;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
      // Do nothing.
    }

    private void setNextChunkDurationUs(int nextChunkDurationUs) {
      this.nextChunkDurationUs = nextChunkDurationUs;
    }

  }

}
