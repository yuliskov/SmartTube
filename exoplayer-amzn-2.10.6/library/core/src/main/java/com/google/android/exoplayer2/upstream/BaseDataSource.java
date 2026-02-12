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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.Nullable;
import java.util.ArrayList;

/**
 * Base {@link DataSource} implementation to keep a list of {@link TransferListener}s.
 *
 * <p>Subclasses must call {@link #transferInitializing(DataSpec)}, {@link
 * #transferStarted(DataSpec)}, {@link #bytesTransferred(int)}, and {@link #transferEnded()} to
 * inform listeners of data transfers.
 */
public abstract class BaseDataSource implements DataSource {

  private final boolean isNetwork;
  private final ArrayList<TransferListener> listeners;

  private int listenerCount;
  private @Nullable DataSpec dataSpec;

  /**
   * Creates base data source.
   *
   * @param isNetwork Whether the data source loads data through a network.
   */
  protected BaseDataSource(boolean isNetwork) {
    this.isNetwork = isNetwork;
    this.listeners = new ArrayList<>(/* initialCapacity= */ 1);
  }

  @Override
  public final void addTransferListener(TransferListener transferListener) {
    if (!listeners.contains(transferListener)) {
      listeners.add(transferListener);
      listenerCount++;
    }
  }

  /**
   * Notifies listeners that data transfer for the specified {@link DataSpec} is being initialized.
   *
   * @param dataSpec {@link DataSpec} describing the data for initializing transfer.
   */
  protected final void transferInitializing(DataSpec dataSpec) {
    for (int i = 0; i < listenerCount; i++) {
      listeners.get(i).onTransferInitializing(/* source= */ this, dataSpec, isNetwork);
    }
  }

  /**
   * Notifies listeners that data transfer for the specified {@link DataSpec} started.
   *
   * @param dataSpec {@link DataSpec} describing the data being transferred.
   */
  protected final void transferStarted(DataSpec dataSpec) {
    this.dataSpec = dataSpec;
    for (int i = 0; i < listenerCount; i++) {
      listeners.get(i).onTransferStart(/* source= */ this, dataSpec, isNetwork);
    }
  }

  /**
   * Notifies listeners that bytes were transferred.
   *
   * @param bytesTransferred The number of bytes transferred since the previous call to this method
   *     (or if the first call, since the transfer was started).
   */
  protected final void bytesTransferred(int bytesTransferred) {
    DataSpec dataSpec = castNonNull(this.dataSpec);
    for (int i = 0; i < listenerCount; i++) {
      listeners
          .get(i)
          .onBytesTransferred(/* source= */ this, dataSpec, isNetwork, bytesTransferred);
    }
  }

  /** Notifies listeners that a transfer ended. */
  protected final void transferEnded() {
    DataSpec dataSpec = castNonNull(this.dataSpec);
    for (int i = 0; i < listenerCount; i++) {
      listeners.get(i).onTransferEnd(/* source= */ this, dataSpec, isNetwork);
    }
    this.dataSpec = null;
  }
}
