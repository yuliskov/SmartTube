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
import com.google.android.exoplayer2.extractor.amr.AmrExtractor;
import com.google.android.exoplayer2.extractor.flv.FlvExtractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.extractor.ogg.OggExtractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac4Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.wav.WavExtractor;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultExtractorsFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultExtractorsFactoryTest {

  @Test
  public void testCreateExtractors_returnExpectedClasses() {
    DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();

    Extractor[] extractors = defaultExtractorsFactory.createExtractors();
    List<Class> listCreatedExtractorClasses = new ArrayList<>();
    for (Extractor extractor : extractors) {
      listCreatedExtractorClasses.add(extractor.getClass());
    }

    Class[] expectedExtractorClassses =
        new Class[] {
          MatroskaExtractor.class,
          FragmentedMp4Extractor.class,
          Mp4Extractor.class,
          Mp3Extractor.class,
          AdtsExtractor.class,
          Ac3Extractor.class,
          TsExtractor.class,
          FlvExtractor.class,
          OggExtractor.class,
          PsExtractor.class,
          WavExtractor.class,
          AmrExtractor.class,
          Ac4Extractor.class
        };

    assertThat(listCreatedExtractorClasses).containsNoDuplicates();
    assertThat(listCreatedExtractorClasses).containsExactlyElementsIn(expectedExtractorClassses);
  }
}
