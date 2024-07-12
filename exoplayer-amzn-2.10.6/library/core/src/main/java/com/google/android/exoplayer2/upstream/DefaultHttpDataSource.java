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
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.upstream.DataSpec.HttpMethod;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.helpers.NetworkHelpers;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link HttpDataSource} that uses Android's {@link HttpURLConnection}.
 *
 * <p>By default this implementation will not follow cross-protocol redirects (i.e. redirects from
 * HTTP to HTTPS or vice versa). Cross-protocol redirects can be enabled by using the {@link
 * #DefaultHttpDataSource(String, Predicate, TransferListener, int, int, boolean,
 * RequestProperties)} constructor and passing {@code true} as the second last argument.
 */
public class DefaultHttpDataSource extends BaseDataSource implements HttpDataSource {

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private static final String TAG = "DefaultHttpDataSource";
  private static final int MAX_REDIRECTS = 20; // Same limit as okhttp.
  private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
  private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;
  private static final long MAX_BYTES_TO_DRAIN = 2048;
  private static final Pattern CONTENT_RANGE_HEADER =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
  private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

  private final boolean allowCrossProtocolRedirects;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final String userAgent;
  private final @Nullable Predicate<String> contentTypePredicate;
  private final @Nullable RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;

  private @Nullable DataSpec dataSpec;
  private @Nullable HttpURLConnection connection;
  private @Nullable InputStream inputStream;
  private boolean opened;
  private int responseCode;

  private long bytesToSkip;
  private long bytesToRead;

  private long bytesSkipped;
  private long bytesRead;

