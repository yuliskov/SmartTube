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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData.Segment;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FakeDataSet} */
@RunWith(AndroidJUnit4.class)
public final class FakeDataSetTest {

  @Test
  public void testMultipleDataSets() {
    byte[][] testData = new byte[4][];
    Uri[] uris = new Uri[3];
    for (int i = 0; i < 4; i++) {
      testData[i] = TestUtil.buildTestData(10, i);
      if (i > 0) {
        uris[i - 1] = Uri.parse("test_uri_" + i);
      }
    }
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .newDefaultData()
            .appendReadData(testData[0])
            .endData()
            .setData(uris[0], testData[1])
            .newData(uris[1])
            .appendReadData(testData[2])
            .endData()
            .setData(uris[2], testData[3]);

    assertThat(fakeDataSet.getAllData().size()).isEqualTo(4);
    assertThat(fakeDataSet.getData("unseen_uri")).isEqualTo(fakeDataSet.getData((Uri) null));
    for (int i = 0; i < 3; i++) {
      assertThat(fakeDataSet.getData(uris[i]).uri).isEqualTo(uris[i]);
    }
    assertThat(fakeDataSet.getData((Uri) null).getData()).isEqualTo(testData[0]);
    for (int i = 1; i < 4; i++) {
      assertThat(fakeDataSet.getData(uris[i - 1]).getData()).isEqualTo(testData[i]);
    }
  }

  @Test
  public void testSegmentTypes() {
    byte[] testData = TestUtil.buildTestData(3);
    Runnable runnable =
        () -> {
          // Do nothing.
        };
    IOException exception = new IOException();
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .newDefaultData()
            .appendReadData(testData)
            .appendReadData(testData)
            .appendReadData(50)
            .appendReadAction(runnable)
            .appendReadError(exception)
            .endData();

    List<Segment> segments = fakeDataSet.getData((Uri) null).getSegments();
    assertThat(segments.size()).isEqualTo(5);
    assertSegment(segments.get(0), testData, 3, 0, null, null);
    assertSegment(segments.get(1), testData, 3, 3, null, null);
    assertSegment(segments.get(2), null, 50, 6, null, null);
    assertSegment(segments.get(3), null, 0, 56, runnable, null);
    assertSegment(segments.get(4), null, 0, 56, null, exception);

    byte[] allData = new byte[6];
    System.arraycopy(testData, 0, allData, 0, 3);
    System.arraycopy(testData, 0, allData, 3, 3);
    assertThat(fakeDataSet.getData((Uri) null).getData()).isEqualTo(allData);
  }

  private static void assertSegment(
      Segment segment,
      byte[] data,
      int length,
      long byteOffset,
      Runnable runnable,
      IOException exception) {
    if (data != null) {
      assertThat(segment.data).isEqualTo(data);
      assertThat(data).hasLength(length);
    } else {
      assertThat(segment.data).isNull();
    }
    assertThat(segment.length).isEqualTo(length);
    assertThat(segment.byteOffset).isEqualTo(byteOffset);
    assertThat(segment.action).isEqualTo(runnable);
    assertThat(segment.isActionSegment()).isEqualTo(runnable != null);
    assertThat(segment.exception).isEqualTo(exception);
    assertThat(segment.isErrorSegment()).isEqualTo(exception != null);
  }
}
