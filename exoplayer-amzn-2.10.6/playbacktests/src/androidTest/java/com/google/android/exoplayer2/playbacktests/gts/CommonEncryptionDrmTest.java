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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test playback of encrypted DASH streams using different CENC scheme types. */
@RunWith(AndroidJUnit4.class)
public final class CommonEncryptionDrmTest {

  private static final String TAG = "CencDrmTest";

  private static final String ID_AUDIO = "0";
  private static final String[] IDS_VIDEO = new String[] {"1", "2"};

  // Seeks help reproduce playback issues in certain devices.
  private static final ActionSchedule ACTION_SCHEDULE_WITH_SEEKS = new ActionSchedule.Builder(TAG)
      .waitForPlaybackState(Player.STATE_READY).delay(30000).seekAndWait(300000).delay(10000)
      .seekAndWait(270000).delay(10000).seekAndWait(200000).delay(10000).seekAndWait(732000)
      .build();

  @Rule public ActivityTestRule<HostActivity> testRule = new ActivityTestRule<>(HostActivity.class);

  private DashTestRunner testRunner;

  @Before
  public void setUp() {
    testRunner =
        new DashTestRunner(TAG, testRule.getActivity())
            .setWidevineInfo(MimeTypes.VIDEO_H264, false)
            .setActionSchedule(ACTION_SCHEDULE_WITH_SEEKS)
            .setAudioVideoFormats(ID_AUDIO, IDS_VIDEO)
            .setCanIncludeAdditionalVideoFormats(true);
  }

  @After
  public void tearDown() {
    testRunner = null;
  }

  @Test
  public void testCencSchemeTypeV18() {
    if (Util.SDK_INT < 18) {
      // Pass.
      return;
    }
    testRunner
        .setStreamName("test_widevine_h264_scheme_cenc")
        .setManifestUrl(DashTestData.WIDEVINE_SCHEME_CENC)
        .run();
  }

  @Test
  public void testCbc1SchemeTypeV25() {
    if (Util.SDK_INT < 25) {
      // cbc1 support was added in API 24, but it is stable from API 25 onwards.
      // See [internal: b/65634809].
      // Pass.
      return;
    }
    testRunner
        .setStreamName("test_widevine_h264_scheme_cbc1")
        .setManifestUrl(DashTestData.WIDEVINE_SCHEME_CBC1)
        .run();
  }

  @Test
  public void testCbcsSchemeTypeV25() {
    if (Util.SDK_INT < 25) {
      // cbcs support was added in API 24, but it is stable from API 25 onwards.
      // See [internal: b/65634809].
      // Pass.
      return;
    }
    testRunner
        .setStreamName("test_widevine_h264_scheme_cbcs")
        .setManifestUrl(DashTestData.WIDEVINE_SCHEME_CBCS)
        .run();
  }

  @Test
  public void testCensSchemeTypeV25() {
    // TODO: Implement once content is available. Track [internal: b/31219813].
  }
}
