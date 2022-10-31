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
package com.google.android.exoplayer2.extractor.mp3;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Mp3Extractor}. */
@RunWith(AndroidJUnit4.class)
public final class Mp3ExtractorTest {

  @Test
  public void testMp3Sample() throws Exception {
    ExtractorAsserts.assertBehavior(Mp3Extractor::new, "mp3/bear.mp3");
  }

  @Test
  public void testTrimmedMp3Sample() throws Exception {
    ExtractorAsserts.assertBehavior(Mp3Extractor::new, "mp3/play-trimmed.mp3");
  }
}
