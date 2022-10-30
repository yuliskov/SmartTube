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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultLoadErrorHandlingPolicy}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultLoadErrorHandlingPolicyTest {

  @Test
  public void getBlacklistDurationMsFor_blacklist404() {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(
            404, "Not Found", Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception))
        .isEqualTo(DefaultLoadErrorHandlingPolicy.DEFAULT_TRACK_BLACKLIST_MS);
  }

  @Test
  public void getBlacklistDurationMsFor_blacklist410() {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(
            410, "Gone", Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception))
        .isEqualTo(DefaultLoadErrorHandlingPolicy.DEFAULT_TRACK_BLACKLIST_MS);
  }

  @Test
  public void getBlacklistDurationMsFor_dontBlacklistUnexpectedHttpCodes() {
    InvalidResponseCodeException exception =
        new InvalidResponseCodeException(
            500, "Internal Server Error", Collections.emptyMap(), new DataSpec(Uri.EMPTY));
    assertThat(getDefaultPolicyBlacklistOutputFor(exception)).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getBlacklistDurationMsFor_dontBlacklistUnexpectedExceptions() {
    IOException exception = new IOException();
    assertThat(getDefaultPolicyBlacklistOutputFor(exception)).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getRetryDelayMsFor_dontRetryParserException() {
    assertThat(getDefaultPolicyRetryDelayOutputFor(new ParserException(), 1))
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getRetryDelayMsFor_successiveRetryDelays() {
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 3)).isEqualTo(2000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 5)).isEqualTo(4000);
    assertThat(getDefaultPolicyRetryDelayOutputFor(new IOException(), 9)).isEqualTo(5000);
  }

  private static long getDefaultPolicyBlacklistOutputFor(IOException exception) {
    return new DefaultLoadErrorHandlingPolicy()
        .getBlacklistDurationMsFor(
            C.DATA_TYPE_MEDIA, /* loadDurationMs= */ 1000, exception, /* errorCount= */ 1);
  }

  private static long getDefaultPolicyRetryDelayOutputFor(IOException exception, int errorCount) {
    return new DefaultLoadErrorHandlingPolicy()
        .getRetryDelayMsFor(C.DATA_TYPE_MEDIA, /* loadDurationMs= */ 1000, exception, errorCount);
  }
}
