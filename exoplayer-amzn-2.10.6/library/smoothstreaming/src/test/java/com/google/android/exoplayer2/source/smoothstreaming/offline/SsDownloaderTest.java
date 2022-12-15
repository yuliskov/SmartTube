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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.DownloaderFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit tests for {@link SsDownloader}. */
@RunWith(AndroidJUnit4.class)
public final class SsDownloaderTest {

  @Test
  public void createWithDefaultDownloaderFactory() throws Exception {
    DownloaderConstructorHelper constructorHelper =
        new DownloaderConstructorHelper(Mockito.mock(Cache.class), DummyDataSource.FACTORY);
    DownloaderFactory factory = new DefaultDownloaderFactory(constructorHelper);

    Downloader downloader =
        factory.createDownloader(
            new DownloadRequest(
                "id",
                DownloadRequest.TYPE_SS,
                Uri.parse("https://www.test.com/download"),
                Collections.singletonList(new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0)),
                /* customCacheKey= */ null,
                /* data= */ null));
    assertThat(downloader).isInstanceOf(SsDownloader.class);
  }
}
