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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link Ac3Util}. */
@RunWith(AndroidJUnit4.class)
public final class Ac3UtilTest {

  private static final int TRUEHD_SYNCFRAME_SAMPLE_COUNT = 40;
  private static final byte[] TRUEHD_SYNCFRAME_HEADER =
      Util.getBytesFromHexString("C07504D8F8726FBA0097C00FB7520000");
  private static final byte[] TRUEHD_NON_SYNCFRAME_HEADER =
      Util.getBytesFromHexString("A025048860224E6F6DEDB6D5B6DBAFE6");

  @Test
  public void testParseTrueHdSyncframeAudioSampleCount_nonSyncframe() {
    assertThat(Ac3Util.parseTrueHdSyncframeAudioSampleCount(TRUEHD_NON_SYNCFRAME_HEADER))
        .isEqualTo(0);
  }

  @Test
  public void testParseTrueHdSyncframeAudioSampleCount_syncframe() {
    assertThat(Ac3Util.parseTrueHdSyncframeAudioSampleCount(TRUEHD_SYNCFRAME_HEADER))
        .isEqualTo(TRUEHD_SYNCFRAME_SAMPLE_COUNT);
  }
}
