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
package com.google.android.exoplayer2.ext.flac;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * JNI wrapper for the libflac Flac decoder.
 */
/* package */ final class FlacDecoderJni {

  /** Exception to be thrown if {@link #decodeSample(ByteBuffer)} fails to decode a frame. */
  public static final class FlacFrameDecodeException extends Exception {

    public final int errorCode;

    public FlacFrameDecodeException(String message, int errorCode) {
      super(message);
      this.errorCode = errorCode;
    }
  }

  private static final int TEMP_BUFFER_SIZE = 8192; // The same buffer size as libflac.

  private final long nativeDecoderContext;

  @Nullable private ByteBuffer byteBufferData;
  @Nullable private ExtractorInput extractorInput;
  @Nullable private byte[] tempBuffer;
  private boolean endOfExtractorInput;

  public FlacDecoderJni() throws FlacDecoderException {
    if (!FlacLibrary.isAvailable()) {
      throw new FlacDecoderException("Failed to load decoder native libraries.");
    }
    nativeDecoderContext = flacInit();
    if (nativeDecoderContext == 0) {
      throw new FlacDecoderException("Failed to initialize decoder");
    }
  }

  /**
   * Sets the data to be parsed.
   *
   * @param byteBufferData Source {@link ByteBuffer}.
   */
  public void setData(ByteBuffer byteBufferData) {
    this.byteBufferData = byteBufferData;
    this.extractorInput = null;
  }

  /**
   * Sets the data to be parsed.
   *
   * @param extractorInput Source {@link ExtractorInput}.
   */
  public void setData(ExtractorInput extractorInput) {
    this.byteBufferData = null;
    this.extractorInput = extractorInput;
    endOfExtractorInput = false;
    if (tempBuffer == null) {
      tempBuffer = new byte[TEMP_BUFFER_SIZE];
    }
  }

  /**
   * Returns whether the end of the data to be parsed has been reached, or true if no data was set.
   */
  public boolean isEndOfData() {
    if (byteBufferData != null) {
      return byteBufferData.remaining() == 0;
    } else if (extractorInput != null) {
      return endOfExtractorInput;
    } else {
      return true;
    }
  }

  /** Clears the data to be parsed. */
  public void clearData() {
    byteBufferData = null;
    extractorInput = null;
  }

  /**
   * Reads up to {@code length} bytes from the data source.
   *
   * <p>This method blocks until at least one byte of data can be read, the end of the input is
   * detected or an exception is thrown.
   *
   * @param target A target {@link ByteBuffer} into which data should be written.
   * @return Returns the number of bytes read, or -1 on failure. If all of the data has already been
   *     read from the source, then 0 is returned.
   */
  @SuppressWarnings("unused") // Called from native code.
  public int read(ByteBuffer target) throws IOException, InterruptedException {
    int byteCount = target.remaining();
    if (byteBufferData != null) {
      byteCount = Math.min(byteCount, byteBufferData.remaining());
      int originalLimit = byteBufferData.limit();
      byteBufferData.limit(byteBufferData.position() + byteCount);
      target.put(byteBufferData);
      byteBufferData.limit(originalLimit);
    } else if (extractorInput != null) {
      ExtractorInput extractorInput = this.extractorInput;
      byte[] tempBuffer = Util.castNonNull(this.tempBuffer);
      byteCount = Math.min(byteCount, TEMP_BUFFER_SIZE);
      int read = readFromExtractorInput(extractorInput, tempBuffer, /* offset= */ 0, byteCount);
      if (read < 4) {
        // Reading less than 4 bytes, most of the time, happens because of getting the bytes left in
        // the buffer of the input. Do another read to reduce the number of calls to this method
        // from the native code.
        read +=
            readFromExtractorInput(
                extractorInput, tempBuffer, read, /* length= */ byteCount - read);
      }
      byteCount = read;
      target.put(tempBuffer, 0, byteCount);
    } else {
      return -1;
    }
    return byteCount;
  }

  /** Decodes and consumes the metadata from the FLAC stream. */
  public FlacStreamMetadata decodeStreamMetadata() throws IOException, InterruptedException {
    FlacStreamMetadata streamMetadata = flacDecodeMetadata(nativeDecoderContext);
    if (streamMetadata == null) {
      throw new ParserException("Failed to decode stream metadata");
    }
    return streamMetadata;
  }

  /**
   * Decodes and consumes the next frame from the FLAC stream into the given byte buffer. If any IO
   * error occurs, resets the stream and input to the given {@code retryPosition}.
   *
   * @param output The byte buffer to hold the decoded frame.
   * @param retryPosition If any error happens, the input will be rewound to {@code retryPosition}.
   */
  public void decodeSampleWithBacktrackPosition(ByteBuffer output, long retryPosition)
      throws InterruptedException, IOException, FlacFrameDecodeException {
    try {
      decodeSample(output);
    } catch (IOException e) {
      if (retryPosition >= 0) {
        reset(retryPosition);
        if (extractorInput != null) {
          extractorInput.setRetryPosition(retryPosition, e);
        }
      }
      throw e;
    }
  }

  /** Decodes and consumes the next sample from the FLAC stream into the given byte buffer. */
  @SuppressWarnings("ByteBufferBackingArray")
  public void decodeSample(ByteBuffer output)
      throws IOException, InterruptedException, FlacFrameDecodeException {
    output.clear();
    int frameSize =
        output.isDirect()
            ? flacDecodeToBuffer(nativeDecoderContext, output)
            : flacDecodeToArray(nativeDecoderContext, output.array());
    if (frameSize < 0) {
      if (!isDecoderAtEndOfInput()) {
        throw new FlacFrameDecodeException("Cannot decode FLAC frame", frameSize);
      }
      // The decoder has read to EOI. Return a 0-size frame to indicate the EOI.
      output.limit(0);
    } else {
      output.limit(frameSize);
    }
  }

  /**
   * Returns the position of the next data to be decoded, or -1 in case of error.
   */
  public long getDecodePosition() {
    return flacGetDecodePosition(nativeDecoderContext);
  }

  /** Returns the timestamp for the first sample in the last decoded frame. */
  public long getLastFrameTimestamp() {
    return flacGetLastFrameTimestamp(nativeDecoderContext);
  }

  /** Returns the first sample index of the last extracted frame. */
  public long getLastFrameFirstSampleIndex() {
    return flacGetLastFrameFirstSampleIndex(nativeDecoderContext);
  }

  /** Returns the first sample index of the frame to be extracted next. */
  public long getNextFrameFirstSampleIndex() {
    return flacGetNextFrameFirstSampleIndex(nativeDecoderContext);
  }

  /**
   * Maps a seek position in microseconds to the corresponding {@link SeekMap.SeekPoints} in the
   * stream.
   *
   * @param timeUs A seek position in microseconds.
   * @return The corresponding {@link SeekMap.SeekPoints} obtained from the seek table, or {@code
   *     null} if the stream doesn't have a seek table.
   */
  @Nullable
  public SeekMap.SeekPoints getSeekPoints(long timeUs) {
    long[] seekPoints = new long[4];
    if (!flacGetSeekPoints(nativeDecoderContext, timeUs, seekPoints)) {
      return null;
    }
    SeekPoint firstSeekPoint = new SeekPoint(seekPoints[0], seekPoints[1]);
    SeekPoint secondSeekPoint =
        seekPoints[2] == seekPoints[0]
            ? firstSeekPoint
            : new SeekPoint(seekPoints[2], seekPoints[3]);
    return new SeekMap.SeekPoints(firstSeekPoint, secondSeekPoint);
  }

  public String getStateString() {
    return flacGetStateString(nativeDecoderContext);
  }

  /** Returns whether the decoder has read to the end of the input. */
  public boolean isDecoderAtEndOfInput() {
    return flacIsDecoderAtEndOfStream(nativeDecoderContext);
  }

  public void flush() {
    flacFlush(nativeDecoderContext);
  }

  /**
   * Resets internal state of the decoder and sets the stream position.
   *
   * @param newPosition Stream's new position.
   */
  public void reset(long newPosition) {
    flacReset(nativeDecoderContext, newPosition);
  }

  public void release() {
    flacRelease(nativeDecoderContext);
  }

  private int readFromExtractorInput(
      ExtractorInput extractorInput, byte[] tempBuffer, int offset, int length)
      throws IOException, InterruptedException {
    int read = extractorInput.read(tempBuffer, offset, length);
    if (read == C.RESULT_END_OF_INPUT) {
      endOfExtractorInput = true;
      read = 0;
    }
    return read;
  }

  private native long flacInit();

  private native FlacStreamMetadata flacDecodeMetadata(long context)
      throws IOException, InterruptedException;

  private native int flacDecodeToBuffer(long context, ByteBuffer outputBuffer)
      throws IOException, InterruptedException;

  private native int flacDecodeToArray(long context, byte[] outputArray)
      throws IOException, InterruptedException;

  private native long flacGetDecodePosition(long context);

  private native long flacGetLastFrameTimestamp(long context);

  private native long flacGetLastFrameFirstSampleIndex(long context);

  private native long flacGetNextFrameFirstSampleIndex(long context);

  private native boolean flacGetSeekPoints(long context, long timeUs, long[] outSeekPoints);

  private native String flacGetStateString(long context);

  private native boolean flacIsDecoderAtEndOfStream(long context);

  private native void flacFlush(long context);

  private native void flacReset(long context, long newPosition);

  private native void flacRelease(long context);

}
