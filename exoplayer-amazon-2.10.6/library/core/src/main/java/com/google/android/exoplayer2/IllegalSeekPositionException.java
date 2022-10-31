/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2;

/**
 * Thrown when an attempt is made to seek to a position that does not exist in the player's
 * {@link Timeline}.
 */
public final class IllegalSeekPositionException extends IllegalStateException {

  /**
   * The {@link Timeline} in which the seek was attempted.
   */
  public final Timeline timeline;
  /**
   * The index of the window being seeked to.
   */
  public final int windowIndex;
  /**
   * The seek position in the specified window.
   */
  public final long positionMs;

  /**
   * @param timeline The {@link Timeline} in which the seek was attempted.
   * @param windowIndex The index of the window being seeked to.
   * @param positionMs The seek position in the specified window.
   */
  public IllegalSeekPositionException(Timeline timeline, int windowIndex, long positionMs) {
    this.timeline = timeline;
    this.windowIndex = windowIndex;
    this.positionMs = positionMs;
  }

}
