/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FakeDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class FakeDataSourceTest {

  private static final String URI_STRING = "test://test.test";
  private static final byte[] BUFFER = new byte[500];
  private static final byte[] TEST_DATA = TestUtil.buildTestData(15);
  private static final byte[] TEST_DATA_PART_1 = Arrays.copyOf(TEST_DATA, 10);
  private static final byte[] TEST_DATA_PART_2 = Arrays.copyOfRange(TEST_DATA, 10, 15);

  private static Uri uri;
  private static FakeDataSet fakeDataSet;

  @Before
  public void setUp() {
    uri = Uri.parse(URI_STRING);
    fakeDataSet =
        new FakeDataSet()
            .newData(uri.toString())
            .appendReadData(TEST_DATA_PART_1)
            .appendReadData(TEST_DATA_PART_2)
            .endData();
  }

  @Test
  public void testReadFull() throws IOException {
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);
    assertThat(dataSource.open(new DataSpec(uri))).isEqualTo(15);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(10);
    assertBuffer(TEST_DATA_PART_1);
    assertThat(dataSource.read(BUFFER, 10, BUFFER.length)).isEqualTo(5);
    assertBuffer(TEST_DATA);
    assertThat(dataSource.read(BUFFER, 15, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    assertBuffer(TEST_DATA);
    assertThat(dataSource.read(BUFFER, 20, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testReadPartialOpenEnded() throws IOException {
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);
    assertThat(dataSource.open(new DataSpec(uri, 7, C.LENGTH_UNSET, null))).isEqualTo(8);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(3);
    assertBuffer(TEST_DATA_PART_1, 7, 3);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(5);
    assertBuffer(TEST_DATA_PART_2);
    assertThat(dataSource.read(BUFFER, 15, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testReadPartialBounded() throws IOException {
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);
    assertThat(dataSource.open(new DataSpec(uri, 9, 3, null))).isEqualTo(3);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(1);
    assertBuffer(TEST_DATA_PART_1, 9, 1);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(2);
    assertBuffer(TEST_DATA_PART_2, 0, 2);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();

    assertThat(dataSource.open(new DataSpec(uri, 11, 4, null))).isEqualTo(4);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(4);
    assertBuffer(TEST_DATA_PART_2, 1, 4);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testDummyData() throws IOException {
    FakeDataSource dataSource =
        new FakeDataSource(
            new FakeDataSet()
                .newData(uri.toString())
                .appendReadData(100)
                .appendReadData(TEST_DATA)
                .appendReadData(200)
                .endData());
    assertThat(dataSource.open(new DataSpec(uri))).isEqualTo(315);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(100);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(200);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testException() throws IOException {
    String errorMessage = "error, error, error";
    IOException exception = new IOException(errorMessage);
    FakeDataSource dataSource =
        new FakeDataSource(
            new FakeDataSet()
                .newData(uri.toString())
                .appendReadData(TEST_DATA)
                .appendReadError(exception)
                .appendReadData(TEST_DATA)
                .endData());
    assertThat(dataSource.open(new DataSpec(uri))).isEqualTo(30);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    try {
      dataSource.read(BUFFER, 0, BUFFER.length);
      fail("IOException expected.");
    } catch (IOException e) {
      assertThat(e).hasMessageThat().isEqualTo(errorMessage);
    }
    try {
      dataSource.read(BUFFER, 0, BUFFER.length);
      fail("IOException expected.");
    } catch (IOException e) {
      assertThat(e).hasMessageThat().isEqualTo(errorMessage);
    }
    dataSource.close();
    assertThat(dataSource.open(new DataSpec(uri, 15, 15, null))).isEqualTo(15);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testRunnable() throws IOException {
    TestRunnable[] runnables = new TestRunnable[3];
    for (int i = 0; i < 3; i++) {
      runnables[i] = new TestRunnable();
    }
    FakeDataSource dataSource =
        new FakeDataSource(
            new FakeDataSet()
                .newData(uri.toString())
                .appendReadData(TEST_DATA)
                .appendReadAction(runnables[0])
                .appendReadData(TEST_DATA)
                .appendReadAction(runnables[1])
                .appendReadAction(runnables[2])
                .appendReadData(TEST_DATA)
                .endData());
    assertThat(dataSource.open(new DataSpec(uri))).isEqualTo(45);
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    for (int i = 0; i < 3; i++) {
      assertThat(runnables[i].ran).isFalse();
    }
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    assertThat(runnables[0].ran).isTrue();
    assertThat(runnables[1].ran).isFalse();
    assertThat(runnables[2].ran).isFalse();
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(15);
    assertBuffer(TEST_DATA);
    for (int i = 0; i < 3; i++) {
      assertThat(runnables[i].ran).isTrue();
    }
    assertThat(dataSource.read(BUFFER, 0, BUFFER.length)).isEqualTo(C.RESULT_END_OF_INPUT);
    dataSource.close();
  }

  @Test
  public void testOpenSourceFailures() throws IOException {
    // Empty data.
    FakeDataSource dataSource =
        new FakeDataSource(new FakeDataSet().newData(uri.toString()).endData());
    try {
      dataSource.open(new DataSpec(uri));
      fail("IOException expected.");
    } catch (IOException e) {
      // Expected.
    } finally {
      dataSource.close();
    }

    // Non-existent data
    dataSource = new FakeDataSource(new FakeDataSet());
    try {
      dataSource.open(new DataSpec(uri));
      fail("IOException expected.");
    } catch (IOException e) {
      // Expected.
    } finally {
      dataSource.close();
    }

    // DataSpec out of bounds.
    dataSource =
        new FakeDataSource(
            new FakeDataSet()
                .newDefaultData()
                .appendReadData(TestUtil.buildTestData(10))
                .endData());
    try {
      dataSource.open(new DataSpec(uri, 5, 10, null));
      fail("IOException expected.");
    } catch (IOException e) {
      // Expected.
    } finally {
      dataSource.close();
    }
  }

  private static void assertBuffer(byte[] expected) {
    assertBuffer(expected, 0, expected.length);
  }

  private static void assertBuffer(byte[] expected, int expectedStart, int expectedLength) {
    for (int i = 0; i < expectedLength; i++) {
      assertThat(BUFFER[i]).isEqualTo(expected[i + expectedStart]);
    }
  }

  private static final class TestRunnable implements Runnable {

    public boolean ran;

    @Override
    public void run() {
      ran = true;
    }
  }
}
