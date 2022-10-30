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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;

/**
 * Assertions for data source tests.
 */
/* package */ final class DataSourceAsserts {

  /**
   * Asserts that data read from a {@link DataSource} matches {@code expected}.
   *
   * @param dataSource The {@link DataSource} through which to read.
   * @param dataSpec The {@link DataSpec} to use when opening the {@link DataSource}.
   * @param expectedData The expected data.
   * @throws IOException If an error occurs reading fom the {@link DataSource}.
   */
  public static void assertDataSourceContent(DataSource dataSource, DataSpec dataSpec,
      byte[] expectedData) throws IOException {
    try {
      long length = dataSource.open(dataSpec);
      assertThat(length).isEqualTo(expectedData.length);
      byte[] readData = TestUtil.readToEnd(dataSource);
      assertThat(readData).isEqualTo(expectedData);
    } finally {
      dataSource.close();
    }
  }

  private DataSourceAsserts() {}

}
