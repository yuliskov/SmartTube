/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.gvr;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.util.Assertions;
import com.google.vr.sdk.audio.GvrAudioSurround;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that uses {@code GvrAudioSurround} to provide binaural rendering of
 * surround sound and ambisonic soundfields.
 */
public final class GvrAudioProcessor implements AudioProcessor {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.gvr");
  }

  private static final int FRAMES_PER_OUTPUT_BUFFER = 1024;
  private static final int OUTPUT_CHANNEL_COUNT = 2;
  private static final int OUTPUT_FRAME_SIZE = OUTPUT_CHANNEL_COUNT * 2; // 16-bit stereo output.
  private static final int NO_SURROUND_FORMAT = GvrAudioSurround.SurroundFormat.INVALID;

  private int sampleRateHz;
  private int channelCount;
  private int pendingGvrAudioSurroundFormat;
  @Nullable private GvrAudioSurround gvrAudioSurround;
  private ByteBuffer buffer;
  private boolean inputEnded;

  private float w;
  private float x;
  private float y;
  private float z;

  /** Creates a new GVR audio processor. */
  public GvrAudioProcessor() {
    // Use the identity for the initial orientation.
    w = 1f;
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    buffer = EMPTY_BUFFER;
    pendingGvrAudioSurroundFormat = NO_SURROUND_FORMAT;
  }

  /**
   * Updates the listener head orientation. May be called on any thread. See
   * {@code GvrAudioSurround.updateNativeOrientation}.
   *
   * @param w The w component of the quaternion.
   * @param x The x component of the quaternion.
   * @param y The y component of the quaternion.
   * @param z The z component of the quaternion.
   */
  public synchronized void updateOrientation(float w, float x, float y, float z) {
    this.w = w;
    this.x = x;
    this.y = y;
    this.z = z;
    if (gvrAudioSurround != null) {
      gvrAudioSurround.updateNativeOrientation(w, x, y, z);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public synchronized boolean configure(
      int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_16BIT) {
      maybeReleaseGvrAudioSurround();
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    switch (channelCount) {
      case 1:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.SURROUND_MONO;
        break;
      case 2:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.SURROUND_STEREO;
        break;
      case 4:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.FIRST_ORDER_AMBISONICS;
        break;
      case 6:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.SURROUND_FIVE_DOT_ONE;
        break;
      case 9:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.SECOND_ORDER_AMBISONICS;
        break;
      case 16:
        pendingGvrAudioSurroundFormat = GvrAudioSurround.SurroundFormat.THIRD_ORDER_AMBISONICS;
        break;
      default:
        throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (buffer == EMPTY_BUFFER) {
      buffer = ByteBuffer.allocateDirect(FRAMES_PER_OUTPUT_BUFFER * OUTPUT_FRAME_SIZE)
          .order(ByteOrder.nativeOrder());
    }
    return true;
  }

  @Override
  public boolean isActive() {
    return pendingGvrAudioSurroundFormat != NO_SURROUND_FORMAT || gvrAudioSurround != null;
  }

  @Override
  public int getOutputChannelCount() {
    return OUTPUT_CHANNEL_COUNT;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public int getOutputSampleRateHz() {
    return sampleRateHz;
  }

  @Override
  public void queueInput(ByteBuffer input) {
    int position = input.position();
    Assertions.checkNotNull(gvrAudioSurround);
    int readBytes = gvrAudioSurround.addInput(input, position, input.limit() - position);
    input.position(position + readBytes);
  }

  @Override
  public void queueEndOfStream() {
    if (gvrAudioSurround != null) {
      gvrAudioSurround.triggerProcessing();
    }
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    if (gvrAudioSurround == null) {
      return EMPTY_BUFFER;
    }
    int writtenBytes = gvrAudioSurround.getOutput(buffer, 0, buffer.capacity());
    buffer.position(0).limit(writtenBytes);
    return buffer;
  }

  @Override
  public boolean isEnded() {
    return inputEnded
        && (gvrAudioSurround == null || gvrAudioSurround.getAvailableOutputSize() == 0);
  }

  @Override
  public void flush() {
    if (pendingGvrAudioSurroundFormat != NO_SURROUND_FORMAT) {
      maybeReleaseGvrAudioSurround();
      gvrAudioSurround =
          new GvrAudioSurround(
              pendingGvrAudioSurroundFormat, sampleRateHz, channelCount, FRAMES_PER_OUTPUT_BUFFER);
      gvrAudioSurround.updateNativeOrientation(w, x, y, z);
      pendingGvrAudioSurroundFormat = NO_SURROUND_FORMAT;
    } else if (gvrAudioSurround != null) {
      gvrAudioSurround.flush();
    }
    inputEnded = false;
  }

  @Override
  public synchronized void reset() {
    maybeReleaseGvrAudioSurround();
    updateOrientation(/* w= */ 1f, /* x= */ 0f, /* y= */ 0f, /* z= */ 0f);
    inputEnded = false;
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    buffer = EMPTY_BUFFER;
    pendingGvrAudioSurroundFormat = NO_SURROUND_FORMAT;
  }

  private void maybeReleaseGvrAudioSurround() {
    if (gvrAudioSurround != null) {
      gvrAudioSurround.release();
      gvrAudioSurround = null;
    }
  }

}
