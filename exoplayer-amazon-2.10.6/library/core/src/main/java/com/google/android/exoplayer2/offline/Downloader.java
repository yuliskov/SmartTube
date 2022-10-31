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
package com.google.android.exoplayer2.offline;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;

/** Downloads and removes a piece of content. */
public interface Downloader {

  /** Receives progress updates during download operations. */
  interface ProgressListener {

    /**
     * Called when progress is made during a download operation.
     *
     * @param contentLength The length of the content in bytes, or {@link C#LENGTH_UNSET} if
     *     unknown.
     * @param bytesDownloaded The number of bytes that have been downloaded.
     * @param percentDownloaded The percentage of the content that has been downloaded, or {@link
     *     C#PERCENTAGE_UNSET}.
     */
    void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded);
  }

  /**
   * Downloads the content.
   *
   * @param progressListener A listener to receive progress updates, or {@code null}.
   * @throws DownloadException Thrown if the content cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws IOException Thrown when there is an io error while downloading.
   */
  void download(@Nullable ProgressListener progressListener)
      throws InterruptedException, IOException;

  /** Cancels the download operation and prevents future download operations from running. */
  void cancel();

  /**
   * Removes the content.
   *
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  void remove() throws InterruptedException;
}
