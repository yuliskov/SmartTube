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
package com.google.android.exoplayer2.audio;

import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import androidx.annotation.Nullable;

/**
 * Represents auxiliary effect information, which can be used to attach an auxiliary effect to an
 * underlying {@link AudioTrack}.
 *
 * <p>Auxiliary effects can only be applied if the application has the {@code
 * android.permission.MODIFY_AUDIO_SETTINGS} permission. Apps are responsible for retaining the
 * associated audio effect instance and releasing it when it's no longer needed. See the
 * documentation of {@link AudioEffect} for more information.
 */
public final class AuxEffectInfo {

  /** Value for {@link #effectId} representing no auxiliary effect. */
  public static final int NO_AUX_EFFECT_ID = 0;

  /**
   * The identifier of the effect, or {@link #NO_AUX_EFFECT_ID} if there is no effect.
   *
   * @see android.media.AudioTrack#attachAuxEffect(int)
   */
  public final int effectId;
  /**
   * The send level for the effect.
   *
   * @see android.media.AudioTrack#setAuxEffectSendLevel(float)
   */
  public final float sendLevel;

  /**
   * Creates an instance with the given effect identifier and send level.
   *
   * @param effectId The effect identifier. This is the value returned by {@link
   *     AudioEffect#getId()} on the effect, or {@value NO_AUX_EFFECT_ID} which represents no
   *     effect. This value is passed to {@link AudioTrack#attachAuxEffect(int)} on the underlying
   *     audio track.
   * @param sendLevel The send level for the effect, where 0 represents no effect and a value of 1
   *     is full send. If {@code effectId} is not {@value #NO_AUX_EFFECT_ID}, this value is passed
   *     to {@link AudioTrack#setAuxEffectSendLevel(float)} on the underlying audio track.
   */
  public AuxEffectInfo(int effectId, float sendLevel) {
    this.effectId = effectId;
    this.sendLevel = sendLevel;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuxEffectInfo auxEffectInfo = (AuxEffectInfo) o;
    return effectId == auxEffectInfo.effectId
        && Float.compare(auxEffectInfo.sendLevel, sendLevel) == 0;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + effectId;
    result = 31 * result + Float.floatToIntBits(sendLevel);
    return result;
  }
}
