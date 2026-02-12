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
package com.google.android.exoplayer2.extractor.flv;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;

/**
 * Parses audio tags from an FLV stream and extracts AAC frames.
 */
/* package */ final class AudioTagPayloadReader extends TagPayloadReader {

  private static final int AUDIO_FORMAT_MP3 = 2;
  private static final int AUDIO_FORMAT_ALAW = 7;
  private static final int AUDIO_FORMAT_ULAW = 8;
  private static final int AUDIO_FORMAT_AAC = 10;

  private static final int AAC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AAC_PACKET_TYPE_AAC_RAW = 1;

  private static final int[] AUDIO_SAMPLING_RATE_TABLE = new int[] {5512, 11025, 22050, 44100};

  // State variables
  private boolean hasParsedAudioDataHeader;
  private boolean hasOutputFormat;
  private int audioFormat;

  public AudioTagPayloadReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
    if (!hasParsedAudioDataHeader) {
      int header = data.readUnsignedByte();
      audioFormat = (header >> 4) & 0x0F;
      if (audioFormat == AUDIO_FORMAT_MP3) {
        int sampleRateIndex = (header >> 2) & 0x03;
        int sampleRate = AUDIO_SAMPLING_RATE_TABLE[sampleRateIndex];
        Format format = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_MPEG, null,
            Format.NO_VALUE, Format.NO_VALUE, 1, sampleRate, null, null, 0, null);
        output.format(format);
        hasOutputFormat = true;
      } else if (audioFormat == AUDIO_FORMAT_ALAW || audioFormat == AUDIO_FORMAT_ULAW) {
        String type = audioFormat == AUDIO_FORMAT_ALAW ? MimeTypes.AUDIO_ALAW
            : MimeTypes.AUDIO_MLAW;
        int pcmEncoding = (header & 0x01) == 1 ? C.ENCODING_PCM_16BIT : C.ENCODING_PCM_8BIT;
        Format format = Format.createAudioSampleFormat(null, type, null, Format.NO_VALUE,
            Format.NO_VALUE, 1, 8000, pcmEncoding, null, null, 0, null);
        output.format(format);
        hasOutputFormat = true;
      } else if (audioFormat != AUDIO_FORMAT_AAC) {
        throw new UnsupportedFormatException("Audio format not supported: " + audioFormat);
      }
      hasParsedAudioDataHeader = true;
    } else {
      // Skip header if it was parsed previously.
      data.skipBytes(1);
    }
    return true;
  }

  @Override
  protected boolean parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
    if (audioFormat == AUDIO_FORMAT_MP3) {
      int sampleSize = data.bytesLeft();
      output.sampleData(data, sampleSize);
      output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
      return true;
    } else {
      int packetType = data.readUnsignedByte();
      if (packetType == AAC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
        // Parse the sequence header.
        byte[] audioSpecificConfig = new byte[data.bytesLeft()];
        data.readBytes(audioSpecificConfig, 0, audioSpecificConfig.length);
        Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(
            audioSpecificConfig);
        Format format = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, audioParams.second, audioParams.first,
            Collections.singletonList(audioSpecificConfig), null, 0, null);
        output.format(format);
        hasOutputFormat = true;
        return false;
      } else if (audioFormat != AUDIO_FORMAT_AAC || packetType == AAC_PACKET_TYPE_AAC_RAW) {
        int sampleSize = data.bytesLeft();
        output.sampleData(data, sampleSize);
        output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
        return true;
      } else {
        return false;
      }
    }
  }
}
