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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MediaClock;
import java.io.IOException;

/**
 * A {@link Renderer} implementation whose track type is {@link C#TRACK_TYPE_NONE} and does not
 * consume data from its {@link SampleStream}.
 */
public abstract class NoSampleRenderer implements Renderer, RendererCapabilities {

  private RendererConfiguration configuration;
  private int index;
  private int state;
  private SampleStream stream;
  private boolean streamIsFinal;

  @Override
  public final int getTrackType() {
    return C.TRACK_TYPE_NONE;
  }

  @Override
  public final RendererCapabilities getCapabilities() {
    return this;
  }

  @Override
  public final void setIndex(int index) {
    this.index = index;
  }

  @Override
  public MediaClock getMediaClock() {
    return null;
  }

  @Override
  public final int getState() {
    return state;
  }

  /**
   * Replaces the {@link SampleStream} that will be associated with this renderer.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_DISABLED}.
   *
   * @param configuration The renderer configuration.
   * @param formats The enabled formats. Should be empty.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param offsetUs The offset that should be subtracted from {@code positionUs}
   *     to get the playback position with respect to the media.
   * @throws ExoPlaybackException If an error occurs.
   */
  @Override
  public final void enable(RendererConfiguration configuration, Format[] formats,
      SampleStream stream, long positionUs, boolean joining, long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(state == STATE_DISABLED);
    this.configuration = configuration;
    state = STATE_ENABLED;
    onEnabled(joining);
    replaceStream(formats, stream, offsetUs);
    onPositionReset(positionUs, joining);
  }

  @Override
  public final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
    onStarted();
  }

  /**
   * Replaces the {@link SampleStream} that will be associated with this renderer.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param formats The enabled formats. Should be empty.
   * @param stream The {@link SampleStream} to be associated with this renderer.
   * @param offsetUs The offset that should be subtracted from {@code positionUs} in
   *     {@link #render(long, long)} to get the playback position with respect to the media.
   * @throws ExoPlaybackException If an error occurs.
   */
  @Override
  public final void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(!streamIsFinal);
    this.stream = stream;
    onRendererOffsetChanged(offsetUs);
  }

  @Override
  public final SampleStream getStream() {
    return stream;
  }

  @Override
  public final boolean hasReadStreamToEnd() {
    return true;
  }

  @Override
  public long getReadingPositionUs() {
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public final void setCurrentStreamFinal() {
    streamIsFinal = true;
  }

  @Override
  public final boolean isCurrentStreamFinal() {
    return streamIsFinal;
  }

  @Override
  public final void maybeThrowStreamError() throws IOException {
  }

  @Override
  public final void resetPosition(long positionUs) throws ExoPlaybackException {
    streamIsFinal = false;
    onPositionReset(positionUs, false);
  }

  @Override
  public final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
    onStopped();
  }

  @Override
  public final void disable() {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_DISABLED;
    stream = null;
    streamIsFinal = false;
    onDisabled();
  }

  @Override
  public final void reset() {
    Assertions.checkState(state == STATE_DISABLED);
    onReset();
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public boolean isEnded() {
    return true;
  }

  // RendererCapabilities implementation.

  @Override
  public int supportsFormat(Format format) throws ExoPlaybackException {
    return FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SUPPORTED;
  }

  // PlayerMessage.Target implementation.

  @Override
  public void handleMessage(int what, @Nullable Object object) throws ExoPlaybackException {
    // Do nothing.
  }

  // Methods to be overridden by subclasses.

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer's offset has been changed.
   * <p>
   * The default implementation is a no-op.
   *
   * @param offsetUs The offset that should be subtracted from {@code positionUs} in
   *     {@link #render(long, long)} to get the playback position with respect to the media.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onRendererOffsetChanged(long offsetUs) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the position is reset. This occurs when the renderer is enabled after
   * {@link #onRendererOffsetChanged(long)} has been called, and also when a position
   * discontinuity is encountered.
   * <p>
   * The default implementation is a no-op.
   *
   * @param positionUs The new playback position in microseconds.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is started.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStarted() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is stopped.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStopped() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onDisabled() {
    // Do nothing.
  }

  /**
   * Called when the renderer is reset.
   *
   * <p>The default implementation is a no-op.
   */
  protected void onReset() {
    // Do nothing.
  }

  // Methods to be called by subclasses.

  /**
   * Returns the configuration set when the renderer was most recently enabled.
   */
  protected final RendererConfiguration getConfiguration() {
    return configuration;
  }

  /**
   * Returns the index of the renderer within the player.
   */
  protected final int getIndex() {
    return index;
  }

}