  /** @param userAgent The User-Agent string that should be used. */
  public DefaultHttpDataSource(String userAgent) {
    this(userAgent, /* contentTypePredicate= */ null);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   */
  public DefaultHttpDataSource(String userAgent, @Nullable Predicate<String> contentTypePredicate) {
    this(
        userAgent,
        contentTypePredicate,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted as
   *     an infinite timeout.
   */
  public DefaultHttpDataSource(
      String userAgent,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMillis,
      int readTimeoutMillis) {
    this(
        userAgent,
        contentTypePredicate,
        connectTimeoutMillis,
        readTimeoutMillis,
        /* allowCrossProtocolRedirects= */ false,
        /* defaultRequestProperties= */ null);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout. Pass {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} to use the
   *     default value.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted as
   *     an infinite timeout. Pass {@link #DEFAULT_READ_TIMEOUT_MILLIS} to use the default value.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   * @param defaultRequestProperties The default request properties to be sent to the server as HTTP
   *     headers or {@code null} if not required.
   */
  public DefaultHttpDataSource(
      String userAgent,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects,
      @Nullable RequestProperties defaultRequestProperties) {
    super(/* isNetwork= */ true);
    this.userAgent = Assertions.checkNotEmpty(userAgent);
    this.contentTypePredicate = contentTypePredicate;
    this.requestProperties = new RequestProperties();
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    this.defaultRequestProperties = defaultRequestProperties;
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param listener An optional listener.
   * @deprecated Use {@link #DefaultHttpDataSource(String, Predicate)} and {@link
   *     #addTransferListener(TransferListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultHttpDataSource(
      String userAgent,
      @Nullable Predicate<String> contentTypePredicate,
      @Nullable TransferListener listener) {
    this(userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted as
   *     an infinite timeout.
   * @deprecated Use {@link #DefaultHttpDataSource(String, Predicate, int, int)} and {@link
   *     #addTransferListener(TransferListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultHttpDataSource(
      String userAgent,
      @Nullable Predicate<String> contentTypePredicate,
      @Nullable TransferListener listener,
      int connectTimeoutMillis,
      int readTimeoutMillis) {
    this(userAgent, contentTypePredicate, listener, connectTimeoutMillis, readTimeoutMillis, false,
        null);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout. Pass {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} to use the
   *     default value.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted as
   *     an infinite timeout. Pass {@link #DEFAULT_READ_TIMEOUT_MILLIS} to use the default value.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   * @param defaultRequestProperties The default request properties to be sent to the server as HTTP
   *     headers or {@code null} if not required.
   * @deprecated Use {@link #DefaultHttpDataSource(String, Predicate, int, int, boolean,
   *     RequestProperties)} and {@link #addTransferListener(TransferListener)}.
   */
  @Deprecated
  public DefaultHttpDataSource(
      String userAgent,
      @Nullable Predicate<String> contentTypePredicate,
      @Nullable TransferListener listener,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects,
      @Nullable RequestProperties defaultRequestProperties) {
    this(
        userAgent,
        contentTypePredicate,
        connectTimeoutMillis,
        readTimeoutMillis,
        allowCrossProtocolRedirects,
        defaultRequestProperties);
    if (listener != null) {
      addTransferListener(listener);
    }
  }

  @Override
  public @Nullable Uri getUri() {
    return connection == null ? null : Uri.parse(connection.getURL().toString());
  }

  @Override
  public int getResponseCode() {
    return connection == null || responseCode <= 0 ? -1 : responseCode;
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return connection == null ? Collections.emptyMap() : connection.getHeaderFields();
  }

  @Override
  public void setRequestProperty(String name, String value) {
    Assertions.checkNotNull(name);
    Assertions.checkNotNull(value);
    requestProperties.set(name, value);
  }

  @Override
  public void clearRequestProperty(String name) {
    Assertions.checkNotNull(name);
    requestProperties.remove(name);
  }

  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    this.dataSpec = dataSpec;
    this.bytesRead = 0;
    this.bytesSkipped = 0;
    transferInitializing(dataSpec);
    try {
      connection = makeConnection(dataSpec);
    } catch (IOException e) {
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    String responseMessage;
    try {
      responseCode = connection.getResponseCode();
      responseMessage = connection.getResponseMessage();
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    // Check for a valid response code.
    if (responseCode < 200 || responseCode > 299) {
      Map<String, List<String>> headers = connection.getHeaderFields();
      closeConnectionQuietly();
      InvalidResponseCodeException exception =
          new InvalidResponseCodeException(responseCode, responseMessage, headers, dataSpec);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }

    // Check for a valid content type.
    String contentType = connection.getContentType();
    if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
      closeConnectionQuietly();
      throw new InvalidContentTypeException(contentType, dataSpec);
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Determine the length of the data to be read, after skipping.
    if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesToRead = dataSpec.length;
      } else {
        long contentLength = getContentLength(connection);
        bytesToRead = contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip)
            : C.LENGTH_UNSET;
      }
    } else {
      // Gzip is enabled. If the server opts to use gzip then the content length in the response
      // will be that of the compressed data, which isn't what we want. Furthermore, there isn't a
      // reliable way to determine whether the gzip was used or not. Always use the dataSpec length
      // in this case.
      bytesToRead = dataSpec.length;
    }

    try {
      inputStream = connection.getInputStream();
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    opened = true;
    transferStarted(dataSpec);

    return bytesToRead;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    try {
      skipInternal();
      return readInternal(buffer, offset, readLength);
    } catch (IOException e) {
      throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ);
    }
  }

  @Override
  public void close() throws HttpDataSourceException {
    try {
      if (inputStream != null) {
        maybeTerminateInputStream(connection, bytesRemaining());
        try {
          inputStream.close();
        } catch (IOException e) {
          throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
        }
      }
    } finally {
      inputStream = null;
      closeConnectionQuietly();
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

  /**
   * Returns the current connection, or null if the source is not currently opened.
   *
   * @return The current open connection, or null.
   */
  protected final @Nullable HttpURLConnection getConnection() {
    return connection;
  }

  /**
   * Returns the number of bytes that have been skipped since the most recent call to
   * {@link #open(DataSpec)}.
   *
   * @return The number of bytes skipped.
   */
  protected final long bytesSkipped() {
    return bytesSkipped;
  }

  /**
   * Returns the number of bytes that have been read since the most recent call to
   * {@link #open(DataSpec)}.
   *
   * @return The number of bytes read.
   */
  protected final long bytesRead() {
    return bytesRead;
  }

  /**
   * Returns the number of bytes that are still to be read for the current {@link DataSpec}.
   * <p>
   * If the total length of the data being read is known, then this length minus {@code bytesRead()}
   * is returned. If the total length is unknown, {@link C#LENGTH_UNSET} is returned.
   *
   * @return The remaining length, or {@link C#LENGTH_UNSET}.
   */
  protected final long bytesRemaining() {
    return bytesToRead == C.LENGTH_UNSET ? bytesToRead : bytesToRead - bytesRead;
  }

  /**
   * Establishes a connection, following redirects to do so where permitted.
   */
  private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
    URL url = new URL(dataSpec.uri.toString());
    @HttpMethod int httpMethod = dataSpec.httpMethod;
    byte[] httpBody = dataSpec.httpBody;
    long position = dataSpec.position;
    long length = dataSpec.length;
    boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);
    boolean allowIcyMetadata = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_ICY_METADATA);

    if (!allowCrossProtocolRedirects) {
      // HttpURLConnection disallows cross-protocol redirects, but otherwise performs redirection
      // automatically. This is the behavior we want, so use it.
      return makeConnection(
          url,
          httpMethod,
          httpBody,
          position,
          length,
          allowGzip,
          allowIcyMetadata,
          /* followRedirects= */ true);
    }

    // We need to handle redirects ourselves to allow cross-protocol redirects.
    int redirectCount = 0;
    while (redirectCount++ <= MAX_REDIRECTS) {
      HttpURLConnection connection =
          makeConnection(
              url,
              httpMethod,
              httpBody,
              position,
              length,
              allowGzip,
              allowIcyMetadata,
              /* followRedirects= */ false);
      int responseCode = connection.getResponseCode();
      String location = connection.getHeaderField("Location");
      if ((httpMethod == DataSpec.HTTP_METHOD_GET || httpMethod == DataSpec.HTTP_METHOD_HEAD)
          && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
              || responseCode == HttpURLConnection.HTTP_MOVED_PERM
              || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
              || responseCode == HttpURLConnection.HTTP_SEE_OTHER
              || responseCode == HTTP_STATUS_TEMPORARY_REDIRECT
              || responseCode == HTTP_STATUS_PERMANENT_REDIRECT)) {
        connection.disconnect();
        url = handleRedirect(url, location);
      } else if (httpMethod == DataSpec.HTTP_METHOD_POST
          && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
              || responseCode == HttpURLConnection.HTTP_MOVED_PERM
              || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
              || responseCode == HttpURLConnection.HTTP_SEE_OTHER)) {
        // POST request follows the redirect and is transformed into a GET request.
        connection.disconnect();
        httpMethod = DataSpec.HTTP_METHOD_GET;
        httpBody = null;
        url = handleRedirect(url, location);
      } else {
        return connection;
      }
    }

    // If we get here we've been redirected more times than are permitted.
    throw new NoRouteToHostException("Too many redirects: " + redirectCount);
  }

  /**
   * Configures a connection and opens it.
   *
   * @param url The url to connect to.
   * @param httpMethod The http method.
   * @param httpBody The body data.
   * @param position The byte offset of the requested data.
   * @param length The length of the requested data, or {@link C#LENGTH_UNSET}.
   * @param allowGzip Whether to allow the use of gzip.
   * @param allowIcyMetadata Whether to allow ICY metadata.
   * @param followRedirects Whether to follow redirects.
   */
  private HttpURLConnection makeConnection(
      URL url,
      @HttpMethod int httpMethod,
      byte[] httpBody,
      long position,
      long length,
      boolean allowGzip,
      boolean allowIcyMetadata,
      boolean followRedirects)
      throws IOException {
    // MODIFIED: Add modern TLS ciphers to HttpUrlConnection and custom Dns
    // https://stackoverflow.com/questions/16299531/how-to-override-the-cipherlist-sent-to-the-server-by-android-when-using-httpsurl
    //HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    HttpURLConnection connection = NetworkHelpers.getHttpsURLConnection(url);
    connection.setConnectTimeout(connectTimeoutMillis);
    connection.setReadTimeout(readTimeoutMillis);
    if (defaultRequestProperties != null) {
      for (Map.Entry<String, String> property : defaultRequestProperties.getSnapshot().entrySet()) {
        connection.setRequestProperty(property.getKey(), property.getValue());
      }
    }
    for (Map.Entry<String, String> property : requestProperties.getSnapshot().entrySet()) {
      connection.setRequestProperty(property.getKey(), property.getValue());
    }
    if (!(position == 0 && length == C.LENGTH_UNSET)) {
      String rangeRequest = "bytes=" + position + "-";
      if (length != C.LENGTH_UNSET) {
        rangeRequest += (position + length - 1);
      }
      connection.setRequestProperty("Range", rangeRequest);
    }
    connection.setRequestProperty("User-Agent", userAgent);
    if (!allowGzip) {
      connection.setRequestProperty("Accept-Encoding", "identity");
    }
    if (allowIcyMetadata) {
      connection.setRequestProperty(
          IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME,
          IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE);
    }
    connection.setInstanceFollowRedirects(followRedirects);
    connection.setDoOutput(httpBody != null);
    connection.setRequestMethod(DataSpec.getStringForHttpMethod(httpMethod));
    if (httpBody != null) {
      connection.setFixedLengthStreamingMode(httpBody.length);
      connection.connect();
      OutputStream os = connection.getOutputStream();
      os.write(httpBody);
      os.close();
    } else {
      connection.connect();
    }
    return connection;
  }

  /**
   * Handles a redirect.
   *
   * @param originalUrl The original URL.
   * @param location The Location header in the response.
   * @return The next URL.
   * @throws IOException If redirection isn't possible.
   */
  private static URL handleRedirect(URL originalUrl, String location) throws IOException {
    if (location == null) {
      throw new ProtocolException("Null location redirect");
    }
    // Form the new url.
    URL url = new URL(originalUrl, location);
    // Check that the protocol of the new url is supported.
    String protocol = url.getProtocol();
    if (!"https".equals(protocol) && !"http".equals(protocol)) {
      throw new ProtocolException("Unsupported protocol redirect: " + protocol);
    }
    // Currently this method is only called if allowCrossProtocolRedirects is true, and so the code
    // below isn't required. If we ever decide to handle redirects ourselves when cross-protocol
    // redirects are disabled, we'll need to uncomment this block of code.
    // if (!allowCrossProtocolRedirects && !protocol.equals(originalUrl.getProtocol())) {
    //   throw new ProtocolException("Disallowed cross-protocol redirect ("
    //       + originalUrl.getProtocol() + " to " + protocol + ")");
    // }
    return url;
  }

  /**
   * Attempts to extract the length of the content from the response headers of an open connection.
   *
   * @param connection The open connection.
   * @return The extracted length, or {@link C#LENGTH_UNSET}.
   */
  private static long getContentLength(HttpURLConnection connection) {
    long contentLength = C.LENGTH_UNSET;
    String contentLengthHeader = connection.getHeaderField("Content-Length");
    if (!TextUtils.isEmpty(contentLengthHeader)) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
      }
    }
    String contentRangeHeader = connection.getHeaderField("Content-Range");
    if (!TextUtils.isEmpty(contentRangeHeader)) {
      Matcher matcher = CONTENT_RANGE_HEADER.matcher(contentRangeHeader);
      if (matcher.find()) {
        try {
          long contentLengthFromRange =
              Long.parseLong(matcher.group(2)) - Long.parseLong(matcher.group(1)) + 1;
          if (contentLength < 0) {
            // Some proxy servers strip the Content-Length header. Fall back to the length
            // calculated here in this case.
            contentLength = contentLengthFromRange;
          } else if (contentLength != contentLengthFromRange) {
            // If there is a discrepancy between the Content-Length and Content-Range headers,
            // assume the one with the larger value is correct. We have seen cases where carrier
            // change one of them to reduce the size of a request, but it is unlikely anybody would
            // increase it.
            Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader
                + "]");
            contentLength = Math.max(contentLength, contentLengthFromRange);
          }
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
        }
      }
    }
    return contentLength;
  }

  /**
   * Skips any bytes that need skipping. Else does nothing.
   * <p>
   * This implementation is based roughly on {@code libcore.io.Streams.skipByReading()}.
   *
   * @throws InterruptedIOException If the thread is interrupted during the operation.
   * @throws EOFException If the end of the input stream is reached before the bytes are skipped.
   */
  private void skipInternal() throws IOException {
    if (bytesSkipped == bytesToSkip) {
      return;
    }

    // Acquire the shared skip buffer.
    byte[] skipBuffer = skipBufferReference.getAndSet(null);
    if (skipBuffer == null) {
      skipBuffer = new byte[4096];
    }

    while (bytesSkipped != bytesToSkip) {
      int readLength = (int) Math.min(bytesToSkip - bytesSkipped, skipBuffer.length);
      int read = inputStream.read(skipBuffer, 0, readLength);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedIOException();
      }
      if (read == -1) {
        throw new EOFException();
      }
      bytesSkipped += read;
      bytesTransferred(read);
    }

    // Release the shared skip buffer.
    skipBufferReference.set(skipBuffer);
  }

  /**
   * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
   * index {@code offset}.
   * <p>
   * This method blocks until at least one byte of data can be read, the end of the opened range is
   * detected, or an exception is thrown.
   *
   * @param buffer The buffer into which the read data should be stored.
   * @param offset The start offset into {@code buffer} at which data should be written.
   * @param readLength The maximum number of bytes to read.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
   *     range is reached.
   * @throws IOException If an error occurs reading from the source.
   */
  private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    }
    if (bytesToRead != C.LENGTH_UNSET) {
      long bytesRemaining = bytesToRead - bytesRead;
      if (bytesRemaining == 0) {
        return C.RESULT_END_OF_INPUT;
      }
      readLength = (int) Math.min(readLength, bytesRemaining);
    }

