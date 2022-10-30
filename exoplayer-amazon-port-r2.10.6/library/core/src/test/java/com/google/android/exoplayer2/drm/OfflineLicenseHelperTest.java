/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import java.util.HashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

/** Tests {@link OfflineLicenseHelper}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class OfflineLicenseHelperTest {

  private OfflineLicenseHelper<?> offlineLicenseHelper;
  @Mock private MediaDrmCallback mediaDrmCallback;
  @Mock private ExoMediaDrm<ExoMediaCrypto> mediaDrm;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mediaDrm.openSession()).thenReturn(new byte[] {1, 2, 3});
    offlineLicenseHelper =
        new OfflineLicenseHelper<>(C.WIDEVINE_UUID, mediaDrm, mediaDrmCallback, null);
  }

  @After
  public void tearDown() throws Exception {
    offlineLicenseHelper.release();
    offlineLicenseHelper = null;
  }

  @Test
  public void testDownloadRenewReleaseKey() throws Exception {
    setStubLicenseAndPlaybackDurationValues(1000, 200);

    byte[] keySetId = {2, 5, 8};
    setStubKeySetId(keySetId);

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    assertOfflineLicenseKeySetIdEqual(keySetId, offlineLicenseKeySetId);

    byte[] keySetId2 = {6, 7, 0, 1, 4};
    setStubKeySetId(keySetId2);

    byte[] offlineLicenseKeySetId2 = offlineLicenseHelper.renewLicense(offlineLicenseKeySetId);

    assertOfflineLicenseKeySetIdEqual(keySetId2, offlineLicenseKeySetId2);

    offlineLicenseHelper.releaseLicense(offlineLicenseKeySetId2);
  }

  @Test
  public void testDownloadLicenseFailsIfNullInitData() throws Exception {
    try {
      offlineLicenseHelper.downloadLicense(null);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testDownloadLicenseFailsIfNoKeySetIdIsReturned() throws Exception {
    setStubLicenseAndPlaybackDurationValues(1000, 200);

    try {
      offlineLicenseHelper.downloadLicense(newDrmInitData());
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void testDownloadLicenseDoesNotFailIfDurationNotAvailable() throws Exception {
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    assertThat(offlineLicenseKeySetId).isNotNull();
  }

  @Test
  public void testGetLicenseDurationRemainingSec() throws Exception {
    long licenseDuration = 1000;
    int playbackDuration = 200;
    setStubLicenseAndPlaybackDurationValues(licenseDuration, playbackDuration);
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    Pair<Long, Long> licenseDurationRemainingSec =
        offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);

    assertThat(licenseDurationRemainingSec.first).isEqualTo(licenseDuration);
    assertThat(licenseDurationRemainingSec.second).isEqualTo(playbackDuration);
  }

  @Test
  public void testGetLicenseDurationRemainingSecExpiredLicense() throws Exception {
    long licenseDuration = 0;
    int playbackDuration = 0;
    setStubLicenseAndPlaybackDurationValues(licenseDuration, playbackDuration);
    setDefaultStubKeySetId();

    byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData());

    Pair<Long, Long> licenseDurationRemainingSec =
        offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);

    assertThat(licenseDurationRemainingSec.first).isEqualTo(licenseDuration);
    assertThat(licenseDurationRemainingSec.second).isEqualTo(playbackDuration);
  }

  private void setDefaultStubKeySetId()
      throws android.media.NotProvisionedException, android.media.DeniedByServerException {
    setStubKeySetId(new byte[] {2, 5, 8});
  }

  private void setStubKeySetId(byte[] keySetId)
      throws android.media.NotProvisionedException, android.media.DeniedByServerException {
    when(mediaDrm.provideKeyResponse(any(byte[].class), any())).thenReturn(keySetId);
  }

  private static void assertOfflineLicenseKeySetIdEqual(
      byte[] expectedKeySetId, byte[] actualKeySetId) throws Exception {
    assertThat(actualKeySetId).isNotNull();
    assertThat(actualKeySetId).isEqualTo(expectedKeySetId);
  }

  private void setStubLicenseAndPlaybackDurationValues(
      long licenseDuration, long playbackDuration) {
    HashMap<String, String> keyStatus = new HashMap<>();
    keyStatus.put(
        WidevineUtil.PROPERTY_LICENSE_DURATION_REMAINING, String.valueOf(licenseDuration));
    keyStatus.put(
        WidevineUtil.PROPERTY_PLAYBACK_DURATION_REMAINING, String.valueOf(playbackDuration));
    when(mediaDrm.queryKeyStatus(any(byte[].class))).thenReturn(keyStatus);
  }

  private static DrmInitData newDrmInitData() {
    return new DrmInitData(
        new SchemeData(C.WIDEVINE_UUID, "mimeType", new byte[] {1, 4, 7, 0, 3, 6}));
  }
}
