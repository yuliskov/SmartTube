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
package com.google.android.exoplayer2.ui;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.widget.TextView;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Locale;

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * a {@link SimpleExoPlayer}.
 */
public class DebugTextViewHelper implements Player.EventListener, Runnable {

  private static final int REFRESH_INTERVAL_MS = 1000;

  private final SimpleExoPlayer player;
  private final TextView textView;

  private boolean started;

  /**
   * @param player The {@link SimpleExoPlayer} from which debug information should be obtained. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   * @param textView The {@link TextView} that should be updated to display the information.
   */
  public DebugTextViewHelper(SimpleExoPlayer player, TextView textView) {
    Assertions.checkArgument(player.getApplicationLooper() == Looper.getMainLooper());
    this.player = player;
    this.textView = textView;
  }

  /**
   * Starts periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public final void start() {
    if (started) {
      return;
    }
    started = true;
    player.addListener(this);
    updateAndPost();
  }

  /**
   * Stops periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public final void stop() {
    if (!started) {
      return;
    }
    started = false;
    player.removeListener(this);
    textView.removeCallbacks(this);
  }

  // Player.EventListener implementation.

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    updateAndPost();
  }

  @Override
  public final void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    updateAndPost();
  }

  // Runnable implementation.

  @Override
  public final void run() {
    updateAndPost();
  }

  // Protected methods.

  @SuppressLint("SetTextI18n")
  protected final void updateAndPost() {
    textView.setText(getDebugString());
    textView.removeCallbacks(this);
    textView.postDelayed(this, REFRESH_INTERVAL_MS);
  }

  /** Returns the debugging information string to be shown by the target {@link TextView}. */
  protected String getDebugString() {
    return getPlayerStateString() + getVideoString() + getAudioString();
  }

  /** Returns a string containing player state debugging information. */
  protected String getPlayerStateString() {
    String playbackStateString;
    switch (player.getPlaybackState()) {
      case Player.STATE_BUFFERING:
        playbackStateString = "buffering";
        break;
      case Player.STATE_ENDED:
        playbackStateString = "ended";
        break;
      case Player.STATE_IDLE:
        playbackStateString = "idle";
        break;
      case Player.STATE_READY:
        playbackStateString = "ready";
        break;
      default:
        playbackStateString = "unknown";
        break;
    }
    return String.format(
        "playWhenReady:%s playbackState:%s window:%s",
        player.getPlayWhenReady(), playbackStateString, player.getCurrentWindowIndex());
  }

  /** Returns a string containing video debugging information. */
  protected String getVideoString() {
    Format format = player.getVideoFormat();
    DecoderCounters decoderCounters = player.getVideoDecoderCounters();
    if (format == null || decoderCounters == null) {
      return "";
    }
    return "\n"
        + format.sampleMimeType
        + "(id:"
        + format.id
        + " r:"
        + format.width
        + "x"
        + format.height
        + getPixelAspectRatioString(format.pixelWidthHeightRatio)
        + getDecoderCountersBufferCountString(decoderCounters)
        + ")";
  }

  /** Returns a string containing audio debugging information. */
  protected String getAudioString() {
    Format format = player.getAudioFormat();
    DecoderCounters decoderCounters = player.getAudioDecoderCounters();
    if (format == null || decoderCounters == null) {
      return "";
    }
    return "\n"
        + format.sampleMimeType
        + "(id:"
        + format.id
        + " hz:"
        + format.sampleRate
        + " ch:"
        + format.channelCount
        + getDecoderCountersBufferCountString(decoderCounters)
        + ")";
  }

  private static String getDecoderCountersBufferCountString(DecoderCounters counters) {
    if (counters == null) {
      return "";
    }
    counters.ensureUpdated();
    return " sib:" + counters.skippedInputBufferCount
        + " sb:" + counters.skippedOutputBufferCount
        + " rb:" + counters.renderedOutputBufferCount
        + " db:" + counters.droppedBufferCount
        + " mcdb:" + counters.maxConsecutiveDroppedBufferCount
        + " dk:" + counters.droppedToKeyframeCount;
  }

  private static String getPixelAspectRatioString(float pixelAspectRatio) {
    return pixelAspectRatio == Format.NO_VALUE || pixelAspectRatio == 1f ? ""
        : (" par:" + String.format(Locale.US, "%.02f", pixelAspectRatio));
  }

}
