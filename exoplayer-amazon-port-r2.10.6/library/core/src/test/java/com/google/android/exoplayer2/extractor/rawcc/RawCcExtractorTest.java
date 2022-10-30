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
package com.google.android.exoplayer2.extractor.rawcc;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RawCcExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class RawCcExtractorTest {

  @Test
  public void testRawCcSample() throws Exception {
    ExtractorAsserts.assertBehavior(
        () ->
            new RawCcExtractor(
                Format.createTextContainerFormat(
                    /* id= */ null,
                    /* label= */ null,
                    /* containerMimeType= */ null,
                    /* sampleMimeType= */ MimeTypes.APPLICATION_CEA608,
                    /* codecs= */ "cea608",
                    /* bitrate= */ Format.NO_VALUE,
                    /* selectionFlags= */ 0,
                    /* roleFlags= */ 0,
                    /* language= */ null,
                    /* accessibilityChannel= */ 1)),
        "rawcc/sample.rawcc");
  }
}
