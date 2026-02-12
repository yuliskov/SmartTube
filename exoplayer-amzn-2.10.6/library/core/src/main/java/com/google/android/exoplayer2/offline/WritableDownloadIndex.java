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

import java.io.IOException;

/** A writable index of {@link Download Downloads}. */
public interface WritableDownloadIndex extends DownloadIndex {

  /**
   * Adds or replaces a {@link Download}.
   *
   * @param download The {@link Download} to be added.
   * @throws IOException If an error occurs setting the state.
   */
  void putDownload(Download download) throws IOException;

  /**
   * Removes the download with the given ID. Does nothing if a download with the given ID does not
   * exist.
   *
   * @param id The ID of the download to remove.
   * @throws IOException If an error occurs removing the state.
   */
  void removeDownload(String id) throws IOException;

  /**
   * Sets all {@link Download#STATE_DOWNLOADING} states to {@link Download#STATE_QUEUED}.
   *
   * @throws IOException If an error occurs updating the state.
   */
  void setDownloadingStatesToQueued() throws IOException;

  /**
   * Sets all states to {@link Download#STATE_REMOVING}.
   *
   * @throws IOException If an error occurs updating the state.
   */
  void setStatesToRemoving() throws IOException;

  /**
   * Sets the stop reason of the downloads in a terminal state ({@link Download#STATE_COMPLETED},
   * {@link Download#STATE_FAILED}).
   *
   * @param stopReason The stop reason.
   * @throws IOException If an error occurs updating the state.
   */
  void setStopReason(int stopReason) throws IOException;

  /**
   * Sets the stop reason of the download with the given ID in a terminal state ({@link
   * Download#STATE_COMPLETED}, {@link Download#STATE_FAILED}). Does nothing if a download with the
   * given ID does not exist, or if it's not in a terminal state.
   *
   * @param id The ID of the download to update.
   * @param stopReason The stop reason.
   * @throws IOException If an error occurs updating the state.
   */
  void setStopReason(String id, int stopReason) throws IOException;
}
