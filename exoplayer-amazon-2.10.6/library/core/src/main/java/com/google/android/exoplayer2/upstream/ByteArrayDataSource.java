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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/** A {@link DataSource} for reading from a byte array. */
public final class ByteArrayDataSource extends BaseDataSource {

  private final byte[] data;

  private @Nullable Uri uri;
  private int readPosition;
  private int bytesRemaining;
  private boolean opened;

  /**
   * @param data The data to be read.
   */
  public ByteArrayDataSource(byte[] data) {
    super(/* isNetwork= */ false);
    Assertions.checkNotNull(data);
    Assertions.checkArgument(data.length > 0);
    this.data = data;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    uri = dataSpec.uri;
    transferInitializing(dataSpec);
    readPosition = (int) dataSpec.position;
    bytesRemaining = (int) ((dataSpec.length == C.LENGTH_UNSET)
        ? (data.length - dataSpec.position) : dataSpec.length);
    if (bytesRemaining <= 0 || readPosition + bytesRemaining > data.length) {
      throw new IOException("Unsatisfiable range: [" + readPosition + ", " + dataSpec.length
          + "], length: " + data.length);
    }
    opened = true;
    transferStarted(dataSpec);
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    readLength = Math.min(readLength, bytesRemaining);
    System.arraycopy(data, readPosition, buffer, offset, readLength);
    readPosition += readLength;
    bytesRemaining -= readLength;
    bytesTransferred(readLength);
    return readLength;
  }

  @Override
  public @Nullable Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws IOException {
    if (opened) {
      opened = false;
      transferEnded();
    }
    uri = null;
  }

}
