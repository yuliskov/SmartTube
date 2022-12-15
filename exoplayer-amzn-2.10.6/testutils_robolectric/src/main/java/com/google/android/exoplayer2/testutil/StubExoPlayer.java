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
package com.google.android.exoplayer2.testutil;

import android.os.Looper;
import com.google.android.exoplayer2.BasePlayer;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

/**
 * An abstract {@link ExoPlayer} implementation that throws {@link UnsupportedOperationException}
 * from every method.
 */
public abstract class StubExoPlayer extends BasePlayer implements ExoPlayer {

  @Override
  public AudioComponent getAudioComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VideoComponent getVideoComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TextComponent getTextComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MetadataComponent getMetadataComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Looper getPlaybackLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Looper getApplicationLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPlaybackState() {
    throw new UnsupportedOperationException();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExoPlaybackException getPlaybackError() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void retry() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getPlayWhenReady() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRepeatMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isLoading() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSeekParameters(SeekParameters seekParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeekParameters getSeekParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop(boolean resetStateAndPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void sendMessages(ExoPlayerMessage... messages) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRendererCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRendererType(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getCurrentManifest() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Timeline getCurrentTimeline() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentPeriodIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getDuration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getCurrentPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getBufferedPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTotalBufferedDuration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPlayingAd() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getContentPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getContentBufferedPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    throw new UnsupportedOperationException();
  }
}
