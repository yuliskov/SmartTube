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

import static com.google.android.exoplayer2.extractor.ogg.VorbisReader.readBits;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ogg.VorbisReader.VorbisSetup;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link VorbisReader}. */
@RunWith(AndroidJUnit4.class)
public final class VorbisReaderTest {

  @Test
  public void testReadBits() throws Exception {
    assertThat(readBits((byte) 0x00, 2, 2)).isEqualTo(0);
    assertThat(readBits((byte) 0x02, 1, 1)).isEqualTo(1);
    assertThat(readBits((byte) 0xF0, 4, 4)).isEqualTo(15);
    assertThat(readBits((byte) 0x80, 1, 7)).isEqualTo(1);
  }

  @Test
  public void testAppendNumberOfSamples() throws Exception {
    ParsableByteArray buffer = new ParsableByteArray(4);
    buffer.setLimit(0);
    VorbisReader.appendNumberOfSamples(buffer, 0x01234567);
    assertThat(buffer.limit()).isEqualTo(4);
    assertThat(buffer.data[0]).isEqualTo(0x67);
    assertThat(buffer.data[1]).isEqualTo(0x45);
    assertThat(buffer.data[2]).isEqualTo(0x23);
    assertThat(buffer.data[3]).isEqualTo(0x01);
  }

  @Test
  public void testReadSetupHeadersWithIOExceptions() throws IOException, InterruptedException {
    byte[] data = OggTestData.getVorbisHeaderPages();
    ExtractorInput input = new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(true).setSimulatePartialReads(true).build();

    VorbisReader reader = new VorbisReader();
    VorbisReader.VorbisSetup vorbisSetup = readSetupHeaders(reader, input);

    assertThat(vorbisSetup.idHeader).isNotNull();
    assertThat(vorbisSetup.commentHeader).isNotNull();
    assertThat(vorbisSetup.setupHeaderData).isNotNull();
    assertThat(vorbisSetup.modes).isNotNull();

    assertThat(vorbisSetup.commentHeader.length).isEqualTo(45);
    assertThat(vorbisSetup.idHeader.data).hasLength(30);
    assertThat(vorbisSetup.setupHeaderData).hasLength(3597);

    assertThat(vorbisSetup.idHeader.bitrateMax).isEqualTo(-1);
    assertThat(vorbisSetup.idHeader.bitrateMin).isEqualTo(-1);
    assertThat(vorbisSetup.idHeader.bitrateNominal).isEqualTo(66666);
    assertThat(vorbisSetup.idHeader.blockSize0).isEqualTo(512);
    assertThat(vorbisSetup.idHeader.blockSize1).isEqualTo(1024);
    assertThat(vorbisSetup.idHeader.channels).isEqualTo(2);
    assertThat(vorbisSetup.idHeader.framingFlag).isTrue();
    assertThat(vorbisSetup.idHeader.sampleRate).isEqualTo(22050);
    assertThat(vorbisSetup.idHeader.version).isEqualTo(0);

    assertThat(vorbisSetup.commentHeader.vendor).isEqualTo("Xiph.Org libVorbis I 20030909");
    assertThat(vorbisSetup.iLogModes).isEqualTo(1);

    assertThat(vorbisSetup.setupHeaderData[vorbisSetup.setupHeaderData.length - 1])
        .isEqualTo(data[data.length - 1]);

    assertThat(vorbisSetup.modes[0].blockFlag).isFalse();
    assertThat(vorbisSetup.modes[1].blockFlag).isTrue();
  }

  private static VorbisSetup readSetupHeaders(VorbisReader reader, ExtractorInput input)
      throws IOException, InterruptedException {
    OggPacket oggPacket = new OggPacket();
    while (true) {
      try {
        if (!oggPacket.populate(input)) {
          fail();
        }
        VorbisSetup vorbisSetup = reader.readSetupHeaders(oggPacket.getPayload());
        if (vorbisSetup != null) {
          return vorbisSetup;
        }
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}
