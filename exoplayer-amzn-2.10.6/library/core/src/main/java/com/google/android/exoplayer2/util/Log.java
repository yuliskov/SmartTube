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
package com.google.android.exoplayer2.util;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Wrapper around {@link android.util.Log} which allows to set the log level. */
public final class Log {

  /**
   * Log level for ExoPlayer logcat logging. One of {@link #LOG_LEVEL_ALL}, {@link #LOG_LEVEL_INFO},
   * {@link #LOG_LEVEL_WARNING}, {@link #LOG_LEVEL_ERROR} or {@link #LOG_LEVEL_OFF}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({LOG_LEVEL_ALL, LOG_LEVEL_INFO, LOG_LEVEL_WARNING, LOG_LEVEL_ERROR, LOG_LEVEL_OFF})
  @interface LogLevel {}
  /** Log level to log all messages. */
  public static final int LOG_LEVEL_ALL = 0;
  /** Log level to only log informative, warning and error messages. */
  public static final int LOG_LEVEL_INFO = 1;
  /** Log level to only log warning and error messages. */
  public static final int LOG_LEVEL_WARNING = 2;
  /** Log level to only log error messages. */
  public static final int LOG_LEVEL_ERROR = 3;
  /** Log level to disable all logging. */
  public static final int LOG_LEVEL_OFF = Integer.MAX_VALUE;

  private static int logLevel = LOG_LEVEL_ALL;
  private static boolean logStackTraces = true;

  private Log() {}

  /** Returns current {@link LogLevel} for ExoPlayer logcat logging. */
  public static @LogLevel int getLogLevel() {
    return logLevel;
  }

  /** Returns whether stack traces of {@link Throwable}s will be logged to logcat. */
  public boolean getLogStackTraces() {
    return logStackTraces;
  }

  /**
   * Sets the {@link LogLevel} for ExoPlayer logcat logging.
   *
   * @param logLevel The new {@link LogLevel}.
   */
  public static void setLogLevel(@LogLevel int logLevel) {
    Log.logLevel = logLevel;
  }

  /**
   * Sets whether stack traces of {@link Throwable}s will be logged to logcat.
   *
   * @param logStackTraces Whether stack traces will be logged.
   */
  public static void setLogStackTraces(boolean logStackTraces) {
    Log.logStackTraces = logStackTraces;
  }

  /** @see android.util.Log#d(String, String) */
  public static void d(String tag, String message) {
    if (logLevel == LOG_LEVEL_ALL) {
      android.util.Log.d(tag, message);
    }
  }

  /** @see android.util.Log#d(String, String, Throwable) */
  public static void d(String tag, String message, @Nullable Throwable throwable) {
    if (!logStackTraces) {
      d(tag, appendThrowableMessage(message, throwable));
    } else if (logLevel == LOG_LEVEL_ALL) {
      android.util.Log.d(tag, message, throwable);
    }
  }

  /** @see android.util.Log#i(String, String) */
  public static void i(String tag, String message) {
    if (logLevel <= LOG_LEVEL_INFO) {
      android.util.Log.i(tag, message);
    }
  }

  /** @see android.util.Log#i(String, String, Throwable) */
  public static void i(String tag, String message, @Nullable Throwable throwable) {
    if (!logStackTraces) {
      i(tag, appendThrowableMessage(message, throwable));
    } else if (logLevel <= LOG_LEVEL_INFO) {
      android.util.Log.i(tag, message, throwable);
    }
  }

  /** @see android.util.Log#w(String, String) */
  public static void w(String tag, String message) {
    if (logLevel <= LOG_LEVEL_WARNING) {
      android.util.Log.w(tag, message);
    }
  }

  /** @see android.util.Log#w(String, String, Throwable) */
  public static void w(String tag, String message, @Nullable Throwable throwable) {
    if (!logStackTraces) {
      w(tag, appendThrowableMessage(message, throwable));
    } else if (logLevel <= LOG_LEVEL_WARNING) {
      android.util.Log.w(tag, message, throwable);
    }
  }

  /** @see android.util.Log#e(String, String) */
  public static void e(String tag, String message) {
    if (logLevel <= LOG_LEVEL_ERROR) {
      android.util.Log.e(tag, message);
    }
  }

  /** @see android.util.Log#e(String, String, Throwable) */
  public static void e(String tag, String message, @Nullable Throwable throwable) {
    if (!logStackTraces) {
      e(tag, appendThrowableMessage(message, throwable));
    } else if (logLevel <= LOG_LEVEL_ERROR) {
      android.util.Log.e(tag, message, throwable);
    }
  }

  private static String appendThrowableMessage(String message, @Nullable Throwable throwable) {
    if (throwable == null) {
      return message;
    }
    String throwableMessage = throwable.getMessage();
    return TextUtils.isEmpty(throwableMessage) ? message : message + " - " + throwableMessage;
  }
}
