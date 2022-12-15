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

import com.google.android.exoplayer2.util.Log;

/**
 * Implementation of {@link MetricsLogger} that prints the metrics to logcat.
 */
public final class LogcatMetricsLogger implements MetricsLogger {

  private final String tag;

  public LogcatMetricsLogger(String tag) {
    this.tag = tag;
  }

  @Override
  public void logMetric(String key, int value) {
    Log.d(tag, key + ": " + value);
  }

  @Override
  public void logMetric(String key, double value) {
    Log.d(tag, key + ": " + value);
  }

  @Override
  public void logMetric(String key, String value) {
    Log.d(tag, key + ": " + value);
  }

  @Override
  public void logMetric(String key, boolean value) {
    Log.d(tag, key + ": " + value);
  }

  @Override
  public void close() {
    // Do nothing.
  }

}
