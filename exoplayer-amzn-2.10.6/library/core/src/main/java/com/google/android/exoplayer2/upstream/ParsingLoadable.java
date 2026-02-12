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
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * A {@link Loadable} for objects that can be parsed from binary data using a {@link Parser}.
 *
 * @param <T> The type of the object being loaded.
 */
public final class ParsingLoadable<T> implements Loadable {

  /**
   * Parses an object from loaded data.
   */
  public interface Parser<T> {

    /**
     * Parses an object from a response.
     *
     * @param uri The source {@link Uri} of the response, after any redirection.
     * @param inputStream An {@link InputStream} from which the response data can be read.
     * @return The parsed object.
     * @throws ParserException If an error occurs parsing the data.
     * @throws IOException If an error occurs reading data from the stream.
     */
    T parse(Uri uri, InputStream inputStream) throws IOException;

  }

  /**
   * Loads a single parsable object.
   *
   * @param dataSource The {@link DataSource} through which the object should be read.
   * @param parser The {@link Parser} to parse the object from the response.
   * @param uri The {@link Uri} of the object to read.
   * @param type The type of the data. One of the {@link C}{@code DATA_TYPE_*} constants.
   * @return The parsed object
   * @throws IOException Thrown if there is an error while loading or parsing.
   */
  public static <T> T load(DataSource dataSource, Parser<? extends T> parser, Uri uri, int type)
      throws IOException {
    ParsingLoadable<T> loadable = new ParsingLoadable<>(dataSource, uri, type, parser);
    loadable.load();
    return Assertions.checkNotNull(loadable.getResult());
  }

  /**
   * Loads a single parsable object.
   *
   * @param dataSource The {@link DataSource} through which the object should be read.
   * @param parser The {@link Parser} to parse the object from the response.
   * @param dataSpec The {@link DataSpec} of the object to read.
   * @param type The type of the data. One of the {@link C}{@code DATA_TYPE_*} constants.
   * @return The parsed object
   * @throws IOException Thrown if there is an error while loading or parsing.
   */
  public static <T> T load(
      DataSource dataSource, Parser<? extends T> parser, DataSpec dataSpec, int type)
      throws IOException {
    ParsingLoadable<T> loadable = new ParsingLoadable<>(dataSource, dataSpec, type, parser);
    loadable.load();
    return Assertions.checkNotNull(loadable.getResult());
  }

  /**
   * The {@link DataSpec} that defines the data to be loaded.
   */
  public final DataSpec dataSpec;
  /**
   * The type of the data. One of the {@code DATA_TYPE_*} constants defined in {@link C}. For
   * reporting only.
   */
  public final int type;

  private final StatsDataSource dataSource;
  private final Parser<? extends T> parser;

  private volatile @Nullable T result;

  /**
   * @param dataSource A {@link DataSource} to use when loading the data.
   * @param uri The {@link Uri} from which the object should be loaded.
   * @param type See {@link #type}.
   * @param parser Parses the object from the response.
   */
  public ParsingLoadable(DataSource dataSource, Uri uri, int type, Parser<? extends T> parser) {
    this(dataSource, new DataSpec(uri, DataSpec.FLAG_ALLOW_GZIP), type, parser);
  }

  /**
   * @param dataSource A {@link DataSource} to use when loading the data.
   * @param dataSpec The {@link DataSpec} from which the object should be loaded.
   * @param type See {@link #type}.
   * @param parser Parses the object from the response.
   */
  public ParsingLoadable(DataSource dataSource, DataSpec dataSpec, int type,
      Parser<? extends T> parser) {
    this.dataSource = new StatsDataSource(dataSource);
    this.dataSpec = dataSpec;
    this.type = type;
    this.parser = parser;
  }

  /** Returns the loaded object, or null if an object has not been loaded. */
  public final @Nullable T getResult() {
    return result;
  }

  /**
   * Returns the number of bytes loaded. In the case that the network response was compressed, the
   * value returned is the size of the data <em>after</em> decompression. Must only be called after
   * the load completed, failed, or was canceled.
   */
  public long bytesLoaded() {
    return dataSource.getBytesRead();
  }

  /**
   * Returns the {@link Uri} from which data was read. If redirection occurred, this is the
   * redirected uri. Must only be called after the load completed, failed, or was canceled.
   */
  public Uri getUri() {
    return dataSource.getLastOpenedUri();
  }

  /**
   * Returns the response headers associated with the load. Must only be called after the load
   * completed, failed, or was canceled.
   */
  public Map<String, List<String>> getResponseHeaders() {
    return dataSource.getLastResponseHeaders();
  }

  @Override
  public final void cancelLoad() {
    // Do nothing.
  }

  @Override
  public final void load() throws IOException {
    // We always load from the beginning, so reset bytesRead to 0.
    dataSource.resetBytesRead();
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
    try {
      inputStream.open();
      Uri dataSourceUri = Assertions.checkNotNull(dataSource.getUri());
      result = parser.parse(dataSourceUri, inputStream);
    } finally {
      Util.closeQuietly(inputStream);
    }
  }
}
