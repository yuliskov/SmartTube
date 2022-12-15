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
package com.google.android.exoplayer2.audio;

import android.media.AudioTrack;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import java.nio.ByteBuffer;

/**
 * A sink that consumes audio data.
 *
 * <p>Before starting playback, specify the input audio format by calling {@link #configure(int,
 * int, int, int, int[], int, int)}.
 *
 * <p>Call {@link #handleBuffer(ByteBuffer, long)} to write data, and {@link #handleDiscontinuity()}
 * when the data being fed is discontinuous. Call {@link #play()} to start playing the written data.
 *
 * <p>Call {@link #configure(int, int, int, int, int[], int, int)} whenever the input format
 * changes. The sink will be reinitialized on the next call to {@link #handleBuffer(ByteBuffer,
 * long)}.
 *
 * <p>Call {@link #flush()} to prepare the sink to receive audio data from a new playback position.
 *
 * <p>Call {@link #playToEndOfStream()} repeatedly to play out all data when no more input buffers
 * will be provided via {@link #handleBuffer(ByteBuffer, long)} until the next {@link #flush()}.
 * Call {@link #reset()} when the instance is no longer required.
 *
 * <p>The implementation may be backed by a platform {@link AudioTrack}. In this case, {@link
 * #setAudioSessionId(int)}, {@link #setAudioAttributes(AudioAttributes)}, {@link
 * #enableTunnelingV21(int)} and/or {@link #disableTunneling()} may be called before writing data to
 * the sink. These methods may also be called after writing data to the sink, in which case it will
 * be reinitialized as required. For implementations that are not based on platform {@link
 * AudioTrack}s, calling methods relating to audio sessions, audio attributes, and tunneling may
 * have no effect.
 */
public interface AudioSink {

  /**
   * Listener for audio sink events.
   */
  interface Listener {

    /**
     * Called if the audio sink has started rendering audio to a new platform audio session.
     *
     * @param audioSessionId The newly generated audio session's identifier.
     */
    void onAudioSessionId(int audioSessionId);

    /**
     * Called when the audio sink handles a buffer whose timestamp is discontinuous with the last
     * buffer handled since it was reset.
     */
    void onPositionDiscontinuity();

    /**
     * Called when the audio sink runs out of data.
     * <p>
     * An audio sink implementation may never call this method (for example, if audio data is
     * consumed in batches rather than based on the sink's own clock).
     *
     * @param bufferSize The size of the sink's buffer, in bytes.
     * @param bufferSizeMs The size of the sink's buffer, in milliseconds, if it is configured for
     *     PCM output. {@link C#TIME_UNSET} if it is configured for encoded audio output, as the
     *     buffered media can have a variable bitrate so the duration may be unknown.
     * @param elapsedSinceLastFeedMs The time since the sink was last fed data, in milliseconds.
     */
    void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

  }

  /**
   * Thrown when a failure occurs configuring the sink.
   */
  final class ConfigurationException extends Exception {

    /**
     * Creates a new configuration exception with the specified {@code cause} and no message.
     */
    public ConfigurationException(Throwable cause) {
      super(cause);
    }

    /**
     * Creates a new configuration exception with the specified {@code message} and no cause.
     */
    public ConfigurationException(String message) {
      super(message);
    }

  }

  /**
   * Thrown when a failure occurs initializing the sink.
   */
  final class InitializationException extends Exception {

    /**
     * The underlying {@link AudioTrack}'s state, if applicable.
     */
    public final int audioTrackState;

    /**
     * @param audioTrackState The underlying {@link AudioTrack}'s state, if applicable.
     * @param sampleRate The requested sample rate in Hz.
     * @param channelConfig The requested channel configuration.
     * @param bufferSize The requested buffer size in bytes.
     */
    public InitializationException(int audioTrackState, int sampleRate, int channelConfig,
        int bufferSize) {
      super("AudioTrack init failed: " + audioTrackState + ", Config(" + sampleRate + ", "
          + channelConfig + ", " + bufferSize + ")");
      this.audioTrackState = audioTrackState;
    }

  }

  /**
   * Thrown when a failure occurs writing to the sink.
   */
  final class WriteException extends Exception {

    /**
     * The error value returned from the sink implementation. If the sink writes to a platform
     * {@link AudioTrack}, this will be the error value returned from
     * {@link AudioTrack#write(byte[], int, int)} or {@link AudioTrack#write(ByteBuffer, int, int)}.
     * Otherwise, the meaning of the error code depends on the sink implementation.
     */
    public final int errorCode;

    /**
     * @param errorCode The error value returned from the sink implementation.
     */
    public WriteException(int errorCode) {
      super("AudioTrack write failed: " + errorCode);
      this.errorCode = errorCode;
    }

  }

  /**
   * Returned by {@link #getCurrentPositionUs(boolean)} when the position is not set.
   */
  long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;

  /**
   * Sets the listener for sink events, which should be the audio renderer.
   *
   * @param listener The listener for sink events, which should be the audio renderer.
   */
  void setListener(Listener listener);

  /**
   * Returns whether the sink supports the audio format.
   *
   * @param channelCount The number of channels, or {@link Format#NO_VALUE} if not known.
   * @param encoding The audio encoding, or {@link Format#NO_VALUE} if not known.
   * @return Whether the sink supports the audio format.
   */
  boolean supportsOutput(int channelCount, @C.Encoding int encoding);

