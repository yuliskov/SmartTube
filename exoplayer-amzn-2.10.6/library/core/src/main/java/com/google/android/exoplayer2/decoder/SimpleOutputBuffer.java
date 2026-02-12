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
package com.google.android.exoplayer2.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Buffer for {@link SimpleDecoder} output.
 */
public class SimpleOutputBuffer extends OutputBuffer {

  private final SimpleDecoder<?, SimpleOutputBuffer, ?> owner;

  public ByteBuffer data;

  public SimpleOutputBuffer(SimpleDecoder<?, SimpleOutputBuffer, ?> owner) {
    this.owner = owner;
  }

  /**
   * Initializes the buffer.
   *
   * @param timeUs The presentation timestamp for the buffer, in microseconds.
   * @param size An upper bound on the size of the data that will be written to the buffer.
   * @return The {@link #data} buffer, for convenience.
   */
  public ByteBuffer init(long timeUs, int size) {
    this.timeUs = timeUs;
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
    data.position(0);
    data.limit(size);
    return data;
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

}
