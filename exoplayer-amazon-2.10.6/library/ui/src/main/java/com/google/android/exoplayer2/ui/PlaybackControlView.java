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
import android.util.AttributeSet;

/** @deprecated Use {@link PlayerControlView}. */
@Deprecated
public class PlaybackControlView extends PlayerControlView {

  /** @deprecated Use {@link com.google.android.exoplayer2.ControlDispatcher}. */
  @Deprecated
  public interface ControlDispatcher extends com.google.android.exoplayer2.ControlDispatcher {}

  @Deprecated
  @SuppressWarnings("deprecation")
  private static final class DefaultControlDispatcher
      extends com.google.android.exoplayer2.DefaultControlDispatcher implements ControlDispatcher {}
  /** @deprecated Use {@link com.google.android.exoplayer2.DefaultControlDispatcher}. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static final ControlDispatcher DEFAULT_CONTROL_DISPATCHER = new DefaultControlDispatcher();

  public PlaybackControlView(Context context) {
    super(context);
  }

  public PlaybackControlView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PlaybackControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public PlaybackControlView(
      Context context, AttributeSet attrs, int defStyleAttr, AttributeSet playbackAttrs) {
    super(context, attrs, defStyleAttr, playbackAttrs);
  }
}
