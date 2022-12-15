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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An HTTP {@link DataSource}.
 */
public interface HttpDataSource extends DataSource {

  /**
   * A factory for {@link HttpDataSource} instances.
   */
  interface Factory extends DataSource.Factory {

    @Override
    HttpDataSource createDataSource();

    /**
     * Gets the default request properties used by all {@link HttpDataSource}s created by the
     * factory. Changes to the properties will be reflected in any future requests made by
     * {@link HttpDataSource}s created by the factory.
     *
     * @return The default request properties of the factory.
     */
    RequestProperties getDefaultRequestProperties();

    /**
     * Sets a default request header for {@link HttpDataSource} instances created by the factory.
     *
     * @deprecated Use {@link #getDefaultRequestProperties} instead.
     * @param name The name of the header field.
     * @param value The value of the field.
     */
    @Deprecated
    void setDefaultRequestProperty(String name, String value);

    /**
     * Clears a default request header for {@link HttpDataSource} instances created by the factory.
     *
     * @deprecated Use {@link #getDefaultRequestProperties} instead.
     * @param name The name of the header field.
     */
    @Deprecated
    void clearDefaultRequestProperty(String name);

    /**
     * Clears all default request headers for all {@link HttpDataSource} instances created by the
     * factory.
     *
     * @deprecated Use {@link #getDefaultRequestProperties} instead.
     */
    @Deprecated
    void clearAllDefaultRequestProperties();

  }

  /**
   * Stores HTTP request properties (aka HTTP headers) and provides methods to modify the headers
   * in a thread safe way to avoid the potential of creating snapshots of an inconsistent or
   * unintended state.
   */
  final class RequestProperties {

    private final Map<String, String> requestProperties;
    private Map<String, String> requestPropertiesSnapshot;

    public RequestProperties() {
      requestProperties = new HashMap<>();
    }

    /**
     * Sets the specified property {@code value} for the specified {@code name}. If a property for
     * this name previously existed, the old value is replaced by the specified value.
     *
     * @param name The name of the request property.
     * @param value The value of the request property.
     */
    public synchronized void set(String name, String value) {
      requestPropertiesSnapshot = null;
      requestProperties.put(name, value);
    }

    /**
     * Sets the keys and values contained in the map. If a property previously existed, the old
     * value is replaced by the specified value. If a property previously existed and is not in the
     * map, the property is left unchanged.
     *
     * @param properties The request properties.
     */
    public synchronized void set(Map<String, String> properties) {
      requestPropertiesSnapshot = null;
      requestProperties.putAll(properties);
    }

    /**
     * Removes all properties previously existing and sets the keys and values of the map.
     *
     * @param properties The request properties.
     */
    public synchronized void clearAndSet(Map<String, String> properties) {
      requestPropertiesSnapshot = null;
      requestProperties.clear();
      requestProperties.putAll(properties);
    }

    /**
     * Removes a request property by name.
     *
     * @param name The name of the request property to remove.
     */
    public synchronized void remove(String name) {
      requestPropertiesSnapshot = null;
      requestProperties.remove(name);
    }

    /**
     * Clears all request properties.
     */
    public synchronized void clear() {
      requestPropertiesSnapshot = null;
      requestProperties.clear();
    }

    /**
     * Gets a snapshot of the request properties.
     *
     * @return A snapshot of the request properties.
     */
    public synchronized Map<String, String> getSnapshot() {
      if (requestPropertiesSnapshot == null) {
        requestPropertiesSnapshot = Collections.unmodifiableMap(new HashMap<>(requestProperties));
      }
      return requestPropertiesSnapshot;
    }

  }

  /**
   * Base implementation of {@link Factory} that sets default request properties.
   */
  abstract class BaseFactory implements Factory {

    private final RequestProperties defaultRequestProperties;

    public BaseFactory() {
      defaultRequestProperties = new RequestProperties();
    }

    @Override
    public final HttpDataSource createDataSource() {
      return createDataSourceInternal(defaultRequestProperties);
    }

    @Override
    public final RequestProperties getDefaultRequestProperties() {
      return defaultRequestProperties;
    }

