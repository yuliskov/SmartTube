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
package com.google.android.exoplayer2.ext.cronet;

import android.net.Uri;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Predicate;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Status;
import org.chromium.net.UrlResponseInfo;

/**
 * DataSource without intermediate buffer based on Cronet API set using UrlRequest.
 *
 * <p>This class's methods are organized in the sequence of expected calls.
 */
public class CronetDataSource extends BaseDataSource implements HttpDataSource {

  /**
   * Thrown when an error is encountered when trying to open a {@link CronetDataSource}.
   */
  public static final class OpenException extends HttpDataSourceException {

    /**
     * Returns the status of the connection establishment at the moment when the error occurred, as
     * defined by {@link UrlRequest.Status}.
     */
    public final int cronetConnectionStatus;

    public OpenException(IOException cause, DataSpec dataSpec, int cronetConnectionStatus) {
      super(cause, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(String errorMessage, DataSpec dataSpec, int cronetConnectionStatus) {
      super(errorMessage, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

  }

  /** Thrown on catching an InterruptedException. */
  public static final class InterruptedIOException extends IOException {

    public InterruptedIOException(InterruptedException e) {
      super(e);
    }
  }

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.cronet");
  }

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  /* package */ final UrlRequest.Callback urlRequestCallback;

  private static final String TAG = "CronetDataSource";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String SET_COOKIE = "Set-Cookie";
  private static final String COOKIE = "Cookie";

  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
  // The size of read buffer passed to cronet UrlRequest.read().
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  private final CronetEngine cronetEngine;
  private final Executor executor;
  @Nullable private final Predicate<String> contentTypePredicate;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final boolean handleSetCookieRequests;
  private final RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;
  private final ConditionVariable operation;
  private final Clock clock;

  // Accessed by the calling thread only.
  private boolean opened;
  private long bytesToSkip;
  private long bytesRemaining;

  // Written from the calling thread only. currentUrlRequest.start() calls ensure writes are visible
  // to reads made by the Cronet thread.
  private UrlRequest currentUrlRequest;
  private DataSpec currentDataSpec;

  // Reference written and read by calling thread only. Passed to Cronet thread as a local variable.
  // operation.open() calls ensure writes into the buffer are visible to reads made by the calling
  // thread.
  private ByteBuffer readBuffer;

  // Written from the Cronet thread only. operation.open() calls ensure writes are visible to reads
  // made by the calling thread.
  private UrlResponseInfo responseInfo;
  private IOException exception;
  private boolean finished;

  private volatile long currentConnectTimeoutMs;

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   */
  public CronetDataSource(CronetEngine cronetEngine, Executor executor) {
    this(cronetEngine, executor, /* contentTypePredicate= */ null);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   */
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate) {
    this(
        cronetEngine,
        executor,
        contentTypePredicate,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        false,
        null,
        false);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties The default request properties to be used.
   */
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      RequestProperties defaultRequestProperties) {
    this(
        cronetEngine,
        executor,
        contentTypePredicate,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        Clock.DEFAULT,
        defaultRequestProperties,
        false);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties The default request properties to be used.
   * @param handleSetCookieRequests Whether "Set-Cookie" requests on redirect should be forwarded to
   *     the redirect url in the "Cookie" header.
   */
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      RequestProperties defaultRequestProperties,
      boolean handleSetCookieRequests) {
    this(
        cronetEngine,
        executor,
        contentTypePredicate,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        Clock.DEFAULT,
        defaultRequestProperties,
        handleSetCookieRequests);
  }

  /* package */ CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      Clock clock,
      RequestProperties defaultRequestProperties,
      boolean handleSetCookieRequests) {
    super(/* isNetwork= */ true);
    this.urlRequestCallback = new UrlRequestCallback();
    this.cronetEngine = Assertions.checkNotNull(cronetEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.contentTypePredicate = contentTypePredicate;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.clock = Assertions.checkNotNull(clock);
    this.defaultRequestProperties = defaultRequestProperties;
    this.handleSetCookieRequests = handleSetCookieRequests;
    requestProperties = new RequestProperties();
    operation = new ConditionVariable();
  }

  // HttpDataSource implementation.

  @Override
  public void setRequestProperty(String name, String value) {
    requestProperties.set(name, value);
  }

  @Override
  public void clearRequestProperty(String name) {
    requestProperties.remove(name);
  }

  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  @Override
  public int getResponseCode() {
    return responseInfo == null || responseInfo.getHttpStatusCode() <= 0
        ? -1
        : responseInfo.getHttpStatusCode();
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return responseInfo == null ? Collections.emptyMap() : responseInfo.getAllHeaders();
  }

  @Override
  public Uri getUri() {
    return responseInfo == null ? null : Uri.parse(responseInfo.getUrl());
  }

  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    Assertions.checkNotNull(dataSpec);
    Assertions.checkState(!opened);

    operation.close();
    resetConnectTimeout();
    currentDataSpec = dataSpec;
    try {
      currentUrlRequest = buildRequestBuilder(dataSpec).build();
    } catch (IOException e) {
      throw new OpenException(e, currentDataSpec, Status.IDLE);
    }
    currentUrlRequest.start();

    transferInitializing(dataSpec);
    try {
      boolean connectionOpened = blockUntilConnectTimeout();
      if (exception != null) {
        throw new OpenException(exception, currentDataSpec, getStatus(currentUrlRequest));
      } else if (!connectionOpened) {
        // The timeout was reached before the connection was opened.
        throw new OpenException(
            new SocketTimeoutException(), dataSpec, getStatus(currentUrlRequest));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OpenException(new InterruptedIOException(e), dataSpec, Status.INVALID);
    }

    // Check for a valid response code.
    int responseCode = responseInfo.getHttpStatusCode();
    if (responseCode < 200 || responseCode > 299) {
      InvalidResponseCodeException exception =
          new InvalidResponseCodeException(
              responseCode,
              responseInfo.getHttpStatusText(),
              responseInfo.getAllHeaders(),
              currentDataSpec);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }

    // Check for a valid content type.
    if (contentTypePredicate != null) {
      List<String> contentTypeHeaders = responseInfo.getAllHeaders().get(CONTENT_TYPE);
      String contentType = isEmpty(contentTypeHeaders) ? null : contentTypeHeaders.get(0);
      if (!contentTypePredicate.evaluate(contentType)) {
        throw new InvalidContentTypeException(contentType, currentDataSpec);
      }
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Calculate the content length.
    if (!getIsCompressed(responseInfo)) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = getContentLength(responseInfo);
      }
    } else {
      // If the response is compressed then the content length will be that of the compressed data
      // which isn't what we want. Always use the dataSpec length in this case.
      bytesRemaining = currentDataSpec.length;
    }

    opened = true;
    transferStarted(dataSpec);

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    Assertions.checkState(opened);

    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    if (readBuffer == null) {
      readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
      readBuffer.limit(0);
    }
    while (!readBuffer.hasRemaining()) {
      // Fill readBuffer with more data from Cronet.
      operation.close();
      readBuffer.clear();
      currentUrlRequest.read(readBuffer);
      try {
        if (!operation.block(readTimeoutMs)) {
          throw new SocketTimeoutException();
        }
      } catch (InterruptedException e) {
        // The operation is ongoing so replace readBuffer to avoid it being written to by this
        // operation during a subsequent request.
        readBuffer = null;
        Thread.currentThread().interrupt();
        throw new HttpDataSourceException(
            new InterruptedIOException(e), currentDataSpec, HttpDataSourceException.TYPE_READ);
      } catch (SocketTimeoutException e) {
        // The operation is ongoing so replace readBuffer to avoid it being written to by this
        // operation during a subsequent request.
        readBuffer = null;
        throw new HttpDataSourceException(e, currentDataSpec, HttpDataSourceException.TYPE_READ);
      }

      if (exception != null) {
        throw new HttpDataSourceException(exception, currentDataSpec,
            HttpDataSourceException.TYPE_READ);
      } else if (finished) {
        bytesRemaining = 0;
        return C.RESULT_END_OF_INPUT;
      } else {
        // The operation didn't time out, fail or finish, and therefore data must have been read.
        readBuffer.flip();
        Assertions.checkState(readBuffer.hasRemaining());
        if (bytesToSkip > 0) {
          int bytesSkipped = (int) Math.min(readBuffer.remaining(), bytesToSkip);
          readBuffer.position(readBuffer.position() + bytesSkipped);
          bytesToSkip -= bytesSkipped;
        }
      }
    }

    int bytesRead = Math.min(readBuffer.remaining(), readLength);
    readBuffer.get(buffer, offset, bytesRead);

    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Override
  public synchronized void close() {
    if (currentUrlRequest != null) {
      currentUrlRequest.cancel();
      currentUrlRequest = null;
    }
    if (readBuffer != null) {
      readBuffer.limit(0);
    }
    currentDataSpec = null;
    responseInfo = null;
    exception = null;
    finished = false;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }

  /** Returns current {@link UrlRequest}. May be null if the data source is not opened. */
  @Nullable
  protected UrlRequest getCurrentUrlRequest() {
    return currentUrlRequest;
  }

  /** Returns current {@link UrlResponseInfo}. May be null if the data source is not opened. */
  @Nullable
  protected UrlResponseInfo getCurrentUrlResponseInfo() {
    return responseInfo;
  }

  // Internal methods.

  private UrlRequest.Builder buildRequestBuilder(DataSpec dataSpec) throws IOException {
    UrlRequest.Builder requestBuilder =
        cronetEngine
            .newUrlRequestBuilder(dataSpec.uri.toString(), urlRequestCallback, executor)
            .allowDirectExecutor();
    // Set the headers.
    boolean isContentTypeHeaderSet = false;
    if (defaultRequestProperties != null) {
      for (Entry<String, String> headerEntry : defaultRequestProperties.getSnapshot().entrySet()) {
        String key = headerEntry.getKey();
        isContentTypeHeaderSet = isContentTypeHeaderSet || CONTENT_TYPE.equals(key);
        requestBuilder.addHeader(key, headerEntry.getValue());
      }
    }
    Map<String, String> requestPropertiesSnapshot = requestProperties.getSnapshot();
    for (Entry<String, String> headerEntry : requestPropertiesSnapshot.entrySet()) {
      String key = headerEntry.getKey();
      isContentTypeHeaderSet = isContentTypeHeaderSet || CONTENT_TYPE.equals(key);
      requestBuilder.addHeader(key, headerEntry.getValue());
    }
    if (dataSpec.httpBody != null && !isContentTypeHeaderSet) {
      throw new IOException("HTTP request with non-empty body must set Content-Type");
    }
    if (dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_ICY_METADATA)) {
      requestBuilder.addHeader(
          IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME,
          IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE);
    }
    // Set the Range header.
    if (dataSpec.position != 0 || dataSpec.length != C.LENGTH_UNSET) {
      StringBuilder rangeValue = new StringBuilder();
      rangeValue.append("bytes=");
      rangeValue.append(dataSpec.position);
      rangeValue.append("-");
      if (dataSpec.length != C.LENGTH_UNSET) {
        rangeValue.append(dataSpec.position + dataSpec.length - 1);
      }
      requestBuilder.addHeader("Range", rangeValue.toString());
    }
    // TODO: Uncomment when https://bugs.chromium.org/p/chromium/issues/detail?id=767025 is fixed
    // (adjusting the code as necessary).
    // Force identity encoding unless gzip is allowed.
    // if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
    //   requestBuilder.addHeader("Accept-Encoding", "identity");
    // }
    // Set the method and (if non-empty) the body.
    requestBuilder.setHttpMethod(dataSpec.getHttpMethodString());
    if (dataSpec.httpBody != null) {
      requestBuilder.setUploadDataProvider(
          new ByteArrayUploadDataProvider(dataSpec.httpBody), executor);
    }
    return requestBuilder;
  }

  private boolean blockUntilConnectTimeout() throws InterruptedException {
    long now = clock.elapsedRealtime();
    boolean opened = false;
    while (!opened && now < currentConnectTimeoutMs) {
      opened = operation.block(currentConnectTimeoutMs - now + 5 /* fudge factor */);
      now = clock.elapsedRealtime();
    }
    return opened;
  }

  private void resetConnectTimeout() {
    currentConnectTimeoutMs = clock.elapsedRealtime() + connectTimeoutMs;
  }

  private static boolean getIsCompressed(UrlResponseInfo info) {
    for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
      if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
        return !entry.getValue().equalsIgnoreCase("identity");
      }
    }
    return false;
  }

