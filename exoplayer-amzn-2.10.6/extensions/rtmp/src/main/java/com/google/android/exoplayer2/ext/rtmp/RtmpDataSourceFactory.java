/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.rtmp;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * A {@link Factory} that produces {@link RtmpDataSource}.
 */
public final class RtmpDataSourceFactory implements DataSource.Factory {

  private final @Nullable TransferListener listener;

  public RtmpDataSourceFactory() {
    this(null);
  }

  /** @param listener An optional listener. */
  public RtmpDataSourceFactory(@Nullable TransferListener listener) {
    this.listener = listener;
  }

  @Override
  public RtmpDataSource createDataSource() {
    RtmpDataSource dataSource = new RtmpDataSource();
    if (listener != null) {
      dataSource.addTransferListener(listener);
    }
    return dataSource;
  }

}
