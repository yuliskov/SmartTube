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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggPageHeader}. */
@RunWith(AndroidJUnit4.class)
public final class OggPageHeaderTest {

  @Test
  public void testPopulatePageHeader() throws IOException, InterruptedException {
    FakeExtractorInput input = OggTestData.createInput(TestUtil.joinByteArrays(
        OggTestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    ), true);
    OggPageHeader header = new OggPageHeader();
    populatePageHeader(input, header, false);

    assertThat(header.type).isEqualTo(0x01);
    assertThat(header.headerSize).isEqualTo(27 + 2);
    assertThat(header.bodySize).isEqualTo(4);
    assertThat(header.pageSegmentCount).isEqualTo(2);
    assertThat(header.granulePosition).isEqualTo(123456);
    assertThat(header.pageSequenceNumber).isEqualTo(4);
    assertThat(header.streamSerialNumber).isEqualTo(0x1000);
    assertThat(header.pageChecksum).isEqualTo(0x100000);
    assertThat(header.revision).isEqualTo(0);
  }

  @Test
  public void testPopulatePageHeaderQuiteOnExceptionLessThan27Bytes()
      throws IOException, InterruptedException {
    FakeExtractorInput input = OggTestData.createInput(TestUtil.createByteArray(2, 2), false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, true)).isFalse();
  }

  @Test
  public void testPopulatePageHeaderQuiteOnExceptionNotOgg()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        OggTestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change from 'O' to 'o'
    headerBytes[0] = 'o';
    FakeExtractorInput input = OggTestData.createInput(headerBytes, false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, true)).isFalse();
  }

  @Test
  public void testPopulatePageHeaderQuiteOnExceptionWrongRevision()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        OggTestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change revision from 0 to 1
    headerBytes[4] = 0x01;
    FakeExtractorInput input = OggTestData.createInput(headerBytes, false);
    OggPageHeader header = new OggPageHeader();
    assertThat(populatePageHeader(input, header, true)).isFalse();
  }

  private boolean populatePageHeader(FakeExtractorInput input, OggPageHeader header,
      boolean quite) throws IOException, InterruptedException {
    while (true) {
      try {
        return header.populate(input, quite);
      } catch (SimulatedIOException e) {
        // ignored
      }
    }
  }

}

