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

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;

import android.annotation.TargetApi;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.DebugRenderersFactory;
import com.google.android.exoplayer2.testutil.DecoderCountersUtil;
import com.google.android.exoplayer2.testutil.ExoHostedTest;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.testutil.HostActivity.HostedTest;
import com.google.android.exoplayer2.testutil.MetricsLogger;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link DashHostedTest} builder. */
public final class DashTestRunner {

  static final int VIDEO_RENDERER_INDEX = 0;
  static final int AUDIO_RENDERER_INDEX = 1;

  private static final long TEST_TIMEOUT_MS = 5 * 60 * 1000;

  // Whether adaptive tests should enable video formats beyond those mandated by the Android CDD
  // if the device advertises support for them.
  private static final boolean ALLOW_ADDITIONAL_VIDEO_FORMATS = Util.SDK_INT >= 24;

  private static final String AUDIO_TAG_SUFFIX = ":Audio";
  private static final String VIDEO_TAG_SUFFIX = ":Video";

  private static final int MIN_LOADABLE_RETRY_COUNT = 10;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;

  private static final String WIDEVINE_SECURITY_LEVEL_1 = "L1";
  private static final String WIDEVINE_SECURITY_LEVEL_3 = "L3";
  private static final String SECURITY_LEVEL_PROPERTY = "securityLevel";

  private final String tag;
  private final HostActivity activity;

  private String streamName;
  private boolean fullPlaybackNoSeeking;
  private String audioFormat;
  private boolean canIncludeAdditionalVideoFormats;
  private ActionSchedule actionSchedule;
  private byte[] offlineLicenseKeySetId;
  private String[] videoFormats;
  private String manifestUrl;
  private boolean useL1Widevine;
  private String widevineLicenseUrl;
  private DataSource.Factory dataSourceFactory;

  @SuppressWarnings("ResourceType")
  public static boolean isL1WidevineAvailable(String mimeType) {
    if (Util.SDK_INT >= 18) {
      try {
        // Force L3 if secure decoder is not available.
        if (MediaCodecUtil.getDecoderInfo(mimeType, /* secure= */ true, /* tunneling= */ false)
            == null) {
          return false;
        }
        MediaDrm mediaDrm = MediaDrmBuilder.build();
        String securityProperty = mediaDrm.getPropertyString(SECURITY_LEVEL_PROPERTY);
        mediaDrm.release();
        return WIDEVINE_SECURITY_LEVEL_1.equals(securityProperty);
      } catch (MediaCodecUtil.DecoderQueryException e) {
        throw new IllegalStateException(e);
      }
    }
    return false;
  }

  public DashTestRunner(String tag, HostActivity activity) {
    this.tag = tag;
    this.activity = activity;
  }

  public DashTestRunner setStreamName(String streamName) {
    this.streamName = streamName;
    return this;
  }

  public DashTestRunner setFullPlaybackNoSeeking(boolean fullPlaybackNoSeeking) {
    this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
    return this;
  }

  public DashTestRunner setCanIncludeAdditionalVideoFormats(
      boolean canIncludeAdditionalVideoFormats) {
    this.canIncludeAdditionalVideoFormats = canIncludeAdditionalVideoFormats
        && ALLOW_ADDITIONAL_VIDEO_FORMATS;
    return this;
  }

  public DashTestRunner setActionSchedule(ActionSchedule actionSchedule) {
    this.actionSchedule = actionSchedule;
    return this;
  }

  public DashTestRunner setOfflineLicenseKeySetId(byte[] offlineLicenseKeySetId) {
    this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    return this;
  }

  public DashTestRunner setAudioVideoFormats(String audioFormat, String... videoFormats) {
    this.audioFormat = audioFormat;
    this.videoFormats = videoFormats;
    return this;
  }

  public DashTestRunner setManifestUrl(String manifestUrl) {
    this.manifestUrl = manifestUrl;
    return this;
  }

  public DashTestRunner setWidevineInfo(String mimeType, boolean videoIdRequiredInLicenseUrl) {
    this.useL1Widevine = isL1WidevineAvailable(mimeType);
    this.widevineLicenseUrl = DashTestData.getWidevineLicenseUrl(videoIdRequiredInLicenseUrl,
        useL1Widevine);
    return this;
  }

