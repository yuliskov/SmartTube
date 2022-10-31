/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DataSourceInputStream}. */
@RunWith(AndroidJUnit4.class)
public final class DataSourceInputStreamTest {

  private static final byte[] TEST_DATA = TestUtil.buildTestData(16);

  @Test
  public void testReadSingleBytes() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // No bytes read yet.
    assertThat(inputStream.bytesRead()).isEqualTo(0);
    // Read bytes.
    for (int i = 0; i < TEST_DATA.length; i++) {
      int readByte = inputStream.read();
      assertThat(0 <= readByte && readByte < 256).isTrue();
      assertThat(readByte).isEqualTo(TEST_DATA[i] & 0xFF);
      assertThat(inputStream.bytesRead()).isEqualTo(i + 1);
    }
    // Check end of stream.
    assertThat(inputStream.read()).isEqualTo(-1);
    assertThat(inputStream.bytesRead()).isEqualTo(TEST_DATA.length);
    // Check close succeeds.
    inputStream.close();
  }

  @Test
  public void testRead() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // Read bytes.
    byte[] readBytes = new byte[TEST_DATA.length];
    int totalBytesRead = 0;
    while (totalBytesRead < TEST_DATA.length) {
      int bytesRead = inputStream.read(readBytes, totalBytesRead,
          TEST_DATA.length - totalBytesRead);
      assertThat(bytesRead).isGreaterThan(0);
      totalBytesRead += bytesRead;
      assertThat(inputStream.bytesRead()).isEqualTo(totalBytesRead);
    }
    // Check the read data.
    assertThat(readBytes).isEqualTo(TEST_DATA);
    // Check end of stream.
    assertThat(inputStream.bytesRead()).isEqualTo(TEST_DATA.length);
    assertThat(totalBytesRead).isEqualTo(TEST_DATA.length);
    assertThat(inputStream.read()).isEqualTo(-1);
    // Check close succeeds.
    inputStream.close();
  }

  @Test
  public void testSkip() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // Skip bytes.
    long totalBytesSkipped = 0;
    while (totalBytesSkipped < TEST_DATA.length) {
      long bytesSkipped = inputStream.skip(Long.MAX_VALUE);
      assertThat(bytesSkipped > 0).isTrue();
      totalBytesSkipped += bytesSkipped;
      assertThat(inputStream.bytesRead()).isEqualTo(totalBytesSkipped);
    }
    // Check end of stream.
    assertThat(inputStream.bytesRead()).isEqualTo(TEST_DATA.length);
    assertThat(totalBytesSkipped).isEqualTo(TEST_DATA.length);
    assertThat(inputStream.read()).isEqualTo(-1);
    // Check close succeeds.
    inputStream.close();
  }

  private static DataSourceInputStream buildTestInputStream() {
    FakeDataSource fakeDataSource = new FakeDataSource();
    fakeDataSource.getDataSet().newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 5))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 5, 10))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 10, 15))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 15, TEST_DATA.length));
    return new DataSourceInputStream(fakeDataSource, new DataSpec(Uri.EMPTY));
  }

}
