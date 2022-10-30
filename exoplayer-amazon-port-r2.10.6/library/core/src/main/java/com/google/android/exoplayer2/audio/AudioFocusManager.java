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

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Manages requesting and responding to changes in audio focus. */
public final class AudioFocusManager {

  /** Interface to allow AudioFocusManager to give commands to a player. */
  public interface PlayerControl {
    /**
     * Called when the volume multiplier on the player should be changed.
     *
     * @param volumeMultiplier The new volume multiplier.
     */
    void setVolumeMultiplier(float volumeMultiplier);

    /**
     * Called when a command must be executed on the player.
     *
     * @param playerCommand The command that must be executed.
     */
    void executePlayerCommand(@PlayerCommand int playerCommand);
  }

  /**
   * Player commands. One of {@link #PLAYER_COMMAND_DO_NOT_PLAY}, {@link
   * #PLAYER_COMMAND_WAIT_FOR_CALLBACK} or {@link #PLAYER_COMMAND_PLAY_WHEN_READY}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PLAYER_COMMAND_DO_NOT_PLAY,
    PLAYER_COMMAND_WAIT_FOR_CALLBACK,
    PLAYER_COMMAND_PLAY_WHEN_READY,
  })
  public @interface PlayerCommand {}
  /** Do not play. */
  public static final int PLAYER_COMMAND_DO_NOT_PLAY = -1;
  /** Do not play now. Wait for callback to play. */
  public static final int PLAYER_COMMAND_WAIT_FOR_CALLBACK = 0;
  /** Play freely. */
  public static final int PLAYER_COMMAND_PLAY_WHEN_READY = 1;

  /** Audio focus state. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    AUDIO_FOCUS_STATE_LOST_FOCUS,
    AUDIO_FOCUS_STATE_NO_FOCUS,
    AUDIO_FOCUS_STATE_HAVE_FOCUS,
    AUDIO_FOCUS_STATE_LOSS_TRANSIENT,
    AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK
  })
  private @interface AudioFocusState {}
  /** No audio focus was held, but has been lost by another app taking it permanently. */
  private static final int AUDIO_FOCUS_STATE_LOST_FOCUS = -1;
  /** No audio focus is currently being held. */
  private static final int AUDIO_FOCUS_STATE_NO_FOCUS = 0;
  /** The requested audio focus is currently held. */
  private static final int AUDIO_FOCUS_STATE_HAVE_FOCUS = 1;
  /** Audio focus has been temporarily lost. */
  private static final int AUDIO_FOCUS_STATE_LOSS_TRANSIENT = 2;
  /** Audio focus has been temporarily lost, but playback may continue with reduced volume. */
  private static final int AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK = 3;

  private static final String TAG = "AudioFocusManager";

  private static final float VOLUME_MULTIPLIER_DUCK = 0.2f;
  private static final float VOLUME_MULTIPLIER_DEFAULT = 1.0f;

  private final AudioManager audioManager;
  private final AudioFocusListener focusListener;
  private final PlayerControl playerControl;
  private @Nullable AudioAttributes audioAttributes;

  private @AudioFocusState int audioFocusState;
  private int focusGain;
  private float volumeMultiplier = 1.0f;

  private @MonotonicNonNull AudioFocusRequest audioFocusRequest;
  private boolean rebuildAudioFocusRequest;

