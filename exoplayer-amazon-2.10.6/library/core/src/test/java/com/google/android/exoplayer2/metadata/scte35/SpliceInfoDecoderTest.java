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
package com.google.android.exoplayer2.metadata.scte35;

import static com.google.android.exoplayer2.C.TIME_UNSET;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link SpliceInfoDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class SpliceInfoDecoderTest {

  private SpliceInfoDecoder decoder;
  private MetadataInputBuffer inputBuffer;

  @Before
  public void setUp() {
    decoder = new SpliceInfoDecoder();
    inputBuffer = new MetadataInputBuffer();
  }

  @Test
  public void testWrappedAroundTimeSignalCommand() {
    byte[] rawTimeSignalSection = new byte[] {
        0, // table_id.
        (byte) 0x80, // section_syntax_indicator, private_indicator, reserved, section_length(4).
        0x14, // section_length(8).
        0x00, // protocol_version.
        0x00, // encrypted_packet, encryption_algorithm, pts_adjustment(1).
        0x00, 0x00, 0x00, 0x00, // pts_adjustment(32).
        0x00, // cw_index.
        0x00, // tier(8).
        0x00, // tier(4), splice_command_length(4).
        0x05, // splice_command_length(8).
        0x06, // splice_command_type = time_signal.
        // Start of splice_time().
        (byte) 0x80, // time_specified_flag, reserved, pts_time(1).
        0x52, 0x03, 0x02, (byte) 0x8f, // pts_time(32). PTS for a second after playback position.
        0x00, 0x00, 0x00, 0x00}; // CRC_32 (ignored, check happens at extraction).

    // The playback position is 57:15:58.43 approximately.
    // With this offset, the playback position pts before wrapping is 0x451ebf851.
    Metadata metadata = feedInputBuffer(rawTimeSignalSection, 0x3000000000L, -0x50000L);
    assertThat(metadata.length()).isEqualTo(1);
    assertThat(((TimeSignalCommand) metadata.get(0)).playbackPositionUs)
        .isEqualTo(removePtsConversionPrecisionError(0x3001000000L, inputBuffer.subsampleOffsetUs));
  }

  @Test
  public void test2SpliceInsertCommands() {
    byte[] rawSpliceInsertCommand1 = new byte[] {
        0, // table_id.
        (byte) 0x80, // section_syntax_indicator, private_indicator, reserved, section_length(4).
        0x19, // section_length(8).
        0x00, // protocol_version.
        0x00, // encrypted_packet, encryption_algorithm, pts_adjustment(1).
        0x00, 0x00, 0x00, 0x00, // pts_adjustment(32).
        0x00, // cw_index.
        0x00, // tier(8).
        0x00, // tier(4), splice_command_length(4).
        0x0e, // splice_command_length(8).
        0x05, // splice_command_type = splice_insert.
        // Start of splice_insert().
        0x00, 0x00, 0x00, 0x42, // splice_event_id.
        0x00, // splice_event_cancel_indicator, reserved.
        0x40, // out_of_network_indicator, program_splice_flag, duration_flag,
              // splice_immediate_flag, reserved.
        // start of splice_time().
        (byte) 0x80, // time_specified_flag, reserved, pts_time(1).
        0x00, 0x00, 0x00, 0x00, // PTS for playback position 3s.
        0x00, 0x10, // unique_program_id.
        0x01, // avail_num.
        0x02, // avails_expected.
        0x00, 0x00, 0x00, 0x00}; // CRC_32 (ignored, check happens at extraction).

    Metadata metadata = feedInputBuffer(rawSpliceInsertCommand1, 2000000, 3000000);
    assertThat(metadata.length()).isEqualTo(1);
    SpliceInsertCommand command = (SpliceInsertCommand) metadata.get(0);
    assertThat(command.spliceEventId).isEqualTo(66);
    assertThat(command.spliceEventCancelIndicator).isFalse();
    assertThat(command.outOfNetworkIndicator).isFalse();
    assertThat(command.programSpliceFlag).isTrue();
    assertThat(command.spliceImmediateFlag).isFalse();
    assertThat(command.programSplicePlaybackPositionUs).isEqualTo(3000000);
    assertThat(command.breakDurationUs).isEqualTo(TIME_UNSET);
    assertThat(command.uniqueProgramId).isEqualTo(16);
    assertThat(command.availNum).isEqualTo(1);
    assertThat(command.availsExpected).isEqualTo(2);

    byte[] rawSpliceInsertCommand2 = new byte[] {
        0, // table_id.
        (byte) 0x80, // section_syntax_indicator, private_indicator, reserved, section_length(4).
        0x22, // section_length(8).
        0x00, // protocol_version.
        0x00, // encrypted_packet, encryption_algorithm, pts_adjustment(1).
        0x00, 0x00, 0x00, 0x00, // pts_adjustment(32).
        0x00, // cw_index.
        0x00, // tier(8).
        0x00, // tier(4), splice_command_length(4).
        0x13, // splice_command_length(8).
        0x05, // splice_command_type = splice_insert.
        // Start of splice_insert().
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // splice_event_id.
        0x00, // splice_event_cancel_indicator, reserved.
        0x00, // out_of_network_indicator, program_splice_flag, duration_flag,
              // splice_immediate_flag, reserved.
        0x02, // component_count.
        0x10, // component_tag.
        // start of splice_time().
        (byte) 0x81, // time_specified_flag, reserved, pts_time(1).
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // PTS for playback position 10s.
        // start of splice_time().
        0x11, // component_tag.
        0x00, // time_specified_flag, reserved.
        0x00, 0x20, // unique_program_id.
        0x01, // avail_num.
        0x02, // avails_expected.
        0x00, 0x00, 0x00, 0x00}; // CRC_32 (ignored, check happens at extraction).

    // By changing the subsample offset we force adjuster reconstruction.
    long subsampleOffset = 1000011;
    metadata = feedInputBuffer(rawSpliceInsertCommand2, 1000000, subsampleOffset);
    assertThat(metadata.length()).isEqualTo(1);
    command = (SpliceInsertCommand) metadata.get(0);
    assertThat(command.spliceEventId).isEqualTo(0xffffffffL);
    assertThat(command.spliceEventCancelIndicator).isFalse();
    assertThat(command.outOfNetworkIndicator).isFalse();
    assertThat(command.programSpliceFlag).isFalse();
    assertThat(command.spliceImmediateFlag).isFalse();
    assertThat(command.programSplicePlaybackPositionUs).isEqualTo(TIME_UNSET);
    assertThat(command.breakDurationUs).isEqualTo(TIME_UNSET);
    List<SpliceInsertCommand.ComponentSplice> componentSplices = command.componentSpliceList;
    assertThat(componentSplices).hasSize(2);
    assertThat(componentSplices.get(0).componentTag).isEqualTo(16);
    assertThat(componentSplices.get(0).componentSplicePlaybackPositionUs).isEqualTo(1000000);
    assertThat(componentSplices.get(1).componentTag).isEqualTo(17);
    assertThat(componentSplices.get(1).componentSplicePts).isEqualTo(TIME_UNSET);
    assertThat(command.uniqueProgramId).isEqualTo(32);
    assertThat(command.availNum).isEqualTo(1);
    assertThat(command.availsExpected).isEqualTo(2);
  }

  private Metadata feedInputBuffer(byte[] data, long timeUs, long subsampleOffset) {
    inputBuffer.clear();
    inputBuffer.data = ByteBuffer.allocate(data.length).put(data);
    inputBuffer.timeUs = timeUs;
    inputBuffer.subsampleOffsetUs = subsampleOffset;
    return decoder.decode(inputBuffer);
  }

  private static long removePtsConversionPrecisionError(long timeUs, long offsetUs) {
    return TimestampAdjuster.ptsToUs(TimestampAdjuster.usToPts(timeUs - offsetUs)) + offsetUs;
  }

}