  public DashTestRunner setDataSourceFactory(DataSource.Factory dataSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
    return this;
  }

  public void run() {
    DashHostedTest test = createDashHostedTest(canIncludeAdditionalVideoFormats, false);
    activity.runTest(test, TEST_TIMEOUT_MS);
    // Retry test exactly once if adaptive test fails due to excessive dropped buffers when
    // playing non-CDD required formats (b/28220076).
    if (test.needsCddLimitedRetry) {
      activity.runTest(createDashHostedTest(false, true), TEST_TIMEOUT_MS);
    }
  }

  private DashHostedTest createDashHostedTest(
      boolean canIncludeAdditionalVideoFormats, boolean isCddLimitedRetry) {
    MetricsLogger metricsLogger = MetricsLogger.Factory.createDefault(tag);
    return new DashHostedTest(tag, streamName, manifestUrl, metricsLogger, fullPlaybackNoSeeking,
        audioFormat, canIncludeAdditionalVideoFormats, isCddLimitedRetry, actionSchedule,
        offlineLicenseKeySetId, widevineLicenseUrl, useL1Widevine, dataSourceFactory,
        videoFormats);
  }

  /**
   * A {@link HostedTest} for DASH playback tests.
   */
  private static final class DashHostedTest extends ExoHostedTest {

    private final String streamName;
    private final String manifestUrl;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final boolean isCddLimitedRetry;
    private final DashTestTrackSelector trackSelector;
    private final byte[] offlineLicenseKeySetId;
    private final String widevineLicenseUrl;
    private final boolean useL1Widevine;
    private final DataSource.Factory dataSourceFactory;

    private boolean needsCddLimitedRetry;

    /**
     * @param tag A tag to use for logging.
     * @param streamName The name of the test stream for metric logging.
     * @param manifestUrl The manifest url.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking Whether the test will play the entire source with no seeking.
     * @param audioFormat The audio format.
     * @param canIncludeAdditionalVideoFormats Whether to use video formats in addition to those
     *     listed in the videoFormats argument, if the device is capable of playing them.
     * @param isCddLimitedRetry Whether this is a CDD limited retry following a previous failure.
     * @param actionSchedule The action schedule for the test.
     * @param offlineLicenseKeySetId The key set id of the license to be used.
     * @param widevineLicenseUrl If the video is Widevine encrypted, this is the license url
     *     otherwise null.
     * @param useL1Widevine Whether to use L1 Widevine.
     * @param dataSourceFactory If not null, used to load manifest and media.
     * @param videoFormats The video formats.
     */
    private DashHostedTest(String tag, String streamName, String manifestUrl,
        MetricsLogger metricsLogger, boolean fullPlaybackNoSeeking, String audioFormat,
        boolean canIncludeAdditionalVideoFormats, boolean isCddLimitedRetry,
        ActionSchedule actionSchedule, byte[] offlineLicenseKeySetId, String widevineLicenseUrl,
        boolean useL1Widevine, DataSource.Factory dataSourceFactory, String... videoFormats) {
      super(tag, fullPlaybackNoSeeking);
      Assertions.checkArgument(!(isCddLimitedRetry && canIncludeAdditionalVideoFormats));
      this.streamName = streamName;
      this.manifestUrl = manifestUrl;
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.isCddLimitedRetry = isCddLimitedRetry;
      this.offlineLicenseKeySetId = offlineLicenseKeySetId;
      this.widevineLicenseUrl = widevineLicenseUrl;
      this.useL1Widevine = useL1Widevine;
      this.dataSourceFactory = dataSourceFactory;
      trackSelector = new DashTestTrackSelector(tag, audioFormat, videoFormats,
          canIncludeAdditionalVideoFormats);
      if (actionSchedule != null) {
        setSchedule(actionSchedule);
      }
    }

    @Override
    protected DefaultTrackSelector buildTrackSelector(HostActivity host) {
      return trackSelector;
    }