    @Deprecated
    @Override
    public final void setDefaultRequestProperty(String name, String value) {
      defaultRequestProperties.set(name, value);
    }

    @Deprecated
    @Override
    public final void clearDefaultRequestProperty(String name) {
      defaultRequestProperties.remove(name);
    }

    @Deprecated
    @Override
    public final void clearAllDefaultRequestProperties() {
      defaultRequestProperties.clear();
    }

    /**
     * Called by {@link #createDataSource()} to create a {@link HttpDataSource} instance.
     *
     * @param defaultRequestProperties The default {@code RequestProperties} to be used by the
     *     {@link HttpDataSource} instance.
     * @return A {@link HttpDataSource} instance.
     */
    protected abstract HttpDataSource createDataSourceInternal(RequestProperties
        defaultRequestProperties);

  }

  /** A {@link Predicate} that rejects content types often used for pay-walls. */
  Predicate<String> REJECT_PAYWALL_TYPES =
      contentType -> {
        contentType = Util.toLowerInvariant(contentType);
        return !TextUtils.isEmpty(contentType)
            && (!contentType.contains("text") || contentType.contains("text/vtt"))
            && !contentType.contains("html")
            && !contentType.contains("xml");
      };

  /**
   * Thrown when an error is encountered when trying to read from a {@link HttpDataSource}.
   */
  class HttpDataSourceException extends IOException {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
    public @interface Type {}

    public static final int TYPE_OPEN = 1;
    public static final int TYPE_READ = 2;
    public static final int TYPE_CLOSE = 3;

    @Type public final int type;

    /**
     * The {@link DataSpec} associated with the current connection.
     */
    public final DataSpec dataSpec;

    public HttpDataSourceException(DataSpec dataSpec, @Type int type) {
      super();
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, DataSpec dataSpec, @Type int type) {
      super(message);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(IOException cause, DataSpec dataSpec, @Type int type) {
      super(cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec,
        @Type int type) {
      super(message, cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

  }

  /**
   * Thrown when the content type is invalid.
   */
  final class InvalidContentTypeException extends HttpDataSourceException {

    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super("Invalid content type: " + contentType, dataSpec, TYPE_OPEN);
      this.contentType = contentType;
    }

  }

  /**
   * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
   */
  final class InvalidResponseCodeException extends HttpDataSourceException {

    /**
     * The response code that was outside of the 2xx range.
     */
    public final int responseCode;

    /** The http status message. */
    @Nullable public final String responseMessage;

    /**
     * An unmodifiable map of the response header fields and values.
     */
    public final Map<String, List<String>> headerFields;

    /** @deprecated Use {@link #InvalidResponseCodeException(int, String, Map, DataSpec)}. */
    @Deprecated
    public InvalidResponseCodeException(
        int responseCode, Map<String, List<String>> headerFields, DataSpec dataSpec) {
      this(responseCode, /* responseMessage= */ null, headerFields, dataSpec);
    }

    public InvalidResponseCodeException(
        int responseCode,
        @Nullable String responseMessage,
        Map<String, List<String>> headerFields,
        DataSpec dataSpec) {
      super("Response code: " + responseCode, dataSpec, TYPE_OPEN);
      this.responseCode = responseCode;
      this.responseMessage = responseMessage;
      this.headerFields = headerFields;
    }

  }

  @Override
  long open(DataSpec dataSpec) throws HttpDataSourceException;

  @Override
  void close() throws HttpDataSourceException;

  @Override
  int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException;

  /**
   * Sets the value of a request header. The value will be used for subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  void setRequestProperty(String name, String value);

  /**
   * Clears the value of a request header. The change will apply to subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   */
  void clearRequestProperty(String name);

  /**
   * Clears all request headers that were set by {@link #setRequestProperty(String, String)}.
   */
  void clearAllRequestProperties();

  /**
   * When the source is open, returns the HTTP response status code associated with the last {@link
   * #open} call. Otherwise, returns a negative value.
   */
  int getResponseCode();

  @Override
  Map<String, List<String>> getResponseHeaders();
}
