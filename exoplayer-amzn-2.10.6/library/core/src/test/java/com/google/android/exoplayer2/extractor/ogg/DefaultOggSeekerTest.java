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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultOggSeeker}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultOggSeekerTest {

  private final Random random = new Random(0);

  @Test
  public void testSetupWithUnsetEndPositionFails() {
    try {
      new DefaultOggSeeker(
          /* streamReader= */ new TestStreamReader(),
          /* payloadStartPosition= */ 0,
          /* payloadEndPosition= */ C.LENGTH_UNSET,
          /* firstPayloadPageSize= */ 1,
          /* firstPayloadPageGranulePosition= */ 1,
          /* firstPayloadPageIsLastPage= */ false);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  @Test
  public void testSeeking() throws IOException, InterruptedException {
    Random random = new Random(0);
    for (int i = 0; i < 100; i++) {
      testSeeking(random);
    }
  }

  @Test
  public void testSkipToNextPage() throws Exception {
    FakeExtractorInput extractorInput =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                TestUtil.buildTestData(4000, random),
                new byte[] {'O', 'g', 'g', 'S'},
                TestUtil.buildTestData(4000, random)),
            false);
    skipToNextPage(extractorInput);
    assertThat(extractorInput.getPosition()).isEqualTo(4000);
  }

  @Test
  public void testSkipToNextPageOverlap() throws Exception {
    FakeExtractorInput extractorInput =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                TestUtil.buildTestData(2046, random),
                new byte[] {'O', 'g', 'g', 'S'},
                TestUtil.buildTestData(4000, random)),
            false);
    skipToNextPage(extractorInput);
    assertThat(extractorInput.getPosition()).isEqualTo(2046);
  }

  @Test
  public void testSkipToNextPageInputShorterThanPeekLength() throws Exception {
    FakeExtractorInput extractorInput =
        OggTestData.createInput(
            TestUtil.joinByteArrays(new byte[] {'x', 'O', 'g', 'g', 'S'}), false);
    skipToNextPage(extractorInput);
    assertThat(extractorInput.getPosition()).isEqualTo(1);
  }

  @Test
  public void testSkipToNextPageNoMatch() throws Exception {
    FakeExtractorInput extractorInput =
        OggTestData.createInput(new byte[] {'g', 'g', 'S', 'O', 'g', 'g'}, false);
    try {
      skipToNextPage(extractorInput);
      fail();
    } catch (EOFException e) {
      // expected
    }
  }

  @Test
  public void testReadGranuleOfLastPage() throws IOException, InterruptedException {
    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                TestUtil.buildTestData(100, random),
                OggTestData.buildOggHeader(0x00, 20000, 66, 3),
                TestUtil.createByteArray(254, 254, 254), // laces
                TestUtil.buildTestData(3 * 254, random),
                OggTestData.buildOggHeader(0x00, 40000, 67, 3),
                TestUtil.createByteArray(254, 254, 254), // laces
                TestUtil.buildTestData(3 * 254, random),
                OggTestData.buildOggHeader(0x05, 60000, 68, 3),
                TestUtil.createByteArray(254, 254, 254), // laces
                TestUtil.buildTestData(3 * 254, random)),
            false);
    assertReadGranuleOfLastPage(input, 60000);
  }

  @Test
  public void testReadGranuleOfLastPageAfterLastHeader() throws IOException, InterruptedException {
    FakeExtractorInput input = OggTestData.createInput(TestUtil.buildTestData(100, random), false);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (EOFException e) {
      // Ignored.
    }
  }

  @Test
  public void testReadGranuleOfLastPageWithUnboundedLength()
      throws IOException, InterruptedException {
    FakeExtractorInput input = OggTestData.createInput(new byte[0], true);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (IllegalArgumentException e) {
      // Ignored.
    }
  }

  private void testSeeking(Random random) throws IOException, InterruptedException {
    OggTestFile testFile = OggTestFile.generate(random, 1000);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(testFile.data).build();
    TestStreamReader streamReader = new TestStreamReader();
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* streamReader= */ streamReader,
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ testFile.data.length,
            /* firstPayloadPageSize= */ testFile.firstPayloadPageSize,
            /* firstPayloadPageGranulePosition= */ testFile.firstPayloadPageGranuleCount,
            /* firstPayloadPageIsLastPage= */ false);
    OggPageHeader pageHeader = new OggPageHeader();

    while (true) {
      long nextSeekPosition = oggSeeker.read(input);
      if (nextSeekPosition == -1) {
        break;
      }
      input.setPosition((int) nextSeekPosition);
    }

    // Test granule 0 from file start.
    long granule = seekTo(input, oggSeeker, 0, 0);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test granule 0 from file end.
    granule = seekTo(input, oggSeeker, 0, testFile.data.length - 1);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test last granule.
    granule = seekTo(input, oggSeeker, testFile.granuleCount - 1, 0);
    assertThat(granule).isEqualTo(testFile.granuleCount - testFile.lastPayloadPageGranuleCount);
    assertThat(input.getPosition()).isEqualTo(testFile.data.length - testFile.lastPayloadPageSize);

    for (int i = 0; i < 100; i += 1) {
      long targetGranule = random.nextInt(testFile.granuleCount);
      int initialPosition = random.nextInt(testFile.data.length);
      granule = seekTo(input, oggSeeker, targetGranule, initialPosition);
      long currentPosition = input.getPosition();
      if (granule == 0) {
        assertThat(currentPosition).isEqualTo(0);
      } else {
        int previousPageStart = testFile.findPreviousPageStart(currentPosition);
        input.setPosition(previousPageStart);
        pageHeader.populate(input, false);
        assertThat(granule).isEqualTo(pageHeader.granulePosition);
      }

      input.setPosition((int) currentPosition);
      pageHeader.populate(input, false);
      // The target granule should be within the current page.
      assertThat(granule).isAtMost(targetGranule);
      assertThat(targetGranule).isLessThan(pageHeader.granulePosition);
    }
  }

  private static void skipToNextPage(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* streamReader= */ new FlacReader(),
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ extractorInput.getLength(),
            /* firstPayloadPageSize= */ 1,
            /* firstPayloadPageGranulePosition= */ 2,
            /* firstPayloadPageIsLastPage= */ false);
    while (true) {
      try {
        oggSeeker.skipToNextPage(extractorInput);
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        /* ignored */
      }
    }
  }

  private static void assertReadGranuleOfLastPage(FakeExtractorInput input, int expected)
      throws IOException, InterruptedException {
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* streamReader= */ new FlacReader(),
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ input.getLength(),
            /* firstPayloadPageSize= */ 1,
            /* firstPayloadPageGranulePosition= */ 2,
            /* firstPayloadPageIsLastPage= */ false);
    while (true) {
      try {
        assertThat(oggSeeker.readGranuleOfLastPage(input)).isEqualTo(expected);
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignored.
      }
    }
  }

  private static long seekTo(
      FakeExtractorInput input, DefaultOggSeeker oggSeeker, long targetGranule, int initialPosition)
      throws IOException, InterruptedException {
    long nextSeekPosition = initialPosition;
    oggSeeker.startSeek(targetGranule);
    int count = 0;
    while (nextSeekPosition >= 0) {
      if (count++ > 100) {
        fail("Seek failed to converge in 100 iterations");
      }
      input.setPosition((int) nextSeekPosition);
      nextSeekPosition = oggSeeker.read(input);
    }
    return -(nextSeekPosition + 2);
  }

  private static class TestStreamReader extends StreamReader {
    @Override
    protected long preparePayload(ParsableByteArray packet) {
      return 0;
    }

    @Override
    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) {
      return false;
    }
  }
}
