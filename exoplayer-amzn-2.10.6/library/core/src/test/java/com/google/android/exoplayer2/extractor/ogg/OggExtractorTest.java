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
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class OggExtractorTest {

  private static final ExtractorFactory OGG_EXTRACTOR_FACTORY = OggExtractor::new;

  @Test
  public void testOpus() throws Exception {
    ExtractorAsserts.assertBehavior(OGG_EXTRACTOR_FACTORY, "ogg/bear.opus");
  }

  @Test
  public void testFlac() throws Exception {
    ExtractorAsserts.assertBehavior(OGG_EXTRACTOR_FACTORY, "ogg/bear_flac.ogg");
  }

  @Test
  public void testFlacNoSeektable() throws Exception {
    ExtractorAsserts.assertBehavior(OGG_EXTRACTOR_FACTORY, "ogg/bear_flac_noseektable.ogg");
  }

  @Test
  public void testVorbis() throws Exception {
    ExtractorAsserts.assertBehavior(OGG_EXTRACTOR_FACTORY, "ogg/bear_vorbis.ogg");
  }

  @Test
  public void testSniffVorbis() throws Exception {
    byte[] data =
        TestUtil.joinByteArrays(
            OggTestData.buildOggHeader(0x02, 0, 1000, 1),
            TestUtil.createByteArray(7), // Laces
            new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertThat(sniff(data)).isTrue();
  }

  @Test
  public void testSniffFlac() throws Exception {
    byte[] data =
        TestUtil.joinByteArrays(
            OggTestData.buildOggHeader(0x02, 0, 1000, 1),
            TestUtil.createByteArray(5), // Laces
            new byte[] {0x7F, 'F', 'L', 'A', 'C'});
    assertThat(sniff(data)).isTrue();
  }

  @Test
  public void testSniffFailsOpusFile() throws Exception {
    byte[] data =
        TestUtil.joinByteArrays(
            OggTestData.buildOggHeader(0x02, 0, 1000, 0x00), new byte[] {'O', 'p', 'u', 's'});
    assertThat(sniff(data)).isFalse();
  }

  @Test
  public void testSniffFailsInvalidOggHeader() throws Exception {
    byte[] data = OggTestData.buildOggHeader(0x00, 0, 1000, 0x00);
    assertThat(sniff(data)).isFalse();
  }

  @Test
  public void testSniffInvalidHeader() throws Exception {
    byte[] data =
        TestUtil.joinByteArrays(
            OggTestData.buildOggHeader(0x02, 0, 1000, 1),
            TestUtil.createByteArray(7), // Laces
            new byte[] {0x7F, 'X', 'o', 'r', 'b', 'i', 's'});
    assertThat(sniff(data)).isFalse();
  }

  @Test
  public void testSniffFailsEOF() throws Exception {
    byte[] data = OggTestData.buildOggHeader(0x02, 0, 1000, 0x00);
    assertThat(sniff(data)).isFalse();
  }

  private boolean sniff(byte[] data) throws InterruptedException, IOException {
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(data)
            .setSimulateIOErrors(true)
            .setSimulateUnknownLength(true)
            .setSimulatePartialReads(true)
            .build();
    return TestUtil.sniffTestData(OGG_EXTRACTOR_FACTORY.create(), input);
  }
}
