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
package com.google.android.exoplayer2.audio;

import static com.google.android.exoplayer2.audio.AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY;
import static com.google.android.exoplayer2.audio.AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.audio.AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;

/** Unit tests for {@link AudioFocusManager}. */
@RunWith(AndroidJUnit4.class)
public class AudioFocusManagerTest {
  private static final int NO_COMMAND_RECEIVED = ~PLAYER_COMMAND_WAIT_FOR_CALLBACK;

  private AudioFocusManager audioFocusManager;
  private TestPlayerControl testPlayerControl;

  private AudioManager audioManager;

  @Before
  public void setUp() {
    audioManager =
        (AudioManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

    testPlayerControl = new TestPlayerControl();
    audioFocusManager =
        new AudioFocusManager(ApplicationProvider.getApplicationContext(), testPlayerControl);
  }

  @Test
  public void setAudioAttributes_withNullUsage_doesNotManageAudioFocus() {
    // Ensure that NULL audio attributes -> don't manage audio focus
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ false, Player.STATE_IDLE))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(request).isNull();
  }

  @Test
  @Config(maxSdk = 25)
  public void setAudioAttributes_withNullUsage_releasesAudioFocus() {
    // Create attributes and request audio focus.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(request.durationHint).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);

    // Ensure that setting null audio attributes with audio focus releases audio focus.
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    AudioManager.OnAudioFocusChangeListener lastRequest =
        Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener();
    assertThat(lastRequest).isNotNull();
  }

  @Test
  @Config(minSdk = 26)
  public void setAudioAttributes_withNullUsage_releasesAudioFocus_v26() {
    // Create attributes and request audio focus.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(getAudioFocusGainFromRequest(request)).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);

    // Ensure that setting null audio attributes with audio focus releases audio focus.
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    AudioFocusRequest lastRequest =
        Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest();
    assertThat(lastRequest).isNotNull();
  }

  @Test
  public void setAudioAttributes_withUsageAlarm_throwsIllegalArgumentException() {
    // Ensure that audio attributes that map to AUDIOFOCUS_GAIN_TRANSIENT* throw
    AudioAttributes alarm = new AudioAttributes.Builder().setUsage(C.USAGE_ALARM).build();
    try {
      audioFocusManager.setAudioAttributes(alarm, /* playWhenReady= */ false, Player.STATE_IDLE);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void setAudioAttributes_withUsageMedia_usesAudioFocusGain() {
    // Ensure setting media type audio attributes requests AUDIOFOCUS_GAIN.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(getAudioFocusGainFromRequest(request)).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
  }

  @Test
  public void setAudioAttributes_inStateEnded_requestsAudioFocus() {
    // Ensure setting audio attributes when player is in STATE_ENDED requests audio focus.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_ENDED))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(getAudioFocusGainFromRequest(request)).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
  }

  @Test
  public void handlePrepare_afterSetAudioAttributes_setsPlayerCommandPlayWhenReady() {
    // Ensure that when playWhenReady is true while the player is IDLE, audio focus is only
    // requested after calling handlePrepare.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_IDLE))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    assertThat(Shadows.shadowOf(audioManager).getLastAudioFocusRequest()).isNull();
    assertThat(audioFocusManager.handlePrepare(/* playWhenReady= */ true))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
  }

  @Test
  public void handleSetPlayWhenReady_afterSetAudioAttributes_setsPlayerCommandPlayWhenReady() {
    // Ensure that audio focus is not requested until playWhenReady is true.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

    assertThat(audioFocusManager.handlePrepare(/* playWhenReady= */ false))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAudioFocusRequest()).isNull();
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ false, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAudioFocusRequest()).isNull();
    assertThat(
            audioFocusManager.handleSetPlayWhenReady(/* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
  }

  @Test
  public void onAudioFocusChange_withDuckEnabled_volumeReducedAndRestored() {
    // Ensure that the volume multiplier is adjusted when audio focus is lost to
    // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, and returns to the default value after focus is
    // regained.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);

    audioFocusManager
        .getFocusListener()
        .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
    assertThat(testPlayerControl.lastVolumeMultiplier).isLessThan(1.0f);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(NO_COMMAND_RECEIVED);
    audioFocusManager.getFocusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    assertThat(testPlayerControl.lastVolumeMultiplier).isEqualTo(1.0f);
  }

  @Test
  public void onAudioFocusChange_withPausedWhenDucked_sendsCommandWaitForCallback() {
    // Ensure that the player is commanded to pause when audio focus is lost with
    // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK and the content type is CONTENT_TYPE_SPEECH.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);

    audioFocusManager
        .getFocusListener()
        .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(PLAYER_COMMAND_WAIT_FOR_CALLBACK);
    assertThat(testPlayerControl.lastVolumeMultiplier).isEqualTo(1.0f);
    audioFocusManager.getFocusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
  }

  @Test
  public void onAudioFocusChange_withTransientLost_sendsCommandWaitForCallback() {
    // Ensure that the player is commanded to pause when audio focus is lost with
    // AUDIOFOCUS_LOSS_TRANSIENT.
    AudioAttributes media = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);

    audioFocusManager.getFocusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    assertThat(testPlayerControl.lastVolumeMultiplier).isEqualTo(1.0f);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(PLAYER_COMMAND_WAIT_FOR_CALLBACK);
  }

  @Test
  @Config(maxSdk = 25)
  public void onAudioFocusChange_withAudioFocusLost_sendsDoNotPlayAndAbandondsFocus() {
    // Ensure that AUDIOFOCUS_LOSS causes AudioFocusManager to pause playback and abandon audio
    // focus.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNull();

    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener())
        .isEqualTo(request.listener);
  }

  @Test
  @Config(minSdk = 26)
  public void onAudioFocusChange_withAudioFocusLost_sendsDoNotPlayAndAbandondsFocus_v26() {
    // Ensure that AUDIOFOCUS_LOSS causes AudioFocusManager to pause playback and abandon audio
    // focus.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest()).isNull();

    audioFocusManager.getFocusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
    assertThat(testPlayerControl.lastPlayerCommand).isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest())
        .isEqualTo(Shadows.shadowOf(audioManager).getLastAudioFocusRequest().audioFocusRequest);
  }

  @Test
  @Config(maxSdk = 25)
  public void handleStop_withAudioFocus_abandonsAudioFocus() {
    // Ensure that handleStop causes AudioFocusManager to abandon audio focus.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNull();

    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    audioFocusManager.handleStop();
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener())
        .isEqualTo(request.listener);
  }

  @Test
  @Config(minSdk = 26)
  public void handleStop_withAudioFocus_abandonsAudioFocus_v26() {
    // Ensure that handleStop causes AudioFocusManager to abandon audio focus.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ true, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_PLAY_WHEN_READY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest()).isNull();

    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    audioFocusManager.handleStop();
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest())
        .isEqualTo(request.audioFocusRequest);
  }

  @Test
  @Config(maxSdk = 25)
  public void handleStop_withoutAudioFocus_stillAbandonsFocus() {
    // Ensure that handleStop causes AudioFocusManager to call through to abandon audio focus
    // even if focus wasn't requested.
    AudioAttributes media =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build();

    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                media, /* playWhenReady= */ false, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNull();
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(request).isNull();

    audioFocusManager.handleStop();
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNotNull();
  }

  @Test
  @Config(maxSdk = 25)
  public void handleStop_withoutHandlingAudioFocus_isNoOp() {
    // Ensure that handleStop is a no-op if audio focus isn't handled.
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ false, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNull();
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(request).isNull();

    audioFocusManager.handleStop();
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusListener()).isNull();
  }

  @Test
  @Config(minSdk = 26)
  public void handleStop_withoutHandlingAudioFocus_isNoOp_v26() {
    // Ensure that handleStop is a no-op if audio focus isn't handled.
    Shadows.shadowOf(audioManager)
        .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    assertThat(
            audioFocusManager.setAudioAttributes(
                /* audioAttributes= */ null, /* playWhenReady= */ false, Player.STATE_READY))
        .isEqualTo(PLAYER_COMMAND_DO_NOT_PLAY);
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest()).isNull();
    ShadowAudioManager.AudioFocusRequest request =
        Shadows.shadowOf(audioManager).getLastAudioFocusRequest();
    assertThat(request).isNull();

    audioFocusManager.handleStop();
    assertThat(Shadows.shadowOf(audioManager).getLastAbandonedAudioFocusRequest()).isNull();
  }

  private int getAudioFocusGainFromRequest(ShadowAudioManager.AudioFocusRequest audioFocusRequest) {
    return Util.SDK_INT >= 26
        ? audioFocusRequest.audioFocusRequest.getFocusGain()
        : audioFocusRequest.durationHint;
  }

  private static class TestPlayerControl implements AudioFocusManager.PlayerControl {
    private float lastVolumeMultiplier = 1.0f;
    private int lastPlayerCommand = NO_COMMAND_RECEIVED;

    @Override
    public void setVolumeMultiplier(float volumeMultiplier) {
      lastVolumeMultiplier = volumeMultiplier;
    }

    @Override
    public void executePlayerCommand(int playerCommand) {
      lastPlayerCommand = playerCommand;
    }
  }
}
