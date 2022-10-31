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
package com.google.android.exoplayer2.video.spherical;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;

/** A {@link Renderer} that parses the camera motion track. */
public class CameraMotionRenderer extends BaseRenderer {

  // The amount of time to read samples ahead of the current time.
  private static final int SAMPLE_WINDOW_DURATION_US = 100000;

  private final FormatHolder formatHolder;
  private final DecoderInputBuffer buffer;
  private final ParsableByteArray scratch;

  private long offsetUs;
  private @Nullable CameraMotionListener listener;
  private long lastTimestampUs;

  public CameraMotionRenderer() {
    super(C.TRACK_TYPE_CAMERA_MOTION);
    formatHolder = new FormatHolder();
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    scratch = new ParsableByteArray();
  }

  @Override
  public int supportsFormat(Format format) {
    return MimeTypes.APPLICATION_CAMERA_MOTION.equals(format.sampleMimeType)
        ? FORMAT_HANDLED
        : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_CAMERA_MOTION_LISTENER) {
      listener = (CameraMotionListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    this.offsetUs = offsetUs;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    resetListener();
  }

  @Override
  protected void onDisabled() {
    resetListener();
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    // Keep reading available samples as long as the sample time is not too far into the future.
    while (!hasReadStreamToEnd() && lastTimestampUs < positionUs + SAMPLE_WINDOW_DURATION_US) {
      buffer.clear();
      int result = readSource(formatHolder, buffer, /* formatRequired= */ false);
      if (result != C.RESULT_BUFFER_READ || buffer.isEndOfStream()) {
        return;
      }

      buffer.flip();
      lastTimestampUs = buffer.timeUs;
      if (listener != null) {
        float[] rotation = parseMetadata(buffer.data);
        if (rotation != null) {
          Util.castNonNull(listener).onCameraMotion(lastTimestampUs - offsetUs, rotation);
        }
      }
    }
  }

  @Override
  public boolean isEnded() {
    return hasReadStreamToEnd();
  }

  @Override
  public boolean isReady() {
    return true;
  }

  private @Nullable float[] parseMetadata(ByteBuffer data) {
    if (data.remaining() != 16) {
      return null;
    }
    scratch.reset(data.array(), data.limit());
    scratch.setPosition(data.arrayOffset() + 4); // skip reserved bytes too.
    float[] result = new float[3];
    for (int i = 0; i < 3; i++) {
      result[i] = Float.intBitsToFloat(scratch.readLittleEndianInt());
    }
    return result;
  }

  private void resetListener() {
    lastTimestampUs = 0;
    if (listener != null) {
      listener.onCameraMotionReset();
    }
  }
}
