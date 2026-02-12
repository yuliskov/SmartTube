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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TsDurationReader}. */
@RunWith(AndroidJUnit4.class)
public final class TsDurationReaderTest {

  private TsDurationReader tsDurationReader;
  private PositionHolder seekPositionHolder;

  @Before
  public void setUp() {
    tsDurationReader = new TsDurationReader();
    seekPositionHolder = new PositionHolder();
  }

  @Test
  public void testIsDurationReadPending_returnFalseByDefault() {
    assertThat(tsDurationReader.isDurationReadFinished()).isFalse();
  }

  @Test
  public void testReadDuration_returnsCorrectDuration() throws IOException, InterruptedException {
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(), "ts/bbb_2500ms.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    while (!tsDurationReader.isDurationReadFinished()) {
      int result = tsDurationReader.readDuration(input, seekPositionHolder, /* pcrPid= */ 256);
      if (result == Extractor.RESULT_END_OF_INPUT) {
        break;
      }
      if (result == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }
    assertThat(tsDurationReader.getDurationUs() / 1000).isEqualTo(2500);
  }

  @Test
  public void testReadDuration_midStream_returnsCorrectDuration()
      throws IOException, InterruptedException {
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(), "ts/bbb_2500ms.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    input.setPosition(1234);
    while (!tsDurationReader.isDurationReadFinished()) {
      int result = tsDurationReader.readDuration(input, seekPositionHolder, /* pcrPid= */ 256);
      if (result == Extractor.RESULT_END_OF_INPUT) {
        break;
      }
      if (result == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }
    assertThat(tsDurationReader.getDurationUs() / 1000).isEqualTo(2500);
  }
}
