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

import com.google.android.exoplayer2.PlaybackParameters;

/**
 * Tracks the progression of media time.
 */
public interface MediaClock {

  /**
   * Returns the current media position in microseconds.
   */
  long getPositionUs();

  /**
   * Attempts to set the playback parameters and returns the active playback parameters, which may
   * differ from those passed in.
   *
   * @param playbackParameters The playback parameters.
   * @return The active playback parameters.
   */
  PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * Returns the active playback parameters.
   */
  PlaybackParameters getPlaybackParameters();

}
