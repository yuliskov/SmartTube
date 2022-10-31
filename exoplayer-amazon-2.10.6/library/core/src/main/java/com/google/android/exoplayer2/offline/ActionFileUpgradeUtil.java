/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.android.exoplayer2.offline.Download.STATE_QUEUED;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.File;
import java.io.IOException;

/** Utility class for upgrading legacy action files into {@link DefaultDownloadIndex}. */
public final class ActionFileUpgradeUtil {

  /** Provides download IDs during action file upgrade. */
  public interface DownloadIdProvider {

    /**
     * Returns a download id for given request.
     *
     * @param downloadRequest The request for which an ID is required.
     * @return A corresponding download ID.
     */
    String getId(DownloadRequest downloadRequest);
  }

  private ActionFileUpgradeUtil() {}

  /**
   * Merges {@link DownloadRequest DownloadRequests} contained in a legacy action file into a {@link
   * DefaultDownloadIndex}, deleting the action file if the merge is successful or if {@code
   * deleteOnFailure} is {@code true}.
   *
   * <p>This method must not be called while the {@link DefaultDownloadIndex} is being used by a
   * {@link DownloadManager}.
   *
   * @param actionFilePath The action file path.
   * @param downloadIdProvider A download ID provider, or {@code null}. If {@code null} then ID of
   *     each download will be its custom cache key if one is specified, or else its URL.
   * @param downloadIndex The index into which the requests will be merged.
   * @param deleteOnFailure Whether to delete the action file if the merge fails.
   * @param addNewDownloadsAsCompleted Whether to add new downloads as completed.
   * @throws IOException If an error occurs loading or merging the requests.
   */
  @SuppressWarnings("deprecation")
  public static void upgradeAndDelete(
      File actionFilePath,
      @Nullable DownloadIdProvider downloadIdProvider,
      DefaultDownloadIndex downloadIndex,
      boolean deleteOnFailure,
      boolean addNewDownloadsAsCompleted)
      throws IOException {
    ActionFile actionFile = new ActionFile(actionFilePath);
    if (actionFile.exists()) {
      boolean success = false;
      try {
        long nowMs = System.currentTimeMillis();
        for (DownloadRequest request : actionFile.load()) {
          if (downloadIdProvider != null) {
            request = request.copyWithId(downloadIdProvider.getId(request));
          }
          mergeRequest(request, downloadIndex, addNewDownloadsAsCompleted, nowMs);
        }
        success = true;
      } finally {
        if (success || deleteOnFailure) {
          actionFile.delete();
        }
      }
    }
  }

  /**
   * Merges a {@link DownloadRequest} into a {@link DefaultDownloadIndex}.
   *
   * @param request The request to be merged.
   * @param downloadIndex The index into which the request will be merged.
   * @param addNewDownloadAsCompleted Whether to add new downloads as completed.
   * @throws IOException If an error occurs merging the request.
   */
  /* package */ static void mergeRequest(
      DownloadRequest request,
      DefaultDownloadIndex downloadIndex,
      boolean addNewDownloadAsCompleted,
      long nowMs)
      throws IOException {
    Download download = downloadIndex.getDownload(request.id);
    if (download != null) {
      download = DownloadManager.mergeRequest(download, request, download.stopReason, nowMs);
    } else {
      download =
          new Download(
              request,
              addNewDownloadAsCompleted ? Download.STATE_COMPLETED : STATE_QUEUED,
              /* startTimeMs= */ nowMs,
              /* updateTimeMs= */ nowMs,
              /* contentLength= */ C.LENGTH_UNSET,
              Download.STOP_REASON_NONE,
              Download.FAILURE_REASON_NONE);
    }
    downloadIndex.putDownload(download);
  }
}
