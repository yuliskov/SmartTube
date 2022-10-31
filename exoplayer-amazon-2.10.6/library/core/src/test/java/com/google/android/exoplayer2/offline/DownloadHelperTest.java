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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.offline.DownloadHelper.Callback;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link DownloadHelper}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadHelperTest {

  private static final String TEST_DOWNLOAD_TYPE = "downloadType";
  private static final String TEST_CACHE_KEY = "cacheKey";
  private static final Timeline TEST_TIMELINE =
      new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ new Object()));
  private static final Object TEST_MANIFEST = new Object();

  private static final Format VIDEO_FORMAT_LOW = createVideoFormat(/* bitrate= */ 200_000);
  private static final Format VIDEO_FORMAT_HIGH = createVideoFormat(/* bitrate= */ 800_000);
  private static final Format AUDIO_FORMAT_US = createAudioFormat(/* language= */ "US");
  private static final Format AUDIO_FORMAT_ZH = createAudioFormat(/* language= */ "ZH");
  private static final Format TEXT_FORMAT_US = createTextFormat(/* language= */ "US");
  private static final Format TEXT_FORMAT_ZH = createTextFormat(/* language= */ "ZH");

  private static final TrackGroup TRACK_GROUP_VIDEO_BOTH =
      new TrackGroup(VIDEO_FORMAT_LOW, VIDEO_FORMAT_HIGH);
  private static final TrackGroup TRACK_GROUP_VIDEO_SINGLE = new TrackGroup(VIDEO_FORMAT_LOW);
  private static final TrackGroup TRACK_GROUP_AUDIO_US = new TrackGroup(AUDIO_FORMAT_US);
  private static final TrackGroup TRACK_GROUP_AUDIO_ZH = new TrackGroup(AUDIO_FORMAT_ZH);
  private static final TrackGroup TRACK_GROUP_TEXT_US = new TrackGroup(TEXT_FORMAT_US);
  private static final TrackGroup TRACK_GROUP_TEXT_ZH = new TrackGroup(TEXT_FORMAT_ZH);
  private static final TrackGroupArray TRACK_GROUP_ARRAY_ALL =
      new TrackGroupArray(
          TRACK_GROUP_VIDEO_BOTH,
          TRACK_GROUP_AUDIO_US,
          TRACK_GROUP_AUDIO_ZH,
          TRACK_GROUP_TEXT_US,
          TRACK_GROUP_TEXT_ZH);
  private static final TrackGroupArray TRACK_GROUP_ARRAY_SINGLE =
      new TrackGroupArray(TRACK_GROUP_VIDEO_SINGLE, TRACK_GROUP_AUDIO_US);
  private static final TrackGroupArray[] TRACK_GROUP_ARRAYS =
      new TrackGroupArray[] {TRACK_GROUP_ARRAY_ALL, TRACK_GROUP_ARRAY_SINGLE};

  private Uri testUri;

  private DownloadHelper downloadHelper;

  @Before
  public void setUp() {
    testUri = Uri.parse("http://test.uri");

    FakeRenderer videoRenderer = new FakeRenderer(VIDEO_FORMAT_LOW, VIDEO_FORMAT_HIGH);
    FakeRenderer audioRenderer = new FakeRenderer(AUDIO_FORMAT_US, AUDIO_FORMAT_ZH);
    FakeRenderer textRenderer = new FakeRenderer(TEXT_FORMAT_US, TEXT_FORMAT_ZH);
    RenderersFactory renderersFactory =
        (handler, videoListener, audioListener, metadata, text, drm) ->
            new Renderer[] {textRenderer, audioRenderer, videoRenderer};

    downloadHelper =
        new DownloadHelper(
            TEST_DOWNLOAD_TYPE,
            testUri,
            TEST_CACHE_KEY,
            new TestMediaSource(),
            DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
            Util.getRendererCapabilities(renderersFactory, /* drmSessionManager= */ null));
  }

  @Test
  public void getManifest_returnsManifest() throws Exception {
    prepareDownloadHelper(downloadHelper);

    Object manifest = downloadHelper.getManifest();

    assertThat(manifest).isEqualTo(TEST_MANIFEST);
  }

  @Test
  public void getPeriodCount_returnsPeriodCount() throws Exception {
    prepareDownloadHelper(downloadHelper);

    int periodCount = downloadHelper.getPeriodCount();

    assertThat(periodCount).isEqualTo(2);
  }

  @Test
  public void getTrackGroups_returnsTrackGroups() throws Exception {
    prepareDownloadHelper(downloadHelper);

    TrackGroupArray trackGroupArrayPeriod0 = downloadHelper.getTrackGroups(/* periodIndex= */ 0);
    TrackGroupArray trackGroupArrayPeriod1 = downloadHelper.getTrackGroups(/* periodIndex= */ 1);

    assertThat(trackGroupArrayPeriod0).isEqualTo(TRACK_GROUP_ARRAYS[0]);
    assertThat(trackGroupArrayPeriod1).isEqualTo(TRACK_GROUP_ARRAYS[1]);
  }

  @Test
  public void getMappedTrackInfo_returnsMappedTrackInfo() throws Exception {
    prepareDownloadHelper(downloadHelper);

    MappedTrackInfo mappedTracks0 = downloadHelper.getMappedTrackInfo(/* periodIndex= */ 0);
    MappedTrackInfo mappedTracks1 = downloadHelper.getMappedTrackInfo(/* periodIndex= */ 1);

    assertThat(mappedTracks0.getRendererCount()).isEqualTo(3);
    assertThat(mappedTracks0.getRendererType(/* rendererIndex= */ 0)).isEqualTo(C.TRACK_TYPE_TEXT);
    assertThat(mappedTracks0.getRendererType(/* rendererIndex= */ 1)).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(mappedTracks0.getRendererType(/* rendererIndex= */ 2)).isEqualTo(C.TRACK_TYPE_VIDEO);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 0).length).isEqualTo(2);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 1).length).isEqualTo(2);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 2).length).isEqualTo(1);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 0).get(/* index= */ 0))
        .isEqualTo(TRACK_GROUP_TEXT_US);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 0).get(/* index= */ 1))
        .isEqualTo(TRACK_GROUP_TEXT_ZH);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 1).get(/* index= */ 0))
        .isEqualTo(TRACK_GROUP_AUDIO_US);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 1).get(/* index= */ 1))
        .isEqualTo(TRACK_GROUP_AUDIO_ZH);
    assertThat(mappedTracks0.getTrackGroups(/* rendererIndex= */ 2).get(/* index= */ 0))
        .isEqualTo(TRACK_GROUP_VIDEO_BOTH);

    assertThat(mappedTracks1.getRendererCount()).isEqualTo(3);
    assertThat(mappedTracks1.getRendererType(/* rendererIndex= */ 0)).isEqualTo(C.TRACK_TYPE_TEXT);
    assertThat(mappedTracks1.getRendererType(/* rendererIndex= */ 1)).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(mappedTracks1.getRendererType(/* rendererIndex= */ 2)).isEqualTo(C.TRACK_TYPE_VIDEO);
    assertThat(mappedTracks1.getTrackGroups(/* rendererIndex= */ 0).length).isEqualTo(0);
    assertThat(mappedTracks1.getTrackGroups(/* rendererIndex= */ 1).length).isEqualTo(1);
    assertThat(mappedTracks1.getTrackGroups(/* rendererIndex= */ 2).length).isEqualTo(1);
    assertThat(mappedTracks1.getTrackGroups(/* rendererIndex= */ 1).get(/* index= */ 0))
        .isEqualTo(TRACK_GROUP_AUDIO_US);
    assertThat(mappedTracks1.getTrackGroups(/* rendererIndex= */ 2).get(/* index= */ 0))
        .isEqualTo(TRACK_GROUP_VIDEO_SINGLE);
  }

  @Test
  public void getTrackSelections_returnsInitialSelection() throws Exception {
    prepareDownloadHelper(downloadHelper);

    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertSingleTrackSelectionEquals(selectedText0, TRACK_GROUP_TEXT_US, 0);
    assertSingleTrackSelectionEquals(selectedAudio0, TRACK_GROUP_AUDIO_US, 0);
    assertSingleTrackSelectionEquals(selectedVideo0, TRACK_GROUP_VIDEO_BOTH, 1);

    assertThat(selectedText1).isEmpty();
    assertSingleTrackSelectionEquals(selectedAudio1, TRACK_GROUP_AUDIO_US, 0);
    assertSingleTrackSelectionEquals(selectedVideo1, TRACK_GROUP_VIDEO_SINGLE, 0);
  }

  @Test
  public void getTrackSelections_afterClearTrackSelections_isEmpty() throws Exception {
    prepareDownloadHelper(downloadHelper);

    // Clear only one period selection to verify second period selection is untouched.
    downloadHelper.clearTrackSelections(/* periodIndex= */ 0);
    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertThat(selectedText0).isEmpty();
    assertThat(selectedAudio0).isEmpty();
    assertThat(selectedVideo0).isEmpty();

    // Verify
    assertThat(selectedText1).isEmpty();
    assertSingleTrackSelectionEquals(selectedAudio1, TRACK_GROUP_AUDIO_US, 0);
    assertSingleTrackSelectionEquals(selectedVideo1, TRACK_GROUP_VIDEO_SINGLE, 0);
  }

  @Test
  public void getTrackSelections_afterReplaceTrackSelections_returnsNewSelections()
      throws Exception {
    prepareDownloadHelper(downloadHelper);
    DefaultTrackSelector.Parameters parameters =
        new ParametersBuilder()
            .setPreferredAudioLanguage("ZH")
            .setPreferredTextLanguage("ZH")
            .setRendererDisabled(/* rendererIndex= */ 2, true)
            .build();

    // Replace only one period selection to verify second period selection is untouched.
    downloadHelper.replaceTrackSelections(/* periodIndex= */ 0, parameters);
    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertSingleTrackSelectionEquals(selectedText0, TRACK_GROUP_TEXT_ZH, 0);
    assertSingleTrackSelectionEquals(selectedAudio0, TRACK_GROUP_AUDIO_ZH, 0);
    assertThat(selectedVideo0).isEmpty();

    assertThat(selectedText1).isEmpty();
    assertSingleTrackSelectionEquals(selectedAudio1, TRACK_GROUP_AUDIO_US, 0);
    assertSingleTrackSelectionEquals(selectedVideo1, TRACK_GROUP_VIDEO_SINGLE, 0);
  }

  @Test
  public void getTrackSelections_afterAddTrackSelections_returnsCombinedSelections()
      throws Exception {
    prepareDownloadHelper(downloadHelper);
    // Select parameters to require some merging of track groups because the new parameters add
    // all video tracks to initial video single track selection.
    DefaultTrackSelector.Parameters parameters =
        new ParametersBuilder()
            .setPreferredAudioLanguage("ZH")
            .setPreferredTextLanguage("US")
            .build();

    // Add only to one period selection to verify second period selection is untouched.
    downloadHelper.addTrackSelection(/* periodIndex= */ 0, parameters);
    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertSingleTrackSelectionEquals(selectedText0, TRACK_GROUP_TEXT_US, 0);
    assertThat(selectedAudio0).hasSize(2);
    assertTrackSelectionEquals(selectedAudio0.get(0), TRACK_GROUP_AUDIO_US, 0);
    assertTrackSelectionEquals(selectedAudio0.get(1), TRACK_GROUP_AUDIO_ZH, 0);
    assertSingleTrackSelectionEquals(selectedVideo0, TRACK_GROUP_VIDEO_BOTH, 0, 1);

    assertThat(selectedText1).isEmpty();
    assertSingleTrackSelectionEquals(selectedAudio1, TRACK_GROUP_AUDIO_US, 0);
    assertSingleTrackSelectionEquals(selectedVideo1, TRACK_GROUP_VIDEO_SINGLE, 0);
  }

  @Test
  public void getTrackSelections_afterAddAudioLanguagesToSelection_returnsCombinedSelections()
      throws Exception {
    prepareDownloadHelper(downloadHelper);
    downloadHelper.clearTrackSelections(/* periodIndex= */ 0);
    downloadHelper.clearTrackSelections(/* periodIndex= */ 1);

    // Add a non-default language, and a non-existing language (which will select the default).
    downloadHelper.addAudioLanguagesToSelection("ZH", "Klingonese");
    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertThat(selectedVideo0).isEmpty();
    assertThat(selectedText0).isEmpty();
    assertThat(selectedAudio0).hasSize(2);
    assertTrackSelectionEquals(selectedAudio0.get(0), TRACK_GROUP_AUDIO_ZH, 0);
    assertTrackSelectionEquals(selectedAudio0.get(1), TRACK_GROUP_AUDIO_US, 0);

    assertThat(selectedVideo1).isEmpty();
    assertThat(selectedText1).isEmpty();
    assertSingleTrackSelectionEquals(selectedAudio1, TRACK_GROUP_AUDIO_US, 0);
  }

  @Test
  public void getTrackSelections_afterAddTextLanguagesToSelection_returnsCombinedSelections()
      throws Exception {
    prepareDownloadHelper(downloadHelper);
    downloadHelper.clearTrackSelections(/* periodIndex= */ 0);
    downloadHelper.clearTrackSelections(/* periodIndex= */ 1);

    // Add a non-default language, and a non-existing language (which will select the default).
    downloadHelper.addTextLanguagesToSelection(
        /* selectUndeterminedTextLanguage= */ true, "ZH", "Klingonese");
    List<TrackSelection> selectedText0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo0 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 0, /* rendererIndex= */ 2);
    List<TrackSelection> selectedText1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 0);
    List<TrackSelection> selectedAudio1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 1);
    List<TrackSelection> selectedVideo1 =
        downloadHelper.getTrackSelections(/* periodIndex= */ 1, /* rendererIndex= */ 2);

    assertThat(selectedVideo0).isEmpty();
    assertThat(selectedAudio0).isEmpty();
    assertThat(selectedText0).hasSize(2);
    assertTrackSelectionEquals(selectedText0.get(0), TRACK_GROUP_TEXT_ZH, 0);
    assertTrackSelectionEquals(selectedText0.get(1), TRACK_GROUP_TEXT_US, 0);

    assertThat(selectedVideo1).isEmpty();
    assertThat(selectedAudio1).isEmpty();
    assertThat(selectedText1).isEmpty();
  }

  @Test
  public void getDownloadRequest_createsDownloadRequest_withAllSelectedTracks() throws Exception {
    prepareDownloadHelper(downloadHelper);
    // Ensure we have track groups with multiple indices, renderers with multiple track groups and
    // also renderers without any track groups.
    DefaultTrackSelector.Parameters parameters =
        new ParametersBuilder()
            .setPreferredAudioLanguage("ZH")
            .setPreferredTextLanguage("US")
            .build();
    downloadHelper.addTrackSelection(/* periodIndex= */ 0, parameters);
    byte[] data = new byte[10];
    Arrays.fill(data, (byte) 123);

    DownloadRequest downloadRequest = downloadHelper.getDownloadRequest(data);

    assertThat(downloadRequest.type).isEqualTo(TEST_DOWNLOAD_TYPE);
    assertThat(downloadRequest.uri).isEqualTo(testUri);
    assertThat(downloadRequest.customCacheKey).isEqualTo(TEST_CACHE_KEY);
    assertThat(downloadRequest.data).isEqualTo(data);
    assertThat(downloadRequest.streamKeys)
        .containsExactly(
            new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 0, /* trackIndex= */ 0),
            new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 0, /* trackIndex= */ 1),
            new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 0),
            new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 2, /* trackIndex= */ 0),
            new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 3, /* trackIndex= */ 0),
            new StreamKey(/* periodIndex= */ 1, /* groupIndex= */ 0, /* trackIndex= */ 0),
            new StreamKey(/* periodIndex= */ 1, /* groupIndex= */ 1, /* trackIndex= */ 0));
  }

  private static void prepareDownloadHelper(DownloadHelper downloadHelper) throws Exception {
    AtomicReference<Exception> prepareException = new AtomicReference<>(null);
    ConditionVariable preparedCondition = new ConditionVariable();
    downloadHelper.prepare(
        new Callback() {
          @Override
          public void onPrepared(DownloadHelper helper) {
            preparedCondition.open();
          }

          @Override
          public void onPrepareError(DownloadHelper helper, IOException e) {
            prepareException.set(e);
            preparedCondition.open();
          }
        });
    while (!preparedCondition.block(0)) {
      ShadowLooper.runMainLooperToNextTask();
    }
    if (prepareException.get() != null) {
      throw prepareException.get();
    }
  }

  private static Format createVideoFormat(int bitrate) {
    return Format.createVideoSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ MimeTypes.VIDEO_H264,
        /* codecs= */ null,
        /* bitrate= */ bitrate,
        /* maxInputSize= */ Format.NO_VALUE,
        /* width= */ 480,
        /* height= */ 360,
        /* frameRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null);
  }

  private static Format createAudioFormat(String language) {
    return Format.createAudioSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ MimeTypes.AUDIO_AAC,
        /* codecs= */ null,
        /* bitrate= */ 48000,
        /* maxInputSize= */ Format.NO_VALUE,
        /* channelCount= */ 2,
        /* sampleRate */ 44100,
        /* initializationData= */ null,
        /* drmInitData= */ null,
        /* selectionFlags= */ C.SELECTION_FLAG_DEFAULT,
        /* language= */ language);
  }

  private static Format createTextFormat(String language) {
    return Format.createTextSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ MimeTypes.TEXT_VTT,
        /* selectionFlags= */ C.SELECTION_FLAG_DEFAULT,
        /* language= */ language);
  }

  private static void assertSingleTrackSelectionEquals(
      List<TrackSelection> trackSelectionList, TrackGroup trackGroup, int... tracks) {
    assertThat(trackSelectionList).hasSize(1);
    assertTrackSelectionEquals(trackSelectionList.get(0), trackGroup, tracks);
  }

  private static void assertTrackSelectionEquals(
      TrackSelection trackSelection, TrackGroup trackGroup, int... tracks) {
    assertThat(trackSelection.getTrackGroup()).isEqualTo(trackGroup);
    assertThat(trackSelection.length()).isEqualTo(tracks.length);
    int[] selectedTracksInGroup = new int[trackSelection.length()];
    for (int i = 0; i < trackSelection.length(); i++) {
      selectedTracksInGroup[i] = trackSelection.getIndexInTrackGroup(i);
    }
    Arrays.sort(selectedTracksInGroup);
    Arrays.sort(tracks);
    assertThat(selectedTracksInGroup).isEqualTo(tracks);
  }

  private static final class TestMediaSource extends FakeMediaSource {

    public TestMediaSource() {
      super(TEST_TIMELINE, TEST_MANIFEST);
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
      int periodIndex = TEST_TIMELINE.getIndexOfPeriod(id.periodUid);
      return new FakeMediaPeriod(
          TRACK_GROUP_ARRAYS[periodIndex],
          new EventDispatcher()
              .withParameters(/* windowIndex= */ 0, id, /* mediaTimeOffsetMs= */ 0)) {
        @Override
        public List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
          List<StreamKey> result = new ArrayList<>();
          for (TrackSelection trackSelection : trackSelections) {
            int groupIndex =
                TRACK_GROUP_ARRAYS[periodIndex].indexOf(trackSelection.getTrackGroup());
            for (int i = 0; i < trackSelection.length(); i++) {
              result.add(
                  new StreamKey(periodIndex, groupIndex, trackSelection.getIndexInTrackGroup(i)));
            }
          }
          return result;
        }
      };
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      // Do nothing.
    }
  }
}