  /**
   * Constructs an AudioFocusManager to automatically handle audio focus for a player.
   *
   * @param context The current context.
   * @param playerControl A {@link PlayerControl} to handle commands from this instance.
   */
  public AudioFocusManager(Context context, PlayerControl playerControl) {
    this.audioManager =
        (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    this.playerControl = playerControl;
    this.focusListener = new AudioFocusListener();
    this.audioFocusState = AUDIO_FOCUS_STATE_NO_FOCUS;
  }

  /** Gets the current player volume multiplier. */
  public float getVolumeMultiplier() {
    return volumeMultiplier;
  }

  /**
   * Sets audio attributes that should be used to manage audio focus.
   *
   * @param audioAttributes The audio attributes or {@code null} if audio focus should not be
   *     managed automatically.
   * @param playWhenReady The current state of {@link ExoPlayer#getPlayWhenReady()}.
   * @param playerState The current player state; {@link ExoPlayer#getPlaybackState()}.
   * @return A {@link PlayerCommand} to execute on the player.
   */
  @PlayerCommand
  public int setAudioAttributes(
      @Nullable AudioAttributes audioAttributes, boolean playWhenReady, int playerState) {
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      focusGain = convertAudioAttributesToFocusGain(audioAttributes);

      Assertions.checkArgument(
          focusGain == C.AUDIOFOCUS_GAIN || focusGain == C.AUDIOFOCUS_NONE,
          "Automatic handling of audio focus is only available for USAGE_MEDIA and USAGE_GAME.");
      if (playWhenReady
          && (playerState == Player.STATE_BUFFERING || playerState == Player.STATE_READY)) {
        return requestAudioFocus();
      }
    }

    return playerState == Player.STATE_IDLE
        ? handleIdle(playWhenReady)
        : handlePrepare(playWhenReady);
  }

  /**
   * Called by a player as part of {@link ExoPlayer#prepare(MediaSource, boolean, boolean)}.
   *
   * @param playWhenReady The current state of {@link ExoPlayer#getPlayWhenReady()}.
   * @return A {@link PlayerCommand} to execute on the player.
   */
  @PlayerCommand
  public int handlePrepare(boolean playWhenReady) {
    return playWhenReady ? requestAudioFocus() : PLAYER_COMMAND_DO_NOT_PLAY;
  }

  /**
   * Called by the player as part of {@link ExoPlayer#setPlayWhenReady(boolean)}.
   *
   * @param playWhenReady The desired value of playWhenReady.
   * @param playerState The current state of the player.
   * @return A {@link PlayerCommand} to execute on the player.
   */
  @PlayerCommand
  public int handleSetPlayWhenReady(boolean playWhenReady, int playerState) {
    if (!playWhenReady) {
      abandonAudioFocus();
      return PLAYER_COMMAND_DO_NOT_PLAY;
    }

    return playerState == Player.STATE_IDLE ? handleIdle(playWhenReady) : requestAudioFocus();
  }

  /** Called by the player as part of {@link ExoPlayer#stop(boolean)}. */
  public void handleStop() {
    abandonAudioFocus(/* forceAbandon= */ true);
  }

  // Internal methods.

  @VisibleForTesting
  /* package */ AudioManager.OnAudioFocusChangeListener getFocusListener() {
    return focusListener;
  }

  @PlayerCommand
  private int handleIdle(boolean playWhenReady) {
    return playWhenReady ? PLAYER_COMMAND_PLAY_WHEN_READY : PLAYER_COMMAND_DO_NOT_PLAY;
  }

  @PlayerCommand
  private int requestAudioFocus() {
    int focusRequestResult;

    if (focusGain == C.AUDIOFOCUS_NONE) {
      if (audioFocusState != AUDIO_FOCUS_STATE_NO_FOCUS) {
        abandonAudioFocus(/* forceAbandon= */ true);
      }
      return PLAYER_COMMAND_PLAY_WHEN_READY;
    }

    if (audioFocusState == AUDIO_FOCUS_STATE_NO_FOCUS) {
      if (Util.SDK_INT >= 26) {
        focusRequestResult = requestAudioFocusV26();
      } else {
        focusRequestResult = requestAudioFocusDefault();
      }
      audioFocusState =
          focusRequestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
              ? AUDIO_FOCUS_STATE_HAVE_FOCUS
              : AUDIO_FOCUS_STATE_NO_FOCUS;
    }

    if (audioFocusState == AUDIO_FOCUS_STATE_NO_FOCUS) {
      return PLAYER_COMMAND_DO_NOT_PLAY;
    }

    return audioFocusState == AUDIO_FOCUS_STATE_LOSS_TRANSIENT
        ? PLAYER_COMMAND_WAIT_FOR_CALLBACK
        : PLAYER_COMMAND_PLAY_WHEN_READY;
  }