    int read = inputStream.read(buffer, offset, readLength);
    if (read == -1) {
      if (bytesToRead != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new EOFException();
      }
      return C.RESULT_END_OF_INPUT;
    }

    bytesRead += read;
    bytesTransferred(read);
    return read;
  }

  /**
   * On platform API levels 19 and 20, okhttp's implementation of {@link InputStream#close} can
   * block for a long time if the stream has a lot of data remaining. Call this method before
   * closing the input stream to make a best effort to cause the input stream to encounter an
   * unexpected end of input, working around this issue. On other platform API levels, the method
   * does nothing.
   *
   * @param connection The connection whose {@link InputStream} should be terminated.
   * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
   *     length is known. {@link C#LENGTH_UNSET} otherwise.
   */
  private static void maybeTerminateInputStream(HttpURLConnection connection, long bytesRemaining) {
    if (Util.SDK_INT != 19 && Util.SDK_INT != 20) {
      return;
    }

    try {
      InputStream inputStream = connection.getInputStream();
      if (bytesRemaining == C.LENGTH_UNSET) {
        // If the input stream has already ended, do nothing. The socket may be re-used.
        if (inputStream.read() == -1) {
          return;
        }
      } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
        // There isn't much data left. Prefer to allow it to drain, which may allow the socket to be
        // re-used.
        return;
      }
      String className = inputStream.getClass().getName();
      if ("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream".equals(className)
          || "com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream"
              .equals(className)) {
        Class<?> superclass = inputStream.getClass().getSuperclass();
        Method unexpectedEndOfInput = superclass.getDeclaredMethod("unexpectedEndOfInput");
        unexpectedEndOfInput.setAccessible(true);
        unexpectedEndOfInput.invoke(inputStream);
      }
    } catch (Exception e) {
      // If an IOException then the connection didn't ever have an input stream, or it was closed
      // already. If another type of exception then something went wrong, most likely the device
      // isn't using okhttp.
    }
  }


  /**
   * Closes the current connection quietly, if there is one.
   */
  private void closeConnectionQuietly() {
    if (connection != null) {
      try {
        connection.disconnect();
      } catch (Exception e) {
        Log.e(TAG, "Unexpected error while disconnecting", e);
      }
      connection = null;
    }
  }

}