    @Override
    protected DefaultDrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(
        final String userAgent) {
      if (widevineLicenseUrl == null) {
        return null;
      }
      try {
        MediaDrmCallback drmCallback = new HttpMediaDrmCallback(widevineLicenseUrl,
            new DefaultHttpDataSourceFactory(userAgent));
        DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager =
            DefaultDrmSessionManager.newWidevineInstance(drmCallback, null);
        if (!useL1Widevine) {
          drmSessionManager.setPropertyString(
              SECURITY_LEVEL_PROPERTY, WIDEVINE_SECURITY_LEVEL_3);
        }
        if (offlineLicenseKeySetId != null) {
          drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK,
              offlineLicenseKeySetId);
        }
        return drmSessionManager;
      } catch (UnsupportedDrmException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    protected SimpleExoPlayer buildExoPlayer(
        HostActivity host,
        Surface surface,
        MappingTrackSelector trackSelector,
        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
      SimpleExoPlayer player =
          ExoPlayerFactory.newSimpleInstance(
              host,
              new DebugRenderersFactory(host),
              trackSelector,
              new DefaultLoadControl(),
              drmSessionManager);
      player.setVideoSurface(surface);
      return player;
    }

    @Override
    protected MediaSource buildSource(HostActivity host, String userAgent) {
      DataSource.Factory dataSourceFactory =
          this.dataSourceFactory != null
              ? this.dataSourceFactory
              : new DefaultDataSourceFactory(host, userAgent);
      Uri manifestUri = Uri.parse(manifestUrl);
      return new DashMediaSource.Factory(dataSourceFactory)
          .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(MIN_LOADABLE_RETRY_COUNT))
          .createMediaSource(manifestUri);
    }

    @Override
    protected void onTestFinished(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      metricsLogger.logMetric(MetricsLogger.KEY_TEST_NAME, streamName);
      metricsLogger.logMetric(MetricsLogger.KEY_IS_CDD_LIMITED_RETRY, isCddLimitedRetry);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_DROPPED_COUNT,
          videoCounters.droppedBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT,
          videoCounters.maxConsecutiveDroppedBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_SKIPPED_COUNT,
          videoCounters.skippedOutputBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_RENDERED_COUNT,
          videoCounters.renderedOutputBufferCount);
      metricsLogger.close();

