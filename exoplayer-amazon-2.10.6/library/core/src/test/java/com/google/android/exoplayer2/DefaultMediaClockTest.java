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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParameterListener;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for {@link DefaultMediaClock}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaClockTest {

  private static final long TEST_POSITION_US = 123456789012345678L;
  private static final long SLEEP_TIME_MS = 1_000;
  private static final PlaybackParameters TEST_PLAYBACK_PARAMETERS =
      new PlaybackParameters(/* speed= */ 2f);

  @Mock private PlaybackParameterListener listener;
  private FakeClock fakeClock;
  private DefaultMediaClock mediaClock;

  @Before
  public void initMediaClockWithFakeClock() {
    initMocks(this);
    fakeClock = new FakeClock(0);
    mediaClock = new DefaultMediaClock(listener, fakeClock);
  }

  @Test
  public void standaloneResetPosition_getPositionShouldReturnSameValue() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void standaloneGetAndResetPosition_shouldNotTriggerCallback() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    mediaClock.syncAndGetPositionUs();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void standaloneClock_shouldNotAutoStart() throws Exception {
    assertClockIsStopped();
  }

  @Test
  public void standaloneResetPosition_shouldNotStartClock() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    assertClockIsStopped();
  }

  @Test
  public void standaloneStart_shouldStartClock() throws Exception {
    mediaClock.start();
    assertClockIsRunning();
  }

  @Test
  public void standaloneStop_shouldKeepClockStopped() throws Exception {
    mediaClock.stop();
    assertClockIsStopped();
  }

  @Test
  public void standaloneStartAndStop_shouldStopClock() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    assertClockIsStopped();
  }

  @Test
  public void standaloneStartStopStart_shouldRestartClock() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    mediaClock.start();
    assertClockIsRunning();
  }

  @Test
  public void standaloneStartAndStop_shouldNotTriggerCallback() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void standaloneGetPlaybackParameters_initializedWithDefaultPlaybackParameters() {
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void standaloneSetPlaybackParameters_getPlaybackParametersShouldReturnSameValue() {
    PlaybackParameters parameters = mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(parameters).isEqualTo(TEST_PLAYBACK_PARAMETERS);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void standaloneSetPlaybackParameters_shouldTriggerCallback() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void standaloneSetPlaybackParameters_shouldApplyNewPlaybackSpeed() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    mediaClock.start();
    // Asserts that clock is running with speed declared in getPlaybackParameters().
    assertClockIsRunning();
  }

  @Test
  public void standaloneSetOtherPlaybackParameters_getPlaybackParametersShouldReturnSameValue() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    PlaybackParameters parameters = mediaClock.setPlaybackParameters(PlaybackParameters.DEFAULT);
    assertThat(parameters).isEqualTo(PlaybackParameters.DEFAULT);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void standaloneSetOtherPlaybackParameters_shouldTriggerCallbackAgain() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    mediaClock.setPlaybackParameters(PlaybackParameters.DEFAULT);
    verify(listener).onPlaybackParametersChanged(PlaybackParameters.DEFAULT);
  }

  @Test
  public void standaloneSetSamePlaybackParametersAgain_shouldTriggerCallbackAgain() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verify(listener, times(2)).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void enableRendererMediaClock_shouldOverwriteRendererPlaybackParametersIfPossible()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void enableRendererMediaClockWithFixedParameters_usesRendererPlaybackParameters()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void enableRendererMediaClockWithFixedParameters_shouldTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void enableRendererMediaClockWithFixedButSamePlaybackParameters_shouldNotTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
        /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void disableRendererMediaClock_shouldKeepPlaybackParameters()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.onRendererDisabled(mediaClockRenderer);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererClockSetPlaybackParameters_getPlaybackParametersShouldReturnSameValue()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
        /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    PlaybackParameters parameters = mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(parameters).isEqualTo(TEST_PLAYBACK_PARAMETERS);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererClockSetPlaybackParameters_shouldTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
        /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererClockSetPlaybackParametersOverwrite_getParametersShouldReturnSameValue()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
        /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    PlaybackParameters parameters = mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(parameters).isEqualTo(PlaybackParameters.DEFAULT);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void rendererClockSetPlaybackParametersOverwrite_shouldTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
        /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verify(listener).onPlaybackParametersChanged(PlaybackParameters.DEFAULT);
  }

  @Test
  public void enableRendererMediaClock_usesRendererClockPosition() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
    // We're not advancing the renderer media clock. Thus, the clock should appear to be stopped.
    assertClockIsStopped();
  }

  @Test
  public void resetPositionWhileUsingRendererMediaClock_shouldHaveNoEffect()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
    mediaClock.resetPosition(0);
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void disableRendererMediaClock_standaloneShouldBeSynced() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    mediaClock.syncAndGetPositionUs();
    mediaClock.onRendererDisabled(mediaClockRenderer);
    fakeClock.advanceTime(SLEEP_TIME_MS);
    assertThat(mediaClock.syncAndGetPositionUs())
        .isEqualTo(TEST_POSITION_US + C.msToUs(SLEEP_TIME_MS));
    assertClockIsRunning();
  }

  @Test
  public void getPositionWithPlaybackParameterChange_shouldTriggerCallback()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer(PlaybackParameters.DEFAULT,
            /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    // Silently change playback parameters of renderer clock.
    mediaClockRenderer.playbackParameters = TEST_PLAYBACK_PARAMETERS;
    mediaClock.syncAndGetPositionUs();
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererNotReady_shouldStillUseRendererClock() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer(/* isReady= */ false,
        /* isEnded= */ false, /* hasReadStreamToEnd= */ false);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    // We're not advancing the renderer media clock. Thus, the clock should appear to be stopped.
    assertClockIsStopped();
  }

  @Test
  public void rendererNotReadyAndReadStreamToEnd_shouldFallbackToStandaloneClock()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer(/* isReady= */ false,
        /* isEnded= */ false, /* hasReadStreamToEnd= */ true);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertClockIsRunning();
  }

  @Test
  public void rendererEnded_shouldFallbackToStandaloneClock()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer(/* isReady= */ true,
        /* isEnded= */ true, /* hasReadStreamToEnd= */ true);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertClockIsRunning();
  }

  @Test
  public void staleDisableRendererClock_shouldNotThrow()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    mediaClock.onRendererDisabled(mediaClockRenderer);
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(C.msToUs(fakeClock.elapsedRealtime()));
  }

  @Test
  public void enableSameRendererClockTwice_shouldNotThrow()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void enableOtherRendererClock_shouldThrow()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer1 = new MediaClockRenderer();
    MediaClockRenderer mediaClockRenderer2 = new MediaClockRenderer();
    mediaClockRenderer1.positionUs = TEST_POSITION_US;
    mediaClock.onRendererEnabled(mediaClockRenderer1);
    try {
      mediaClock.onRendererEnabled(mediaClockRenderer2);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected.
    }
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(TEST_POSITION_US);
  }

  private void assertClockIsRunning() {
    long clockStartUs = mediaClock.syncAndGetPositionUs();
    fakeClock.advanceTime(SLEEP_TIME_MS);
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(clockStartUs
        + mediaClock.getPlaybackParameters().getMediaTimeUsForPlayoutTimeMs(SLEEP_TIME_MS));
  }

  private void assertClockIsStopped() {
    long positionAtStartUs = mediaClock.syncAndGetPositionUs();
    fakeClock.advanceTime(SLEEP_TIME_MS);
    assertThat(mediaClock.syncAndGetPositionUs()).isEqualTo(positionAtStartUs);
  }

  @SuppressWarnings("HidingField")
  private static class MediaClockRenderer extends FakeMediaClockRenderer {

    private final boolean playbackParametersAreMutable;
    private final boolean isReady;
    private final boolean isEnded;

    public PlaybackParameters playbackParameters;
    public long positionUs;

    public MediaClockRenderer() throws ExoPlaybackException {
      this(PlaybackParameters.DEFAULT, false, true, false, false);
    }

    public MediaClockRenderer(PlaybackParameters playbackParameters,
        boolean playbackParametersAreMutable)
        throws ExoPlaybackException {
      this(playbackParameters, playbackParametersAreMutable, true, false, false);
    }

    public MediaClockRenderer(boolean isReady, boolean isEnded, boolean hasReadStreamToEnd)
        throws ExoPlaybackException {
      this(PlaybackParameters.DEFAULT, false, isReady, isEnded, hasReadStreamToEnd);
    }

    private MediaClockRenderer(PlaybackParameters playbackParameters,
        boolean playbackParametersAreMutable, boolean isReady, boolean isEnded,
        boolean hasReadStreamToEnd)
        throws ExoPlaybackException {
      this.playbackParameters = playbackParameters;
      this.playbackParametersAreMutable = playbackParametersAreMutable;
      this.isReady = isReady;
      this.isEnded = isEnded;
      this.positionUs = TEST_POSITION_US;
      if (!hasReadStreamToEnd) {
        resetPosition(0);
      }
    }

    @Override
    public long getPositionUs() {
      return positionUs;
    }

    @Override
    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
      if (playbackParametersAreMutable) {
        this.playbackParameters = playbackParameters;
      }
      return this.playbackParameters;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      return playbackParameters;
    }

    @Override
    public boolean isReady() {
      return isReady;
    }

    @Override
    public boolean isEnded() {
      return isEnded;
    }
  }

}
