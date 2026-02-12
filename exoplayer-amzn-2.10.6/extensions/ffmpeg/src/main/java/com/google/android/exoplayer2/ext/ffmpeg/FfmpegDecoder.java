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
package com.google.android.exoplayer2.ext.ffmpeg;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * FFmpeg audio decoder.
 */
/* package */ final class FfmpegDecoder extends
    SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, FfmpegDecoderException> {

  // Output buffer sizes when decoding PCM mu-law streams, which is the maximum FFmpeg outputs.
  private static final int OUTPUT_BUFFER_SIZE_16BIT = 65536;
  private static final int OUTPUT_BUFFER_SIZE_32BIT = OUTPUT_BUFFER_SIZE_16BIT * 2;

  // Error codes matching ffmpeg_jni.cc.
  private static final int DECODER_ERROR_INVALID_DATA = -1;
  private static final int DECODER_ERROR_OTHER = -2;

  private final String codecName;
  private final @Nullable byte[] extraData;
  private final @C.Encoding int encoding;
  private final int outputBufferSize;

  private long nativeContext; // May be reassigned on resetting the codec.
  private boolean hasOutputFormat;
  private volatile int channelCount;
  private volatile int sampleRate;

  public FfmpegDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      Format format,
      boolean outputFloat)
      throws FfmpegDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    if (!FfmpegLibrary.isAvailable()) {
      throw new FfmpegDecoderException("Failed to load decoder native libraries.");
    }
    Assertions.checkNotNull(format.sampleMimeType);
    codecName =
        Assertions.checkNotNull(
            FfmpegLibrary.getCodecName(format.sampleMimeType, format.pcmEncoding));
    extraData = getExtraData(format.sampleMimeType, format.initializationData);
    encoding = outputFloat ? C.ENCODING_PCM_FLOAT : C.ENCODING_PCM_16BIT;
    outputBufferSize = outputFloat ? OUTPUT_BUFFER_SIZE_32BIT : OUTPUT_BUFFER_SIZE_16BIT;
    nativeContext =
        ffmpegInitialize(codecName, extraData, outputFloat, format.sampleRate, format.channelCount);
    if (nativeContext == 0) {
      throw new FfmpegDecoderException("Initialization failed.");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName;
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this);
  }

  @Override
  protected FfmpegDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FfmpegDecoderException("Unexpected decode error", error);
  }

  @Override
  protected @Nullable FfmpegDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      nativeContext = ffmpegReset(nativeContext, extraData);
      if (nativeContext == 0) {
        return new FfmpegDecoderException("Error resetting (see logcat).");
      }
    }
    ByteBuffer inputData = inputBuffer.data;
    int inputSize = inputData.limit();
    ByteBuffer outputData = outputBuffer.init(inputBuffer.timeUs, outputBufferSize);
    int result = ffmpegDecode(nativeContext, inputData, inputSize, outputData, outputBufferSize);
    if (result == DECODER_ERROR_INVALID_DATA) {
      // Treat invalid data errors as non-fatal to match the behavior of MediaCodec. No output will
      // be produced for this buffer, so mark it as decode-only to ensure that the audio sink's
      // position is reset when more audio is produced.
      outputBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
      return null;
    } else if (result == DECODER_ERROR_OTHER) {
      return new FfmpegDecoderException("Error decoding (see logcat).");
    }
    if (!hasOutputFormat) {
      channelCount = ffmpegGetChannelCount(nativeContext);
      sampleRate = ffmpegGetSampleRate(nativeContext);
      if (sampleRate == 0 && "alac".equals(codecName)) {
        Assertions.checkNotNull(extraData);
        // ALAC decoder did not set the sample rate in earlier versions of FFMPEG.
        // See https://trac.ffmpeg.org/ticket/6096
        ParsableByteArray parsableExtraData = new ParsableByteArray(extraData);
        parsableExtraData.setPosition(extraData.length - 4);
        sampleRate = parsableExtraData.readUnsignedIntToInt();
      }
      hasOutputFormat = true;
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    return null;
  }

  @Override
  public void release() {
    super.release();
    ffmpegRelease(nativeContext);
    nativeContext = 0;
  }

  /**
   * Returns the channel count of output audio. May only be called after {@link #decode}.
   */
  public int getChannelCount() {
    return channelCount;
  }

  /**
   * Returns the sample rate of output audio. May only be called after {@link #decode}.
   */
  public int getSampleRate() {
    return sampleRate;
  }

  /**
   * Returns the encoding of output audio.
   */
  public @C.Encoding int getEncoding() {
    return encoding;
  }

  /**
   * Returns FFmpeg-compatible codec-specific initialization data ("extra data"), or {@code null} if
   * not required.
   */
  private static @Nullable byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
      case MimeTypes.AUDIO_OPUS:
        return initializationData.get(0);
      case MimeTypes.AUDIO_ALAC:
        return getAlacExtraData(initializationData);
      case MimeTypes.AUDIO_VORBIS:
        return getVorbisExtraData(initializationData);
      default:
        // Other codecs do not require extra data.
        return null;
    }
  }

  private static byte[] getAlacExtraData(List<byte[]> initializationData) {
    // FFmpeg's ALAC decoder expects an ALAC atom, which contains the ALAC "magic cookie", as extra
    // data. initializationData[0] contains only the magic cookie, and so we need to package it into
    // an ALAC atom. See:
    // https://ffmpeg.org/doxygen/0.6/alac_8c.html
    // https://github.com/macosforge/alac/blob/master/ALACMagicCookieDescription.txt
    byte[] magicCookie = initializationData.get(0);
    int alacAtomLength = 12 + magicCookie.length;
    ByteBuffer alacAtom = ByteBuffer.allocate(alacAtomLength);
    alacAtom.putInt(alacAtomLength);
    alacAtom.putInt(0x616c6163); // type=alac
    alacAtom.putInt(0); // version=0, flags=0
    alacAtom.put(magicCookie, /* offset= */ 0, magicCookie.length);
    return alacAtom.array();
  }

  private static byte[] getVorbisExtraData(List<byte[]> initializationData) {
    byte[] header0 = initializationData.get(0);
    byte[] header1 = initializationData.get(1);
    byte[] extraData = new byte[header0.length + header1.length + 6];
    extraData[0] = (byte) (header0.length >> 8);
    extraData[1] = (byte) (header0.length & 0xFF);
    System.arraycopy(header0, 0, extraData, 2, header0.length);
    extraData[header0.length + 2] = 0;
    extraData[header0.length + 3] = 0;
    extraData[header0.length + 4] = (byte) (header1.length >> 8);
    extraData[header0.length + 5] = (byte) (header1.length & 0xFF);
    System.arraycopy(header1, 0, extraData, header0.length + 6, header1.length);
    return extraData;
  }

  private native long ffmpegInitialize(
      String codecName,
      @Nullable byte[] extraData,
      boolean outputFloat,
      int rawSampleRate,
      int rawChannelCount);

  private native int ffmpegDecode(long context, ByteBuffer inputData, int inputSize,
      ByteBuffer outputData, int outputSize);
  private native int ffmpegGetChannelCount(long context);
  private native int ffmpegGetSampleRate(long context);

  private native long ffmpegReset(long context, @Nullable byte[] extraData);

  private native void ffmpegRelease(long context);

}
