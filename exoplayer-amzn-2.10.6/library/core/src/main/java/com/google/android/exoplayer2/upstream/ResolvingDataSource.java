/*
 * Copyright (C) 2019 The Android Open Source Project
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
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** {@link DataSource} wrapper allowing just-in-time resolution of {@link DataSpec DataSpecs}. */
public final class ResolvingDataSource implements DataSource {

  /** Resolves {@link DataSpec DataSpecs}. */
  public interface Resolver {

    /**
     * Resolves a {@link DataSpec} before forwarding it to the wrapped {@link DataSource}. This
     * method is allowed to block until the {@link DataSpec} has been resolved.
     *
     * <p>Note that this method is called for every new connection, so caching of results is
     * recommended, especially if network operations are involved.
     *
     * @param dataSpec The original {@link DataSpec}.
     * @return The resolved {@link DataSpec}.
     * @throws IOException If an {@link IOException} occurred while resolving the {@link DataSpec}.
     */
    DataSpec resolveDataSpec(DataSpec dataSpec) throws IOException;

    /**
     * Resolves a URI reported by {@link DataSource#getUri()} for event reporting and caching
     * purposes.
     *
     * <p>Implementations do not need to overwrite this method unless they want to change the
     * reported URI.
     *
     * <p>This method is <em>not</em> allowed to block.
     *
     * @param uri The URI as reported by {@link DataSource#getUri()}.
     * @return The resolved URI used for event reporting and caching.
     */
    default Uri resolveReportedUri(Uri uri) {
      return uri;
    }
  }

  /** {@link DataSource.Factory} for {@link ResolvingDataSource} instances. */
  public static final class Factory implements DataSource.Factory {

    private final DataSource.Factory upstreamFactory;
    private final Resolver resolver;

    /**
     * @param upstreamFactory The wrapped {@link DataSource.Factory} for handling resolved {@link
     *     DataSpec DataSpecs}.
     * @param resolver The {@link Resolver} to resolve the {@link DataSpec DataSpecs}.
     */
    public Factory(DataSource.Factory upstreamFactory, Resolver resolver) {
      this.upstreamFactory = upstreamFactory;
      this.resolver = resolver;
    }

    @Override
    public ResolvingDataSource createDataSource() {
      return new ResolvingDataSource(upstreamFactory.createDataSource(), resolver);
    }
  }

  private final DataSource upstreamDataSource;
  private final Resolver resolver;

  private boolean upstreamOpened;

  /**
   * @param upstreamDataSource The wrapped {@link DataSource}.
   * @param resolver The {@link Resolver} to resolve the {@link DataSpec DataSpecs}.
   */
  public ResolvingDataSource(DataSource upstreamDataSource, Resolver resolver) {
    this.upstreamDataSource = upstreamDataSource;
    this.resolver = resolver;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
    upstreamDataSource.addTransferListener(transferListener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    DataSpec resolvedDataSpec = resolver.resolveDataSpec(dataSpec);
    upstreamOpened = true;
    return upstreamDataSource.open(resolvedDataSpec);
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return upstreamDataSource.read(buffer, offset, readLength);
  }

  @Nullable
  @Override
  public Uri getUri() {
    Uri reportedUri = upstreamDataSource.getUri();
    return reportedUri == null ? null : resolver.resolveReportedUri(reportedUri);
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return upstreamDataSource.getResponseHeaders();
  }

  @Override
  public void close() throws IOException {
    if (upstreamOpened) {
      upstreamOpened = false;
      upstreamDataSource.close();
    }
  }
}
