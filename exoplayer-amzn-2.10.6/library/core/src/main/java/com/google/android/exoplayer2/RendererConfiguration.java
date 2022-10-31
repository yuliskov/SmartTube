/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;

/**
 * The configuration of a {@link Renderer}.
 */
public final class RendererConfiguration {

  /**
   * The default configuration.
   */
  public static final RendererConfiguration DEFAULT =
      new RendererConfiguration(C.AUDIO_SESSION_ID_UNSET);

  /**
   * The audio session id to use for tunneling, or {@link C#AUDIO_SESSION_ID_UNSET} if tunneling
   * should not be enabled.
   */
  public final int tunnelingAudioSessionId;

  /**
   * @param tunnelingAudioSessionId The audio session id to use for tunneling, or
   *     {@link C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   */
  public RendererConfiguration(int tunnelingAudioSessionId) {
    this.tunnelingAudioSessionId = tunnelingAudioSessionId;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RendererConfiguration other = (RendererConfiguration) obj;
    return tunnelingAudioSessionId == other.tunnelingAudioSessionId;
  }

  @Override
  public int hashCode() {
    return tunnelingAudioSessionId;
  }

}
