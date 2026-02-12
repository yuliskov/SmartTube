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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A fake {@link TrackOutput}.
 */
public final class FakeTrackOutput implements TrackOutput, Dumper.Dumpable {

  private final ArrayList<Long> sampleTimesUs;
  private final ArrayList<Integer> sampleFlags;
  private final ArrayList<Integer> sampleStartOffsets;
  private final ArrayList<Integer> sampleEndOffsets;
  private final ArrayList<CryptoData> cryptoDatas;

  private byte[] sampleData;
  public Format format;

  public FakeTrackOutput() {
    sampleData = Util.EMPTY_BYTE_ARRAY;
    sampleTimesUs = new ArrayList<>();
    sampleFlags = new ArrayList<>();
    sampleStartOffsets = new ArrayList<>();
    sampleEndOffsets = new ArrayList<>();
    cryptoDatas = new ArrayList<>();
  }

  public void clear() {
    sampleData = Util.EMPTY_BYTE_ARRAY;
    sampleTimesUs.clear();
    sampleFlags.clear();
    sampleStartOffsets.clear();
    sampleEndOffsets.clear();
    cryptoDatas.clear();
  }

  @Override
  public void format(Format format) {
    this.format = format;
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    byte[] newData = new byte[length];
    int bytesAppended = input.read(newData, 0, length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    newData = Arrays.copyOf(newData, bytesAppended);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
    return bytesAppended;
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    byte[] newData = new byte[length];
    data.readBytes(newData, 0, length);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
      CryptoData cryptoData) {
    sampleTimesUs.add(timeUs);
    sampleFlags.add(flags);
    sampleStartOffsets.add(sampleData.length - offset - size);
    sampleEndOffsets.add(sampleData.length - offset);
    cryptoDatas.add(cryptoData);
  }

  public void assertSampleCount(int count) {
    assertThat(sampleTimesUs).hasSize(count);
  }

  public void assertSample(int index, byte[] data, long timeUs, int flags, CryptoData cryptoData) {
    byte[] actualData = getSampleData(index);
    assertThat(actualData).isEqualTo(data);
    assertThat(sampleTimesUs.get(index)).isEqualTo(timeUs);
    assertThat(sampleFlags.get(index)).isEqualTo(flags);
    assertThat(cryptoDatas.get(index)).isEqualTo(cryptoData);
  }

  public byte[] getSampleData(int index) {
    return Arrays.copyOfRange(sampleData, sampleStartOffsets.get(index),
        sampleEndOffsets.get(index));
  }

  public long getSampleTimeUs(int index) {
    return sampleTimesUs.get(index);
  }

  public int getSampleFlags(int index) {
    return sampleFlags.get(index);
  }

  public CryptoData getSampleCryptoData(int index) {
    return cryptoDatas.get(index);
  }

  public int getSampleCount() {
    return sampleTimesUs.size();
  }

  public List<Long> getSampleTimesUs() {
    return Collections.unmodifiableList(sampleTimesUs);
  }

  public void assertEquals(FakeTrackOutput expected) {
    assertThat(format).isEqualTo(expected.format);
    assertThat(sampleTimesUs).hasSize(expected.sampleTimesUs.size());
    assertThat(sampleData).isEqualTo(expected.sampleData);
    for (int i = 0; i < sampleTimesUs.size(); i++) {
      assertThat(sampleTimesUs.get(i)).isEqualTo(expected.sampleTimesUs.get(i));
      assertThat(sampleFlags.get(i)).isEqualTo(expected.sampleFlags.get(i));
      assertThat(sampleStartOffsets.get(i)).isEqualTo(expected.sampleStartOffsets.get(i));
      assertThat(sampleEndOffsets.get(i)).isEqualTo(expected.sampleEndOffsets.get(i));
      if (expected.cryptoDatas.get(i) == null) {
        assertThat(cryptoDatas.get(i)).isNull();
      } else {
        assertThat(cryptoDatas.get(i)).isEqualTo(expected.cryptoDatas.get(i));
      }
    }
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.startBlock("format")
        .add("bitrate", format.bitrate)
        .add("id", format.id)
        .add("containerMimeType", format.containerMimeType)
        .add("sampleMimeType", format.sampleMimeType)
        .add("maxInputSize", format.maxInputSize)
        .add("width", format.width)
        .add("height", format.height)
        .add("frameRate", format.frameRate)
        .add("rotationDegrees", format.rotationDegrees)
        .add("pixelWidthHeightRatio", format.pixelWidthHeightRatio)
        .add("channelCount", format.channelCount)
        .add("sampleRate", format.sampleRate)
        .add("pcmEncoding", format.pcmEncoding)
        .add("encoderDelay", format.encoderDelay)
        .add("encoderPadding", format.encoderPadding)
        .add("subsampleOffsetUs", format.subsampleOffsetUs)
        .add("selectionFlags", format.selectionFlags)
        .add("language", format.language)
        .add("drmInitData", format.drmInitData != null ? format.drmInitData.hashCode() : "-");

    dumper.startBlock("initializationData");
    for (int i = 0; i < format.initializationData.size(); i++) {
      dumper.add("data", format.initializationData.get(i));
    }
    dumper.endBlock().endBlock();

    dumper.add("total output bytes", sampleData.length);
    dumper.add("sample count", sampleTimesUs.size());

    for (int i = 0; i < sampleTimesUs.size(); i++) {
      dumper.startBlock("sample " + i)
          .add("time", sampleTimesUs.get(i))
          .add("flags", sampleFlags.get(i))
          .add("data", getSampleData(i));
      CryptoData cryptoData = cryptoDatas.get(i);
      if (cryptoData != null) {
        dumper.add("crypto mode", cryptoData.cryptoMode);
        dumper.add("encryption key", cryptoData.encryptionKey);
      }
      dumper.endBlock();
    }
  }

}