      if (fullPlaybackNoSeeking) {
        // We shouldn't have skipped any output buffers.
        DecoderCountersUtil
            .assertSkippedOutputBufferCount(tag + AUDIO_TAG_SUFFIX, audioCounters, 0);
        DecoderCountersUtil
            .assertSkippedOutputBufferCount(tag + VIDEO_TAG_SUFFIX, videoCounters, 0);
        // We allow one fewer output buffer due to the way that MediaCodecRenderer and the
        // underlying decoders handle the end of stream. This should be tightened up in the future.
        DecoderCountersUtil.assertTotalBufferCount(tag + AUDIO_TAG_SUFFIX, audioCounters,
            audioCounters.inputBufferCount - 1, audioCounters.inputBufferCount);
        DecoderCountersUtil.assertTotalBufferCount(tag + VIDEO_TAG_SUFFIX, videoCounters,
            videoCounters.inputBufferCount - 1, videoCounters.inputBufferCount);
      }
      try {
        int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
            * DecoderCountersUtil.getTotalBufferCount(videoCounters));
        // Assert that performance is acceptable.
        // Assert that total dropped frames were within limit.
        DecoderCountersUtil.assertDroppedBufferLimit(tag + VIDEO_TAG_SUFFIX, videoCounters,
            droppedFrameLimit);
        // Assert that consecutive dropped frames were within limit.
        DecoderCountersUtil.assertConsecutiveDroppedBufferLimit(tag + VIDEO_TAG_SUFFIX,
            videoCounters, MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES);
      } catch (AssertionError e) {
        if (trackSelector.includedAdditionalVideoFormats) {
          // Retry limiting to CDD mandated formats (b/28220076).
          Log.e(tag, "Too many dropped or consecutive dropped frames.", e);
          needsCddLimitedRetry = true;
        } else {
          throw e;
        }
      }
    }
  }

  private static final class DashTestTrackSelector extends DefaultTrackSelector {

    private final String tag;
    private final String audioFormatId;
    private final String[] videoFormatIds;
    private final boolean canIncludeAdditionalVideoFormats;

    public boolean includedAdditionalVideoFormats;

    private DashTestTrackSelector(String tag, String audioFormatId, String[] videoFormatIds,
        boolean canIncludeAdditionalVideoFormats) {
      super(new RandomTrackSelection.Factory(/* seed= */ 0));
      this.tag = tag;
      this.audioFormatId = audioFormatId;
      this.videoFormatIds = videoFormatIds;
      this.canIncludeAdditionalVideoFormats = canIncludeAdditionalVideoFormats;
    }

    @Override
    protected TrackSelection.Definition[] selectAllTracks(
        MappedTrackInfo mappedTrackInfo,
        int[][][] rendererFormatSupports,
        int[] rendererMixedMimeTypeAdaptationSupports,
        Parameters parameters)
        throws ExoPlaybackException {
      Assertions.checkState(
          mappedTrackInfo.getRendererType(VIDEO_RENDERER_INDEX) == C.TRACK_TYPE_VIDEO);
      Assertions.checkState(
          mappedTrackInfo.getRendererType(AUDIO_RENDERER_INDEX) == C.TRACK_TYPE_AUDIO);
      TrackGroupArray videoTrackGroups = mappedTrackInfo.getTrackGroups(VIDEO_RENDERER_INDEX);
      TrackGroupArray audioTrackGroups = mappedTrackInfo.getTrackGroups(AUDIO_RENDERER_INDEX);
      Assertions.checkState(videoTrackGroups.length == 1);
      Assertions.checkState(audioTrackGroups.length == 1);
      TrackSelection.Definition[] definitions =
          new TrackSelection.Definition[mappedTrackInfo.getRendererCount()];
      definitions[VIDEO_RENDERER_INDEX] =
          new TrackSelection.Definition(
              videoTrackGroups.get(0),
              getVideoTrackIndices(
                  videoTrackGroups.get(0),
                  rendererFormatSupports[VIDEO_RENDERER_INDEX][0],
                  videoFormatIds,
                  canIncludeAdditionalVideoFormats));
      definitions[AUDIO_RENDERER_INDEX] =
          new TrackSelection.Definition(
              audioTrackGroups.get(0), getTrackIndex(audioTrackGroups.get(0), audioFormatId));
      includedAdditionalVideoFormats =
          definitions[VIDEO_RENDERER_INDEX].tracks.length > videoFormatIds.length;
      return definitions;
    }

    private int[] getVideoTrackIndices(
        TrackGroup trackGroup,
        int[] formatSupports,
        String[] formatIds,
        boolean canIncludeAdditionalFormats) {
      List<Integer> trackIndices = new ArrayList<>();

      // Always select explicitly listed representations.
      for (String formatId : formatIds) {
        int trackIndex = getTrackIndex(trackGroup, formatId);
        Log.d(tag, "Adding base video format: "
            + Format.toLogString(trackGroup.getFormat(trackIndex)));
        trackIndices.add(trackIndex);
      }

      // Select additional video representations, if supported by the device.
      if (canIncludeAdditionalFormats) {
        for (int i = 0; i < trackGroup.length; i++) {
          if (!trackIndices.contains(i) && isFormatHandled(formatSupports[i])) {
            Log.d(tag, "Adding extra video format: "
                + Format.toLogString(trackGroup.getFormat(i)));
            trackIndices.add(i);
          }
        }
      }

      int[] trackIndicesArray = Util.toArray(trackIndices);
      Arrays.sort(trackIndicesArray);
      return trackIndicesArray;
    }

    private static int getTrackIndex(TrackGroup trackGroup, String formatId) {
      for (int i = 0; i < trackGroup.length; i++) {
        if (trackGroup.getFormat(i).id.equals(formatId)) {
          return i;
        }
      }
      throw new IllegalStateException("Format " + formatId + " not found.");
    }

    private static boolean isFormatHandled(int formatSupport) {
      return (formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK)
          == RendererCapabilities.FORMAT_HANDLED;
    }

  }

  /**
   * Creates a new {@code MediaDrm} object. The encapsulation ensures that the tests can be
   * executed for API level < 18.
   */
  @TargetApi(18)
  private static final class MediaDrmBuilder {

    public static MediaDrm build () {
      try {
        return new MediaDrm(WIDEVINE_UUID);
      } catch (UnsupportedSchemeException e) {
        throw new IllegalStateException(e);
      }
    }

  }

}
