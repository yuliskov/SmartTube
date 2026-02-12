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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link C}. */
@RunWith(AndroidJUnit4.class)
public class CTest {

  @SuppressLint("InlinedApi")
  @Test
  public void testConstants() {
    // Sanity check that constant values match those defined by the platform.
    assertThat(C.BUFFER_FLAG_KEY_FRAME).isEqualTo(MediaCodec.BUFFER_FLAG_KEY_FRAME);
    assertThat(C.BUFFER_FLAG_END_OF_STREAM).isEqualTo(MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    assertThat(C.CRYPTO_MODE_AES_CTR).isEqualTo(MediaCodec.CRYPTO_MODE_AES_CTR);
  }

}
