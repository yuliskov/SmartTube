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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.C.RESULT_END_OF_INPUT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DataSchemeDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class DataSchemeDataSourceTest {

  private static final String DATA_SCHEME_URI =
      "data:text/plain;base64,eyJwcm92aWRlciI6IndpZGV2aW5lX3Rlc3QiLCJjb250ZW50X2lkIjoiTWpBeE5WOTBaV"
          + "0Z5Y3c9PSIsImtleV9pZHMiOlsiMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAiXX0=";
  private DataSource schemeDataDataSource;

  @Before
  public void setUp() {
    schemeDataDataSource = new DataSchemeDataSource();
  }

  @Test
  public void testBase64Data() throws IOException {
    DataSpec dataSpec = buildDataSpec(DATA_SCHEME_URI);
    DataSourceAsserts.assertDataSourceContent(
        schemeDataDataSource,
        dataSpec,
        Util.getUtf8Bytes(
            "{\"provider\":\"widevine_test\",\"content_id\":\"MjAxNV90ZWFycw==\",\"key_ids\":"
                + "[\"00000000000000000000000000000000\"]}"));
  }

  @Test
  public void testAsciiData() throws IOException {
    DataSourceAsserts.assertDataSourceContent(
        schemeDataDataSource,
        buildDataSpec("data:,A%20brief%20note"),
        Util.getUtf8Bytes("A brief note"));
  }

  @Test
  public void testPartialReads() throws IOException {
    byte[] buffer = new byte[18];
    DataSpec dataSpec = buildDataSpec("data:,012345678901234567");
    assertThat(schemeDataDataSource.open(dataSpec)).isEqualTo(18);
    assertThat(schemeDataDataSource.read(buffer, 0, 9)).isEqualTo(9);
    assertThat(schemeDataDataSource.read(buffer, 3, 0)).isEqualTo(0);
    assertThat(schemeDataDataSource.read(buffer, 9, 15)).isEqualTo(9);
    assertThat(schemeDataDataSource.read(buffer, 1, 0)).isEqualTo(0);
    assertThat(schemeDataDataSource.read(buffer, 1, 1)).isEqualTo(RESULT_END_OF_INPUT);
    assertThat(Util.fromUtf8Bytes(buffer, 0, 18)).isEqualTo("012345678901234567");
  }

  @Test
  public void testSequentialRangeRequests() throws IOException {
    DataSpec dataSpec =
        buildDataSpec(DATA_SCHEME_URI, /* position= */ 1, /* length= */ C.LENGTH_UNSET);
    DataSourceAsserts.assertDataSourceContent(
        schemeDataDataSource,
        dataSpec,
        Util.getUtf8Bytes(
            "\"provider\":\"widevine_test\",\"content_id\":\"MjAxNV90ZWFycw==\",\"key_ids\":"
                + "[\"00000000000000000000000000000000\"]}"));
    dataSpec = buildDataSpec(DATA_SCHEME_URI, /* position= */ 10, /* length= */ C.LENGTH_UNSET);
    DataSourceAsserts.assertDataSourceContent(
        schemeDataDataSource,
        dataSpec,
        Util.getUtf8Bytes(
            "\":\"widevine_test\",\"content_id\":\"MjAxNV90ZWFycw==\",\"key_ids\":"
                + "[\"00000000000000000000000000000000\"]}"));
    dataSpec = buildDataSpec(DATA_SCHEME_URI, /* position= */ 15, /* length= */ 5);
    DataSourceAsserts.assertDataSourceContent(
        schemeDataDataSource, dataSpec, Util.getUtf8Bytes("devin"));
  }

  @Test
  public void testInvalidStartPositionRequest() throws IOException {
    try {
      // Try to open a range starting one byte beyond the resource's length.
      schemeDataDataSource.open(
          buildDataSpec(DATA_SCHEME_URI, /* position= */ 108, /* length= */ C.LENGTH_UNSET));
      fail();
    } catch (DataSourceException e) {
      assertThat(e.reason).isEqualTo(DataSourceException.POSITION_OUT_OF_RANGE);
    }
  }

  @Test
  public void testRangeExceedingResourceLengthRequest() throws IOException {
    try {
      // Try to open a range exceeding the resource's length.
      schemeDataDataSource.open(
          buildDataSpec(DATA_SCHEME_URI, /* position= */ 97, /* length= */ 11));
      fail();
    } catch (DataSourceException e) {
      assertThat(e.reason).isEqualTo(DataSourceException.POSITION_OUT_OF_RANGE);
    }
  }

  @Test
  public void testIncorrectScheme() {
    try {
      schemeDataDataSource.open(buildDataSpec("http://www.google.com"));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void testMalformedData() {
    try {
      schemeDataDataSource.open(buildDataSpec("data:text/plain;base64,,This%20is%20Content"));
      fail();
    } catch (IOException e) {
      // Expected.
    }
    try {
      schemeDataDataSource.open(buildDataSpec("data:text/plain;base64,IncorrectPadding=="));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  private static DataSpec buildDataSpec(String uriString) {
    return buildDataSpec(uriString, /* position= */ 0, /* length= */ C.LENGTH_UNSET);
  }

  private static DataSpec buildDataSpec(String uriString, int position, int length) {
    return new DataSpec(Uri.parse(uriString), position, length, /* key= */ null);
  }

}
