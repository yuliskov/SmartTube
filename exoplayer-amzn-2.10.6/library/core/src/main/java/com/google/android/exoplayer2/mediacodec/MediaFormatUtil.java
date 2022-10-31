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
package com.google.android.exoplayer2.mediacodec;

import android.media.MediaFormat;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;
import java.util.List;

/** Helper class for configuring {@link MediaFormat} instances. */
public final class MediaFormatUtil {

  private MediaFormatUtil() {}

  /**
   * Sets a {@link MediaFormat} {@link String} value.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void setString(MediaFormat format, String key, String value) {
    format.setString(key, value);
  }

  /**
   * Sets a {@link MediaFormat}'s codec specific data buffers.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param csdBuffers The csd buffers to set.
   */
  public static void setCsdBuffers(MediaFormat format, List<byte[]> csdBuffers) {
    for (int i = 0; i < csdBuffers.size(); i++) {
      format.setByteBuffer("csd-" + i, ByteBuffer.wrap(csdBuffers.get(i)));
    }
  }

  /**
   * Sets a {@link MediaFormat} integer value. Does nothing if {@code value} is {@link
   * Format#NO_VALUE}.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void maybeSetInteger(MediaFormat format, String key, int value) {
    if (value != Format.NO_VALUE) {
      format.setInteger(key, value);
    }
  }

  /**
   * Sets a {@link MediaFormat} float value. Does nothing if {@code value} is {@link
   * Format#NO_VALUE}.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void maybeSetFloat(MediaFormat format, String key, float value) {
    if (value != Format.NO_VALUE) {
      format.setFloat(key, value);
    }
  }

  /**
   * Sets a {@link MediaFormat} {@link ByteBuffer} value. Does nothing if {@code value} is null.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The {@link byte[]} that will be wrapped to obtain the value.
   */
  public static void maybeSetByteBuffer(MediaFormat format, String key, @Nullable byte[] value) {
    if (value != null) {
      format.setByteBuffer(key, ByteBuffer.wrap(value));
    }
  }

  /**
   * Sets a {@link MediaFormat}'s color information. Does nothing if {@code colorInfo} is null.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param colorInfo The color info to set.
   */
  @SuppressWarnings("InlinedApi")
  public static void maybeSetColorInfo(MediaFormat format, @Nullable ColorInfo colorInfo) {
    if (colorInfo != null) {
      maybeSetInteger(format, MediaFormat.KEY_COLOR_TRANSFER, colorInfo.colorTransfer);
      maybeSetInteger(format, MediaFormat.KEY_COLOR_STANDARD, colorInfo.colorSpace);
      maybeSetInteger(format, MediaFormat.KEY_COLOR_RANGE, colorInfo.colorRange);
      maybeSetByteBuffer(format, MediaFormat.KEY_HDR_STATIC_INFO, colorInfo.hdrStaticInfo);
    }
  }
}
