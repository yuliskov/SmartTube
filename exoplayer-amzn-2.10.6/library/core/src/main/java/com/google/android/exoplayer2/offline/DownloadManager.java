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

import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.Download.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.Download.STATE_DOWNLOADING;
import static com.google.android.exoplayer2.offline.Download.STATE_FAILED;
import static com.google.android.exoplayer2.offline.Download.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.Download.STATE_REMOVING;
import static com.google.android.exoplayer2.offline.Download.STATE_RESTARTING;
import static com.google.android.exoplayer2.offline.Download.STATE_STOPPED;
import static com.google.android.exoplayer2.offline.Download.STOP_REASON_NONE;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheEvictor;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages downloads.
 *
 * <p>Normally a download manager should be accessed via a {@link DownloadService}. When a download
 * manager is used directly instead, downloads will be initially paused and so must be resumed by
 * calling {@link #resumeDownloads()}.
 *
 * <p>A download manager instance must be accessed only from the thread that created it, unless that
 * thread does not have a {@link Looper}. In that case, it must be accessed only from the
 * application's main thread. Registered listeners will be called on the same thread.
 */
public final class DownloadManager {

  /** Listener for {@link DownloadManager} events. */
  public interface Listener {

    /**
     * Called when all downloads have been restored.
     *
     * @param downloadManager The reporting instance.
     */
    default void onInitialized(DownloadManager downloadManager) {}

    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param download The state of the download.
     */
    default void onDownloadChanged(DownloadManager downloadManager, Download download) {}

    /**
     * Called when a download is removed.
     *
     * @param downloadManager The reporting instance.
     * @param download The last state of the download before it was removed.
     */
    default void onDownloadRemoved(DownloadManager downloadManager, Download download) {}

    /**
     * Called when there is no active download left.
     *
     * @param downloadManager The reporting instance.
     */
    default void onIdle(DownloadManager downloadManager) {}

    /**
     * Called when the download requirements state changed.
     *
     * @param downloadManager The reporting instance.
     * @param requirements Requirements needed to be met to start downloads.
     * @param notMetRequirements {@link Requirements.RequirementFlags RequirementFlags} that are not
     *     met, or 0.
     */
    default void onRequirementsStateChanged(
        DownloadManager downloadManager,
        Requirements requirements,
        @Requirements.RequirementFlags int notMetRequirements) {}
  }

  /** The default maximum number of parallel downloads. */
  public static final int DEFAULT_MAX_PARALLEL_DOWNLOADS = 3;
  /** The default minimum number of times a download must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;
  /** The default requirement is that the device has network connectivity. */
  public static final Requirements DEFAULT_REQUIREMENTS = new Requirements(Requirements.NETWORK);

  // Messages posted to the main handler.
  private static final int MSG_INITIALIZED = 0;
  private static final int MSG_PROCESSED = 1;
  private static final int MSG_DOWNLOAD_UPDATE = 2;

  // Messages posted to the background handler.
  private static final int MSG_INITIALIZE = 0;
  private static final int MSG_SET_DOWNLOADS_PAUSED = 1;
  private static final int MSG_SET_NOT_MET_REQUIREMENTS = 2;
  private static final int MSG_SET_STOP_REASON = 3;
  private static final int MSG_SET_MAX_PARALLEL_DOWNLOADS = 4;
  private static final int MSG_SET_MIN_RETRY_COUNT = 5;
  private static final int MSG_ADD_DOWNLOAD = 6;
  private static final int MSG_REMOVE_DOWNLOAD = 7;
  private static final int MSG_REMOVE_ALL_DOWNLOADS = 8;
  private static final int MSG_TASK_STOPPED = 9;
  private static final int MSG_CONTENT_LENGTH_CHANGED = 10;
  private static final int MSG_UPDATE_PROGRESS = 11;
  private static final int MSG_RELEASE = 12;

  private static final String TAG = "DownloadManager";

  private final Context context;
  private final WritableDownloadIndex downloadIndex;
  private final Handler mainHandler;
  private final InternalHandler internalHandler;
  private final RequirementsWatcher.Listener requirementsListener;
  private final CopyOnWriteArraySet<Listener> listeners;

  private int pendingMessages;
  private int activeTaskCount;
  private boolean initialized;
  private boolean downloadsPaused;
  private int maxParallelDownloads;
  private int minRetryCount;
  private int notMetRequirements;
  private List<Download> downloads;
  private RequirementsWatcher requirementsWatcher;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   * @param cache A cache to be used to store downloaded data. The cache should be configured with
   *     an {@link CacheEvictor} that will not evict downloaded content, for example {@link
   *     NoOpCacheEvictor}.
   * @param upstreamFactory A {@link Factory} for creating {@link DataSource}s for downloading data.
   */
  public DownloadManager(
      Context context, DatabaseProvider databaseProvider, Cache cache, Factory upstreamFactory) {
    this(
        context,
        new DefaultDownloadIndex(databaseProvider),
        new DefaultDownloaderFactory(new DownloaderConstructorHelper(cache, upstreamFactory)));
  }

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param downloadIndex The download index used to hold the download information.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   */
  public DownloadManager(
      Context context, WritableDownloadIndex downloadIndex, DownloaderFactory downloaderFactory) {
    this.context = context.getApplicationContext();
    this.downloadIndex = downloadIndex;

    maxParallelDownloads = DEFAULT_MAX_PARALLEL_DOWNLOADS;
    minRetryCount = DEFAULT_MIN_RETRY_COUNT;
    downloadsPaused = true;
    downloads = Collections.emptyList();
    listeners = new CopyOnWriteArraySet<>();

    @SuppressWarnings("methodref.receiver.bound.invalid")
    Handler mainHandler = Util.createHandler(this::handleMainMessage);
    this.mainHandler = mainHandler;
    HandlerThread internalThread = new HandlerThread("DownloadManager file i/o");
    internalThread.start();
    internalHandler =
        new InternalHandler(
            internalThread,
            downloadIndex,
            downloaderFactory,
            mainHandler,
            maxParallelDownloads,
            minRetryCount,
            downloadsPaused);

    @SuppressWarnings("methodref.receiver.bound.invalid")
    RequirementsWatcher.Listener requirementsListener = this::onRequirementsStateChanged;
    this.requirementsListener = requirementsListener;
    requirementsWatcher =
        new RequirementsWatcher(context, requirementsListener, DEFAULT_REQUIREMENTS);
    notMetRequirements = requirementsWatcher.start();

    pendingMessages = 1;
    internalHandler
        .obtainMessage(MSG_INITIALIZE, notMetRequirements, /* unused */ 0)
        .sendToTarget();
  }

  /** Returns whether the manager has completed initialization. */
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Returns whether the manager is currently idle. The manager is idle if all downloads are in a
   * terminal state (i.e. completed or failed), or if no progress can be made (e.g. because the
   * download requirements are not met).
   */
  public boolean isIdle() {
    return activeTaskCount == 0 && pendingMessages == 0;
  }

  /**
   * Returns whether this manager has one or more downloads that are not progressing for the sole
   * reason that the {@link #getRequirements() Requirements} are not met.
   */
  public boolean isWaitingForRequirements() {
    if (!downloadsPaused && notMetRequirements != 0) {
      for (int i = 0; i < downloads.size(); i++) {
        if (downloads.get(i).state == STATE_QUEUED) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Adds a {@link Listener}.
   *
   * @param listener The listener to be added.
   */
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a {@link Listener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Returns the requirements needed to be met to progress. */
  public Requirements getRequirements() {
    return requirementsWatcher.getRequirements();
  }

  /**
   * Returns the requirements needed for downloads to progress that are not currently met.
   *
   * @return The not met {@link Requirements.RequirementFlags}, or 0 if all requirements are met.
   */
  @Requirements.RequirementFlags
  public int getNotMetRequirements() {
    return getRequirements().getNotMetRequirements(context);
  }

  /**
   * Sets the requirements that need to be met for downloads to progress.
   *
   * @param requirements A {@link Requirements}.
   */
  public void setRequirements(Requirements requirements) {
    if (requirements.equals(requirementsWatcher.getRequirements())) {
      return;
    }
    requirementsWatcher.stop();
    requirementsWatcher = new RequirementsWatcher(context, requirementsListener, requirements);
    int notMetRequirements = requirementsWatcher.start();
    onRequirementsStateChanged(requirementsWatcher, notMetRequirements);
  }

  /** Returns the maximum number of parallel downloads. */
  public int getMaxParallelDownloads() {
    return maxParallelDownloads;
  }

  /**
   * Sets the maximum number of parallel downloads.
   *
   * @param maxParallelDownloads The maximum number of parallel downloads. Must be greater than 0.
   */
  public void setMaxParallelDownloads(int maxParallelDownloads) {
    Assertions.checkArgument(maxParallelDownloads > 0);
    if (this.maxParallelDownloads == maxParallelDownloads) {
      return;
    }
    this.maxParallelDownloads = maxParallelDownloads;
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_MAX_PARALLEL_DOWNLOADS, maxParallelDownloads, /* unused */ 0)
        .sendToTarget();
  }

  /**
   * Returns the minimum number of times that a download will be retried. A download will fail if
   * the specified number of retries is exceeded without any progress being made.
   */
  public int getMinRetryCount() {
    return minRetryCount;
  }

  /**
   * Sets the minimum number of times that a download will be retried. A download will fail if the
   * specified number of retries is exceeded without any progress being made.
   *
   * @param minRetryCount The minimum number of times that a download will be retried.
   */
  public void setMinRetryCount(int minRetryCount) {
    Assertions.checkArgument(minRetryCount >= 0);
    if (this.minRetryCount == minRetryCount) {
      return;
    }
    this.minRetryCount = minRetryCount;
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_MIN_RETRY_COUNT, minRetryCount, /* unused */ 0)
        .sendToTarget();
  }

  /** Returns the used {@link DownloadIndex}. */
  public DownloadIndex getDownloadIndex() {
    return downloadIndex;
  }

  /**
   * Returns current downloads. Downloads that are in terminal states (i.e. completed or failed) are
   * not included. To query all downloads including those in terminal states, use {@link
   * #getDownloadIndex()} instead.
   */
  public List<Download> getCurrentDownloads() {
    return downloads;
  }

  /** Returns whether downloads are currently paused. */
  public boolean getDownloadsPaused() {
    return downloadsPaused;
  }

  /**
   * Resumes downloads.
   *
   * <p>If the {@link #setRequirements(Requirements) Requirements} are met up to {@link
   * #getMaxParallelDownloads() maxParallelDownloads} will be started, excluding those with non-zero
   * {@link Download#stopReason stopReasons}.
   */
  public void resumeDownloads() {
    if (!downloadsPaused) {
      return;
    }
    downloadsPaused = false;
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_DOWNLOADS_PAUSED, /* downloadsPaused */ 0, /* unused */ 0)
        .sendToTarget();
  }

  /**
   * Pauses downloads. Downloads that would otherwise be making progress transition to {@link
   * Download#STATE_QUEUED}.
   */
  public void pauseDownloads() {
    if (downloadsPaused) {
      return;
    }
    downloadsPaused = true;
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_DOWNLOADS_PAUSED, /* downloadsPaused */ 1, /* unused */ 0)
        .sendToTarget();
  }

  /**
   * Sets the stop reason for one or all downloads. To clear the stop reason, pass {@link
   * Download#STOP_REASON_NONE}.
   *
   * @param id The content id of the download to update, or {@code null} to set the stop reason for
   *     all downloads.
   * @param stopReason The stop reason, or {@link Download#STOP_REASON_NONE}.
   */
  public void setStopReason(@Nullable String id, int stopReason) {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_STOP_REASON, stopReason, /* unused */ 0, id)
        .sendToTarget();
  }

  /**
   * Adds a download defined by the given request.
   *
   * @param request The download request.
   */
  public void addDownload(DownloadRequest request) {
    addDownload(request, STOP_REASON_NONE);
  }

  /**
   * Adds a download defined by the given request and with the specified stop reason.
   *
   * @param request The download request.
   * @param stopReason An initial stop reason for the download, or {@link Download#STOP_REASON_NONE}
   *     if the download should be started.
   */
  public void addDownload(DownloadRequest request, int stopReason) {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_ADD_DOWNLOAD, stopReason, /* unused */ 0, request)
        .sendToTarget();
  }

  /**
   * Cancels the download with the {@code id} and removes all downloaded data.
   *
   * @param id The unique content id of the download to be started.
   */
  public void removeDownload(String id) {
    pendingMessages++;
    internalHandler.obtainMessage(MSG_REMOVE_DOWNLOAD, id).sendToTarget();
  }

  /** Cancels all pending downloads and removes all downloaded data. */
  public void removeAllDownloads() {
    pendingMessages++;
    internalHandler.obtainMessage(MSG_REMOVE_ALL_DOWNLOADS).sendToTarget();
  }

  /**
   * Stops the downloads and releases resources. Waits until the downloads are persisted to the
   * download index. The manager must not be accessed after this method has been called.
   */
  public void release() {
    synchronized (internalHandler) {
      if (internalHandler.released) {
        return;
      }
      internalHandler.sendEmptyMessage(MSG_RELEASE);
      boolean wasInterrupted = false;
      while (!internalHandler.released) {
        try {
          internalHandler.wait();
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
      if (wasInterrupted) {
        // Restore the interrupted status.
        Thread.currentThread().interrupt();
      }
      mainHandler.removeCallbacksAndMessages(/* token= */ null);
      // Reset state.
      downloads = Collections.emptyList();
      pendingMessages = 0;
      activeTaskCount = 0;
      initialized = false;
    }
  }

  private void onRequirementsStateChanged(
      RequirementsWatcher requirementsWatcher,
      @Requirements.RequirementFlags int notMetRequirements) {
    Requirements requirements = requirementsWatcher.getRequirements();
    for (Listener listener : listeners) {
      listener.onRequirementsStateChanged(this, requirements, notMetRequirements);
    }
    if (this.notMetRequirements == notMetRequirements) {
      return;
    }
    this.notMetRequirements = notMetRequirements;
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_NOT_MET_REQUIREMENTS, notMetRequirements, /* unused */ 0)
        .sendToTarget();
  }

  // Main thread message handling.

  @SuppressWarnings("unchecked")
  private boolean handleMainMessage(Message message) {
    switch (message.what) {
      case MSG_INITIALIZED:
        List<Download> downloads = (List<Download>) message.obj;
        onInitialized(downloads);
        break;
      case MSG_DOWNLOAD_UPDATE:
        DownloadUpdate update = (DownloadUpdate) message.obj;
        onDownloadUpdate(update);
        break;
      case MSG_PROCESSED:
        int processedMessageCount = message.arg1;
        int activeTaskCount = message.arg2;
        onMessageProcessed(processedMessageCount, activeTaskCount);
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }

  private void onInitialized(List<Download> downloads) {
    initialized = true;
    this.downloads = Collections.unmodifiableList(downloads);
    for (Listener listener : listeners) {
      listener.onInitialized(DownloadManager.this);
    }
  }

  private void onDownloadUpdate(DownloadUpdate update) {
    downloads = Collections.unmodifiableList(update.downloads);
    Download updatedDownload = update.download;
    if (update.isRemove) {
      for (Listener listener : listeners) {
        listener.onDownloadRemoved(this, updatedDownload);
      }
    } else {
      for (Listener listener : listeners) {
        listener.onDownloadChanged(this, updatedDownload);
      }
    }
  }

  private void onMessageProcessed(int processedMessageCount, int activeTaskCount) {
    this.pendingMessages -= processedMessageCount;
    this.activeTaskCount = activeTaskCount;
    if (isIdle()) {
      for (Listener listener : listeners) {
        listener.onIdle(this);
      }
    }
  }

  /* package */ static Download mergeRequest(
      Download download, DownloadRequest request, int stopReason, long nowMs) {
    @Download.State int state = download.state;
    // Treat the merge as creating a new download if we're currently removing the existing one, or
    // if the existing download is in a terminal state. Else treat the merge as updating the
    // existing download.
    long startTimeMs =
        state == STATE_REMOVING || download.isTerminalState() ? nowMs : download.startTimeMs;
    if (state == STATE_REMOVING || state == STATE_RESTARTING) {
      state = STATE_RESTARTING;
    } else if (stopReason != STOP_REASON_NONE) {
      state = STATE_STOPPED;
    } else {
      state = STATE_QUEUED;
    }
    return new Download(
        download.request.copyWithMergedRequest(request),
        state,
        startTimeMs,
        /* updateTimeMs= */ nowMs,
        /* contentLength= */ C.LENGTH_UNSET,
        stopReason,
        FAILURE_REASON_NONE);
  }

  private static final class InternalHandler extends Handler {

    private static final int UPDATE_PROGRESS_INTERVAL_MS = 5000;

    public boolean released;

    private final HandlerThread thread;
    private final WritableDownloadIndex downloadIndex;
    private final DownloaderFactory downloaderFactory;
    private final Handler mainHandler;
    private final ArrayList<Download> downloads;
    private final HashMap<String, Task> activeTasks;

    @Requirements.RequirementFlags private int notMetRequirements;
    private boolean downloadsPaused;
    private int maxParallelDownloads;
    private int minRetryCount;
    private int activeDownloadTaskCount;

    public InternalHandler(
        HandlerThread thread,
        WritableDownloadIndex downloadIndex,
        DownloaderFactory downloaderFactory,
        Handler mainHandler,
        int maxParallelDownloads,
        int minRetryCount,
        boolean downloadsPaused) {
      super(thread.getLooper());
      this.thread = thread;
      this.downloadIndex = downloadIndex;
      this.downloaderFactory = downloaderFactory;
      this.mainHandler = mainHandler;
      this.maxParallelDownloads = maxParallelDownloads;
      this.minRetryCount = minRetryCount;
      this.downloadsPaused = downloadsPaused;
      downloads = new ArrayList<>();
      activeTasks = new HashMap<>();
    }

    @Override
    public void handleMessage(Message message) {
      boolean processedExternalMessage = true;
      switch (message.what) {
        case MSG_INITIALIZE:
          int notMetRequirements = message.arg1;
          initialize(notMetRequirements);
          break;
        case MSG_SET_DOWNLOADS_PAUSED:
          boolean downloadsPaused = message.arg1 != 0;
          setDownloadsPaused(downloadsPaused);
          break;
        case MSG_SET_NOT_MET_REQUIREMENTS:
          notMetRequirements = message.arg1;
          setNotMetRequirements(notMetRequirements);
          break;
        case MSG_SET_STOP_REASON:
          String id = (String) message.obj;
          int stopReason = message.arg1;
          setStopReason(id, stopReason);
          break;
        case MSG_SET_MAX_PARALLEL_DOWNLOADS:
          int maxParallelDownloads = message.arg1;
          setMaxParallelDownloads(maxParallelDownloads);
          break;
        case MSG_SET_MIN_RETRY_COUNT:
          int minRetryCount = message.arg1;
          setMinRetryCount(minRetryCount);
          break;
        case MSG_ADD_DOWNLOAD:
          DownloadRequest request = (DownloadRequest) message.obj;
          stopReason = message.arg1;
          addDownload(request, stopReason);
          break;
        case MSG_REMOVE_DOWNLOAD:
          id = (String) message.obj;
          removeDownload(id);
          break;
        case MSG_REMOVE_ALL_DOWNLOADS:
          removeAllDownloads();
          break;
        case MSG_TASK_STOPPED:
          Task task = (Task) message.obj;
          onTaskStopped(task);
          processedExternalMessage = false; // This message is posted internally.
          break;
        case MSG_CONTENT_LENGTH_CHANGED:
          task = (Task) message.obj;
          onContentLengthChanged(task);
          return; // No need to post back to mainHandler.
        case MSG_UPDATE_PROGRESS:
          updateProgress();
          return; // No need to post back to mainHandler.
        case MSG_RELEASE:
          release();
          return; // No need to post back to mainHandler.
        default:
          throw new IllegalStateException();
      }
      mainHandler
          .obtainMessage(MSG_PROCESSED, processedExternalMessage ? 1 : 0, activeTasks.size())
          .sendToTarget();
    }

    private void initialize(int notMetRequirements) {
      this.notMetRequirements = notMetRequirements;
      DownloadCursor cursor = null;
      try {
        downloadIndex.setDownloadingStatesToQueued();
        cursor =
            downloadIndex.getDownloads(
                STATE_QUEUED, STATE_STOPPED, STATE_DOWNLOADING, STATE_REMOVING, STATE_RESTARTING);
        while (cursor.moveToNext()) {
          downloads.add(cursor.getDownload());
        }
      } catch (IOException e) {
        Log.e(TAG, "Failed to load index.", e);
        downloads.clear();
      } finally {
        Util.closeQuietly(cursor);
      }
      // A copy must be used for the message to ensure that subsequent changes to the downloads list
      // are not visible to the main thread when it processes the message.
      ArrayList<Download> downloadsForMessage = new ArrayList<>(downloads);
      mainHandler.obtainMessage(MSG_INITIALIZED, downloadsForMessage).sendToTarget();
      syncTasks();
    }

    private void setDownloadsPaused(boolean downloadsPaused) {
      this.downloadsPaused = downloadsPaused;
      syncTasks();
    }

    private void setNotMetRequirements(@Requirements.RequirementFlags int notMetRequirements) {
      this.notMetRequirements = notMetRequirements;
      syncTasks();
    }

    private void setStopReason(@Nullable String id, int stopReason) {
      if (id == null) {
        for (int i = 0; i < downloads.size(); i++) {
          setStopReason(downloads.get(i), stopReason);
        }
        try {
          // Set the stop reason for downloads in terminal states as well.
          downloadIndex.setStopReason(stopReason);
        } catch (IOException e) {
          Log.e(TAG, "Failed to set manual stop reason", e);
        }
      } else {
        Download download = getDownload(id, /* loadFromIndex= */ false);
        if (download != null) {
          setStopReason(download, stopReason);
        } else {
          try {
            // Set the stop reason if the download is in a terminal state.
            downloadIndex.setStopReason(id, stopReason);
          } catch (IOException e) {
            Log.e(TAG, "Failed to set manual stop reason: " + id, e);
          }
        }
      }
      syncTasks();
    }

    private void setStopReason(Download download, int stopReason) {
      if (stopReason == STOP_REASON_NONE) {
        if (download.state == STATE_STOPPED) {
          putDownloadWithState(download, STATE_QUEUED);
        }
      } else if (stopReason != download.stopReason) {
        @Download.State int state = download.state;
        if (state == STATE_QUEUED || state == STATE_DOWNLOADING) {
          state = STATE_STOPPED;
        }
        putDownload(
            new Download(
                download.request,
                state,
                download.startTimeMs,
                /* updateTimeMs= */ System.currentTimeMillis(),
                download.contentLength,
                stopReason,
                FAILURE_REASON_NONE,
                download.progress));
      }
    }

    private void setMaxParallelDownloads(int maxParallelDownloads) {
      this.maxParallelDownloads = maxParallelDownloads;
      syncTasks();
    }

    private void setMinRetryCount(int minRetryCount) {
      this.minRetryCount = minRetryCount;
    }

    private void addDownload(DownloadRequest request, int stopReason) {
      Download download = getDownload(request.id, /* loadFromIndex= */ true);
      long nowMs = System.currentTimeMillis();
      if (download != null) {
        putDownload(mergeRequest(download, request, stopReason, nowMs));
      } else {
        putDownload(
            new Download(
                request,
                stopReason != STOP_REASON_NONE ? STATE_STOPPED : STATE_QUEUED,
                /* startTimeMs= */ nowMs,
                /* updateTimeMs= */ nowMs,
                /* contentLength= */ C.LENGTH_UNSET,
                stopReason,
                FAILURE_REASON_NONE));
      }
      syncTasks();
    }

    private void removeDownload(String id) {
      Download download = getDownload(id, /* loadFromIndex= */ true);
      if (download == null) {
        Log.e(TAG, "Failed to remove nonexistent download: " + id);
        return;
      }
      putDownloadWithState(download, STATE_REMOVING);
      syncTasks();
    }

    private void removeAllDownloads() {
      List<Download> terminalDownloads = new ArrayList<>();
      try (DownloadCursor cursor = downloadIndex.getDownloads(STATE_COMPLETED, STATE_FAILED)) {
        while (cursor.moveToNext()) {
          terminalDownloads.add(cursor.getDownload());
        }
      } catch (IOException e) {
        Log.e(TAG, "Failed to load downloads.");
      }
      for (int i = 0; i < downloads.size(); i++) {
        downloads.set(i, copyDownloadWithState(downloads.get(i), STATE_REMOVING));
      }
      for (int i = 0; i < terminalDownloads.size(); i++) {
        downloads.add(copyDownloadWithState(terminalDownloads.get(i), STATE_REMOVING));
      }
      Collections.sort(downloads, InternalHandler::compareStartTimes);
      try {
        downloadIndex.setStatesToRemoving();
      } catch (IOException e) {
        Log.e(TAG, "Failed to update index.", e);
      }
      ArrayList<Download> updateList = new ArrayList<>(downloads);
      for (int i = 0; i < downloads.size(); i++) {
        DownloadUpdate update =
            new DownloadUpdate(downloads.get(i), /* isRemove= */ false, updateList);
        mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget();
      }
      syncTasks();
    }

    private void release() {
      for (Task task : activeTasks.values()) {
        task.cancel(/* released= */ true);
      }
      try {
        downloadIndex.setDownloadingStatesToQueued();
      } catch (IOException e) {
        Log.e(TAG, "Failed to update index.", e);
      }
      downloads.clear();
      thread.quit();
      synchronized (this) {
        released = true;
        notifyAll();
      }
    }

    // Start and cancel tasks based on the current download and manager states.

    private void syncTasks() {
      int accumulatingDownloadTaskCount = 0;
      for (int i = 0; i < downloads.size(); i++) {
        Download download = downloads.get(i);
        Task activeTask = activeTasks.get(download.request.id);
        switch (download.state) {
          case STATE_STOPPED:
            syncStoppedDownload(activeTask);
            break;
          case STATE_QUEUED:
            activeTask = syncQueuedDownload(activeTask, download);
            break;
          case STATE_DOWNLOADING:
            Assertions.checkNotNull(activeTask);
            syncDownloadingDownload(activeTask, download, accumulatingDownloadTaskCount);
            break;
          case STATE_REMOVING:
          case STATE_RESTARTING:
            syncRemovingDownload(activeTask, download);
            break;
          case STATE_COMPLETED:
          case STATE_FAILED:
          default:
            throw new IllegalStateException();
        }
        if (activeTask != null && !activeTask.isRemove) {
          accumulatingDownloadTaskCount++;
        }
      }
    }

    private void syncStoppedDownload(@Nullable Task activeTask) {
      if (activeTask != null) {
        // We have a task, which must be a download task. Cancel it.
        Assertions.checkState(!activeTask.isRemove);
        activeTask.cancel(/* released= */ false);
      }
    }

    @Nullable
    @CheckResult
    private Task syncQueuedDownload(@Nullable Task activeTask, Download download) {
      if (activeTask != null) {
        // We have a task, which must be a download task. If the download state is queued we need to
        // cancel it and start a new one, since a new request has been merged into the download.
        Assertions.checkState(!activeTask.isRemove);
        activeTask.cancel(/* released= */ false);
        return activeTask;
      }

      if (!canDownloadsRun() || activeDownloadTaskCount >= maxParallelDownloads) {
        return null;
      }

      // We can start a download task.
      download = putDownloadWithState(download, STATE_DOWNLOADING);
      Downloader downloader = downloaderFactory.createDownloader(download.request);
      activeTask =
          new Task(
              download.request,
              downloader,
              download.progress,
              /* isRemove= */ false,
              minRetryCount,
              /* internalHandler= */ this);
      activeTasks.put(download.request.id, activeTask);
      if (activeDownloadTaskCount++ == 0) {
        sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, UPDATE_PROGRESS_INTERVAL_MS);
      }
      activeTask.start();
      return activeTask;
    }

    private void syncDownloadingDownload(
        Task activeTask, Download download, int accumulatingDownloadTaskCount) {
      Assertions.checkState(!activeTask.isRemove);
      if (!canDownloadsRun() || accumulatingDownloadTaskCount >= maxParallelDownloads) {
        putDownloadWithState(download, STATE_QUEUED);
        activeTask.cancel(/* released= */ false);
      }
    }

    private void syncRemovingDownload(@Nullable Task activeTask, Download download) {
      if (activeTask != null) {
        if (!activeTask.isRemove) {
          // Cancel the downloading task.
          activeTask.cancel(/* released= */ false);
        }
        // The activeTask is either a remove task, or a downloading task that we just cancelled. In
        // the latter case we need to wait for the task to stop before we start a remove task.
        return;
      }

      // We can start a remove task.
      Downloader downloader = downloaderFactory.createDownloader(download.request);
      activeTask =
          new Task(
              download.request,
              downloader,
              download.progress,
              /* isRemove= */ true,
              minRetryCount,
              /* internalHandler= */ this);
      activeTasks.put(download.request.id, activeTask);
      activeTask.start();
    }

    // Task event processing.

    private void onContentLengthChanged(Task task) {
      String downloadId = task.request.id;
      long contentLength = task.contentLength;
      Download download =
          Assertions.checkNotNull(getDownload(downloadId, /* loadFromIndex= */ false));
      if (contentLength == download.contentLength || contentLength == C.LENGTH_UNSET) {
        return;
      }
      putDownload(
          new Download(
              download.request,
              download.state,
              download.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              contentLength,
              download.stopReason,
              download.failureReason,
              download.progress));
    }

    private void onTaskStopped(Task task) {
      String downloadId = task.request.id;
      activeTasks.remove(downloadId);

      boolean isRemove = task.isRemove;
      if (!isRemove && --activeDownloadTaskCount == 0) {
        removeMessages(MSG_UPDATE_PROGRESS);
      }

      if (task.isCanceled) {
        syncTasks();
        return;
      }

      Throwable finalError = task.finalError;
      if (finalError != null) {
        Log.e(TAG, "Task failed: " + task.request + ", " + isRemove, finalError);
      }

      Download download =
          Assertions.checkNotNull(getDownload(downloadId, /* loadFromIndex= */ false));
      switch (download.state) {
        case STATE_DOWNLOADING:
          Assertions.checkState(!isRemove);
          onDownloadTaskStopped(download, finalError);
          break;
        case STATE_REMOVING:
        case STATE_RESTARTING:
          Assertions.checkState(isRemove);
          onRemoveTaskStopped(download);
          break;
        case STATE_QUEUED:
        case STATE_STOPPED:
        case STATE_COMPLETED:
        case STATE_FAILED:
        default:
          throw new IllegalStateException();
      }

      syncTasks();
    }

    private void onDownloadTaskStopped(Download download, @Nullable Throwable finalError) {
      download =
          new Download(
              download.request,
              finalError == null ? STATE_COMPLETED : STATE_FAILED,
              download.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              download.contentLength,
              download.stopReason,
              finalError == null ? FAILURE_REASON_NONE : FAILURE_REASON_UNKNOWN,
              download.progress);
      // The download is now in a terminal state, so should not be in the downloads list.
      downloads.remove(getDownloadIndex(download.request.id));
      // We still need to update the download index and main thread.
      try {
        downloadIndex.putDownload(download);
      } catch (IOException e) {
        Log.e(TAG, "Failed to update index.", e);
      }
      DownloadUpdate update =
          new DownloadUpdate(download, /* isRemove= */ false, new ArrayList<>(downloads));
      mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget();
    }

    private void onRemoveTaskStopped(Download download) {
      if (download.state == STATE_RESTARTING) {
        putDownloadWithState(
            download, download.stopReason == STOP_REASON_NONE ? STATE_QUEUED : STATE_STOPPED);
        syncTasks();
      } else {
        int removeIndex = getDownloadIndex(download.request.id);
        downloads.remove(removeIndex);
        try {
          downloadIndex.removeDownload(download.request.id);
        } catch (IOException e) {
          Log.e(TAG, "Failed to remove from database");
        }
        DownloadUpdate update =
            new DownloadUpdate(download, /* isRemove= */ true, new ArrayList<>(downloads));
        mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget();
      }
    }

    // Progress updates.

    private void updateProgress() {
      for (int i = 0; i < downloads.size(); i++) {
        Download download = downloads.get(i);
        if (download.state == STATE_DOWNLOADING) {
          try {
            downloadIndex.putDownload(download);
          } catch (IOException e) {
            Log.e(TAG, "Failed to update index.", e);
          }
        }
      }
      sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, UPDATE_PROGRESS_INTERVAL_MS);
    }

    // Helper methods.

    private boolean canDownloadsRun() {
      return !downloadsPaused && notMetRequirements == 0;
    }

    private Download putDownloadWithState(Download download, @Download.State int state) {
      // Downloads in terminal states shouldn't be in the downloads list. This method cannot be used
      // to set STATE_STOPPED either, because it doesn't have a stopReason argument.
      Assertions.checkState(
          state != STATE_COMPLETED && state != STATE_FAILED && state != STATE_STOPPED);
      return putDownload(copyDownloadWithState(download, state));
    }

    private Download putDownload(Download download) {
      // Downloads in terminal states shouldn't be in the downloads list.
      Assertions.checkState(download.state != STATE_COMPLETED && download.state != STATE_FAILED);
      int changedIndex = getDownloadIndex(download.request.id);
      if (changedIndex == C.INDEX_UNSET) {
        downloads.add(download);
        Collections.sort(downloads, InternalHandler::compareStartTimes);
      } else {
        boolean needsSort = download.startTimeMs != downloads.get(changedIndex).startTimeMs;
        downloads.set(changedIndex, download);
        if (needsSort) {
          Collections.sort(downloads, InternalHandler::compareStartTimes);
        }
      }
      try {
        downloadIndex.putDownload(download);
      } catch (IOException e) {
        Log.e(TAG, "Failed to update index.", e);
      }
      DownloadUpdate update =
          new DownloadUpdate(download, /* isRemove= */ false, new ArrayList<>(downloads));
      mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget();
      return download;
    }

    @Nullable
    private Download getDownload(String id, boolean loadFromIndex) {
      int index = getDownloadIndex(id);
      if (index != C.INDEX_UNSET) {
        return downloads.get(index);
      }
      if (loadFromIndex) {
        try {
          return downloadIndex.getDownload(id);
        } catch (IOException e) {
          Log.e(TAG, "Failed to load download: " + id, e);
        }
      }
      return null;
    }

    private int getDownloadIndex(String id) {
      for (int i = 0; i < downloads.size(); i++) {
        Download download = downloads.get(i);
        if (download.request.id.equals(id)) {
          return i;
        }
      }
      return C.INDEX_UNSET;
    }

    private static Download copyDownloadWithState(Download download, @Download.State int state) {
      return new Download(
          download.request,
          state,
          download.startTimeMs,
          /* updateTimeMs= */ System.currentTimeMillis(),
          download.contentLength,
          /* stopReason= */ 0,
          FAILURE_REASON_NONE,
          download.progress);
    }

    private static int compareStartTimes(Download first, Download second) {
      return Util.compareLong(first.startTimeMs, second.startTimeMs);
    }
  }

  private static class Task extends Thread implements Downloader.ProgressListener {

    private final DownloadRequest request;
    private final Downloader downloader;
    private final DownloadProgress downloadProgress;
    private final boolean isRemove;
    private final int minRetryCount;

    private volatile InternalHandler internalHandler;
    private volatile boolean isCanceled;
    @Nullable private Throwable finalError;

    private long contentLength;

    private Task(
        DownloadRequest request,
        Downloader downloader,
        DownloadProgress downloadProgress,
        boolean isRemove,
        int minRetryCount,
        InternalHandler internalHandler) {
      this.request = request;
      this.downloader = downloader;
      this.downloadProgress = downloadProgress;
      this.isRemove = isRemove;
      this.minRetryCount = minRetryCount;
      this.internalHandler = internalHandler;
      contentLength = C.LENGTH_UNSET;
    }

    @SuppressWarnings("nullness:assignment.type.incompatible")
    public void cancel(boolean released) {
      if (released) {
        // Download threads are GC roots for as long as they're running. The time taken for
        // cancellation to complete depends on the implementation of the downloader being used. We
        // null the handler reference here so that it doesn't prevent garbage collection of the
        // download manager whilst cancellation is ongoing.
        internalHandler = null;
      }
      if (!isCanceled) {
        isCanceled = true;
        downloader.cancel();
        interrupt();
      }
    }

    // Methods running on download thread.

    @Override
    public void run() {
      try {
        if (isRemove) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (!isCanceled) {
            try {
              downloader.download(/* progressListener= */ this);
              break;
            } catch (IOException e) {
              if (!isCanceled) {
                long bytesDownloaded = downloadProgress.bytesDownloaded;
                if (bytesDownloaded != errorPosition) {
                  errorPosition = bytesDownloaded;
                  errorCount = 0;
                }
                if (++errorCount > minRetryCount) {
                  throw e;
                }
                Thread.sleep(getRetryDelayMillis(errorCount));
              }
            }
          }
        }
      } catch (Throwable e) {
        finalError = e;
      }
      Handler internalHandler = this.internalHandler;
      if (internalHandler != null) {
        internalHandler.obtainMessage(MSG_TASK_STOPPED, this).sendToTarget();
      }
    }

    @Override
    public void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded) {
      downloadProgress.bytesDownloaded = bytesDownloaded;
      downloadProgress.percentDownloaded = percentDownloaded;
      if (contentLength != this.contentLength) {
        this.contentLength = contentLength;
        Handler internalHandler = this.internalHandler;
        if (internalHandler != null) {
          internalHandler.obtainMessage(MSG_CONTENT_LENGTH_CHANGED, this).sendToTarget();
        }
      }
    }

    private static int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

  private static final class DownloadUpdate {

    public final Download download;
    public final boolean isRemove;
    public final List<Download> downloads;

    public DownloadUpdate(Download download, boolean isRemove, List<Download> downloads) {
      this.download = download;
      this.isRemove = isRemove;
      this.downloads = downloads;
    }
  }
}
