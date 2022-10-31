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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ExecutorService;

/**
 * Manages the background loading of {@link Loadable}s.
 */
public final class Loader implements LoaderErrorThrower {

  /**
   * Thrown when an unexpected exception or error is encountered during loading.
   */
  public static final class UnexpectedLoaderException extends IOException {

    public UnexpectedLoaderException(Throwable cause) {
      super("Unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
    }

  }

  /**
   * An object that can be loaded using a {@link Loader}.
   */
  public interface Loadable {

    /**
     * Cancels the load.
     */
    void cancelLoad();

    /**
     * Performs the load, returning on completion or cancellation.
     *
     * @throws IOException If the input could not be loaded.
     * @throws InterruptedException If the thread was interrupted.
     */
    void load() throws IOException, InterruptedException;

  }

  /**
   * A callback to be notified of {@link Loader} events.
   */
  public interface Callback<T extends Loadable> {

    /**
     * Called when a load has completed.
     *
     * <p>Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting
     * and this callback being called.
     *
     * @param loadable The loadable whose load has completed.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs The duration in milliseconds of the load since {@link #startLoading}
     *     was called.
     */
    void onLoadCompleted(T loadable, long elapsedRealtimeMs, long loadDurationMs);

    /**
     * Called when a load has been canceled.
     *
     * <p>Note: If the {@link Loader} has not been released then there is guaranteed to be a memory
     * barrier between {@link Loadable#load()} exiting and this callback being called. If the {@link
     * Loader} has been released then this callback may be called before {@link Loadable#load()}
     * exits.
     *
     * @param loadable The loadable whose load has been canceled.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load was canceled.
     * @param loadDurationMs The duration in milliseconds of the load since {@link #startLoading}
     *     was called up to the point at which it was canceled.
     * @param released True if the load was canceled because the {@link Loader} was released. False
     *     otherwise.
     */
    void onLoadCanceled(T loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released);

    /**
     * Called when a load encounters an error.
     *
     * <p>Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting
     * and this callback being called.
     *
     * @param loadable The loadable whose load has encountered an error.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the error occurred.
     * @param loadDurationMs The duration in milliseconds of the load since {@link #startLoading}
     *     was called up to the point at which the error occurred.
     * @param error The load error.
     * @param errorCount The number of errors this load has encountered, including this one.
     * @return The desired error handling action. One of {@link Loader#RETRY}, {@link
     *     Loader#RETRY_RESET_ERROR_COUNT}, {@link Loader#DONT_RETRY}, {@link
     *     Loader#DONT_RETRY_FATAL} or a retry action created by {@link #createRetryAction}.
     */
    LoadErrorAction onLoadError(
        T loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error, int errorCount);
  }

  /**
   * A callback to be notified when a {@link Loader} has finished being released.
   */
  public interface ReleaseCallback {

    /**
     * Called when the {@link Loader} has finished being released.
     */
    void onLoaderReleased();

  }

  /** Types of action that can be taken in response to a load error. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ACTION_TYPE_RETRY,
    ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT,
    ACTION_TYPE_DONT_RETRY,
    ACTION_TYPE_DONT_RETRY_FATAL
  })
  private @interface RetryActionType {}

  private static final int ACTION_TYPE_RETRY = 0;
  private static final int ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT = 1;
  private static final int ACTION_TYPE_DONT_RETRY = 2;
  private static final int ACTION_TYPE_DONT_RETRY_FATAL = 3;

  /** Retries the load using the default delay. */
  public static final LoadErrorAction RETRY =
      createRetryAction(/* resetErrorCount= */ false, C.TIME_UNSET);
  /** Retries the load using the default delay and resets the error count. */
  public static final LoadErrorAction RETRY_RESET_ERROR_COUNT =
      createRetryAction(/* resetErrorCount= */ true, C.TIME_UNSET);
  /** Discards the failed loading task and ignores any errors that have occurred. */
  public static final LoadErrorAction DONT_RETRY =
      new LoadErrorAction(ACTION_TYPE_DONT_RETRY, C.TIME_UNSET);
  /**
   * Discards the failed load. The next call to {@link #maybeThrowError()} will throw the last load
   * error.
   */
  public static final LoadErrorAction DONT_RETRY_FATAL =
      new LoadErrorAction(ACTION_TYPE_DONT_RETRY_FATAL, C.TIME_UNSET);

  /**
   * Action that can be taken in response to {@link Callback#onLoadError(Loadable, long, long,
   * IOException, int)}.
   */
  public static final class LoadErrorAction {

    private final @RetryActionType int type;
    private final long retryDelayMillis;

    private LoadErrorAction(@RetryActionType int type, long retryDelayMillis) {
      this.type = type;
      this.retryDelayMillis = retryDelayMillis;
    }

    /** Returns whether this is a retry action. */
    public boolean isRetry() {
      return type == ACTION_TYPE_RETRY || type == ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT;
    }
  }

