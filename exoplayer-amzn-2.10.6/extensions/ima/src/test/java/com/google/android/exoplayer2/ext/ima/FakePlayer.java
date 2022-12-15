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
package com.google.android.exoplayer2.ext.ima;

import android.os.Looper;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.StubExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import java.util.ArrayList;

/** A fake player for testing content/ad playback. */
/* package */ final class FakePlayer extends StubExoPlayer {

  private final ArrayList<Player.EventListener> listeners;
  private final Timeline.Period period;
  private final Timeline timeline;

  private boolean prepared;
  private int state;
  private boolean playWhenReady;
  private long position;
  private long contentPosition;
  private boolean isPlayingAd;
  private int adGroupIndex;
  private int adIndexInAdGroup;

  public FakePlayer() {
    listeners = new ArrayList<>();
    period = new Timeline.Period();
    state = Player.STATE_IDLE;
    playWhenReady = true;
    timeline = Timeline.EMPTY;
  }

  /** Sets the timeline on this fake player, which notifies listeners with the changed timeline. */
  public void updateTimeline(Timeline timeline) {
    for (Player.EventListener listener : listeners) {
      listener.onTimelineChanged(
          timeline,
          null,
          prepared ? TIMELINE_CHANGE_REASON_DYNAMIC : TIMELINE_CHANGE_REASON_PREPARED);
    }
    prepared = true;
  }

  /**
   * Sets the state of this player as if it were playing content at the given {@code position}. If
   * an ad is currently playing, this will trigger a position discontinuity.
   */
  public void setPlayingContentPosition(long position) {
    boolean notify = isPlayingAd;
    isPlayingAd = false;
    adGroupIndex = C.INDEX_UNSET;
    adIndexInAdGroup = C.INDEX_UNSET;
    this.position = position;
    contentPosition = position;
    if (notify) {
      for (Player.EventListener listener : listeners) {
        listener.onPositionDiscontinuity(DISCONTINUITY_REASON_AD_INSERTION);
      }
    }
  }

  /**
   * Sets the state of this player as if it were playing an ad with the given indices at the given
   * {@code position}. If the player is playing a different ad or content, this will trigger a
   * position discontinuity.
   */
  public void setPlayingAdPosition(
      int adGroupIndex, int adIndexInAdGroup, long position, long contentPosition) {
    boolean notify = !isPlayingAd || this.adIndexInAdGroup != adIndexInAdGroup;
    isPlayingAd = true;
    this.adGroupIndex = adGroupIndex;
    this.adIndexInAdGroup = adIndexInAdGroup;
    this.position = position;
    this.contentPosition = contentPosition;
    if (notify) {
      for (Player.EventListener listener : listeners) {
        listener.onPositionDiscontinuity(DISCONTINUITY_REASON_AD_INSERTION);
      }
    }
  }

  /** Sets the state of this player with the given {@code STATE} constant. */
  public void setState(int state, boolean playWhenReady) {
    boolean notify = this.state != state || this.playWhenReady != playWhenReady;
    this.state = state;
    this.playWhenReady = playWhenReady;
    if (notify) {
      for (Player.EventListener listener : listeners) {
        listener.onPlayerStateChanged(playWhenReady, state);
      }
    }
  }

  // ExoPlayer methods. Other methods are unsupported.

  @Override
  public AudioComponent getAudioComponent() {
    return null;
  }

  @Override
  public Looper getApplicationLooper() {
    return Looper.getMainLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getPlaybackState() {
    return state;
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public int getRendererCount() {
    return 0;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return new TrackSelectionArray();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return 0;
  }

  @Override
  public int getCurrentWindowIndex() {
    return 0;
  }

  @Override
  public long getDuration() {
    if (timeline.isEmpty()) {
      return C.INDEX_UNSET;
    }
    if (isPlayingAd()) {
      long adDurationUs =
          timeline.getPeriod(0, period).getAdDurationUs(adGroupIndex, adIndexInAdGroup);
      return C.usToMs(adDurationUs);
    } else {
      return timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }
  }

  @Override
  public long getCurrentPosition() {
    return position;
  }

  @Override
  public boolean isPlayingAd() {
    return isPlayingAd;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return adGroupIndex;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return adIndexInAdGroup;
  }

  @Override
  public long getContentPosition() {
    return contentPosition;
  }
}
