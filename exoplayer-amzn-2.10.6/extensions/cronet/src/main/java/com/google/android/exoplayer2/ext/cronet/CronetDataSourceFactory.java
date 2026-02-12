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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidContentTypeException;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Predicate;
import java.util.concurrent.Executor;
import org.chromium.net.CronetEngine;

/**
 * A {@link Factory} that produces {@link CronetDataSource}.
 */
public final class CronetDataSourceFactory extends BaseFactory {

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS =
      CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;

  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS =
      CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS;

  private final CronetEngineWrapper cronetEngineWrapper;
  private final Executor executor;
  private final Predicate<String> contentTypePredicate;
  private final @Nullable TransferListener transferListener;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final HttpDataSource.Factory fallbackFactory;

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, the provided
   * fallback {@link HttpDataSource.Factory} will be used instead.
   *
   * <p>Sets {@link CronetDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout,
   * {@link CronetDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
   * cross-protocol redirects.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param fallbackFactory A {@link HttpDataSource.Factory} which is used as a fallback in case no
   *     suitable CronetEngine can be build.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      HttpDataSource.Factory fallbackFactory) {
    this(
        cronetEngineWrapper,
        executor,
        contentTypePredicate,
        /* transferListener= */ null,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        false,
        fallbackFactory);
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, a {@link
   * DefaultHttpDataSourceFactory} will be used instead.
   *
   * <p>Sets {@link CronetDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout,
   * {@link CronetDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
   * cross-protocol redirects.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param userAgent A user agent used to create a fallback HttpDataSource if needed.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      String userAgent) {
    this(
        cronetEngineWrapper,
        executor,
        contentTypePredicate,
        /* transferListener= */ null,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        false,
        new DefaultHttpDataSourceFactory(
            userAgent,
            /* listener= */ null,
            DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DEFAULT_READ_TIMEOUT_MILLIS,
            false));
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, a {@link
   * DefaultHttpDataSourceFactory} will be used instead.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param userAgent A user agent used to create a fallback HttpDataSource if needed.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      String userAgent) {
    this(
        cronetEngineWrapper,
        executor,
        contentTypePredicate,
        /* transferListener= */ null,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        resetTimeoutOnRedirects,
        new DefaultHttpDataSourceFactory(
            userAgent,
            /* listener= */ null,
            connectTimeoutMs,
            readTimeoutMs,
            resetTimeoutOnRedirects));
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, the provided
   * fallback {@link HttpDataSource.Factory} will be used instead.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param fallbackFactory A {@link HttpDataSource.Factory} which is used as a fallback in case no
   *     suitable CronetEngine can be build.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      HttpDataSource.Factory fallbackFactory) {
    this(
        cronetEngineWrapper,
        executor,
        contentTypePredicate,
        /* transferListener= */ null,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        fallbackFactory);
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, the provided
   * fallback {@link HttpDataSource.Factory} will be used instead.
   *
   * <p>Sets {@link CronetDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout,
   * {@link CronetDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
   * cross-protocol redirects.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param transferListener An optional listener.
   * @param fallbackFactory A {@link HttpDataSource.Factory} which is used as a fallback in case no
   *     suitable CronetEngine can be build.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      @Nullable TransferListener transferListener,
      HttpDataSource.Factory fallbackFactory) {
    this(cronetEngineWrapper, executor, contentTypePredicate, transferListener,
        DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, false, fallbackFactory);
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, a {@link
   * DefaultHttpDataSourceFactory} will be used instead.
   *
   * <p>Sets {@link CronetDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout,
   * {@link CronetDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
   * cross-protocol redirects.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param transferListener An optional listener.
   * @param userAgent A user agent used to create a fallback HttpDataSource if needed.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      @Nullable TransferListener transferListener,
      String userAgent) {
    this(cronetEngineWrapper, executor, contentTypePredicate, transferListener,
        DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, false,
        new DefaultHttpDataSourceFactory(userAgent, transferListener,
            DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, false));
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, a {@link
   * DefaultHttpDataSourceFactory} will be used instead.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param transferListener An optional listener.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param userAgent A user agent used to create a fallback HttpDataSource if needed.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      @Nullable TransferListener transferListener,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      String userAgent) {
    this(cronetEngineWrapper, executor, contentTypePredicate, transferListener,
        DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, resetTimeoutOnRedirects,
        new DefaultHttpDataSourceFactory(userAgent, transferListener, connectTimeoutMs,
            readTimeoutMs, resetTimeoutOnRedirects));
  }

  /**
   * Constructs a CronetDataSourceFactory.
   *
   * <p>If the {@link CronetEngineWrapper} fails to provide a {@link CronetEngine}, the provided
   * fallback {@link HttpDataSource.Factory} will be used instead.
   *
   * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     CronetDataSource#open}.
   * @param transferListener An optional listener.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param fallbackFactory A {@link HttpDataSource.Factory} which is used as a fallback in case no
   *     suitable CronetEngine can be build.
   */
  public CronetDataSourceFactory(
      CronetEngineWrapper cronetEngineWrapper,
      Executor executor,
      Predicate<String> contentTypePredicate,
      @Nullable TransferListener transferListener,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      HttpDataSource.Factory fallbackFactory) {
    this.cronetEngineWrapper = cronetEngineWrapper;
    this.executor = executor;
    this.contentTypePredicate = contentTypePredicate;
    this.transferListener = transferListener;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.fallbackFactory = fallbackFactory;
  }

  @Override
  protected HttpDataSource createDataSourceInternal(HttpDataSource.RequestProperties
      defaultRequestProperties) {
    CronetEngine cronetEngine = cronetEngineWrapper.getCronetEngine();
    if (cronetEngine == null) {
      return fallbackFactory.createDataSource();
    }
    CronetDataSource dataSource =
        new CronetDataSource(
            cronetEngine,
            executor,
            contentTypePredicate,
            connectTimeoutMs,
            readTimeoutMs,
            resetTimeoutOnRedirects,
            defaultRequestProperties);
    if (transferListener != null) {
      dataSource.addTransferListener(transferListener);
    }
    return dataSource;
  }

}
