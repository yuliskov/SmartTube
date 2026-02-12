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
package com.google.android.exoplayer2.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This is a subclass of {@link BufferedOutputStream} with a {@link #reset(OutputStream)} method
 * that allows an instance to be re-used with another underlying output stream.
 */
public final class ReusableBufferedOutputStream extends BufferedOutputStream {

  private boolean closed;

  public ReusableBufferedOutputStream(OutputStream out) {
    super(out);
  }

  public ReusableBufferedOutputStream(OutputStream out, int size) {
    super(out, size);
  }

  @Override
  public void close() throws IOException {
    closed = true;

    Throwable thrown = null;
    try {
      flush();
    } catch (Throwable e) {
      thrown = e;
    }
    try {
      out.close();
    } catch (Throwable e) {
      if (thrown == null) {
        thrown = e;
      }
    }
    if (thrown != null) {
      Util.sneakyThrow(thrown);
    }
  }

  /**
   * Resets this stream and uses the given output stream for writing. This stream must be closed
   * before resetting.
   *
   * @param out New output stream to be used for writing.
   * @throws IllegalStateException If the stream isn't closed.
   */
  public void reset(OutputStream out) {
    Assertions.checkState(closed);
    this.out = out;
    count = 0;
    closed = false;
  }
}
