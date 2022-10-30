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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;

/** Defines a seek point in a media stream. */
public final class SeekPoint {

  /** A {@link SeekPoint} whose time and byte offset are both set to 0. */
  public static final SeekPoint START = new SeekPoint(0, 0);

  /** The time of the seek point, in microseconds. */
  public final long timeUs;

  /** The byte offset of the seek point. */
  public final long position;

  /**
   * @param timeUs The time of the seek point, in microseconds.
   * @param position The byte offset of the seek point.
   */
  public SeekPoint(long timeUs, long position) {
    this.timeUs = timeUs;
    this.position = position;
  }

  @Override
  public String toString() {
    return "[timeUs=" + timeUs + ", position=" + position + "]";
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SeekPoint other = (SeekPoint) obj;
    return timeUs == other.timeUs && position == other.position;
  }

  @Override
  public int hashCode() {
    int result = (int) timeUs;
    result = 31 * result + (int) position;
    return result;
  }
}
