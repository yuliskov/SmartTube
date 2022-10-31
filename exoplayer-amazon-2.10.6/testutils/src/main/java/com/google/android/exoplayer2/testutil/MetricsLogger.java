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
package com.google.android.exoplayer2.testutil;

/**
 * Metric Logging interface for ExoPlayer playback tests.
 */
public interface MetricsLogger {

  String KEY_FRAMES_DROPPED_COUNT = "frames_dropped_count";
  String KEY_FRAMES_RENDERED_COUNT = "frames_rendered_count";
  String KEY_FRAMES_SKIPPED_COUNT = "frames_skipped_count";
  String KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT = "maximum_consecutive_frames_dropped_count";
  String KEY_TEST_NAME = "test_name";
  String KEY_IS_CDD_LIMITED_RETRY = "is_cdd_limited_retry";

  /**
   * Logs an int metric provided from a test.
   *
   * @param key The key of the metric to be logged.
   * @param value The value of the metric to be logged.
   */
  void logMetric(String key, int value);

  /**
   * Logs a double metric provided from a test.
   *
   * @param key The key of the metric to be logged.
   * @param value The value of the metric to be logged.
   */
  void logMetric(String key, double value);

  /**
   * Logs a string metric provided from a test.
   *
   * @param key The key of the metric to be logged.
   * @param value The value of the metric to be logged.
   */
  void logMetric(String key, String value);

  /**
   * Logs a boolean metric provided from a test.
   *
   * @param key The key of the metric to be logged.
   * @param value The value of the metric to be logged.
   */
  void logMetric(String key, boolean value);

  /**
   * Closes the logger.
   */
  void close();

  /**
   * A factory for instantiating {@link MetricsLogger} instances.
   */
  final class Factory {

    private Factory() {}

    /**
     * Obtains a new instance of {@link MetricsLogger}.
     *
     * @param tag The tag to be used for logcat logs.
     */
    public static MetricsLogger createDefault(String tag) {
      return new LogcatMetricsLogger(tag);
    }
  }

}
