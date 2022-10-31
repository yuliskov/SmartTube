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
package com.google.android.exoplayer2.ext.flac;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Before;
import org.junit.runner.RunWith;

/** Unit test for {@link FlacBinarySearchSeeker}. */
@RunWith(AndroidJUnit4.class)
public final class FlacBinarySearchSeekerTest {

  private static final String NOSEEKTABLE_FLAC = "bear_no_seek.flac";
  private static final int DURATION_US = 2_741_000;

  @Before
  public void setUp() {
    if (!FlacLibrary.isAvailable()) {
      fail("Flac library not available.");
    }
  }

  public void testGetSeekMap_returnsSeekMapWithCorrectDuration()
      throws IOException, FlacDecoderException, InterruptedException {
    byte[] data =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), NOSEEKTABLE_FLAC);

    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    FlacDecoderJni decoderJni = new FlacDecoderJni();
    decoderJni.setData(input);

    FlacBinarySearchSeeker seeker =
        new FlacBinarySearchSeeker(
            decoderJni.decodeStreamMetadata(),
            /* firstFramePosition= */ 0,
            data.length,
            decoderJni);

    SeekMap seekMap = seeker.getSeekMap();
    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  public void testSetSeekTargetUs_returnsSeekPending()
      throws IOException, FlacDecoderException, InterruptedException {
    byte[] data =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), NOSEEKTABLE_FLAC);

    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    FlacDecoderJni decoderJni = new FlacDecoderJni();
    decoderJni.setData(input);
    FlacBinarySearchSeeker seeker =
        new FlacBinarySearchSeeker(
            decoderJni.decodeStreamMetadata(),
            /* firstFramePosition= */ 0,
            data.length,
            decoderJni);

    seeker.setSeekTargetUs(/* timeUs= */ 1000);
    assertThat(seeker.isSeeking()).isTrue();
  }
}
