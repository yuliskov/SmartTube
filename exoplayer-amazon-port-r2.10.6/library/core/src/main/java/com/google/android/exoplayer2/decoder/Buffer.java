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

import com.google.android.exoplayer2.C;

/**
 * Base class for buffers with flags.
 */
public abstract class Buffer {

  @C.BufferFlags
  private int flags;

  /**
   * Clears the buffer.
   */
  public void clear() {
    flags = 0;
  }

  /**
   * Returns whether the {@link C#BUFFER_FLAG_DECODE_ONLY} flag is set.
   */
  public final boolean isDecodeOnly() {
    return getFlag(C.BUFFER_FLAG_DECODE_ONLY);
  }

  /**
   * Returns whether the {@link C#BUFFER_FLAG_END_OF_STREAM} flag is set.
   */
  public final boolean isEndOfStream() {
    return getFlag(C.BUFFER_FLAG_END_OF_STREAM);
  }

  /**
   * Returns whether the {@link C#BUFFER_FLAG_KEY_FRAME} flag is set.
   */
  public final boolean isKeyFrame() {
    return getFlag(C.BUFFER_FLAG_KEY_FRAME);
  }

  /**
   * Replaces this buffer's flags with {@code flags}.
   *
   * @param flags The flags to set, which should be a combination of the {@code C.BUFFER_FLAG_*}
   *     constants.
   */
  public final void setFlags(@C.BufferFlags int flags) {
    this.flags = flags;
  }

  /**
   * Adds the {@code flag} to this buffer's flags.
   *
   * @param flag The flag to add to this buffer's flags, which should be one of the
   *     {@code C.BUFFER_FLAG_*} constants.
   */
  public final void addFlag(@C.BufferFlags int flag) {
    flags |= flag;
  }

  /**
   * Removes the {@code flag} from this buffer's flags, if it is set.
   *
   * @param flag The flag to remove.
   */
  public final void clearFlag(@C.BufferFlags int flag) {
    flags &= ~flag;
  }

  /**
   * Returns whether the specified flag has been set on this buffer.
   *
   * @param flag The flag to check.
   * @return Whether the flag is set.
   */
  protected final boolean getFlag(@C.BufferFlags int flag) {
    return (flags & flag) == flag;
  }

}
