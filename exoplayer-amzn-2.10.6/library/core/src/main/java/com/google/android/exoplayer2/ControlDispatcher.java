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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.Player.RepeatMode;

/**
 * Dispatches operations to the {@link Player}.
 * <p>
 * Implementations may choose to suppress (e.g. prevent playback from resuming if audio focus is
 * denied) or modify (e.g. change the seek position to prevent a user from seeking past a
 * non-skippable advert) operations.
 */
public interface ControlDispatcher {

  /**
   * Dispatches a {@link Player#setPlayWhenReady(boolean)} operation.
   *
   * @param player The {@link Player} to which the operation should be dispatched.
   * @param playWhenReady Whether playback should proceed when ready.
   * @return True if the operation was dispatched. False if suppressed.
   */
  boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady);

  /**
   * Dispatches a {@link Player#seekTo(int, long)} operation.
   *
   * @param player The {@link Player} to which the operation should be dispatched.
   * @param windowIndex The index of the window.
   * @param positionMs The seek position in the specified window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   * @return True if the operation was dispatched. False if suppressed.
   */
  boolean dispatchSeekTo(Player player, int windowIndex, long positionMs);

  /**
   * Dispatches a {@link Player#setRepeatMode(int)} operation.
   *
   * @param player The {@link Player} to which the operation should be dispatched.
   * @param repeatMode The repeat mode.
   * @return True if the operation was dispatched. False if suppressed.
   */
  boolean dispatchSetRepeatMode(Player player, @RepeatMode int repeatMode);

  /**
   * Dispatches a {@link Player#setShuffleModeEnabled(boolean)} operation.
   *
   * @param player The {@link Player} to which the operation should be dispatched.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return True if the operation was dispatched. False if suppressed.
   */
  boolean dispatchSetShuffleModeEnabled(Player player, boolean shuffleModeEnabled);

  /**
   * Dispatches a {@link Player#stop()} operation.
   *
   * @param player The {@link Player} to which the operation should be dispatched.
   * @param reset Whether the player should be reset.
   * @return True if the operation was dispatched. False if suppressed.
   */
  boolean dispatchStop(Player player, boolean reset);
}
