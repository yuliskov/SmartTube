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
package com.google.android.exoplayer2.drm;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import java.util.Map;

/**
 * Utility methods for Widevine.
 */
public final class WidevineUtil {

  /** Widevine specific key status field name for the remaining license duration, in seconds. */
  public static final String PROPERTY_LICENSE_DURATION_REMAINING = "LicenseDurationRemaining";
  /** Widevine specific key status field name for the remaining playback duration, in seconds. */
  public static final String PROPERTY_PLAYBACK_DURATION_REMAINING = "PlaybackDurationRemaining";

  private WidevineUtil() {}

  /**
   * Returns license and playback durations remaining in seconds.
   *
   * @param drmSession The drm session to query.
   * @return A {@link Pair} consisting of the remaining license and playback durations in seconds,
   *     or null if called before the session has been opened or after it's been released.
   */
  public static @Nullable Pair<Long, Long> getLicenseDurationRemainingSec(
      DrmSession<?> drmSession) {
    Map<String, String> keyStatus = drmSession.queryKeyStatus();
    if (keyStatus == null) {
      return null;
    }
    return new Pair<>(getDurationRemainingSec(keyStatus, PROPERTY_LICENSE_DURATION_REMAINING),
        getDurationRemainingSec(keyStatus, PROPERTY_PLAYBACK_DURATION_REMAINING));
  }

  private static long getDurationRemainingSec(Map<String, String> keyStatus, String property) {
    if (keyStatus != null) {
      try {
        String value = keyStatus.get(property);
        if (value != null) {
          return Long.parseLong(value);
        }
      } catch (NumberFormatException e) {
        // do nothing.
      }
    }
    return C.TIME_UNSET;
  }

}
