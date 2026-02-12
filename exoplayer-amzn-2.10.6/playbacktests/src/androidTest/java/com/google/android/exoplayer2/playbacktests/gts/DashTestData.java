/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.playbacktests.gts;

import com.google.android.exoplayer2.util.Util;

/**
 * Test data for DASH tests.
 */
public final class DashTestData {

  private static final String BASE_URL =
      "https://storage.googleapis.com/exoplayer-test-media-1/gen-4/";

  private static final String BASE_URL_SCREENS = BASE_URL + "screens/dash-vod-single-segment/";
  private static final String BASE_URL_COMMON_ENCRYPTION = BASE_URL + "common-encryption/";
  // Clear content manifests.
  public static final String H264_MANIFEST = BASE_URL_SCREENS + "manifest-h264.mpd";
  public static final String H265_MANIFEST = BASE_URL_SCREENS + "manifest-h265.mpd";
  public static final String VP9_MANIFEST = BASE_URL_SCREENS + "manifest-vp9.mpd";
  public static final String H264_23_MANIFEST = BASE_URL_SCREENS + "manifest-h264-23.mpd";
  public static final String H264_24_MANIFEST = BASE_URL_SCREENS + "manifest-h264-24.mpd";
  public static final String H264_29_MANIFEST = BASE_URL_SCREENS + "manifest-h264-29.mpd";
  // Widevine encrypted content manifests.
  public static final String WIDEVINE_H264_MANIFEST = BASE_URL_SCREENS + "manifest-h264-enc.mpd";
  public static final String WIDEVINE_H265_MANIFEST = BASE_URL_SCREENS + "manifest-h265-enc.mpd";
  public static final String WIDEVINE_VP9_MANIFEST = BASE_URL_SCREENS + "manifest-vp9-enc.mpd";
  public static final String WIDEVINE_H264_23_MANIFEST =
      BASE_URL_SCREENS + "manifest-h264-23-enc.mpd";
  public static final String WIDEVINE_H264_24_MANIFEST =
      BASE_URL_SCREENS + "manifest-h264-24-enc.mpd";
  public static final String WIDEVINE_H264_29_MANIFEST =
      BASE_URL_SCREENS + "manifest-h264-29-enc.mpd";

  // Widevine encrypted content manifests using different common encryption schemes.
  public static final String WIDEVINE_SCHEME_CENC = BASE_URL_COMMON_ENCRYPTION + "tears-cenc.mpd";
  public static final String WIDEVINE_SCHEME_CBC1 =
      BASE_URL_COMMON_ENCRYPTION + "tears-aes-cbc1.mpd";
  public static final String WIDEVINE_SCHEME_CBCS =
      BASE_URL_COMMON_ENCRYPTION + "tears-aes-cbcs.mpd";

  public static final String AAC_AUDIO_REPRESENTATION_ID = "141";
  public static final String H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "avc-baseline-240";
  public static final String H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "avc-baseline-480";
  public static final String H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "avc-main-240";
  public static final String H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "avc-main-480";
  // The highest quality H264 format mandated by the Android CDD.
  public static final String H264_CDD_FIXED = Util.SDK_INT < 23
      ? H264_BASELINE_480P_VIDEO_REPRESENTATION_ID : H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  public static final String[] H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  public static final String H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-23";
  public static final String H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-24";
  public static final String H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-29";

  public static final String H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "hevc-main-288";
  public static final String H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "hevc-main-360";
  // The highest quality H265 format mandated by the Android CDD.
  public static final String H265_CDD_FIXED = H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  public static final String[] H265_CDD_ADAPTIVE =
      new String[] {
          H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  public static final String VP9_VORBIS_AUDIO_REPRESENTATION_ID = "4";
  public static final String VP9_180P_VIDEO_REPRESENTATION_ID = "0";
  public static final String VP9_360P_VIDEO_REPRESENTATION_ID = "1";
  // The highest quality VP9 format mandated by the Android CDD.
  public static final String VP9_CDD_FIXED = VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  public static final String[] VP9_CDD_ADAPTIVE =
      new String[] {
          VP9_180P_VIDEO_REPRESENTATION_ID,
          VP9_360P_VIDEO_REPRESENTATION_ID};

  // Widevine encrypted content representation ids.
  public static final String WIDEVINE_AAC_AUDIO_REPRESENTATION_ID = "0";
  public static final String WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "3";
  public static final String WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "4";
  public static final String WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "5";
  // The highest quality H264 format mandated by the Android CDD.
  public static final String WIDEVINE_H264_CDD_FIXED = Util.SDK_INT < 23
      ? WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID
      : WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  public static final String[] WIDEVINE_H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
          WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  public static final String WIDEVINE_H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID = "3";
  public static final String WIDEVINE_H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID = "3";
  public static final String WIDEVINE_H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID = "3";

  public static final String WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "3";
  // The highest quality H265 format mandated by the Android CDD.
  public static final String WIDEVINE_H265_CDD_FIXED =
      WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  public static final String[] WIDEVINE_H265_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  public static final String WIDEVINE_VP9_AAC_AUDIO_REPRESENTATION_ID = "0";
  public static final String WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID = "3";
  // The highest quality VP9 format mandated by the Android CDD.
  public static final String WIDEVINE_VP9_CDD_FIXED = WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  public static final String[] WIDEVINE_VP9_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID};

  private static final String WIDEVINE_LICENSE_URL =
      "https://proxy.uat.widevine.com/proxy?provider=widevine_test";
  private static final String WIDEVINE_SW_CRYPTO_CONTENT_ID = "&video_id=exoplayer_test_1";
  private static final String WIDEVINE_HW_SECURE_DECODE_CONTENT_ID = "&video_id=exoplayer_test_2";

  public static String getWidevineLicenseUrl(boolean videoIdRequiredInLicenseUrl,
      boolean useL1Widevine) {
    if (!videoIdRequiredInLicenseUrl) {
      return WIDEVINE_LICENSE_URL;
    } else {
      return WIDEVINE_LICENSE_URL
          + (useL1Widevine ? WIDEVINE_HW_SECURE_DECODE_CONTENT_ID : WIDEVINE_SW_CRYPTO_CONTENT_ID);
    }
  }

  private DashTestData() {
  }

}
