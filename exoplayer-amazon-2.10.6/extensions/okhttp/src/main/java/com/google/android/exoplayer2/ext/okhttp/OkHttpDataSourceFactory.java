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
package com.google.android.exoplayer2.ext.okhttp;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.upstream.TransferListener;
import okhttp3.CacheControl;
import okhttp3.Call;

/**
 * A {@link Factory} that produces {@link OkHttpDataSource}.
 */
public final class OkHttpDataSourceFactory extends BaseFactory {

  private final Call.Factory callFactory;
  private final @Nullable String userAgent;
  private final @Nullable TransferListener listener;
  private final @Nullable CacheControl cacheControl;

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the sources created by the factory.
   * @param userAgent An optional User-Agent string.
   */
  public OkHttpDataSourceFactory(Call.Factory callFactory, @Nullable String userAgent) {
    this(callFactory, userAgent, /* listener= */ null, /* cacheControl= */ null);
  }

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the sources created by the factory.
   * @param userAgent An optional User-Agent string.
   * @param cacheControl An optional {@link CacheControl} for setting the Cache-Control header.
   */
  public OkHttpDataSourceFactory(
      Call.Factory callFactory, @Nullable String userAgent, @Nullable CacheControl cacheControl) {
    this(callFactory, userAgent, /* listener= */ null, cacheControl);
  }

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the sources created by the factory.
   * @param userAgent An optional User-Agent string.
   * @param listener An optional listener.
   */
  public OkHttpDataSourceFactory(
      Call.Factory callFactory, @Nullable String userAgent, @Nullable TransferListener listener) {
    this(callFactory, userAgent, listener, /* cacheControl= */ null);
  }

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the sources created by the factory.
   * @param userAgent An optional User-Agent string.
   * @param listener An optional listener.
   * @param cacheControl An optional {@link CacheControl} for setting the Cache-Control header.
   */
  public OkHttpDataSourceFactory(
      Call.Factory callFactory,
      @Nullable String userAgent,
      @Nullable TransferListener listener,
      @Nullable CacheControl cacheControl) {
    this.callFactory = callFactory;
    this.userAgent = userAgent;
    this.listener = listener;
    this.cacheControl = cacheControl;
  }

  @Override
  protected OkHttpDataSource createDataSourceInternal(
      HttpDataSource.RequestProperties defaultRequestProperties) {
    OkHttpDataSource dataSource =
        new OkHttpDataSource(
            callFactory,
            userAgent,
            /* contentTypePredicate= */ null,
            cacheControl,
            defaultRequestProperties);
    if (listener != null) {
      dataSource.addTransferListener(listener);
    }
    return dataSource;
  }
}
