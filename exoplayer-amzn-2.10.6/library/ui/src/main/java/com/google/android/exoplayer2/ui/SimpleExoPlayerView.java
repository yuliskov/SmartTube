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
package com.google.android.exoplayer2.ui;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

/** @deprecated Use {@link PlayerView}. */
@Deprecated
public final class SimpleExoPlayerView extends PlayerView {

  public SimpleExoPlayerView(Context context) {
    super(context);
  }

  public SimpleExoPlayerView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SimpleExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  /**
   * Switches the view targeted by a given {@link SimpleExoPlayer}.
   *
   * @param player The player whose target view is being switched.
   * @param oldPlayerView The old view to detach from the player.
   * @param newPlayerView The new view to attach to the player.
   * @deprecated Use {@link PlayerView#switchTargetView(Player, PlayerView, PlayerView)} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static void switchTargetView(
      @NonNull SimpleExoPlayer player,
      @Nullable SimpleExoPlayerView oldPlayerView,
      @Nullable SimpleExoPlayerView newPlayerView) {
    PlayerView.switchTargetView(player, oldPlayerView, newPlayerView);
  }

}
