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
package com.google.android.exoplayer2.ext.rtmp;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultDataSource} with RTMP URIs. */
@RunWith(AndroidJUnit4.class)
public final class DefaultDataSourceTest {

  @Test
  public void openRtmpDataSpec_instantiatesRtmpDataSourceViaReflection() throws IOException {
    DefaultDataSource dataSource =
        new DefaultDataSource(
            ApplicationProvider.getApplicationContext(),
            "userAgent",
            /* allowCrossProtocolRedirects= */ false);
    DataSpec dataSpec = new DataSpec(Uri.parse("rtmp://test.com/stream"));
    try {
      dataSource.open(dataSpec);
    } catch (UnsatisfiedLinkError e) {
      // RtmpDataSource was successfully instantiated (test run using Gradle).
    } catch (UnsupportedOperationException e) {
      // RtmpDataSource was successfully instantiated (test run using Blaze).
    }
  }
}
