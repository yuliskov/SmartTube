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
package com.google.android.exoplayer2.ext.vp9;

import com.google.android.exoplayer2.decoder.OutputBuffer;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;

/** Output buffer containing video frame data, populated by {@link VpxDecoder}. */
public final class VpxOutputBuffer extends OutputBuffer {

  public static final int COLORSPACE_UNKNOWN = 0;
  public static final int COLORSPACE_BT601 = 1;
  public static final int COLORSPACE_BT709 = 2;
  public static final int COLORSPACE_BT2020 = 3;

  private final VpxDecoder owner;
  /** Decoder private data. */
  public int decoderPrivate;

  public int mode;
  /**
   * RGB buffer for RGB mode.
   */
  public ByteBuffer data;
  public int width;
  public int height;
  public ColorInfo colorInfo;

  /**
   * YUV planes for YUV mode.
   */
  public ByteBuffer[] yuvPlanes;
  public int[] yuvStrides;
  public int colorspace;

  public VpxOutputBuffer(VpxDecoder owner) {
    this.owner = owner;
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

  /**
   * Initializes the buffer.
   *
   * @param timeUs The presentation timestamp for the buffer, in microseconds.
   * @param mode The output mode. One of {@link VpxDecoder#OUTPUT_MODE_NONE}, {@link
   *     VpxDecoder#OUTPUT_MODE_YUV} and {@link VpxDecoder#OUTPUT_MODE_SURFACE_YUV}.
   */
  public void init(long timeUs, int mode) {
    this.timeUs = timeUs;
    this.mode = mode;
  }

  /**
   * Resizes the buffer based on the given stride. Called via JNI after decoding completes.
   *
   * @return Whether the buffer was resized successfully.
   */
  public boolean initForYuvFrame(int width, int height, int yStride, int uvStride, int colorspace) {
    this.width = width;
    this.height = height;
    this.colorspace = colorspace;
    int uvHeight = (int) (((long) height + 1) / 2);
    if (!isSafeToMultiply(yStride, height) || !isSafeToMultiply(uvStride, uvHeight)) {
      return false;
    }
    int yLength = yStride * height;
    int uvLength = uvStride * uvHeight;
    int minimumYuvSize = yLength + (uvLength * 2);
    if (!isSafeToMultiply(uvLength, 2) || minimumYuvSize < yLength) {
      return false;
    }
    initData(minimumYuvSize);

    if (yuvPlanes == null) {
      yuvPlanes = new ByteBuffer[3];
    }
    // Rewrapping has to be done on every frame since the stride might have changed.
    yuvPlanes[0] = data.slice();
    yuvPlanes[0].limit(yLength);
    data.position(yLength);
    yuvPlanes[1] = data.slice();
    yuvPlanes[1].limit(uvLength);
    data.position(yLength + uvLength);
    yuvPlanes[2] = data.slice();
    yuvPlanes[2].limit(uvLength);
    if (yuvStrides == null) {
      yuvStrides = new int[3];
    }
    yuvStrides[0] = yStride;
    yuvStrides[1] = uvStride;
    yuvStrides[2] = uvStride;
    return true;
  }

  /**
   * Configures the buffer for the given frame dimensions when passing actual frame data via {@link
   * #decoderPrivate}. Called via JNI after decoding completes.
   */
  public void initForPrivateFrame(int width, int height) {
    this.width = width;
    this.height = height;
  }

  private void initData(int size) {
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size);
    } else {
      data.position(0);
      data.limit(size);
    }
  }

  /**
   * Ensures that the result of multiplying individual numbers can fit into the size limit of an
   * integer.
   */
  private boolean isSafeToMultiply(int a, int b) {
    return a >= 0 && b >= 0 && !(b > 0 && a >= Integer.MAX_VALUE / b);
  }

}
