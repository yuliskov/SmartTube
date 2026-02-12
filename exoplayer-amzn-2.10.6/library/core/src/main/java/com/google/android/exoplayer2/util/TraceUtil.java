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
package com.google.android.exoplayer2.util;

import android.annotation.TargetApi;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;

/**
 * Calls through to {@link android.os.Trace} methods on supported API levels.
 */
public final class TraceUtil {

  private TraceUtil() {}

  /**
   * Writes a trace message to indicate that a given section of code has begun.
   *
   * @see android.os.Trace#beginSection(String)
   * @param sectionName The name of the code section to appear in the trace. This may be at most 127
   *     Unicode code units long.
   */
  public static void beginSection(String sectionName) {
    if (ExoPlayerLibraryInfo.TRACE_ENABLED && Util.SDK_INT >= 18) {
      beginSectionV18(sectionName);
    }
  }

  /**
   * Writes a trace message to indicate that a given section of code has ended.
   *
   * @see android.os.Trace#endSection()
   */
  public static void endSection() {
    if (ExoPlayerLibraryInfo.TRACE_ENABLED && Util.SDK_INT >= 18) {
      endSectionV18();
    }
  }

  @TargetApi(18)
  private static void beginSectionV18(String sectionName) {
    android.os.Trace.beginSection(sectionName);
  }

  @TargetApi(18)
  private static void endSectionV18() {
    android.os.Trace.endSection();
  }

}
