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
package com.google.android.exoplayer2.source.chunk;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeMediaChunk;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaChunkListIterator}. */
@RunWith(AndroidJUnit4.class)
public class MediaChunkListIteratorTest {

  private static final Format TEST_FORMAT = Format.createSampleFormat(null, null, 0);

  private FakeMediaChunk testChunk1;
  private FakeMediaChunk testChunk2;

  @Before
  public void setUp() {
    testChunk1 = new FakeMediaChunk(TEST_FORMAT, 0, 10);
    testChunk2 = new FakeMediaChunk(TEST_FORMAT, 10, 20);
  }

  @Test
  public void iterator_reverseOrderFalse_returnsItemsInNormalOrder() {
    MediaChunkListIterator iterator =
        new MediaChunkListIterator(
            Arrays.asList(testChunk1, testChunk2), /* reverseOrder= */ false);
    assertThat(iterator.isEnded()).isFalse();
    assertThat(iterator.next()).isTrue();
    assertEqual(iterator, testChunk1);
    assertThat(iterator.next()).isTrue();
    assertEqual(iterator, testChunk2);
    assertThat(iterator.next()).isFalse();
    assertThat(iterator.isEnded()).isTrue();
  }

  @Test
  public void iterator_reverseOrderTrue_returnsItemsInReverseOrder() {
    MediaChunkListIterator iterator =
        new MediaChunkListIterator(
            Arrays.asList(testChunk1, testChunk2), /* reverseOrder= */ true);
    assertThat(iterator.isEnded()).isFalse();
    assertThat(iterator.next()).isTrue();
    assertEqual(iterator, testChunk2);
    assertThat(iterator.next()).isTrue();
    assertEqual(iterator, testChunk1);
    assertThat(iterator.next()).isFalse();
    assertThat(iterator.isEnded()).isTrue();
  }

  private static void assertEqual(MediaChunkListIterator iterator, FakeMediaChunk chunk) {
    assertThat(iterator.getChunkStartTimeUs()).isEqualTo(chunk.startTimeUs);
    assertThat(iterator.getChunkEndTimeUs()).isEqualTo(chunk.endTimeUs);
    assertThat(iterator.getDataSpec()).isEqualTo(chunk.dataSpec);
  }
}
