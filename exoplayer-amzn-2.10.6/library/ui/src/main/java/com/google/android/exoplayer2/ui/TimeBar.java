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
package com.google.android.exoplayer2.ui;

import androidx.annotation.Nullable;
import android.view.View;

/**
 * Interface for time bar views that can display a playback position, buffered position, duration
 * and ad markers, and that have a listener for scrubbing (seeking) events.
 */
public interface TimeBar {

  /**
   * Adds a listener for scrubbing events.
   *
   * @param listener The listener to add.
   */
  void addListener(OnScrubListener listener);

  /**
   * Removes a listener for scrubbing events.
   *
   * @param listener The listener to remove.
   */
  void removeListener(OnScrubListener listener);

  /**
   * @see View#isEnabled()
   */
  void setEnabled(boolean enabled);

  /**
   * Sets the position increment for key presses and accessibility actions, in milliseconds.
   * <p>
   * Clears any increment specified in a preceding call to {@link #setKeyCountIncrement(int)}.
   *
   * @param time The time increment, in milliseconds.
   */
  void setKeyTimeIncrement(long time);

  /**
   * Sets the position increment for key presses and accessibility actions, as a number of
   * increments that divide the duration of the media. For example, passing 20 will cause key
   * presses to increment/decrement the position by 1/20th of the duration (if known).
   * <p>
   * Clears any increment specified in a preceding call to {@link #setKeyTimeIncrement(long)}.
   *
   * @param count The number of increments that divide the duration of the media.
   */
  void setKeyCountIncrement(int count);

  /**
   * Sets the current position.
   *
   * @param position The current position to show, in milliseconds.
   */
  void setPosition(long position);

  /**
   * Sets the buffered position.
   *
   * @param bufferedPosition The current buffered position to show, in milliseconds.
   */
  void setBufferedPosition(long bufferedPosition);

  /**
   * Sets the duration.
   *
   * @param duration The duration to show, in milliseconds.
   */
  void setDuration(long duration);

  /**
   * Returns the preferred delay in milliseconds of media time after which the time bar position
   * should be updated.
   *
   * @return Preferred delay, in milliseconds of media time.
   */
  long getPreferredUpdateDelay();

  /**
   * Sets the times of ad groups and whether each ad group has been played.
   *
   * @param adGroupTimesMs An array where the first {@code adGroupCount} elements are the times of
   *     ad groups in milliseconds. May be {@code null} if there are no ad groups.
   * @param playedAdGroups An array where the first {@code adGroupCount} elements indicate whether
   *     the corresponding ad groups have been played. May be {@code null} if there are no ad
   *     groups.
   * @param adGroupCount The number of ad groups.
   */
  void setAdGroupTimesMs(@Nullable long[] adGroupTimesMs, @Nullable boolean[] playedAdGroups,
      int adGroupCount);

  /**
   * Listener for scrubbing events.
   */
  interface OnScrubListener {

    /**
     * Called when the user starts moving the scrubber.
     *
     * @param timeBar The time bar.
     * @param position The scrub position in milliseconds.
     */
    void onScrubStart(TimeBar timeBar, long position);

    /**
     * Called when the user moves the scrubber.
     *
     * @param timeBar The time bar.
     * @param position The scrub position in milliseconds.
     */
    void onScrubMove(TimeBar timeBar, long position);

    /**
     * Called when the user stops moving the scrubber.
     *
     * @param timeBar The time bar.
     * @param position The scrub position in milliseconds.
     * @param canceled Whether scrubbing was canceled.
     */
    void onScrubStop(TimeBar timeBar, long position, boolean canceled);
  }

}
