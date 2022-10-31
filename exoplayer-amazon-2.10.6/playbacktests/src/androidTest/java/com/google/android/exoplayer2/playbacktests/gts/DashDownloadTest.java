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
package com.google.android.exoplayer2.playbacktests.gts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.offline.DashDownloader;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests downloaded DASH playbacks. */
@RunWith(AndroidJUnit4.class)
public final class DashDownloadTest {

  private static final String TAG = "DashDownloadTest";

  private static final Uri MANIFEST_URI = Uri.parse(DashTestData.H264_MANIFEST);

  @Rule public ActivityTestRule<HostActivity> testRule = new ActivityTestRule<>(HostActivity.class);

  private DashTestRunner testRunner;
  private File tempFolder;
  private SimpleCache cache;
  private DefaultHttpDataSourceFactory httpDataSourceFactory;
  private CacheDataSourceFactory offlineDataSourceFactory;

  @Before
  public void setUp() throws Exception {
    testRunner =
        new DashTestRunner(TAG, testRule.getActivity())
            .setManifestUrl(DashTestData.H264_MANIFEST)
            .setFullPlaybackNoSeeking(true)
            .setCanIncludeAdditionalVideoFormats(false)
            .setAudioVideoFormats(
                DashTestData.AAC_AUDIO_REPRESENTATION_ID, DashTestData.H264_CDD_FIXED);
    tempFolder = Util.createTempDirectory(testRule.getActivity(), "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
    httpDataSourceFactory = new DefaultHttpDataSourceFactory("ExoPlayer", null);
    offlineDataSourceFactory =
        new CacheDataSourceFactory(
            cache, DummyDataSource.FACTORY, CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  @After
  public void tearDown() {
    testRunner = null;
    Util.recursiveDelete(tempFolder);
    cache = null;
  }

  // Download tests

  @Test
  public void testDownload() throws Exception {
    DashDownloader dashDownloader = downloadContent();
    dashDownloader.download(/* progressListener= */ null);

    testRunner
        .setStreamName("test_h264_fixed_download")
        .setDataSourceFactory(offlineDataSourceFactory)
        .run();

    dashDownloader.remove();

    assertWithMessage("There should be no cache key left").that(cache.getKeys()).isEmpty();
    assertWithMessage("There should be no content left").that(cache.getCacheSpace()).isEqualTo(0);
  }

  private DashDownloader downloadContent() throws Exception {
    DashManifest dashManifest =
        DashUtil.loadManifest(httpDataSourceFactory.createDataSource(), MANIFEST_URI);
    ArrayList<StreamKey> keys = new ArrayList<>();
    for (int pIndex = 0; pIndex < dashManifest.getPeriodCount(); pIndex++) {
      List<AdaptationSet> adaptationSets = dashManifest.getPeriod(pIndex).adaptationSets;
      for (int aIndex = 0; aIndex < adaptationSets.size(); aIndex++) {
        AdaptationSet adaptationSet = adaptationSets.get(aIndex);
        List<Representation> representations = adaptationSet.representations;
        for (int rIndex = 0; rIndex < representations.size(); rIndex++) {
          String id = representations.get(rIndex).format.id;
          if (DashTestData.AAC_AUDIO_REPRESENTATION_ID.equals(id)
              || DashTestData.H264_CDD_FIXED.equals(id)) {
            keys.add(new StreamKey(pIndex, aIndex, rIndex));
          }
        }
      }
    }
    DownloaderConstructorHelper constructorHelper =
        new DownloaderConstructorHelper(cache, httpDataSourceFactory);
    return new DashDownloader(MANIFEST_URI, keys, constructorHelper);
  }

}
