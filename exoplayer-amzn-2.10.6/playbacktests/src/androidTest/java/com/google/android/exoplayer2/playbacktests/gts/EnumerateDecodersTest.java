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
package com.google.android.exoplayer2.playbacktests.gts;


import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.testutil.MetricsLogger;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests enumeration of decoders using {@link MediaCodecUtil}. */
@RunWith(AndroidJUnit4.class)
public class EnumerateDecodersTest {

  private static final String TAG = "EnumerateDecodersTest";

  private MetricsLogger metricsLogger;

  @Before
  public void setUp() {
    metricsLogger = MetricsLogger.Factory.createDefault(TAG);
  }

  @Test
  public void testEnumerateDecoders() throws Exception {
    enumerateDecoders(MimeTypes.VIDEO_H263);
    enumerateDecoders(MimeTypes.VIDEO_H264);
    enumerateDecoders(MimeTypes.VIDEO_H265);
    enumerateDecoders(MimeTypes.VIDEO_VP8);
    enumerateDecoders(MimeTypes.VIDEO_VP9);
    enumerateDecoders(MimeTypes.VIDEO_MP4V);
    enumerateDecoders(MimeTypes.VIDEO_MPEG);
    enumerateDecoders(MimeTypes.VIDEO_MPEG2);
    enumerateDecoders(MimeTypes.VIDEO_VC1);
    enumerateDecoders(MimeTypes.AUDIO_AAC);
    enumerateDecoders(MimeTypes.AUDIO_MPEG_L1);
    enumerateDecoders(MimeTypes.AUDIO_MPEG_L2);
    enumerateDecoders(MimeTypes.AUDIO_MPEG);
    enumerateDecoders(MimeTypes.AUDIO_RAW);
    enumerateDecoders(MimeTypes.AUDIO_ALAW);
    enumerateDecoders(MimeTypes.AUDIO_MLAW);
    enumerateDecoders(MimeTypes.AUDIO_AC3);
    enumerateDecoders(MimeTypes.AUDIO_E_AC3);
    enumerateDecoders(MimeTypes.AUDIO_E_AC3_JOC);
    enumerateDecoders(MimeTypes.AUDIO_TRUEHD);
    enumerateDecoders(MimeTypes.AUDIO_DTS);
    enumerateDecoders(MimeTypes.AUDIO_DTS_HD);
    enumerateDecoders(MimeTypes.AUDIO_DTS_EXPRESS);
    enumerateDecoders(MimeTypes.AUDIO_VORBIS);
    enumerateDecoders(MimeTypes.AUDIO_OPUS);
    enumerateDecoders(MimeTypes.AUDIO_AMR_NB);
    enumerateDecoders(MimeTypes.AUDIO_AMR_WB);
    enumerateDecoders(MimeTypes.AUDIO_FLAC);
    enumerateDecoders(MimeTypes.AUDIO_ALAC);
    enumerateDecoders(MimeTypes.AUDIO_MSGSM);
  }

  private void enumerateDecoders(String mimeType) throws DecoderQueryException {
    logDecoderInfos(mimeType, /* secure= */ false, /* tunneling= */ false);
    logDecoderInfos(mimeType, /* secure= */ true, /* tunneling= */ false);
    logDecoderInfos(mimeType, /* secure= */ false, /* tunneling= */ true);
    logDecoderInfos(mimeType, /* secure= */ true, /* tunneling= */ true);
  }

  private void logDecoderInfos(String mimeType, boolean secure, boolean tunneling)
      throws DecoderQueryException {
    List<MediaCodecInfo> mediaCodecInfos =
        MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling);
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
      CodecCapabilities capabilities = Assertions.checkNotNull(mediaCodecInfo.capabilities);
      metricsLogger.logMetric(
          "capabilities_" + mediaCodecInfo.name, codecCapabilitiesToString(mimeType, capabilities));
    }
  }

  private static String codecCapabilitiesToString(
      String requestedMimeType, CodecCapabilities codecCapabilities) {
    boolean isVideo = MimeTypes.isVideo(requestedMimeType);
    boolean isAudio = MimeTypes.isAudio(requestedMimeType);
    StringBuilder result = new StringBuilder();
    result.append("[requestedMimeType=").append(requestedMimeType);
    if (Util.SDK_INT >= 21) {
      result.append(", mimeType=").append(codecCapabilities.getMimeType());
    }
    result.append(", profileLevels=");
    appendProfileLevels(codecCapabilities.profileLevels, result);
    if (Util.SDK_INT >= 23) {
      result
          .append(", maxSupportedInstances=")
          .append(codecCapabilities.getMaxSupportedInstances());
    }
    if (Util.SDK_INT >= 21) {
      if (isVideo) {
        result.append(", videoCapabilities=");
        appendVideoCapabilities(codecCapabilities.getVideoCapabilities(), result);
        result.append(", colorFormats=").append(Arrays.toString(codecCapabilities.colorFormats));
      } else if (isAudio) {
        result.append(", audioCapabilities=");
        appendAudioCapabilities(codecCapabilities.getAudioCapabilities(), result);
      }
    }
    if (Util.SDK_INT >= 19
        && isVideo
        && codecCapabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)) {
      result.append(", FEATURE_AdaptivePlayback");
    }
    if (Util.SDK_INT >= 21
        && isVideo
        && codecCapabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback)) {
      result.append(", FEATURE_SecurePlayback");
    }
    if (Util.SDK_INT >= 26
        && isVideo
        && codecCapabilities.isFeatureSupported(CodecCapabilities.FEATURE_PartialFrame)) {
      result.append(", FEATURE_PartialFrame");
    }
    if (Util.SDK_INT >= 21
        && (isVideo || isAudio)
        && codecCapabilities.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback)) {
      result.append(", FEATURE_TunneledPlayback");
    }
    result.append(']');
    return result.toString();
  }

  private static void appendAudioCapabilities(
      AudioCapabilities audioCapabilities, StringBuilder result) {
    result
        .append("[bitrateRange=")
        .append(audioCapabilities.getBitrateRange())
        .append(", maxInputChannelCount=")
        .append(audioCapabilities.getMaxInputChannelCount())
        .append(", supportedSampleRateRanges=")
        .append(Arrays.toString(audioCapabilities.getSupportedSampleRateRanges()))
        .append(']');
  }

  private static void appendVideoCapabilities(
      VideoCapabilities videoCapabilities, StringBuilder result) {
    result
        .append("[bitrateRange=")
        .append(videoCapabilities.getBitrateRange())
        .append(", heightAlignment=")
        .append(videoCapabilities.getHeightAlignment())
        .append(", widthAlignment=")
        .append(videoCapabilities.getWidthAlignment())
        .append(", supportedWidths=")
        .append(videoCapabilities.getSupportedWidths())
        .append(", supportedHeights=")
        .append(videoCapabilities.getSupportedHeights())
        .append(", supportedFrameRates=")
        .append(videoCapabilities.getSupportedFrameRates())
        .append(']');
  }

  private static void appendProfileLevels(CodecProfileLevel[] profileLevels, StringBuilder result) {
    result.append('[');
    int count = profileLevels.length;
    for (int i = 0; i < count; i++) {
      CodecProfileLevel profileLevel = profileLevels[i];
      if (i != 0) {
        result.append(", ");
      }
      result
          .append("[profile=")
          .append(profileLevel.profile)
          .append(", level=")
          .append(profileLevel.level)
          .append(']');
    }
    result.append(']');
  }
}
