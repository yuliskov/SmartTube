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
 * Default {@link ControlDispatcher} that dispatches all operations to the player without
 * modification.
 */
public class DefaultControlDispatcher implements ControlDispatcher {

  @Override
  public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
    return true;
  }

  @Override
  public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
    return true;
  }

  @Override
  public boolean dispatchSetRepeatMode(Player player, @RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
    return true;
  }

  @Override
  public boolean dispatchSetShuffleModeEnabled(Player player, boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
    return true;
  }

  @Override
  public boolean dispatchStop(Player player, boolean reset) {
    player.stop(reset);
    return true;
  }
}
