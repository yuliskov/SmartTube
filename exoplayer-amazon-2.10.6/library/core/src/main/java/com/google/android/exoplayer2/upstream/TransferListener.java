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

/**
 * A listener of data transfer events.
 *
 * <p>A transfer usually progresses through multiple steps:
 *
 * <ol>
 *   <li>Initializing the underlying resource (e.g. opening a HTTP connection). {@link
 *       #onTransferInitializing(DataSource, DataSpec, boolean)} is called before the initialization
 *       starts.
 *   <li>Starting the transfer after successfully initializing the resource. {@link
 *       #onTransferStart(DataSource, DataSpec, boolean)} is called. Note that this only happens if
 *       the initialization was successful.
 *   <li>Transferring data. {@link #onBytesTransferred(DataSource, DataSpec, boolean, int)} is
 *       called frequently during the transfer to indicate progress.
 *   <li>Closing the transfer and the underlying resource. {@link #onTransferEnd(DataSource,
 *       DataSpec, boolean)} is called. Note that each {@link #onTransferStart(DataSource, DataSpec,
 *       boolean)} will have exactly one corresponding call to {@link #onTransferEnd(DataSource,
 *       DataSpec, boolean)}.
 * </ol>
 */
public interface TransferListener {

  /**
   * Called when a transfer is being initialized.
   *
   * @param source The source performing the transfer.
   * @param dataSpec Describes the data for which the transfer is initialized.
   * @param isNetwork Whether the data is transferred through a network.
   */
  void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork);

  /**
   * Called when a transfer starts.
   *
   * @param source The source performing the transfer.
   * @param dataSpec Describes the data being transferred.
   * @param isNetwork Whether the data is transferred through a network.
   */
  void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork);

  /**
   * Called incrementally during a transfer.
   *
   * @param source The source performing the transfer.
   * @param dataSpec Describes the data being transferred.
   * @param isNetwork Whether the data is transferred through a network.
   * @param bytesTransferred The number of bytes transferred since the previous call to this method
   */
  void onBytesTransferred(
      DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred);

  /**
   * Called when a transfer ends.
   *
   * @param source The source performing the transfer.
   * @param dataSpec Describes the data being transferred.
   * @param isNetwork Whether the data is transferred through a network.
   */
  void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork);
}