  private final ExecutorService downloadExecutorService;

  private LoadTask<? extends Loadable> currentTask;
  private IOException fatalError;

  /**
   * @param threadName A name for the loader's thread.
   */
  public Loader(String threadName) {
    this.downloadExecutorService = Util.newSingleThreadExecutor(threadName);
  }

  /**
   * Creates a {@link LoadErrorAction} for retrying with the given parameters.
   *
   * @param resetErrorCount Whether the previous error count should be set to zero.
   * @param retryDelayMillis The number of milliseconds to wait before retrying.
   * @return A {@link LoadErrorAction} for retrying with the given parameters.
   */
  public static LoadErrorAction createRetryAction(boolean resetErrorCount, long retryDelayMillis) {
    return new LoadErrorAction(
        resetErrorCount ? ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT : ACTION_TYPE_RETRY,
        retryDelayMillis);
  }

  /**
   * Whether the last call to {@link #startLoading} resulted in a fatal error. Calling {@link
   * #maybeThrowError()} will throw the fatal error.
   */
  public boolean hasFatalError() {
    return fatalError != null;
  }

  /** Clears any stored fatal error. */
  public void clearFatalError() {
    fatalError = null;
  }

  /**
   * Starts loading a {@link Loadable}.
   *
   * <p>The calling thread must be a {@link Looper} thread, which is the thread on which the {@link
   * Callback} will be called.
   *
   * @param <T> The type of the loadable.
   * @param loadable The {@link Loadable} to load.
   * @param callback A callback to be called when the load ends.
   * @param defaultMinRetryCount The minimum number of times the load must be retried before {@link
   *     #maybeThrowError()} will propagate an error.
   * @throws IllegalStateException If the calling thread does not have an associated {@link Looper}.
   * @return {@link SystemClock#elapsedRealtime} when the load started.
   */
  public <T extends Loadable> long startLoading(
      T loadable, Callback<T> callback, int defaultMinRetryCount) {
    Looper looper = Looper.myLooper();
    Assertions.checkState(looper != null);
    fatalError = null;
    long startTimeMs = SystemClock.elapsedRealtime();
    new LoadTask<>(looper, loadable, callback, defaultMinRetryCount, startTimeMs).start(0);
    return startTimeMs;
  }

  /**
   * Returns whether the {@link Loader} is currently loading a {@link Loadable}.
   */
  public boolean isLoading() {
    return currentTask != null;
  }

  /**
   * Cancels the current load. This method should only be called when a load is in progress.
   */
  public void cancelLoading() {
    currentTask.cancel(false);
  }

  /**
   * Releases the {@link Loader}. This method should be called when the {@link Loader} is no longer
   * required.
   */
  public void release() {
    release(null);
  }

  /**
   * Releases the {@link Loader}. This method should be called when the {@link Loader} is no longer
   * required.
   *
   * @param callback An optional callback to be called on the loading thread once the loader has
   *     been released.
   */
  public void release(@Nullable ReleaseCallback callback) {
    if (currentTask != null) {
      currentTask.cancel(true);
    }
    if (callback != null) {
      downloadExecutorService.execute(new ReleaseTask(callback));
    }
    downloadExecutorService.shutdown();
  }

  // LoaderErrorThrower implementation.

  @Override
  public void maybeThrowError() throws IOException {
    maybeThrowError(Integer.MIN_VALUE);
  }

  @Override
  public void maybeThrowError(int minRetryCount) throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else if (currentTask != null) {
      currentTask.maybeThrowError(minRetryCount == Integer.MIN_VALUE
          ? currentTask.defaultMinRetryCount : minRetryCount);
    }
  }

  // Internal classes.

  @SuppressLint("HandlerLeak")
  private final class LoadTask<T extends Loadable> extends Handler implements Runnable {

    private static final String TAG = "LoadTask";

    private static final int MSG_START = 0;
    private static final int MSG_CANCEL = 1;
    private static final int MSG_END_OF_SOURCE = 2;
    private static final int MSG_IO_EXCEPTION = 3;
    private static final int MSG_FATAL_ERROR = 4;

    public final int defaultMinRetryCount;

    private final T loadable;
    private final long startTimeMs;

    private @Nullable Loader.Callback<T> callback;
    private IOException currentError;
    private int errorCount;

    private volatile Thread executorThread;
    private volatile boolean canceled;
    private volatile boolean released;

    public LoadTask(Looper looper, T loadable, Loader.Callback<T> callback,
        int defaultMinRetryCount, long startTimeMs) {
      super(looper);
      this.loadable = loadable;
      this.callback = callback;
      this.defaultMinRetryCount = defaultMinRetryCount;
      this.startTimeMs = startTimeMs;
    }

    public void maybeThrowError(int minRetryCount) throws IOException {
      if (currentError != null && errorCount > minRetryCount) {
        throw currentError;
      }
    }

