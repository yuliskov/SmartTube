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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;

// TODO: Clarify the requirements for implementing this interface [Internal ref: b/36250203].
/**
 * A loader that can proceed in approximate synchronization with other loaders.
 */
public interface SequenceableLoader {

  /**
   * A callback to be notified of {@link SequenceableLoader} events.
   */
  interface Callback<T extends SequenceableLoader> {

    /**
     * Called by the loader to indicate that it wishes for its {@link #continueLoading(long)} method
     * to be called when it can continue to load data. Called on the playback thread.
     */
    void onContinueLoadingRequested(T source);

  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the data is fully buffered.
   */
  long getBufferedPositionUs();

  /**
   * Returns the next load time, or {@link C#TIME_END_OF_SOURCE} if loading has finished.
   */
  long getNextLoadPositionUs();

  /**
   * Attempts to continue loading.
   *
   * @param positionUs The current playback position in microseconds. If playback of the period to
   *     which this loader belongs has not yet started, the value will be the starting position
   *     in the period minus the duration of any media in previous periods still to be played.
   * @return True if progress was made, meaning that {@link #getNextLoadPositionUs()} will return
   *     a different value than prior to the call. False otherwise.
   */
  boolean continueLoading(long positionUs);

  /**
   * Re-evaluates the buffer given the playback position.
   *
   * <p>Re-evaluation may discard buffered media so that it can be re-buffered in a different
   * quality.
   *
   * @param positionUs The current playback position in microseconds. If playback of this period has
   *     not yet started, the value will be the starting position in this period minus the duration
   *     of any media in previous periods still to be played.
   */
  void reevaluateBuffer(long positionUs);
}
