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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Splits ICY stream metadata out from a stream.
 *
 * <p>Note: {@link #open(DataSpec)} and {@link #close()} are not supported. This implementation is
 * intended to wrap upstream {@link DataSource} instances that are opened and closed directly.
 */
/* package */ final class IcyDataSource implements DataSource {

  public interface Listener {

    /**
     * Called when ICY stream metadata has been split from the stream.
     *
     * @param metadata The stream metadata in binary form.
     */
    void onIcyMetadata(ParsableByteArray metadata);
  }

  private final DataSource upstream;
  private final int metadataIntervalBytes;
  private final Listener listener;
  private final byte[] metadataLengthByteHolder;
  private int bytesUntilMetadata;

  /**
   * @param upstream The upstream {@link DataSource}.
   * @param metadataIntervalBytes The interval between ICY stream metadata, in bytes.
   * @param listener A listener to which stream metadata is delivered.
   */
  public IcyDataSource(DataSource upstream, int metadataIntervalBytes, Listener listener) {
    Assertions.checkArgument(metadataIntervalBytes > 0);
    this.upstream = upstream;
    this.metadataIntervalBytes = metadataIntervalBytes;
    this.listener = listener;
    metadataLengthByteHolder = new byte[1];
    bytesUntilMetadata = metadataIntervalBytes;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
    upstream.addTransferListener(transferListener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (bytesUntilMetadata == 0) {
      if (readMetadata()) {
        bytesUntilMetadata = metadataIntervalBytes;
      } else {
        return C.RESULT_END_OF_INPUT;
      }
    }
    int bytesRead = upstream.read(buffer, offset, Math.min(bytesUntilMetadata, readLength));
    if (bytesRead != C.RESULT_END_OF_INPUT) {
      bytesUntilMetadata -= bytesRead;
    }
    return bytesRead;
  }

  @Nullable
  @Override
  public Uri getUri() {
    return upstream.getUri();
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return upstream.getResponseHeaders();
  }

  @Override
  public void close() throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Reads an ICY stream metadata block, passing it to {@link #listener} unless the block is empty.
   *
   * @return True if the block was extracted, including if it's length byte indicated a length of
   *     zero. False if the end of the stream was reached.
   * @throws IOException If an error occurs reading from the wrapped {@link DataSource}.
   */
  private boolean readMetadata() throws IOException {
    int bytesRead = upstream.read(metadataLengthByteHolder, 0, 1);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return false;
    }
    int metadataLength = (metadataLengthByteHolder[0] & 0xFF) << 4;
    if (metadataLength == 0) {
      return true;
    }

    int offset = 0;
    int lengthRemaining = metadataLength;
    byte[] metadata = new byte[metadataLength];
    while (lengthRemaining > 0) {
      bytesRead = upstream.read(metadata, offset, lengthRemaining);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        return false;
      }
      offset += bytesRead;
      lengthRemaining -= bytesRead;
    }

    // Discard trailing zero bytes.
    while (metadataLength > 0 && metadata[metadataLength - 1] == 0) {
      metadataLength--;
    }

    if (metadataLength > 0) {
      listener.onIcyMetadata(new ParsableByteArray(metadata, metadataLength));
    }
    return true;
  }
}