    public void start(long delayMillis) {
      Assertions.checkState(currentTask == null);
      currentTask = this;
      if (delayMillis > 0) {
        sendEmptyMessageDelayed(MSG_START, delayMillis);
      } else {
        execute();
      }
    }

    public void cancel(boolean released) {
      this.released = released;
      currentError = null;
      if (hasMessages(MSG_START)) {
        removeMessages(MSG_START);
        if (!released) {
          sendEmptyMessage(MSG_CANCEL);
        }
      } else {
        canceled = true;
        loadable.cancelLoad();
        if (executorThread != null) {
          executorThread.interrupt();
        }
      }
      if (released) {
        finish();
        long nowMs = SystemClock.elapsedRealtime();
        callback.onLoadCanceled(loadable, nowMs, nowMs - startTimeMs, true);
        // If loading, this task will be referenced from a GC root (the loading thread) until
        // cancellation completes. The time taken for cancellation to complete depends on the
        // implementation of the Loadable that the task is loading. We null the callback reference
        // here so that it doesn't prevent garbage collection whilst cancellation is ongoing.
        callback = null;
      }
    }

    @Override
    public void run() {
      try {
        executorThread = Thread.currentThread();
        if (!canceled) {
          TraceUtil.beginSection("load:" + loadable.getClass().getSimpleName());
          try {
            loadable.load();
          } finally {
            TraceUtil.endSection();
          }
        }
        if (!released) {
          sendEmptyMessage(MSG_END_OF_SOURCE);
        }
      } catch (IOException e) {
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, e).sendToTarget();
        }
      } catch (InterruptedException e) {
        // The load was canceled.
        Assertions.checkState(canceled);
        if (!released) {
          sendEmptyMessage(MSG_END_OF_SOURCE);
        }
      } catch (Exception e) {
        // This should never happen, but handle it anyway.
        Log.e(TAG, "Unexpected exception loading stream", e);
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
        }
      } catch (OutOfMemoryError e) {
        // This can occur if a stream is malformed in a way that causes an extractor to think it
        // needs to allocate a large amount of memory. We don't want the process to die in this
        // case, but we do want the playback to fail.
        Log.e(TAG, "OutOfMemory error loading stream", e);
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
        }
      } catch (Error e) {
        // We'd hope that the platform would kill the process if an Error is thrown here, but the
        // executor may catch the error (b/20616433). Throw it here, but also pass and throw it from
        // the handler thread so that the process dies even if the executor behaves in this way.
        Log.e(TAG, "Unexpected error loading stream", e);
        if (!released) {
          obtainMessage(MSG_FATAL_ERROR, e).sendToTarget();
        }
        throw e;
      }
    }

    @Override
    public void handleMessage(Message msg) {
      if (released) {
        return;
      }
      if (msg.what == MSG_START) {
        execute();
        return;
      }
      if (msg.what == MSG_FATAL_ERROR) {
        throw (Error) msg.obj;
      }
      finish();
      long nowMs = SystemClock.elapsedRealtime();
      long durationMs = nowMs - startTimeMs;
      if (canceled) {
        callback.onLoadCanceled(loadable, nowMs, durationMs, false);
        return;
      }
      switch (msg.what) {
        case MSG_CANCEL:
          callback.onLoadCanceled(loadable, nowMs, durationMs, false);
          break;
        case MSG_END_OF_SOURCE:
          try {
            callback.onLoadCompleted(loadable, nowMs, durationMs);
          } catch (RuntimeException e) {
            // This should never happen, but handle it anyway.
            Log.e(TAG, "Unexpected exception handling load completed", e);
            fatalError = new UnexpectedLoaderException(e);
          }
          break;
        case MSG_IO_EXCEPTION:
          currentError = (IOException) msg.obj;
          errorCount++;
          LoadErrorAction action =
              callback.onLoadError(loadable, nowMs, durationMs, currentError, errorCount);
          if (action.type == ACTION_TYPE_DONT_RETRY_FATAL) {
            fatalError = currentError;
          } else if (action.type != ACTION_TYPE_DONT_RETRY) {
            if (action.type == ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT) {
              errorCount = 1;
            }
            start(
                action.retryDelayMillis != C.TIME_UNSET
                    ? action.retryDelayMillis
                    : getRetryDelayMillis());
          }
          break;
        default:
          // Never happens.
          break;
      }
    }

    private void execute() {
      currentError = null;
      downloadExecutorService.execute(currentTask);
    }

    private void finish() {
      currentTask = null;
    }

    private long getRetryDelayMillis() {
      return Math.min((errorCount - 1) * 1000, 5000);
    }

  }

  private static final class ReleaseTask implements Runnable {

    private final ReleaseCallback callback;

    public ReleaseTask(ReleaseCallback callback) {
      this.callback = callback;
    }

    @Override
    public void run() {
      callback.onLoaderReleased();
    }

  }

}
