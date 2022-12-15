/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Audio processor that outputs its input unmodified and also outputs its input to a given sink.
 * This is intended to be used for diagnostics and debugging.
 *
 * <p>This audio processor can be inserted into the audio processor chain to access audio data
 * before/after particular processing steps have been applied. For example, to get audio output
 * after playback speed adjustment and silence skipping have been applied it is necessary to pass a
 * custom {@link com.google.android.exoplayer2.audio.DefaultAudioSink.AudioProcessorChain} when
 * creating the audio sink, and include this audio processor after all other audio processors.
 */
public final class TeeAudioProcessor extends BaseAudioProcessor {

  /** A sink for audio buffers handled by the audio processor. */
  public interface AudioBufferSink {

    /** Called when the audio processor is flushed with a format of subsequent input. */
    void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding);

    /**
     * Called when data is written to the audio processor.
     *
     * @param buffer A read-only buffer containing input which the audio processor will handle.
     */
    void handleBuffer(ByteBuffer buffer);
  }

  private final AudioBufferSink audioBufferSink;

  /**
   * Creates a new tee audio processor, sending incoming data to the given {@link AudioBufferSink}.
   *
   * @param audioBufferSink The audio buffer sink that will receive input queued to this audio
   *     processor.
   */
  public TeeAudioProcessor(AudioBufferSink audioBufferSink) {
    this.audioBufferSink = Assertions.checkNotNull(audioBufferSink);
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
    return setInputFormat(sampleRateHz, channelCount, encoding);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int remaining = inputBuffer.remaining();
    if (remaining == 0) {
      return;
    }
    audioBufferSink.handleBuffer(inputBuffer.asReadOnlyBuffer());
    replaceOutputBuffer(remaining).put(inputBuffer).flip();
  }

  @Override
  protected void onFlush() {
    if (isActive()) {
      audioBufferSink.flush(sampleRateHz, channelCount, encoding);
    }
  }

  /**
   * A sink for audio buffers that writes output audio as .wav files with a given path prefix. When
   * new audio data is handled after flushing the audio processor, a counter is incremented and its
   * value is appended to the output file name.
   *
   * <p>Note: if writing to external storage it's necessary to grant the {@code
   * WRITE_EXTERNAL_STORAGE} permission.
   */
  public static final class WavFileAudioBufferSink implements AudioBufferSink {

    private static final String TAG = "WaveFileAudioBufferSink";

    private static final int FILE_SIZE_MINUS_8_OFFSET = 4;
    private static final int FILE_SIZE_MINUS_44_OFFSET = 40;
    private static final int HEADER_LENGTH = 44;

    private final String outputFileNamePrefix;
    private final byte[] scratchBuffer;
    private final ByteBuffer scratchByteBuffer;

    private int sampleRateHz;
    private int channelCount;
    @C.PcmEncoding private int encoding;
    @Nullable private RandomAccessFile randomAccessFile;
    private int counter;
    private int bytesWritten;

    /**
     * Creates a new audio buffer sink that writes to .wav files with the given prefix.
     *
     * @param outputFileNamePrefix The prefix for output files.
     */
    public WavFileAudioBufferSink(String outputFileNamePrefix) {
      this.outputFileNamePrefix = outputFileNamePrefix;
      scratchBuffer = new byte[1024];
      scratchByteBuffer = ByteBuffer.wrap(scratchBuffer).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
      try {
        reset();
      } catch (IOException e) {
        Log.e(TAG, "Error resetting", e);
      }
      this.sampleRateHz = sampleRateHz;
      this.channelCount = channelCount;
      this.encoding = encoding;
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
      try {
        maybePrepareFile();
        writeBuffer(buffer);
      } catch (IOException e) {
        Log.e(TAG, "Error writing data", e);
      }
    }

    private void maybePrepareFile() throws IOException {
      if (randomAccessFile != null) {
        return;
      }
      RandomAccessFile randomAccessFile = new RandomAccessFile(getNextOutputFileName(), "rw");
      writeFileHeader(randomAccessFile);
      this.randomAccessFile = randomAccessFile;
      bytesWritten = HEADER_LENGTH;
    }

    private void writeFileHeader(RandomAccessFile randomAccessFile) throws IOException {
      // Write the start of the header as big endian data.
      randomAccessFile.writeInt(WavUtil.RIFF_FOURCC);
      randomAccessFile.writeInt(-1);
      randomAccessFile.writeInt(WavUtil.WAVE_FOURCC);
      randomAccessFile.writeInt(WavUtil.FMT_FOURCC);

      // Write the rest of the header as little endian data.
      scratchByteBuffer.clear();
      scratchByteBuffer.putInt(16);
      scratchByteBuffer.putShort((short) WavUtil.getTypeForEncoding(encoding));
      scratchByteBuffer.putShort((short) channelCount);
      scratchByteBuffer.putInt(sampleRateHz);
      int bytesPerSample = Util.getPcmFrameSize(encoding, channelCount);
      scratchByteBuffer.putInt(bytesPerSample * sampleRateHz);
      scratchByteBuffer.putShort((short) bytesPerSample);
      scratchByteBuffer.putShort((short) (8 * bytesPerSample / channelCount));
      randomAccessFile.write(scratchBuffer, 0, scratchByteBuffer.position());

      // Write the start of the data chunk as big endian data.
      randomAccessFile.writeInt(WavUtil.DATA_FOURCC);
      randomAccessFile.writeInt(-1);
    }

    private void writeBuffer(ByteBuffer buffer) throws IOException {
      RandomAccessFile randomAccessFile = Assertions.checkNotNull(this.randomAccessFile);
      while (buffer.hasRemaining()) {
        int bytesToWrite = Math.min(buffer.remaining(), scratchBuffer.length);
        buffer.get(scratchBuffer, 0, bytesToWrite);
        randomAccessFile.write(scratchBuffer, 0, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
    }

    private void reset() throws IOException {
      RandomAccessFile randomAccessFile = this.randomAccessFile;
      if (randomAccessFile == null) {
        return;
      }

      try {
        scratchByteBuffer.clear();
        scratchByteBuffer.putInt(bytesWritten - 8);
        randomAccessFile.seek(FILE_SIZE_MINUS_8_OFFSET);
        randomAccessFile.write(scratchBuffer, 0, 4);

        scratchByteBuffer.clear();
        scratchByteBuffer.putInt(bytesWritten - 44);
        randomAccessFile.seek(FILE_SIZE_MINUS_44_OFFSET);
        randomAccessFile.write(scratchBuffer, 0, 4);
      } catch (IOException e) {
        // The file may still be playable, so just log a warning.
        Log.w(TAG, "Error updating file size", e);
      }

      try {
        randomAccessFile.close();
      } finally {
        this.randomAccessFile = null;
      }
    }

    private String getNextOutputFileName() {
      return Util.formatInvariant("%s-%04d.wav", outputFileNamePrefix, counter++);
    }
  }
}
