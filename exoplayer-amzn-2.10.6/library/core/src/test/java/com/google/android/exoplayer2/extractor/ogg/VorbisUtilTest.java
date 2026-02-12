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

import static com.google.android.exoplayer2.extractor.ogg.VorbisUtil.iLog;
import static com.google.android.exoplayer2.extractor.ogg.VorbisUtil.verifyVorbisHeaderCapturePattern;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link VorbisUtil}. */
@RunWith(AndroidJUnit4.class)
public final class VorbisUtilTest {

  @Test
  public void testILog() throws Exception {
    assertThat(iLog(0)).isEqualTo(0);
    assertThat(iLog(1)).isEqualTo(1);
    assertThat(iLog(2)).isEqualTo(2);
    assertThat(iLog(3)).isEqualTo(2);
    assertThat(iLog(4)).isEqualTo(3);
    assertThat(iLog(5)).isEqualTo(3);
    assertThat(iLog(8)).isEqualTo(4);
    assertThat(iLog(-1)).isEqualTo(0);
    assertThat(iLog(-122)).isEqualTo(0);
  }

  @Test
  public void testReadIdHeader() throws Exception {
    byte[] data = OggTestData.getIdentificationHeaderData();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.VorbisIdHeader vorbisIdHeader =
        VorbisUtil.readVorbisIdentificationHeader(headerData);

    assertThat(vorbisIdHeader.sampleRate).isEqualTo(22050);
    assertThat(vorbisIdHeader.version).isEqualTo(0);
    assertThat(vorbisIdHeader.framingFlag).isTrue();
    assertThat(vorbisIdHeader.channels).isEqualTo(2);
    assertThat(vorbisIdHeader.blockSize0).isEqualTo(512);
    assertThat(vorbisIdHeader.blockSize1).isEqualTo(1024);
    assertThat(vorbisIdHeader.bitrateMax).isEqualTo(-1);
    assertThat(vorbisIdHeader.bitrateMin).isEqualTo(-1);
    assertThat(vorbisIdHeader.bitrateNominal).isEqualTo(66666);
    assertThat(vorbisIdHeader.getApproximateBitrate()).isEqualTo(66666);
  }

  @Test
  public void testReadCommentHeader() throws ParserException {
    byte[] data = OggTestData.getCommentHeaderDataUTF8();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.CommentHeader commentHeader = VorbisUtil.readVorbisCommentHeader(headerData);

    assertThat(commentHeader.vendor).isEqualTo("Xiph.Org libVorbis I 20120203 (Omnipresent)");
    assertThat(commentHeader.comments).hasLength(3);
    assertThat(commentHeader.comments[0]).isEqualTo("ALBUM=äö");
    assertThat(commentHeader.comments[1]).isEqualTo("TITLE=A sample song");
    assertThat(commentHeader.comments[2]).isEqualTo("ARTIST=Google");
  }

  @Test
  public void testReadVorbisModes() throws ParserException {
    byte[] data = OggTestData.getSetupHeaderData();
    ParsableByteArray headerData = new ParsableByteArray(data, data.length);
    VorbisUtil.Mode[] modes = VorbisUtil.readVorbisModes(headerData, 2);

    assertThat(modes).hasLength(2);
    assertThat(modes[0].blockFlag).isFalse();
    assertThat(modes[0].mapping).isEqualTo(0);
    assertThat(modes[0].transformType).isEqualTo(0);
    assertThat(modes[0].windowType).isEqualTo(0);
    assertThat(modes[1].blockFlag).isTrue();
    assertThat(modes[1].mapping).isEqualTo(1);
    assertThat(modes[1].transformType).isEqualTo(0);
    assertThat(modes[1].windowType).isEqualTo(0);
  }

  @Test
  public void testVerifyVorbisHeaderCapturePattern() throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertThat(verifyVorbisHeaderCapturePattern(0x01, header, false)).isTrue();
  }

  @Test
  public void testVerifyVorbisHeaderCapturePatternInvalidHeader() {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    try {
      VorbisUtil.verifyVorbisHeaderCapturePattern(0x99, header, false);
      fail();
    } catch (ParserException e) {
      assertThat(e.getMessage()).isEqualTo("expected header type 99");
    }
  }

  @Test
  public void testVerifyVorbisHeaderCapturePatternInvalidHeaderQuite() throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertThat(verifyVorbisHeaderCapturePattern(0x99, header, true)).isFalse();
  }

  @Test
  public void testVerifyVorbisHeaderCapturePatternInvalidPattern() {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'x', 'v', 'o', 'r', 'b', 'i', 's'});
    try {
      VorbisUtil.verifyVorbisHeaderCapturePattern(0x01, header, false);
      fail();
    } catch (ParserException e) {
      assertThat(e.getMessage()).isEqualTo("expected characters 'vorbis'");
    }
  }

  @Test
  public void testVerifyVorbisHeaderCapturePatternQuiteInvalidPatternQuite()
      throws ParserException {
    ParsableByteArray header = new ParsableByteArray(
        new byte[] {0x01, 'x', 'v', 'o', 'r', 'b', 'i', 's'});
    assertThat(verifyVorbisHeaderCapturePattern(0x01, header, true)).isFalse();
  }

}
