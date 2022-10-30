/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for the track selection created by {@link BufferSizeAdaptationBuilder}. */
@RunWith(AndroidJUnit4.class)
public final class BufferSizeAdaptiveTrackSelectionTest {

  private static final int MIN_BUFFER_MS = 15_000;
  private static final int MAX_BUFFER_MS = 50_000;
  private static final int HYSTERESIS_BUFFER_MS = 10_000;
  private static final float BANDWIDTH_FRACTION = 0.5f;
  private static final int MIN_BUFFER_FOR_QUALITY_INCREASE_MS = 10_000;

  /**
   * Factor between bitrates is always the same (=2.2). That means buffer levels should be linearly
   * distributed between MIN_BUFFER=15s and MAX_BUFFER-HYSTERESIS=50s-10s=40s.
   */
  private static final Format format1 =
      createVideoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);

  private static final Format format2 =
      createVideoFormat(/* bitrate= */ 1100, /* width= */ 640, /* height= */ 480);
  private static final Format format3 =
      createVideoFormat(/* bitrate= */ 2420, /* width= */ 960, /* height= */ 720);
  private static final int BUFFER_LEVEL_FORMAT_2 =
      (MIN_BUFFER_MS + MAX_BUFFER_MS - HYSTERESIS_BUFFER_MS) / 2;
  private static final int BUFFER_LEVEL_FORMAT_3 = MAX_BUFFER_MS - HYSTERESIS_BUFFER_MS;

  @Mock private BandwidthMeter mockBandwidthMeter;
  private TrackSelection trackSelection;

  @Before
  public void setUp() {
    initMocks(this);
    Pair<TrackSelection.Factory, LoadControl> trackSelectionFactoryAndLoadControl =
        new BufferSizeAdaptationBuilder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                /* bufferForPlaybackMs= */ 1000,
                /* bufferForPlaybackAfterRebufferMs= */ 1000)
            .setHysteresisBufferMs(HYSTERESIS_BUFFER_MS)
            .setStartUpTrackSelectionParameters(
                BANDWIDTH_FRACTION, MIN_BUFFER_FOR_QUALITY_INCREASE_MS)
            .buildPlayerComponents();
    trackSelection =
        trackSelectionFactoryAndLoadControl
            .first
            .createTrackSelections(
                new TrackSelection.Definition[] {
                  new TrackSelection.Definition(
                      new TrackGroup(format1, format2, format3), /* tracks= */ 0, 1, 2)
                },
                mockBandwidthMeter)[0];
    trackSelection.enable();
  }

  @Test
  public void updateSelectedTrack_usesBandwidthEstimateForInitialSelection() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));

    updateSelectedTrack(/* bufferedDurationMs= */ 0);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrack_withLowerBandwidthEstimateDuringStartUp_switchesDown() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);

    updateSelectedTrack(/* bufferedDurationMs= */ 0);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void
      updateSelectedTrack_withHigherBandwidthEstimateDuringStartUp_andLowBuffer_keepsSelection() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format3));

    updateSelectedTrack(/* bufferedDurationMs= */ MIN_BUFFER_FOR_QUALITY_INCREASE_MS - 1);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void
      updateSelectedTrack_withHigherBandwidthEstimateDuringStartUp_andHighBuffer_switchesUp() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format3));

    updateSelectedTrack(/* bufferedDurationMs= */ MIN_BUFFER_FOR_QUALITY_INCREASE_MS);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format3);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void
      updateSelectedTrack_withIncreasedBandwidthEstimate_onceSteadyStateBufferIsReached_keepsSelection() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format3));

    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void
      updateSelectedTrack_withDecreasedBandwidthEstimate_onceSteadyStateBufferIsReached_keepsSelection() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);

    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrack_withIncreasedBufferInSteadyState_switchesUp() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);

    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_3);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format3);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void updateSelectedTrack_withDecreasedBufferInSteadyState_switchesDown() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);

    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2 - HYSTERESIS_BUFFER_MS - 1);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void
      updateSelectedTrack_withDecreasedBufferInSteadyState_withinHysteresis_keepsSelection() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);

    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2 - HYSTERESIS_BUFFER_MS);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void onDiscontinuity_switchesBackToStartUpState() {
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(getBitrateEstimateEnoughFor(format2));
    updateSelectedTrack(/* bufferedDurationMs= */ 0);
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(0L);

    trackSelection.onDiscontinuity();
    updateSelectedTrack(/* bufferedDurationMs= */ BUFFER_LEVEL_FORMAT_2 - 1);

    assertThat(trackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(trackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  private void updateSelectedTrack(long bufferedDurationMs) {
    trackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ C.msToUs(bufferedDurationMs),
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ new MediaChunkIterator[] {
          MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY
        });
  }

  private static Format createVideoFormat(int bitrate, int width, int height) {
    return Format.createVideoSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ MimeTypes.VIDEO_H264,
        /* codecs= */ null,
        /* bitrate= */ bitrate,
        /* maxInputSize= */ Format.NO_VALUE,
        /* width= */ width,
        /* height= */ height,
        /* frameRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null);
  }

  private static long getBitrateEstimateEnoughFor(Format format) {
    return (long) (format.bitrate / BANDWIDTH_FRACTION) + 1;
  }
}
