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
package com.google.android.exoplayer2.extractor;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Extractor}. */
@RunWith(AndroidJUnit4.class)
public final class ExtractorTest {

  @Test
  public void testConstants() {
    // Sanity check that constant values match those defined by {@link C}.
    assertThat(Extractor.RESULT_END_OF_INPUT).isEqualTo(C.RESULT_END_OF_INPUT);
    // Sanity check that the other constant values don't overlap.
    assertThat(C.RESULT_END_OF_INPUT != Extractor.RESULT_CONTINUE).isTrue();
    assertThat(C.RESULT_END_OF_INPUT != Extractor.RESULT_SEEK).isTrue();
  }

}
