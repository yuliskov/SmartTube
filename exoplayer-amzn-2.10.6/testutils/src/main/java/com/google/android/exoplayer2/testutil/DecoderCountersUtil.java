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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.android.exoplayer2.decoder.DecoderCounters;

/**
 * Assertions for {@link DecoderCounters}.
 */
public final class DecoderCountersUtil {

  private DecoderCountersUtil() {}

  /**
   * Returns the sum of the skipped, dropped and rendered buffers.
   *
   * @param counters The counters for which the total should be calculated.
   * @return The sum of the skipped, dropped and rendered buffers.
   */
  public static int getTotalBufferCount(DecoderCounters counters) {
    counters.ensureUpdated();
    return counters.skippedOutputBufferCount + counters.droppedBufferCount
        + counters.renderedOutputBufferCount;
  }

  public static void assertSkippedOutputBufferCount(String name, DecoderCounters counters,
      int expected) {
    counters.ensureUpdated();
    int actual = counters.skippedOutputBufferCount;
    assertWithMessage(
            "Codec(" + name + ") skipped " + actual + " buffers. Expected " + expected + ".")
        .that(actual)
        .isEqualTo(expected);
  }

  public static void assertTotalBufferCount(String name, DecoderCounters counters, int minCount,
      int maxCount) {
    int actual = getTotalBufferCount(counters);
    assertWithMessage(
            "Codec("
                + name
                + ") output "
                + actual
                + " buffers. Expected in range ["
                + minCount
                + ", "
                + maxCount
                + "].")
        .that(minCount <= actual && actual <= maxCount)
        .isTrue();
  }

  public static void assertDroppedBufferLimit(String name, DecoderCounters counters, int limit) {
    counters.ensureUpdated();
    int actual = counters.droppedBufferCount;
    assertWithMessage(
            "Codec("
                + name
                + ") was late decoding: "
                + actual
                + " buffers. "
                + "Limit: "
                + limit
                + ".")
        .that(actual)
        .isAtMost(limit);
  }

  public static void assertConsecutiveDroppedBufferLimit(String name, DecoderCounters counters,
      int limit) {
    counters.ensureUpdated();
    int actual = counters.maxConsecutiveDroppedBufferCount;
    assertWithMessage(
            "Codec("
                + name
                + ") was late decoding: "
                + actual
                + " buffers consecutively. "
                + "Limit: "
                + limit
                + ".")
        .that(actual)
        .isAtMost(limit);
  }

}