  private void abandonAudioFocus() {
    abandonAudioFocus(/* forceAbandon= */ false);
  }

  private void abandonAudioFocus(boolean forceAbandon) {
    if (focusGain == C.AUDIOFOCUS_NONE && audioFocusState == AUDIO_FOCUS_STATE_NO_FOCUS) {
      return;
    }

    if (focusGain != C.AUDIOFOCUS_GAIN
        || audioFocusState == AUDIO_FOCUS_STATE_LOST_FOCUS
        || forceAbandon) {
      if (Util.SDK_INT >= 26) {
        abandonAudioFocusV26();
      } else {
        abandonAudioFocusDefault();
      }
      audioFocusState = AUDIO_FOCUS_STATE_NO_FOCUS;
    }
  }

  private int requestAudioFocusDefault() {
    return audioManager.requestAudioFocus(
        focusListener,
        Util.getStreamTypeForAudioUsage(Assertions.checkNotNull(audioAttributes).usage),
        focusGain);
  }

  @RequiresApi(26)
  private int requestAudioFocusV26() {
    if (audioFocusRequest == null || rebuildAudioFocusRequest) {
      AudioFocusRequest.Builder builder =
          audioFocusRequest == null
              ? new AudioFocusRequest.Builder(focusGain)
              : new AudioFocusRequest.Builder(audioFocusRequest);

      boolean willPauseWhenDucked = willPauseWhenDucked();
      audioFocusRequest =
          builder
              .setAudioAttributes(Assertions.checkNotNull(audioAttributes).getAudioAttributesV21())
              .setWillPauseWhenDucked(willPauseWhenDucked)
              .setOnAudioFocusChangeListener(focusListener)
              .build();

      rebuildAudioFocusRequest = false;
    }
    return audioManager.requestAudioFocus(audioFocusRequest);
  }

  private void abandonAudioFocusDefault() {
    audioManager.abandonAudioFocus(focusListener);
  }

  @RequiresApi(26)
  private void abandonAudioFocusV26() {
    if (audioFocusRequest != null) {
      audioManager.abandonAudioFocusRequest(audioFocusRequest);
    }
  }

  private boolean willPauseWhenDucked() {
    return audioAttributes != null && audioAttributes.contentType == C.CONTENT_TYPE_SPEECH;
  }

