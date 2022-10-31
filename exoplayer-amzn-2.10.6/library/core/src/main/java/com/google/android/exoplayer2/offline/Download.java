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
package com.google.android.exoplayer2.offline;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents state of a download. */
public final class Download {

  /**
   * Download states. One of {@link #STATE_QUEUED}, {@link #STATE_STOPPED}, {@link
   * #STATE_DOWNLOADING}, {@link #STATE_COMPLETED}, {@link #STATE_FAILED}, {@link #STATE_REMOVING}
   * or {@link #STATE_RESTARTING}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_QUEUED,
    STATE_STOPPED,
    STATE_DOWNLOADING,
    STATE_COMPLETED,
    STATE_FAILED,
    STATE_REMOVING,
    STATE_RESTARTING
  })
  public @interface State {}
  // Important: These constants are persisted into DownloadIndex. Do not change them.
  /**
   * The download is waiting to be started. A download may be queued because the {@link
   * DownloadManager}
   *
   * <ul>
   *   <li>Is {@link DownloadManager#getDownloadsPaused() paused}
   *   <li>Has {@link DownloadManager#getRequirements() Requirements} that are not met
   *   <li>Has already started {@link DownloadManager#getMaxParallelDownloads()
   *       maxParallelDownloads}
   * </ul>
   */
  public static final int STATE_QUEUED = 0;
  /** The download is stopped for a specified {@link #stopReason}. */
  public static final int STATE_STOPPED = 1;
  /** The download is currently started. */
  public static final int STATE_DOWNLOADING = 2;
  /** The download completed. */
  public static final int STATE_COMPLETED = 3;
  /** The download failed. */
  public static final int STATE_FAILED = 4;
  /** The download is being removed. */
  public static final int STATE_REMOVING = 5;
  /** The download will restart after all downloaded data is removed. */
  public static final int STATE_RESTARTING = 7;

  /** Failure reasons. Either {@link #FAILURE_REASON_NONE} or {@link #FAILURE_REASON_UNKNOWN}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FAILURE_REASON_NONE, FAILURE_REASON_UNKNOWN})
  public @interface FailureReason {}
  /** The download isn't failed. */
  public static final int FAILURE_REASON_NONE = 0;
  /** The download is failed because of unknown reason. */
  public static final int FAILURE_REASON_UNKNOWN = 1;

  /** The download isn't stopped. */
  public static final int STOP_REASON_NONE = 0;

  /** The download request. */
  public final DownloadRequest request;
  /** The state of the download. */
  @State public final int state;
  /** The first time when download entry is created. */
  public final long startTimeMs;
  /** The last update time. */
  public final long updateTimeMs;
  /** The total size of the content in bytes, or {@link C#LENGTH_UNSET} if unknown. */
  public final long contentLength;
  /** The reason the download is stopped, or {@link #STOP_REASON_NONE}. */
  public final int stopReason;
  /**
   * If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise {@link
   * #FAILURE_REASON_NONE}.
   */
  @FailureReason public final int failureReason;

  /* package */ final DownloadProgress progress;

  public Download(
      DownloadRequest request,
      @State int state,
      long startTimeMs,
      long updateTimeMs,
      long contentLength,
      int stopReason,
      @FailureReason int failureReason) {
    this(
        request,
        state,
        startTimeMs,
        updateTimeMs,
        contentLength,
        stopReason,
        failureReason,
        new DownloadProgress());
  }

  public Download(
      DownloadRequest request,
      @State int state,
      long startTimeMs,
      long updateTimeMs,
      long contentLength,
      int stopReason,
      @FailureReason int failureReason,
      DownloadProgress progress) {
    Assertions.checkNotNull(progress);
    Assertions.checkState((failureReason == FAILURE_REASON_NONE) == (state != STATE_FAILED));
    if (stopReason != 0) {
      Assertions.checkState(state != STATE_DOWNLOADING && state != STATE_QUEUED);
    }
    this.request = request;
    this.state = state;
    this.startTimeMs = startTimeMs;
    this.updateTimeMs = updateTimeMs;
    this.contentLength = contentLength;
    this.stopReason = stopReason;
    this.failureReason = failureReason;
    this.progress = progress;
  }

  /** Returns whether the download is completed or failed. These are terminal states. */
  public boolean isTerminalState() {
    return state == STATE_COMPLETED || state == STATE_FAILED;
  }

  /** Returns the total number of downloaded bytes. */
  public long getBytesDownloaded() {
    return progress.bytesDownloaded;
  }

  /**
   * Returns the estimated download percentage, or {@link C#PERCENTAGE_UNSET} if no estimate is
   * available.
   */
  public float getPercentDownloaded() {
    return progress.percentDownloaded;
  }
}
