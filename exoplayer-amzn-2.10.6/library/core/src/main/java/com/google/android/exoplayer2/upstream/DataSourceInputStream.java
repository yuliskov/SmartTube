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
package com.google.android.exoplayer2.upstream;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.io.InputStream;

/**
 * Allows data corresponding to a given {@link DataSpec} to be read from a {@link DataSource} and
 * consumed through an {@link InputStream}.
 */
public final class DataSourceInputStream extends InputStream {

  private final DataSource dataSource;
  private final DataSpec dataSpec;
  private final byte[] singleByteArray;

  private boolean opened = false;
  private boolean closed = false;
  private long totalBytesRead;

  /**
   * @param dataSource The {@link DataSource} from which the data should be read.
   * @param dataSpec The {@link DataSpec} defining the data to be read from {@code dataSource}.
   */
  public DataSourceInputStream(DataSource dataSource, DataSpec dataSpec) {
    this.dataSource = dataSource;
    this.dataSpec = dataSpec;
    singleByteArray = new byte[1];
  }

  /**
   * Returns the total number of bytes that have been read or skipped.
   */
  public long bytesRead() {
    return totalBytesRead;
  }

  /**
   * Optional call to open the underlying {@link DataSource}.
   * <p>
   * Calling this method does nothing if the {@link DataSource} is already open. Calling this
   * method is optional, since the read and skip methods will automatically open the underlying
   * {@link DataSource} if it's not open already.
   *
   * @throws IOException If an error occurs opening the {@link DataSource}.
   */
  public void open() throws IOException {
    checkOpened();
  }

  @Override
  public int read() throws IOException {
    int length = read(singleByteArray);
    return length == -1 ? -1 : (singleByteArray[0] & 0xFF);
  }

  @Override
  public int read(@NonNull byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
    Assertions.checkState(!closed);
    checkOpened();
    int bytesRead = dataSource.read(buffer, offset, length);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return -1;
    } else {
      totalBytesRead += bytesRead;
      return bytesRead;
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      dataSource.close();
      closed = true;
    }
  }

  private void checkOpened() throws IOException {
    if (!opened) {
      dataSource.open(dataSpec);
      opened = true;
    }
  }

}