  /**
   * Converts {@link AudioAttributes} to one of the audio focus request.
   *
   * <p>This follows the class Javadoc of {@link AudioFocusRequest}.
   *
   * @param audioAttributes The audio attributes associated with this focus request.
   * @return The type of audio focus gain that should be requested.
   */
  private static int convertAudioAttributesToFocusGain(@Nullable AudioAttributes audioAttributes) {

    if (audioAttributes == null) {
      // Don't handle audio focus. It may be either video only contents or developers
      // want to have more finer grained control. (e.g. adding audio focus listener)
      return C.AUDIOFOCUS_NONE;
    }

    switch (audioAttributes.usage) {
        // USAGE_VOICE_COMMUNICATION_SIGNALLING is for DTMF that may happen multiple times
        // during the phone call when AUDIOFOCUS_GAIN_TRANSIENT is requested for that.
        // Don't request audio focus here.
      case C.USAGE_VOICE_COMMUNICATION_SIGNALLING:
        return C.AUDIOFOCUS_NONE;

        // Javadoc says 'AUDIOFOCUS_GAIN: Examples of uses of this focus gain are for music
        // playback, for a game or a video player'
      case C.USAGE_GAME:
      case C.USAGE_MEDIA:
        return C.AUDIOFOCUS_GAIN;

        // Special usages: USAGE_UNKNOWN shouldn't be used. Request audio focus to prevent
        // multiple media playback happen at the same time.
      case C.USAGE_UNKNOWN:
        Log.w(
            TAG,
            "Specify a proper usage in the audio attributes for audio focus"
                + " handling. Using AUDIOFOCUS_GAIN by default.");
        return C.AUDIOFOCUS_GAIN;

        // Javadoc says 'AUDIOFOCUS_GAIN_TRANSIENT: An example is for playing an alarm, or
        // during a VoIP call'
      case C.USAGE_ALARM:
      case C.USAGE_VOICE_COMMUNICATION:
        return C.AUDIOFOCUS_GAIN_TRANSIENT;

        // Javadoc says 'AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK: Examples are when playing
        // driving directions or notifications'
      case C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      case C.USAGE_ASSISTANCE_SONIFICATION:
      case C.USAGE_NOTIFICATION:
      case C.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
      case C.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
      case C.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
      case C.USAGE_NOTIFICATION_EVENT:
      case C.USAGE_NOTIFICATION_RINGTONE:
        return C.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;

        // Javadoc says 'AUDIOFOCUS_GAIN_EXCLUSIVE: This is typically used if you are doing
        // audio recording or speech recognition'.
        // Assistant is considered as both recording and notifying developer
      case C.USAGE_ASSISTANT:
        if (Util.SDK_INT >= 19) {
          return C.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
        } else {
          return C.AUDIOFOCUS_GAIN_TRANSIENT;
        }

        // Special usages:
      case C.USAGE_ASSISTANCE_ACCESSIBILITY:
        if (audioAttributes.contentType == C.CONTENT_TYPE_SPEECH) {
          // Voice shouldn't be interrupted by other playback.
          return C.AUDIOFOCUS_GAIN_TRANSIENT;
        }
        return C.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
      default:
        Log.w(TAG, "Unidentified audio usage: " + audioAttributes.usage);
        return C.AUDIOFOCUS_NONE;
    }
  }

  // Internal audio focus listener.

  private class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
    @Override
    public void onAudioFocusChange(int focusChange) {
      // Convert the platform focus change to internal state.
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_LOSS:
          audioFocusState = AUDIO_FOCUS_STATE_LOST_FOCUS;
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          audioFocusState = AUDIO_FOCUS_STATE_LOSS_TRANSIENT;
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          if (willPauseWhenDucked()) {
            audioFocusState = AUDIO_FOCUS_STATE_LOSS_TRANSIENT;
          } else {
            audioFocusState = AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK;
          }
          break;
        case AudioManager.AUDIOFOCUS_GAIN:
          audioFocusState = AUDIO_FOCUS_STATE_HAVE_FOCUS;
          break;
        default:
          Log.w(TAG, "Unknown focus change type: " + focusChange);
          // Early return.
          return;
      }

      // Handle the internal state (change).
      switch (audioFocusState) {
        case AUDIO_FOCUS_STATE_NO_FOCUS:
          // Focus was not requested; nothing to do.
          break;
        case AUDIO_FOCUS_STATE_LOST_FOCUS:
          playerControl.executePlayerCommand(PLAYER_COMMAND_DO_NOT_PLAY);
          abandonAudioFocus(/* forceAbandon= */ true);
          break;
        case AUDIO_FOCUS_STATE_LOSS_TRANSIENT:
          playerControl.executePlayerCommand(PLAYER_COMMAND_WAIT_FOR_CALLBACK);
          break;
        case AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK:
          // Volume will be adjusted by the code below.
          break;
        case AUDIO_FOCUS_STATE_HAVE_FOCUS:
          playerControl.executePlayerCommand(PLAYER_COMMAND_PLAY_WHEN_READY);
          break;
        default:
          throw new IllegalStateException("Unknown audio focus state: " + audioFocusState);
      }

      float volumeMultiplier =
          (audioFocusState == AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK)
              ? AudioFocusManager.VOLUME_MULTIPLIER_DUCK
              : AudioFocusManager.VOLUME_MULTIPLIER_DEFAULT;
      if (AudioFocusManager.this.volumeMultiplier != volumeMultiplier) {
        AudioFocusManager.this.volumeMultiplier = volumeMultiplier;
        playerControl.setVolumeMultiplier(volumeMultiplier);
      }
    }
  }
}
