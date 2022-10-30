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
package com.google.android.exoplayer2.drm;

import com.google.android.exoplayer2.Player;

/** Listener of {@link DefaultDrmSessionManager} events. */
public interface DefaultDrmSessionEventListener {

  /** Called each time a drm session is acquired. */
  default void onDrmSessionAcquired() {}

  /** Called each time keys are loaded. */
  void onDrmKeysLoaded();

  /**
   * Called when a drm error occurs.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error and continue. Hence applications should
   * <em>not</em> implement this method to display a user visible error or initiate an application
   * level retry ({@link Player.EventListener#onPlayerError} is the appropriate place to implement
   * such behavior). This method is called to provide the application with an opportunity to log the
   * error if it wishes to do so.
   *
   * @param error The corresponding exception.
   */
  void onDrmSessionManagerError(Exception error);

  /** Called each time offline keys are restored. */
  void onDrmKeysRestored();

  /** Called each time offline keys are removed. */
  void onDrmKeysRemoved();

  /** Called each time a drm session is released. */
  default void onDrmSessionReleased() {}
}
