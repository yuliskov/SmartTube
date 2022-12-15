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
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link DataSource} wrapper which keeps track of bytes transferred, redirected uris, and response
 * headers.
 */
public final class StatsDataSource implements DataSource {

  private final DataSource dataSource;

  private long bytesRead;
  private Uri lastOpenedUri;
  private Map<String, List<String>> lastResponseHeaders;

  /**
   * Creates the stats data source.
   *
   * @param dataSource The wrapped {@link DataSource}.
   */
  public StatsDataSource(DataSource dataSource) {
    this.dataSource = Assertions.checkNotNull(dataSource);
    lastOpenedUri = Uri.EMPTY;
    lastResponseHeaders = Collections.emptyMap();
  }

  /** Resets the number of bytes read as returned from {@link #getBytesRead()} to zero. */
  public void resetBytesRead() {
    bytesRead = 0;
  }

  /** Returns the total number of bytes that have been read from the data source. */
  public long getBytesRead() {
    return bytesRead;
  }

  /**
   * Returns the {@link Uri} associated with the last {@link #open(DataSpec)} call. If redirection
   * occurred, this is the redirected uri.
   */
  public Uri getLastOpenedUri() {
    return lastOpenedUri;
  }

  /** Returns the response headers associated with the last {@link #open(DataSpec)} call. */
  public Map<String, List<String>> getLastResponseHeaders() {
    return lastResponseHeaders;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
    dataSource.addTransferListener(transferListener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    // Reassign defaults in case dataSource.open throws an exception.
    lastOpenedUri = dataSpec.uri;
    lastResponseHeaders = Collections.emptyMap();
    long availableBytes = dataSource.open(dataSpec);
    lastOpenedUri = Assertions.checkNotNull(getUri());
    lastResponseHeaders = getResponseHeaders();
    return availableBytes;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    int bytesRead = dataSource.read(buffer, offset, readLength);
    if (bytesRead != C.RESULT_END_OF_INPUT) {
      this.bytesRead += bytesRead;
    }
    return bytesRead;
  }

  @Override
  public @Nullable Uri getUri() {
    return dataSource.getUri();
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return dataSource.getResponseHeaders();
  }

  @Override
  public void close() throws IOException {
    dataSource.close();
  }
}
