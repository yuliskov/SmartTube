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

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DataSource.Factory;

/**
 * A {@link Factory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link DefaultHttpDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultDataSourceFactory implements Factory {

  private final Context context;
  private final @Nullable TransferListener listener;
  private final DataSource.Factory baseDataSourceFactory;

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   */
  public DefaultDataSourceFactory(Context context, String userAgent) {
    this(context, userAgent, /* listener= */ null);
  }

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   * @param listener An optional listener.
   */
  public DefaultDataSourceFactory(
      Context context, String userAgent, @Nullable TransferListener listener) {
    this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener));
  }

  /**
   * @param context A context.
   * @param baseDataSourceFactory A {@link Factory} to be used to create a base {@link DataSource}
   *     for {@link DefaultDataSource}.
   * @see DefaultDataSource#DefaultDataSource(Context, TransferListener, DataSource)
   */
  public DefaultDataSourceFactory(Context context, DataSource.Factory baseDataSourceFactory) {
    this(context, /* listener= */ null, baseDataSourceFactory);
  }

  /**
   * @param context A context.
   * @param listener An optional listener.
   * @param baseDataSourceFactory A {@link Factory} to be used to create a base {@link DataSource}
   *     for {@link DefaultDataSource}.
   * @see DefaultDataSource#DefaultDataSource(Context, TransferListener, DataSource)
   */
  public DefaultDataSourceFactory(
      Context context,
      @Nullable TransferListener listener,
      DataSource.Factory baseDataSourceFactory) {
    this.context = context.getApplicationContext();
    this.listener = listener;
    this.baseDataSourceFactory = baseDataSourceFactory;
  }

  @Override
  public DefaultDataSource createDataSource() {
    DefaultDataSource dataSource =
        new DefaultDataSource(context, baseDataSourceFactory.createDataSource());
    if (listener != null) {
      dataSource.addTransferListener(listener);
    }
    return dataSource;
  }
}
