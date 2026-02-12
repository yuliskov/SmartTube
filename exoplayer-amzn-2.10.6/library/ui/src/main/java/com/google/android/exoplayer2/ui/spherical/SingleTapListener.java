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
package com.google.android.exoplayer2.ui.spherical;

import android.view.MotionEvent;

/** Listens tap events on a {@link android.view.View}. */
public interface SingleTapListener {
  /**
   * Notified when a tap occurs with the up {@link MotionEvent} that triggered it.
   *
   * @param e The up motion event that completed the first tap.
   * @return Whether the event is consumed.
   */
  boolean onSingleTapUp(MotionEvent e);
}
