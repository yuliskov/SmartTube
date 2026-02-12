/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.metadata;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.scte35.TimeSignalCommand;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MetadataRenderer}. */
@RunWith(AndroidJUnit4.class)
public class MetadataRendererTest {

  private static final byte[] SCTE35_TIME_SIGNAL_BYTES =
      TestUtil.joinByteArrays(
          TestUtil.createByteArray(
              0, // table_id.
              0x80, // section_syntax_indicator, private_indicator, reserved, section_length(4).
              0x14, // section_length(8).
              0x00, // protocol_version.
              0x00), // encrypted_packet, encryption_algorithm, pts_adjustment(1).
          TestUtil.createByteArray(0x00, 0x00, 0x00, 0x00), // pts_adjustment(32).
          TestUtil.createByteArray(
              0x00, // cw_index.
              0x00, // tier(8).
              0x00, // tier(4), splice_command_length(4).
              0x05, // splice_command_length(8).
              0x06, // splice_command_type = time_signal.
              // Start of splice_time().
              0x80), // time_specified_flag, reserved, pts_time(1).
          TestUtil.createByteArray(
              0x52, 0x03, 0x02, 0x8f), // pts_time(32). PTS for a second after playback position.
          TestUtil.createByteArray(
              0x00, 0x00, 0x00, 0x00)); // CRC_32 (ignored, check happens at extraction).

  private static final Format EMSG_FORMAT =
      Format.createSampleFormat(null, MimeTypes.APPLICATION_EMSG, Format.OFFSET_SAMPLE_RELATIVE);

  private final EventMessageEncoder eventMessageEncoder = new EventMessageEncoder();

  @Test
  public void decodeMetadata() throws Exception {
    EventMessage emsg =
        new EventMessage(
            "urn:test-scheme-id",
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Test data".getBytes(UTF_8));

    List<Metadata> metadata = runRenderer(EMSG_FORMAT, eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    assertThat(metadata.get(0).get(0)).isEqualTo(emsg);
  }

  @Test
  public void decodeMetadata_skipsMalformed() throws Exception {
    List<Metadata> metadata = runRenderer(EMSG_FORMAT, "not valid emsg bytes".getBytes(UTF_8));

    assertThat(metadata).isEmpty();
  }

  @Test
  public void decodeMetadata_handlesId3WrappedInEmsg() throws Exception {
    EventMessage emsg =
        new EventMessage(
            EventMessage.ID3_SCHEME_ID_AOM,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            encodeTxxxId3Frame("Test description", "Test value"));

    List<Metadata> metadata = runRenderer(EMSG_FORMAT, eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    TextInformationFrame expectedId3Frame =
        new TextInformationFrame("TXXX", "Test description", "Test value");
    assertThat(metadata.get(0).get(0)).isEqualTo(expectedId3Frame);
  }

  @Test
  public void decodeMetadata_handlesScte35WrappedInEmsg() throws Exception {

    EventMessage emsg =
        new EventMessage(
            EventMessage.SCTE35_SCHEME_ID,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            SCTE35_TIME_SIGNAL_BYTES);

    List<Metadata> metadata = runRenderer(EMSG_FORMAT, eventMessageEncoder.encode(emsg));

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).length()).isEqualTo(1);
    assertThat(metadata.get(0).get(0)).isInstanceOf(TimeSignalCommand.class);
  }

  @Test
  public void decodeMetadata_skipsMalformedWrappedMetadata() throws Exception {
    EventMessage emsg =
        new EventMessage(
            EventMessage.ID3_SCHEME_ID_AOM,
            /* value= */ "",
            /* durationMs= */ 1,
            /* id= */ 0,
            "Not a real ID3 tag".getBytes(ISO_8859_1));

    List<Metadata> metadata = runRenderer(EMSG_FORMAT, eventMessageEncoder.encode(emsg));

    assertThat(metadata).isEmpty();
  }

  private static List<Metadata> runRenderer(Format format, byte[] input)
      throws ExoPlaybackException {
    List<Metadata> metadata = new ArrayList<>();
    MetadataRenderer renderer = new MetadataRenderer(metadata::add, /* outputLooper= */ null);
    renderer.replaceStream(
        new Format[] {format},
        new FakeSampleStream(format, /* eventDispatcher= */ null, input),
        /* offsetUs= */ 0L);
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0); // Read the format
    renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0); // Read the data

    return Collections.unmodifiableList(metadata);
  }

  /**
   * Builds an ID3v2 tag containing a single 'user defined text information frame' (id='TXXX') with
   * {@code description} and {@code value}.
   *
   * <ul>
   *   <li><a href="http://id3.org/id3v2.4.0-structure">ID3v2 overall structure</a>
   *   <li><a href="http://id3.org/id3v2.4.0-frames">'user defined text information frame' spec</a>
   *       (item 4.2.6)
   * </ul>
   */
  private static byte[] encodeTxxxId3Frame(String description, String value) {
    byte[] id3FrameData =
        TestUtil.joinByteArrays(
            "TXXX".getBytes(ISO_8859_1), // ID for a 'user defined text information frame'
            TestUtil.createByteArray(0, 0, 0, 0), // Frame size (set later)
            TestUtil.createByteArray(0, 0), // Frame flags
            TestUtil.createByteArray(0), // Character encoding = ISO-8859-1
            description.getBytes(ISO_8859_1),
            TestUtil.createByteArray(0), // String null terminator
            value.getBytes(ISO_8859_1),
            TestUtil.createByteArray(0)); // String null terminator
    int frameSizeIndex = 7;
    int frameSize = id3FrameData.length - 10;
    Assertions.checkArgument(
        frameSize < 128, "frameSize must fit in 7 bits to avoid synch-safe encoding: " + frameSize);
    id3FrameData[frameSizeIndex] = (byte) frameSize;

    byte[] id3Bytes =
        TestUtil.joinByteArrays(
            "ID3".getBytes(ISO_8859_1), // identifier
            TestUtil.createByteArray(0x04, 0x00), // version
            TestUtil.createByteArray(0), // Tag flags
            TestUtil.createByteArray(0, 0, 0, 0), // Tag size (set later)
            id3FrameData);
    int tagSizeIndex = 9;
    int tagSize = id3Bytes.length - 10;
    Assertions.checkArgument(
        tagSize < 128, "tagSize must fit in 7 bits to avoid synch-safe encoding: " + tagSize);
    id3Bytes[tagSizeIndex] = (byte) tagSize;
    return id3Bytes;
  }
}
