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

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Holds input for a decoder.
 */
public class DecoderInputBuffer extends Buffer {

  /**
   * The buffer replacement mode, which may disable replacement. One of {@link
   * #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} or {@link
   * #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    BUFFER_REPLACEMENT_MODE_DISABLED,
    BUFFER_REPLACEMENT_MODE_NORMAL,
    BUFFER_REPLACEMENT_MODE_DIRECT
  })
  public @interface BufferReplacementMode {}
  /**
   * Disallows buffer replacement.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;
  /**
   * Allows buffer replacement using {@link ByteBuffer#allocate(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;
  /**
   * Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

  /**
   * {@link CryptoInfo} for encrypted data.
   */
  public final CryptoInfo cryptoInfo;

  /**
   * The buffer's data, or {@code null} if no data has been set.
   */
  public ByteBuffer data;

  /**
   * The time at which the sample should be presented.
   */
  public long timeUs;

  @BufferReplacementMode private final int bufferReplacementMode;

  /**
   * Creates a new instance for which {@link #isFlagsOnly()} will return true.
   *
   * @return A new flags only input buffer.
   */
  public static DecoderInputBuffer newFlagsOnlyInstance() {
    return new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  /**
   * @param bufferReplacementMode Determines the behavior of {@link #ensureSpaceForWrite(int)}. One
   *     of {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
   *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
    this.cryptoInfo = new CryptoInfo();
    this.bufferReplacementMode = bufferReplacementMode;
  }

  /**
   * Ensures that {@link #data} is large enough to accommodate a write of a given length at its
   * current position.
   *
   * <p>If the capacity of {@link #data} is sufficient this method does nothing. If the capacity is
   * insufficient then an attempt is made to replace {@link #data} with a new {@link ByteBuffer}
   * whose capacity is sufficient. Data up to the current position is copied to the new buffer.
   *
   * @param length The length of the write that must be accommodated, in bytes.
   * @throws IllegalStateException If there is insufficient capacity to accommodate the write and
   *     the buffer replacement mode of the holder is {@link #BUFFER_REPLACEMENT_MODE_DISABLED}.
   */
  public void ensureSpaceForWrite(int length) {
    if (data == null) {
      data = createReplacementByteBuffer(length);
      return;
    }
    // Check whether the current buffer is sufficient.
    int capacity = data.capacity();
    int position = data.position();
    int requiredCapacity = position + length;
    if (capacity >= requiredCapacity) {
      return;
    }
    // Instantiate a new buffer if possible.
    ByteBuffer newData = createReplacementByteBuffer(requiredCapacity);
    // Copy data up to the current position from the old buffer to the new one.
    if (position > 0) {
      data.position(0);
      data.limit(position);
      newData.put(data);
    }
    // Set the new buffer.
    data = newData;
  }

  /**
   * Returns whether the buffer is only able to hold flags, meaning {@link #data} is null and
   * its replacement mode is {@link #BUFFER_REPLACEMENT_MODE_DISABLED}.
   */
  public final boolean isFlagsOnly() {
    return data == null && bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DISABLED;
  }

  /**
   * Returns whether the {@link C#BUFFER_FLAG_ENCRYPTED} flag is set.
   */
  public final boolean isEncrypted() {
    return getFlag(C.BUFFER_FLAG_ENCRYPTED);
  }

  /**
   * Flips {@link #data} in preparation for being queued to a decoder.
   *
   * @see java.nio.Buffer#flip()
   */
  public final void flip() {
    data.flip();
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
  }

  private ByteBuffer createReplacementByteBuffer(int requiredCapacity) {
    if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_NORMAL) {
      return ByteBuffer.allocate(requiredCapacity);
    } else if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DIRECT) {
      return ByteBuffer.allocateDirect(requiredCapacity);
    } else {
      int currentCapacity = data == null ? 0 : data.capacity();
      throw new IllegalStateException("Buffer too small (" + currentCapacity + " < "
          + requiredCapacity + ")");
    }
  }

}
