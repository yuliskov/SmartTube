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
package com.google.android.exoplayer2.trackselection;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MappingTrackSelector}. */
@RunWith(AndroidJUnit4.class)
public final class MappingTrackSelectorTest {

  private static final RendererCapabilities VIDEO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);
  private static final RendererCapabilities AUDIO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities[] RENDERER_CAPABILITIES = new RendererCapabilities[] {
      VIDEO_CAPABILITIES, AUDIO_CAPABILITIES
  };
  private static final TrackGroup VIDEO_TRACK_GROUP = new TrackGroup(
      Format.createVideoSampleFormat("video", MimeTypes.VIDEO_H264, null, Format.NO_VALUE,
          Format.NO_VALUE, 1024, 768, Format.NO_VALUE, null, null));
  private static final TrackGroup AUDIO_TRACK_GROUP = new TrackGroup(
      Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
          Format.NO_VALUE, 2, 44100, null, null, 0, null));
  private static final TrackGroupArray TRACK_GROUPS = new TrackGroupArray(
      VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP);
  private static final Timeline TIMELINE = new FakeTimeline(/* windowCount= */ 1);

  private static MediaPeriodId periodId;

  @BeforeClass
  public static void setUpBeforeClass() {
    periodId = new MediaPeriodId(TIMELINE.getUidOfPeriod(/* periodIndex= */ 0));
  }

  /**
   * Tests that the video and audio track groups are mapped onto the correct renderers.
   */
  @Test
  public void testMapping() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS, periodId, TIMELINE);
    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, AUDIO_TRACK_GROUP);
  }

  /**
   * Tests that the video and audio track groups are mapped onto the correct renderers when the
   * renderer ordering is reversed.
   */
  @Test
  public void testMappingReverseOrder() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    RendererCapabilities[] reverseOrderRendererCapabilities = new RendererCapabilities[] {
        AUDIO_CAPABILITIES, VIDEO_CAPABILITIES};
    trackSelector.selectTracks(reverseOrderRendererCapabilities, TRACK_GROUPS, periodId, TIMELINE);
    trackSelector.assertMappedTrackGroups(0, AUDIO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, VIDEO_TRACK_GROUP);
  }

  /**
   * Tests video and audio track groups are mapped onto the correct renderers when there are
   * multiple track groups of the same type.
   */
  @Test
  public void testMappingMulti() throws ExoPlaybackException {
    FakeMappingTrackSelector trackSelector = new FakeMappingTrackSelector();
    TrackGroupArray multiTrackGroups = new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP,
        VIDEO_TRACK_GROUP);
    trackSelector.selectTracks(RENDERER_CAPABILITIES, multiTrackGroups, periodId, TIMELINE);
    trackSelector.assertMappedTrackGroups(0, VIDEO_TRACK_GROUP, VIDEO_TRACK_GROUP);
    trackSelector.assertMappedTrackGroups(1, AUDIO_TRACK_GROUP);
  }

  /**
   * A {@link MappingTrackSelector} that stashes the {@link MappedTrackInfo} passed to {@link
   * #selectTracks(MappedTrackInfo, int[][][], int[])}.
   */
  private static final class FakeMappingTrackSelector extends MappingTrackSelector {

    private MappedTrackInfo lastMappedTrackInfo;

    @Override
    protected Pair<RendererConfiguration[], TrackSelection[]> selectTracks(
        MappedTrackInfo mappedTrackInfo,
        int[][][] rendererFormatSupports,
        int[] rendererMixedMimeTypeAdaptationSupports)
        throws ExoPlaybackException {
      int rendererCount = mappedTrackInfo.getRendererCount();
      lastMappedTrackInfo = mappedTrackInfo;
      return Pair.create(
          new RendererConfiguration[rendererCount], new TrackSelection[rendererCount]);
    }

    public void assertMappedTrackGroups(int rendererIndex, TrackGroup... expected) {
      TrackGroupArray rendererTrackGroupArray = lastMappedTrackInfo.getTrackGroups(rendererIndex);
      assertThat(rendererTrackGroupArray.length).isEqualTo(expected.length);
      for (int i = 0; i < expected.length; i++) {
        assertThat(rendererTrackGroupArray.get(i)).isEqualTo(expected[i]);
      }
    }

  }

  /**
   * A {@link RendererCapabilities} that advertises adaptive support for all tracks of a given type.
   */
  private static final class FakeRendererCapabilities implements RendererCapabilities {

    private final int trackType;

    public FakeRendererCapabilities(int trackType) {
      this.trackType = trackType;
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? (FORMAT_HANDLED | ADAPTIVE_SEAMLESS) : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

}