  private static long getContentLength(UrlResponseInfo info) {
    long contentLength = C.LENGTH_UNSET;
    Map<String, List<String>> headers = info.getAllHeaders();
    List<String> contentLengthHeaders = headers.get("Content-Length");
    String contentLengthHeader = null;
    if (!isEmpty(contentLengthHeaders)) {
      contentLengthHeader = contentLengthHeaders.get(0);
      if (!TextUtils.isEmpty(contentLengthHeader)) {
        try {
          contentLength = Long.parseLong(contentLengthHeader);
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
        }
      }
    }
    List<String> contentRangeHeaders = headers.get("Content-Range");
    if (!isEmpty(contentRangeHeaders)) {
      String contentRangeHeader = contentRangeHeaders.get(0);
      Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRangeHeader);
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
            // change one of them to reduce the size of a request, but it is unlikely anybody
            // would increase it.
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

  private static String parseCookies(List<String> setCookieHeaders) {
    return TextUtils.join(";", setCookieHeaders);
  }

  private static void attachCookies(UrlRequest.Builder requestBuilder, String cookies) {
    if (TextUtils.isEmpty(cookies)) {
      return;
    }
    requestBuilder.addHeader(COOKIE, cookies);
  }

  private static int getStatus(UrlRequest request) throws InterruptedException {
    final ConditionVariable conditionVariable = new ConditionVariable();
    final int[] statusHolder = new int[1];
    request.getStatus(new UrlRequest.StatusListener() {
      @Override
      public void onStatus(int status) {
        statusHolder[0] = status;
        conditionVariable.open();
      }
    });
    conditionVariable.block();
    return statusHolder[0];
  }

  private static boolean isEmpty(List<?> list) {
    return list == null || list.isEmpty();
  }

  private final class UrlRequestCallback extends UrlRequest.Callback {

    @Override
    public synchronized void onRedirectReceived(
        UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
      if (request != currentUrlRequest) {
        return;
      }
      if (currentDataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        int responseCode = info.getHttpStatusCode();
        // The industry standard is to disregard POST redirects when the status code is 307 or 308.
        if (responseCode == 307 || responseCode == 308) {
          exception =
              new InvalidResponseCodeException(
                  responseCode, info.getHttpStatusText(), info.getAllHeaders(), currentDataSpec);
          operation.open();
          return;
        }
      }
      if (resetTimeoutOnRedirects) {
        resetConnectTimeout();
      }

      Map<String, List<String>> headers = info.getAllHeaders();
      if (!handleSetCookieRequests || isEmpty(headers.get(SET_COOKIE))) {
        request.followRedirect();
      } else {
        currentUrlRequest.cancel();
        DataSpec redirectUrlDataSpec;
        if (currentDataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
          // For POST redirects that aren't 307 or 308, the redirect is followed but request is
          // transformed into a GET.
          redirectUrlDataSpec =
              new DataSpec(
                  Uri.parse(newLocationUrl),
                  DataSpec.HTTP_METHOD_GET,
                  /* httpBody= */ null,
                  currentDataSpec.absoluteStreamPosition,
                  currentDataSpec.position,
                  currentDataSpec.length,
                  currentDataSpec.key,
                  currentDataSpec.flags);
        } else {
          redirectUrlDataSpec = currentDataSpec.withUri(Uri.parse(newLocationUrl));
        }
        UrlRequest.Builder requestBuilder;
        try {
          requestBuilder = buildRequestBuilder(redirectUrlDataSpec);
        } catch (IOException e) {
          exception = e;
          return;
        }
        String cookieHeadersValue = parseCookies(headers.get(SET_COOKIE));
        attachCookies(requestBuilder, cookieHeadersValue);
        currentUrlRequest = requestBuilder.build();
        currentUrlRequest.start();
      }
    }

    @Override
    public synchronized void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
      if (request != currentUrlRequest) {
        return;
      }
      responseInfo = info;
      operation.open();
    }

    @Override
    public synchronized void onReadCompleted(
        UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
      if (request != currentUrlRequest) {
        return;
      }
      operation.open();
    }

    @Override
    public synchronized void onSucceeded(UrlRequest request, UrlResponseInfo info) {
      if (request != currentUrlRequest) {
        return;
      }
      finished = true;
      operation.open();
    }

    @Override
    public synchronized void onFailed(
        UrlRequest request, UrlResponseInfo info, CronetException error) {
      if (request != currentUrlRequest) {
        return;
      }
      if (error instanceof NetworkException
          && ((NetworkException) error).getErrorCode()
              == NetworkException.ERROR_HOSTNAME_NOT_RESOLVED) {
        exception = new UnknownHostException();
      } else {
        exception = error;
      }
      operation.open();
    }
  }
}
