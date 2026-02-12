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
package com.google.android.exoplayer2.source.hls;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.EOFException;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link WebvttExtractor}. */
@RunWith(AndroidJUnit4.class)
public class WebvttExtractorTest {

  @Test
  public void sniff_sniffsWebvttHeaderWithTrailingSpace() throws IOException, InterruptedException {
    byte[] data = new byte[] {'W', 'E', 'B', 'V', 'T', 'T', ' ', '\t'};
    assertThat(sniffData(data)).isTrue();
  }

  @Test
  public void sniff_discardsByteOrderMark() throws IOException, InterruptedException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'W', 'E', 'B', 'V', 'T', 'T', '\n', ' '};
    assertThat(sniffData(data)).isTrue();
  }

  @Test
  public void sniff_failsForIncorrectBom() throws IOException, InterruptedException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBB, 'W', 'E', 'B', 'V', 'T', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  @Test
  public void sniff_failsForIncompleteHeader() throws IOException, InterruptedException {
    byte[] data = new byte[] {'W', 'E', 'B', 'V', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  @Test
  public void sniff_failsForIncorrectHeader() throws IOException, InterruptedException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'W', 'e', 'B', 'V', 'T', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  private static boolean sniffData(byte[] data) throws IOException, InterruptedException {
    ExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    try {
      return new WebvttExtractor(/* language= */ null, new TimestampAdjuster(0)).sniff(input);
    } catch (EOFException e) {
      return false;
    }
  }
}