  /**
   * Returns the playback position in the stream starting at zero, in microseconds, or
   * {@link #CURRENT_POSITION_NOT_SET} if it is not yet available.
   *
   * @param sourceEnded Specify {@code true} if no more input buffers will be provided.
   * @return The playback position relative to the start of playback, in microseconds.
   */
  long getCurrentPositionUs(boolean sourceEnded);

  /**
   * Configures (or reconfigures) the sink.
   *
   * @param inputEncoding The encoding of audio data provided in the input buffers.
   * @param inputChannelCount The number of channels.
   * @param inputSampleRate The sample rate in Hz.
   * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to infer a
   *     suitable buffer size.
   * @param outputChannels A mapping from input to output channels that is applied to this sink's
   *     input as a preprocessing step, if handling PCM input. Specify {@code null} to leave the
   *     input unchanged. Otherwise, the element at index {@code i} specifies index of the input
   *     channel to map to output channel {@code i} when preprocessing input buffers. After the map
   *     is applied the audio data will have {@code outputChannels.length} channels.
   * @param trimStartFrames The number of audio frames to trim from the start of data written to the
   *     sink after this call.
   * @param trimEndFrames The number of audio frames to trim from data written to the sink
   *     immediately preceding the next call to {@link #flush()} or this method.
   * @throws ConfigurationException If an error occurs configuring the sink.
   */
  void configure(
      @C.Encoding int inputEncoding,
      int inputChannelCount,
      int inputSampleRate,
      int specifiedBufferSize,
      @Nullable int[] outputChannels,
      int trimStartFrames,
      int trimEndFrames)
      throws ConfigurationException;

  /**
   * Starts or resumes consuming audio if initialized.
   */
  void play();

  /** Signals to the sink that the next buffer may be discontinuous with the previous buffer. */
  void handleDiscontinuity();

  /**
   * Attempts to process data from a {@link ByteBuffer}, starting from its current position and
   * ending at its limit (exclusive). The position of the {@link ByteBuffer} is advanced by the
   * number of bytes that were handled. {@link Listener#onPositionDiscontinuity()} will be called if
   * {@code presentationTimeUs} is discontinuous with the last buffer handled since the last reset.
   *
   * <p>Returns whether the data was handled in full. If the data was not handled in full then the
   * same {@link ByteBuffer} must be provided to subsequent calls until it has been fully consumed,
   * except in the case of an intervening call to {@link #flush()} (or to {@link #configure(int,
   * int, int, int, int[], int, int)} that causes the sink to be flushed).
   *
   * @param buffer The buffer containing audio data.
   * @param presentationTimeUs The presentation timestamp of the buffer in microseconds.
   * @return Whether the buffer was handled fully.
   * @throws InitializationException If an error occurs initializing the sink.
   * @throws WriteException If an error occurs writing the audio data.
   */
  boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs)
      throws InitializationException, WriteException;

  /**
   * Processes any remaining data. {@link #isEnded()} will return {@code true} when no data remains.
   *
   * @throws WriteException If an error occurs draining data to the sink.
   */
  void playToEndOfStream() throws WriteException;

  /**
   * Returns whether {@link #playToEndOfStream} has been called and all buffers have been processed.
   */
  boolean isEnded();

  /**
   * Returns whether the sink has data pending that has not been consumed yet.
   */
  boolean hasPendingData();

  /**
   * Attempts to set the playback parameters and returns the active playback parameters, which may
   * differ from those passed in.
   *
   * @param playbackParameters The new playback parameters to attempt to set.
   * @return The active playback parameters.
   */
  PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * Gets the active {@link PlaybackParameters}.
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * Sets attributes for audio playback. If the attributes have changed and if the sink is not
   * configured for use with tunneling, then it is reset and the audio session id is cleared.
   * <p>
   * If the sink is configured for use with tunneling then the audio attributes are ignored. The
   * sink is not reset and the audio session id is not cleared. The passed attributes will be used
   * if the sink is later re-configured into non-tunneled mode.
   *
   * @param audioAttributes The attributes for audio playback.
   */
  void setAudioAttributes(AudioAttributes audioAttributes);

  /** Sets the audio session id. */
  void setAudioSessionId(int audioSessionId);

  /** Sets the auxiliary effect. */
  void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

  /**
   * Enables tunneling, if possible. The sink is reset if tunneling was previously disabled or if
   * the audio session id has changed. Enabling tunneling is only possible if the sink is based on a
   * platform {@link AudioTrack}, and requires platform API version 21 onwards.
   *
   * @param tunnelingAudioSessionId The audio session id to use.
   * @throws IllegalStateException Thrown if enabling tunneling on platform API version &lt; 21.
   */
  void enableTunnelingV21(int tunnelingAudioSessionId);

  /**
   * Disables tunneling. If tunneling was previously enabled then the sink is reset and any audio
   * session id is cleared.
   */
  void disableTunneling();

  /**
   * Sets the playback volume.
   *
   * @param volume A volume in the range [0.0, 1.0].
   */
  void setVolume(float volume);

  /**
   * Pauses playback.
   */
  void pause();

  /**
   * Flushes the sink, after which it is ready to receive buffers from a new playback position.
   *
   * <p>The audio session may remain active until {@link #reset()} is called.
   */
  void flush();

  /** Resets the renderer, releasing any resources that it currently holds. */
  void reset();
}
