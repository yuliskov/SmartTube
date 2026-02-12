/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static junit.framework.TestCase.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DataSpec}. */
@RunWith(AndroidJUnit4.class)
public class DataSpecTest {

  @Test
  public void createDataSpec_withDefaultValues_setsEmptyHttpRequestParameters() {
    Uri uri = Uri.parse("www.google.com");
    DataSpec dataSpec = new DataSpec(uri);

    assertThat(dataSpec.httpRequestHeaders.isEmpty()).isTrue();

    dataSpec = new DataSpec(uri, /*flags= */ 0);
    assertThat(dataSpec.httpRequestHeaders.isEmpty()).isTrue();

    dataSpec =
        new DataSpec(
            uri,
            /* httpMethod= */ 0,
            /* httpBody= */ new byte[] {0, 0, 0, 0},
            /* absoluteStreamPosition= */ 0,
            /* position= */ 0,
            /* length= */ 1,
            /* key= */ "key",
            /* flags= */ 0);
    assertThat(dataSpec.httpRequestHeaders.isEmpty()).isTrue();
  }

  @Test
  public void createDataSpec_setsHttpRequestParameters() {
    Map<String, String> httpRequestParameters = new HashMap<>();
    httpRequestParameters.put("key1", "value1");
    httpRequestParameters.put("key2", "value2");
    httpRequestParameters.put("key3", "value3");

    DataSpec dataSpec =
        new DataSpec(
            Uri.parse("www.google.com"),
            /* httpMethod= */ 0,
            /* httpBody= */ new byte[] {0, 0, 0, 0},
            /* absoluteStreamPosition= */ 0,
            /* position= */ 0,
            /* length= */ 1,
            /* key= */ "key",
            /* flags= */ 0,
            httpRequestParameters);

    assertThat(dataSpec.httpRequestHeaders).isEqualTo(httpRequestParameters);
  }

  @Test
  public void httpRequestParameters_areReadOnly() {
    DataSpec dataSpec =
        new DataSpec(
            Uri.parse("www.google.com"),
            /* httpMethod= */ 0,
            /* httpBody= */ new byte[] {0, 0, 0, 0},
            /* absoluteStreamPosition= */ 0,
            /* position= */ 0,
            /* length= */ 1,
            /* key= */ "key",
            /* flags= */ 0,
            /* httpRequestHeaders= */ new HashMap<>());

    try {
      dataSpec.httpRequestHeaders.put("key", "value");
      fail();
    } catch (UnsupportedOperationException expected) {
      // Expected
    }
  }

  @Test
  public void copyMethods_copiesHttpRequestHeaders() {
    Map<String, String> httpRequestParameters = new HashMap<>();
    httpRequestParameters.put("key1", "value1");
    httpRequestParameters.put("key2", "value2");
    httpRequestParameters.put("key3", "value3");

    DataSpec dataSpec =
        new DataSpec(
            Uri.parse("www.google.com"),
            /* httpMethod= */ 0,
            /* httpBody= */ new byte[] {0, 0, 0, 0},
            /* absoluteStreamPosition= */ 0,
            /* position= */ 0,
            /* length= */ 1,
            /* key= */ "key",
            /* flags= */ 0,
            httpRequestParameters);

    DataSpec dataSpecCopy = dataSpec.withUri(Uri.parse("www.new-uri.com"));
    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestParameters);

    dataSpecCopy = dataSpec.subrange(2);
    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestParameters);

    dataSpecCopy = dataSpec.subrange(2, 2);
    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestParameters);
  }
}
