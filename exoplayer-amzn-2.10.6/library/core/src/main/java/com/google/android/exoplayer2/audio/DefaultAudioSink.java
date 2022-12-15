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
package com.google.android.exoplayer2.audio;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.ConditionVariable;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.AmazonQuirks;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import com.google.android.exoplayer2.util.Logger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Plays audio data. The implementation delegates to an {@link AudioTrack} and handles playback
 * position smoothing, non-blocking writes and reconfiguration.
 * <p>
 * If tunneling mode is enabled, care must be taken that audio processors do not output buffers with
 * a different duration than their input, and buffer processors must produce output corresponding to
 * their last input immediately after that input is queued. This means that, for example, speed
 * adjustment is not possible while using tunneling.
 */
public final class DefaultAudioSink implements AudioSink {

  /**
   * Thrown when the audio track has provided a spurious timestamp, if {@link
   * #failOnSpuriousAudioTimestamp} is set.
   */
  public static final class InvalidAudioTrackTimestampException extends RuntimeException {

    /**
     * Creates a new invalid timestamp exception with the specified message.
     *
     * @param message The detail message for this exception.
     */
    private InvalidAudioTrackTimestampException(String message) {
      super(message);
    }

  }

  /**
   * Provides a chain of audio processors, which are used for any user-defined processing and
   * applying playback parameters (if supported). Because applying playback parameters can skip and
   * stretch/compress audio, the sink will query the chain for information on how to transform its
   * output position to map it onto a media position, via {@link #getMediaDuration(long)} and {@link
   * #getSkippedOutputFrameCount()}.
   */
  public interface AudioProcessorChain {

    /**
     * Returns the fixed chain of audio processors that will process audio. This method is called
     * once during initialization, but audio processors may change state to become active/inactive
     * during playback.
     */
    AudioProcessor[] getAudioProcessors();

    /**
     * Configures audio processors to apply the specified playback parameters immediately, returning
     * the new parameters, which may differ from those passed in. Only called when processors have
     * no input pending.
     *
     * @param playbackParameters The playback parameters to try to apply.
     * @return The playback parameters that were actually applied.
     */
    PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters);

    /**
     * Scales the specified playout duration to take into account speedup due to audio processing,
     * returning an input media duration, in arbitrary units.
     */
    long getMediaDuration(long playoutDuration);

    /**
     * Returns the number of output audio frames skipped since the audio processors were last
     * flushed.
     */
    long getSkippedOutputFrameCount();
  }

  /**
   * The default audio processor chain, which applies a (possibly empty) chain of user-defined audio
   * processors followed by {@link SilenceSkippingAudioProcessor} and {@link SonicAudioProcessor}.
   */
  public static class DefaultAudioProcessorChain implements AudioProcessorChain {

    private final AudioProcessor[] audioProcessors;
    private final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;
    private final SonicAudioProcessor sonicAudioProcessor;

    /**
     * Creates a new default chain of audio processors, with the user-defined {@code
     * audioProcessors} applied before silence skipping and playback parameters.
     */
    public DefaultAudioProcessorChain(AudioProcessor... audioProcessors) {
      // The passed-in type may be more specialized than AudioProcessor[], so allocate a new array
      // rather than using Arrays.copyOf.
      this.audioProcessors = new AudioProcessor[audioProcessors.length + 2];
      System.arraycopy(
          /* src= */ audioProcessors,
          /* srcPos= */ 0,
          /* dest= */ this.audioProcessors,
          /* destPos= */ 0,
          /* length= */ audioProcessors.length);
      silenceSkippingAudioProcessor = new SilenceSkippingAudioProcessor();
      sonicAudioProcessor = new SonicAudioProcessor();
      this.audioProcessors[audioProcessors.length] = silenceSkippingAudioProcessor;
      this.audioProcessors[audioProcessors.length + 1] = sonicAudioProcessor;
    }

    @Override
    public AudioProcessor[] getAudioProcessors() {
      return audioProcessors;
    }

    @Override
    public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
      silenceSkippingAudioProcessor.setEnabled(playbackParameters.skipSilence);
      return new PlaybackParameters(
          sonicAudioProcessor.setSpeed(playbackParameters.speed),
          sonicAudioProcessor.setPitch(playbackParameters.pitch),
          playbackParameters.skipSilence);
    }

    @Override
    public long getMediaDuration(long playoutDuration) {
      return sonicAudioProcessor.scaleDurationForSpeedup(playoutDuration);
    }

    @Override
    public long getSkippedOutputFrameCount() {
      return silenceSkippingAudioProcessor.getSkippedFrames();
    }
  }

  /**
   * A minimum length for the {@link AudioTrack} buffer, in microseconds.
   */
  private static final long MIN_BUFFER_DURATION_US = 250000;
  /**
   * A maximum length for the {@link AudioTrack} buffer, in microseconds.
   */
  private static final long MAX_BUFFER_DURATION_US = 750000;
  /**
   * The length for passthrough {@link AudioTrack} buffers, in microseconds.
   */
  private static final long PASSTHROUGH_BUFFER_DURATION_US = 250000;
  /**
   * A multiplication factor to apply to the minimum buffer size requested by the underlying
   * {@link AudioTrack}.
   */
  private static final int BUFFER_MULTIPLICATION_FACTOR = 4;

  /** To avoid underruns on some devices (e.g., Broadcom 7271), scale up the AC3 buffer duration. */
  private static final int AC3_BUFFER_MULTIPLICATION_FACTOR = 2;

  /**
   * @see AudioTrack#ERROR_BAD_VALUE
   */
  private static final int ERROR_BAD_VALUE = AudioTrack.ERROR_BAD_VALUE;
  /**
   * @see AudioTrack#MODE_STATIC
   */
  private static final int MODE_STATIC = AudioTrack.MODE_STATIC;
  /**
   * @see AudioTrack#MODE_STREAM
   */
  private static final int MODE_STREAM = AudioTrack.MODE_STREAM;
  /**
   * @see AudioTrack#STATE_INITIALIZED
   */
  private static final int STATE_INITIALIZED = AudioTrack.STATE_INITIALIZED;
  /**
   * @see AudioTrack#WRITE_NON_BLOCKING
   */
  @SuppressLint("InlinedApi")
  private static final int WRITE_NON_BLOCKING = AudioTrack.WRITE_NON_BLOCKING;

  private static final String TAG = "AudioTrack";


  /** Represents states of the {@link #startMediaTimeUs} value. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({START_NOT_SET, START_IN_SYNC, START_NEED_SYNC})
  private @interface StartMediaTimeState {}

  private static final int START_NOT_SET = 0;
  private static final int START_IN_SYNC = 1;
  private static final int START_NEED_SYNC = 2;

  /**
   * Whether to enable a workaround for an issue where an audio effect does not keep its session
   * active across releasing/initializing a new audio track, on platform builds where
   * {@link Util#SDK_INT} &lt; 21.
   * <p>
   * The flag must be set before creating a player.
   */
  public static boolean enablePreV21AudioSessionWorkaround = false;

  /**
   * Whether to throw an {@link InvalidAudioTrackTimestampException} when a spurious timestamp is
   * reported from {@link AudioTrack#getTimestamp}.
   * <p>
   * The flag must be set before creating a player. Should be set to {@code true} for testing and
   * debugging purposes only.
   */
  public static boolean failOnSpuriousAudioTimestamp = false;

  @Nullable private final AudioCapabilities audioCapabilities;
  private final AudioProcessorChain audioProcessorChain;
  private final boolean enableConvertHighResIntPcmToFloat;
  private final ChannelMappingAudioProcessor channelMappingAudioProcessor;
  private final TrimmingAudioProcessor trimmingAudioProcessor;
  private final AudioProcessor[] toIntPcmAvailableAudioProcessors;
  private final AudioProcessor[] toFloatPcmAvailableAudioProcessors;
  private final ConditionVariable releasingConditionVariable;
  private final AudioTrackPositionTracker audioTrackPositionTracker;
  private final ArrayDeque<PlaybackParametersCheckpoint> playbackParametersCheckpoints;

  @Nullable private Listener listener;
  /** Used to keep the audio session active on pre-V21 builds (see {@link #initialize()}). */
  @Nullable private AudioTrack keepSessionIdAudioTrack;

  @Nullable private Configuration pendingConfiguration;
  private Configuration configuration;
  private AudioTrack audioTrack;

  private AudioAttributes audioAttributes;
  @Nullable private PlaybackParameters afterDrainPlaybackParameters;
  private PlaybackParameters playbackParameters;
  private long playbackParametersOffsetUs;
  private long playbackParametersPositionUs;

  @Nullable private ByteBuffer avSyncHeader;
  private int bytesUntilNextAvSync;

  private long submittedPcmBytes;
  private long submittedEncodedFrames;
  private long writtenPcmBytes;
  private long writtenEncodedFrames;
  private int framesPerEncodedSample;
  private @StartMediaTimeState int startMediaTimeState;
  private long startMediaTimeUs;
  private float volume;

  private AudioProcessor[] activeAudioProcessors;
  private ByteBuffer[] outputBuffers;
  @Nullable private ByteBuffer inputBuffer;
  @Nullable private ByteBuffer outputBuffer;
  private byte[] preV21OutputBuffer;
  private int preV21OutputBufferOffset;
  private int drainingAudioProcessorIndex;
  private boolean handledEndOfStream;
  private boolean stoppedAudioTrack;

  private boolean playing;
  private int audioSessionId;
  private AuxEffectInfo auxEffectInfo;
  private boolean tunneling;
  private long lastFeedElapsedRealtimeMs;
  // AMZN_CHANGE_BEGIN
  private final Logger log = new Logger(Logger.Module.Audio, TAG);
  private final boolean DBG = log.allowDebug();
  private final boolean VDBG = log.allowVerbose();
  private static final boolean isLatencyQuirkEnabled = AmazonQuirks.isLatencyQuirkEnabled();
  private static final boolean isLegacyPassthroughQuirkEnabled = AmazonQuirks.isDolbyPassthroughQuirkEnabled();
  // AMZN_CHANGE_END

  /**
   * Creates a new default audio sink.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio before
   *     output. May be empty.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors) {
    this(audioCapabilities, audioProcessors, /* enableConvertHighResIntPcmToFloat= */ false);
  }

  /**
   * Creates a new default audio sink, optionally using float output for high resolution PCM.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio before
   *     output. May be empty.
   * @param enableConvertHighResIntPcmToFloat Whether to enable conversion of high resolution
   *     integer PCM to 32-bit float for output, if possible. Functionality that uses 16-bit integer
   *     audio processing (for example, speed and pitch adjustment) will not be available when float
   *     output is in use.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor[] audioProcessors,
      boolean enableConvertHighResIntPcmToFloat) {
    this(
        audioCapabilities,
        new DefaultAudioProcessorChain(audioProcessors),
        enableConvertHighResIntPcmToFloat);
  }

  /**
   * Creates a new default audio sink, optionally using float output for high resolution PCM and
   * with the specified {@code audioProcessorChain}.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessorChain An {@link AudioProcessorChain} which is used to apply playback
   *     parameters adjustments. The instance passed in must not be reused in other sinks.
   * @param enableConvertHighResIntPcmToFloat Whether to enable conversion of high resolution
   *     integer PCM to 32-bit float for output, if possible. Functionality that uses 16-bit integer
   *     audio processing (for example, speed and pitch adjustment) will not be available when float
   *     output is in use.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessorChain audioProcessorChain,
      boolean enableConvertHighResIntPcmToFloat) {
    this.audioCapabilities = audioCapabilities;
    this.audioProcessorChain = Assertions.checkNotNull(audioProcessorChain);
    this.enableConvertHighResIntPcmToFloat = enableConvertHighResIntPcmToFloat;
    releasingConditionVariable = new ConditionVariable(true);
    audioTrackPositionTracker = new AudioTrackPositionTracker(new PositionTrackerListener(),
                                      isLatencyQuirkEnabled); // AMZN_CHANGE_ONELINE
    channelMappingAudioProcessor = new ChannelMappingAudioProcessor();
    trimmingAudioProcessor = new TrimmingAudioProcessor();
    ArrayList<AudioProcessor> toIntPcmAudioProcessors = new ArrayList<>();
    Collections.addAll(
        toIntPcmAudioProcessors,
        new ResamplingAudioProcessor(),
        channelMappingAudioProcessor,
        trimmingAudioProcessor);
    Collections.addAll(toIntPcmAudioProcessors, audioProcessorChain.getAudioProcessors());
    toIntPcmAvailableAudioProcessors = toIntPcmAudioProcessors.toArray(new AudioProcessor[0]);
    toFloatPcmAvailableAudioProcessors = new AudioProcessor[] {new FloatResamplingAudioProcessor()};
    // AMZN_CHANGE_BEGIN
    log.i("Amazon quirks:"
            + " Latency:" + (isLatencyQuirkEnabled ? "on" : "off")
            + "; Dolby" + (isLegacyPassthroughQuirkEnabled ? "on" : "off")
            + ". On Sdk: " + Util.SDK_INT);
    // AMZN_CHANGE_END
    volume = 1.0f;
    startMediaTimeState = START_NOT_SET;
    audioAttributes = AudioAttributes.DEFAULT;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    auxEffectInfo = new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f);
    playbackParameters = PlaybackParameters.DEFAULT;
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    activeAudioProcessors = new AudioProcessor[0];
    outputBuffers = new ByteBuffer[0];
    playbackParametersCheckpoints = new ArrayDeque<>();
  }

  // AudioSink implementation.

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }
  // AMZN_CHANGE_BEGIN
  // This API is called from MediaCodecAudioTrackRenderer to skip
  // calling hasPendingData  to detect if the playback has ended or not since these APIs
  // always return true and fake the buffering state of audio track.
  // there is no way for us to depend on the audio track states to decide
  // if the playback has ended or not.
  public boolean applyDolbyPassthroughQuirk() {
    return (!configuration.isInputPcm && isLegacyPassthroughQuirkEnabled);
  }
  // AMZN_CHANGE_END

  @Override
  public boolean supportsOutput(int channelCount, @C.Encoding int encoding) {
    if (Util.isEncodingLinearPcm(encoding)) {
      // AudioTrack supports 16-bit integer PCM output in all platform API versions, and float
      // output from platform API version 21 only. Other integer PCM encodings are resampled by this
      // sink to 16-bit PCM. We assume that the audio framework will downsample any number of
      // channels to the output device's required number of channels.
      return encoding != C.ENCODING_PCM_FLOAT || Util.SDK_INT >= 21;
    } else {
      return audioCapabilities != null
          && audioCapabilities.supportsEncoding(encoding)
          && (channelCount == Format.NO_VALUE
              || channelCount <= audioCapabilities.getMaxChannelCount());
    }
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (!isInitialized() || startMediaTimeState == START_NOT_SET) {
      return CURRENT_POSITION_NOT_SET;
    }
    long positionUs = audioTrackPositionTracker.getCurrentPositionUs(sourceEnded);
    positionUs = Math.min(positionUs, configuration.framesToDurationUs(getWrittenFrames()));
    return startMediaTimeUs + applySkipping(applySpeedup(positionUs));
  }

  @Override
  public void configure(
      @C.Encoding int inputEncoding,
      int inputChannelCount,
      int inputSampleRate,
      int specifiedBufferSize,
      @Nullable int[] outputChannels,
      int trimStartFrames,
      int trimEndFrames)
      throws ConfigurationException {
    if (Util.SDK_INT < 21 && inputChannelCount == 8 && outputChannels == null) {
      // AudioTrack doesn't support 8 channel output before Android L. Discard the last two (side)
      // channels to give a 6 channel stream that is supported.
      outputChannels = new int[6];
      for (int i = 0; i < outputChannels.length; i++) {
        outputChannels[i] = i;
      }
    }

    boolean isInputPcm = Util.isEncodingLinearPcm(inputEncoding);
    boolean processingEnabled = isInputPcm && inputEncoding != C.ENCODING_PCM_FLOAT;
    int sampleRate = inputSampleRate;
    int channelCount = inputChannelCount;
    @C.Encoding int encoding = inputEncoding;
    boolean shouldConvertHighResIntPcmToFloat =
        enableConvertHighResIntPcmToFloat
            && supportsOutput(inputChannelCount, C.ENCODING_PCM_FLOAT)
            && Util.isEncodingHighResolutionIntegerPcm(inputEncoding);
    AudioProcessor[] availableAudioProcessors =
        shouldConvertHighResIntPcmToFloat
            ? toFloatPcmAvailableAudioProcessors
            : toIntPcmAvailableAudioProcessors;
    boolean flushAudioProcessors = false;
    if (processingEnabled) {
      trimmingAudioProcessor.setTrimFrameCount(trimStartFrames, trimEndFrames);
      channelMappingAudioProcessor.setChannelMap(outputChannels);
      for (AudioProcessor audioProcessor : availableAudioProcessors) {
        try {
          flushAudioProcessors |= audioProcessor.configure(sampleRate, channelCount, encoding);
        } catch (AudioProcessor.UnhandledFormatException e) {
          throw new ConfigurationException(e);
        }
        if (audioProcessor.isActive()) {
          channelCount = audioProcessor.getOutputChannelCount();
          sampleRate = audioProcessor.getOutputSampleRateHz();
          encoding = audioProcessor.getOutputEncoding();
        }
      }
    }

    int outputChannelConfig = getChannelConfig(channelCount, isInputPcm);
    if (outputChannelConfig == AudioFormat.CHANNEL_INVALID) {
      throw new ConfigurationException("Unsupported channel count: " + channelCount);
    }

    int inputPcmFrameSize =
        isInputPcm ? Util.getPcmFrameSize(inputEncoding, inputChannelCount) : C.LENGTH_UNSET;
    int outputPcmFrameSize =
        isInputPcm ? Util.getPcmFrameSize(encoding, channelCount) : C.LENGTH_UNSET;
    boolean canApplyPlaybackParameters = processingEnabled && !shouldConvertHighResIntPcmToFloat;
    Configuration pendingConfiguration =
        new Configuration(
            isInputPcm,
            inputPcmFrameSize,
            inputSampleRate,
            outputPcmFrameSize,
            sampleRate,
            outputChannelConfig,
            encoding,
            specifiedBufferSize,
            processingEnabled,
            canApplyPlaybackParameters,
            availableAudioProcessors);
    // If we have a pending configuration already, we always drain audio processors as the preceding
    // configuration may have required it (even if this one doesn't).
    boolean drainAudioProcessors = flushAudioProcessors || this.pendingConfiguration != null;
    if (isInitialized()
        && (!pendingConfiguration.canReuseAudioTrack(configuration) || drainAudioProcessors)) {
      this.pendingConfiguration = pendingConfiguration;
    } else {
      configuration = pendingConfiguration;
    }
  }

  private void setupAudioProcessors() {
    AudioProcessor[] audioProcessors = configuration.availableAudioProcessors;
    ArrayList<AudioProcessor> newAudioProcessors = new ArrayList<>();
    for (AudioProcessor audioProcessor : audioProcessors) {
      if (audioProcessor.isActive()) {
        newAudioProcessors.add(audioProcessor);
      } else {
        audioProcessor.flush();
      }
    }
    int count = newAudioProcessors.size();
    activeAudioProcessors = newAudioProcessors.toArray(new AudioProcessor[count]);
    outputBuffers = new ByteBuffer[count];
    flushAudioProcessors();
  }

  private void flushAudioProcessors() {
    for (int i = 0; i < activeAudioProcessors.length; i++) {
      AudioProcessor audioProcessor = activeAudioProcessors[i];
      audioProcessor.flush();
      outputBuffers[i] = audioProcessor.getOutput();
    }
  }

  private void initialize(long presentationTimeUs) throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    releasingConditionVariable.block();

    audioTrack =
        Assertions.checkNotNull(configuration)
            .buildAudioTrack(tunneling, audioAttributes, audioSessionId);
    int audioSessionId = audioTrack.getAudioSessionId();
    if (enablePreV21AudioSessionWorkaround) {
      if (Util.SDK_INT < 21) {
        // The workaround creates an audio track with a two byte buffer on the same session, and
        // does not release it until this object is released, which keeps the session active.
        if (keepSessionIdAudioTrack != null
            && audioSessionId != keepSessionIdAudioTrack.getAudioSessionId()) {
          releaseKeepSessionIdAudioTrack();
        }
        if (keepSessionIdAudioTrack == null) {
          keepSessionIdAudioTrack = initializeKeepSessionIdAudioTrack(audioSessionId);
        }
      }
    }
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      if (listener != null) {
        listener.onAudioSessionId(audioSessionId);
      }
    }

    applyPlaybackParameters(playbackParameters, presentationTimeUs);

    audioTrackPositionTracker.setAudioTrack(
        audioTrack,
        configuration.outputEncoding,
        configuration.outputPcmFrameSize,
        configuration.bufferSize,
       applyDolbyPassthroughQuirk()); // AMZN_CHANGE_ONELINE
    setVolumeInternal();

    if (auxEffectInfo.effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
      audioTrack.attachAuxEffect(auxEffectInfo.effectId);
      audioTrack.setAuxEffectSendLevel(auxEffectInfo.sendLevel);
    }
  }

  @Override
  public void play() {
    playing = true;
    if (isInitialized()) {
      audioTrackPositionTracker.start();
      audioTrack.play();
    }
  }

  @Override
  public void handleDiscontinuity() {
    // Force resynchronization after a skipped buffer.
    if (startMediaTimeState == START_IN_SYNC) {
      startMediaTimeState = START_NEED_SYNC;
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs)
      throws InitializationException, WriteException {
    Assertions.checkArgument(inputBuffer == null || buffer == inputBuffer);

    if (pendingConfiguration != null) {
      if (!drainAudioProcessorsToEndOfStream()) {
        // There's still pending data in audio processors to write to the track.
        return false;
      } else if (!pendingConfiguration.canReuseAudioTrack(configuration)) {
        playPendingData();
        if (hasPendingData()) {
          // We're waiting for playout on the current audio track to finish.
          return false;
        }
        flush();
      } else {
        // The current audio track can be reused for the new configuration.
        configuration = pendingConfiguration;
        pendingConfiguration = null;
      }
      // Re-apply playback parameters.
      applyPlaybackParameters(playbackParameters, presentationTimeUs);
    }

    if (!isInitialized()) {
      initialize(presentationTimeUs);
      if (playing) {
        play();
      }
    }

    if (!audioTrackPositionTracker.mayHandleBuffer(getWrittenFrames())) {
      return false;
    }

    if (inputBuffer == null) {
      // We are seeing this buffer for the first time.
      if (!buffer.hasRemaining()) {
        // The buffer is empty.
        return true;
      }

      if (!configuration.isInputPcm && framesPerEncodedSample == 0) {
        // If this is the first encoded sample, calculate the sample size in frames.
        framesPerEncodedSample = getFramesPerEncodedSample(configuration.outputEncoding, buffer);
        if (framesPerEncodedSample == 0) {
          // We still don't know the number of frames per sample, so drop the buffer.
          // For TrueHD this can occur after some seek operations, as not every sample starts with
          // a syncframe header. If we chunked samples together so the extracted samples always
          // started with a syncframe header, the chunks would be too large.
          return true;
        }
      }

      if (afterDrainPlaybackParameters != null) {
        if (!drainAudioProcessorsToEndOfStream()) {
          // Don't process any more input until draining completes.
          return false;
        }
        PlaybackParameters newPlaybackParameters = afterDrainPlaybackParameters;
        afterDrainPlaybackParameters = null;
        applyPlaybackParameters(newPlaybackParameters, presentationTimeUs);
      }

      if (startMediaTimeState == START_NOT_SET) {
        startMediaTimeUs = Math.max(0, presentationTimeUs);
        log.i("Setting StartMediaTimeUs = " + startMediaTimeUs);
        startMediaTimeState = START_IN_SYNC;
      } else {
        // Sanity check that presentationTimeUs is consistent with the expected value.
        long expectedPresentationTimeUs =
            startMediaTimeUs
                + configuration.inputFramesToDurationUs(
                    getSubmittedFrames() - trimmingAudioProcessor.getTrimmedFrameCount());
        if (startMediaTimeState == START_IN_SYNC
            && Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000) {
          log.w("Discontinuity detected [expected " + expectedPresentationTimeUs + ", got "
              + presentationTimeUs + "]");
          startMediaTimeState = START_NEED_SYNC;
        }
        if (startMediaTimeState == START_NEED_SYNC) {
          // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
          // number of bytes submitted.
          long adjustmentUs = presentationTimeUs - expectedPresentationTimeUs;
          startMediaTimeUs += adjustmentUs;
          startMediaTimeState = START_IN_SYNC;
          if (listener != null && adjustmentUs != 0) {
            listener.onPositionDiscontinuity();
          }
        }
      }

      if (configuration.isInputPcm) {
        submittedPcmBytes += buffer.remaining();
      } else {
        submittedEncodedFrames += framesPerEncodedSample;
      }

      inputBuffer = buffer;
    }

    if (configuration.processingEnabled) {
      processBuffers(presentationTimeUs);
    } else {
      writeBuffer(inputBuffer, presentationTimeUs);
    }

    if (!inputBuffer.hasRemaining()) {
      inputBuffer = null;
      return true;
    }

    if (audioTrackPositionTracker.isStalled(getWrittenFrames())) {
      Log.w(TAG, "Resetting stalled audio track");
      flush();
      return true;
    }

    return false;
  }

  private void processBuffers(long avSyncPresentationTimeUs) throws WriteException {
    int count = activeAudioProcessors.length;
    int index = count;
    while (index >= 0) {
      ByteBuffer input = index > 0 ? outputBuffers[index - 1]
          : (inputBuffer != null ? inputBuffer : AudioProcessor.EMPTY_BUFFER);
      if (index == count) {
        writeBuffer(input, avSyncPresentationTimeUs);
      } else {
        AudioProcessor audioProcessor = activeAudioProcessors[index];
        audioProcessor.queueInput(input);
        ByteBuffer output = audioProcessor.getOutput();
        outputBuffers[index] = output;
        if (output.hasRemaining()) {
          // Handle the output as input to the next audio processor or the AudioTrack.
          index++;
          continue;
        }
      }

      if (input.hasRemaining()) {
        // The input wasn't consumed and no output was produced, so give up for now.
        return;
      }

      // Get more input from upstream.
      index--;
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void writeBuffer(ByteBuffer buffer, long avSyncPresentationTimeUs) throws WriteException {
    if (DBG) {
      log.d("writeBuffer : offset = " + buffer.position() + ", limit = " + buffer.limit() +
              ", presentationTimeUs = " + avSyncPresentationTimeUs);
    }
    if (!buffer.hasRemaining()) {
      return;
    }
    if (outputBuffer != null) {
      Assertions.checkArgument(outputBuffer == buffer);
    } else {
      outputBuffer = buffer;
      // AMZN: we need to copy data to temp buffer in case of dolby passthrough also
      // irrespective of SDK version.
      if (Util.SDK_INT < 21 || applyDolbyPassthroughQuirk()) { // AMZN_CHANGE_ONELINE
        int bytesRemaining = buffer.remaining();
        if (preV21OutputBuffer == null || preV21OutputBuffer.length < bytesRemaining) {
          preV21OutputBuffer = new byte[bytesRemaining];
        }
        int originalPosition = buffer.position();
        buffer.get(preV21OutputBuffer, 0, bytesRemaining);
        buffer.position(originalPosition);
        preV21OutputBufferOffset = 0;
      }
    }
    int bytesRemaining = buffer.remaining();
    int bytesWritten = 0;

    // AMZN_CHANGE_BEGIN
    // for dolby passthrough case, just write into the DolbyPassthroughAudioTrack
    // since its implementation is different than standard pcm audio track.
    // The DolbyPassthroughAudioTrack takes care of writing only in play state
    // and also writes into the track asynchronously. Also, we
    // cannot depend on playback head position to decide how much more data to write.
    if (applyDolbyPassthroughQuirk()) {
      // if there are no free buffers in AudioTrack, the write returns 0, indicating
      // it did not consume the buffer.
      bytesWritten = audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesRemaining);
      if (bytesWritten > 0) {
        preV21OutputBufferOffset += bytesWritten;
        buffer.position(buffer.position() + bytesWritten);
      }
    } else if (Util.SDK_INT < 21) { // isInputPcm  == true
      // AMZN_CHANGE_END
      // Work out how many bytes we can write without the risk of blocking.
      int bytesToWrite = audioTrackPositionTracker.getAvailableBufferSize(writtenPcmBytes);
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(bytesRemaining, bytesToWrite);
        bytesWritten = audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesToWrite);
        if (bytesWritten > 0) {
          preV21OutputBufferOffset += bytesWritten;
          buffer.position(buffer.position() + bytesWritten);
        }
      }
    } else if (tunneling) {
      Assertions.checkState(avSyncPresentationTimeUs != C.TIME_UNSET);
      bytesWritten = writeNonBlockingWithAvSyncV21(audioTrack, buffer, bytesRemaining,
          avSyncPresentationTimeUs);
    } else {
      bytesWritten = writeNonBlockingV21(audioTrack, buffer, bytesRemaining);
    }

    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();

    if (bytesWritten < 0) {
      throw new WriteException(bytesWritten);
    }

    if (configuration.isInputPcm) {
      writtenPcmBytes += bytesWritten;
    }
    if (bytesWritten == bytesRemaining) {
      if (!configuration.isInputPcm) {
        writtenEncodedFrames += framesPerEncodedSample;
      }
      outputBuffer = null;
    }
  }

  /**
   * Plays out remaining audio. {@link #isEnded()} will return {@code true} when playback has ended.
   *
   * @throws WriteException If an error occurs draining data to the track.
   */
  @Override
  public void playToEndOfStream() throws WriteException {
    log.i("calling playToEndOfStream");
    if (!handledEndOfStream && isInitialized() && drainAudioProcessorsToEndOfStream()) {
      playPendingData();
      handledEndOfStream = true;
    }
  }

  private boolean drainAudioProcessorsToEndOfStream() throws WriteException {
    boolean audioProcessorNeedsEndOfStream = false;
    if (drainingAudioProcessorIndex == C.INDEX_UNSET) {
      drainingAudioProcessorIndex =
          configuration.processingEnabled ? 0 : activeAudioProcessors.length;
      audioProcessorNeedsEndOfStream = true;
    }
    while (drainingAudioProcessorIndex < activeAudioProcessors.length) {
      AudioProcessor audioProcessor = activeAudioProcessors[drainingAudioProcessorIndex];
      if (audioProcessorNeedsEndOfStream) {
        audioProcessor.queueEndOfStream();
      }
      processBuffers(C.TIME_UNSET);
      if (!audioProcessor.isEnded()) {
        return false;
      }
      audioProcessorNeedsEndOfStream = true;
      drainingAudioProcessorIndex++;
    }

    // Finish writing any remaining output to the track.
    if (outputBuffer != null) {
      writeBuffer(outputBuffer, C.TIME_UNSET);
      if (outputBuffer != null) {
        return false;
      }
    }
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    return true;
  }

  @Override
  public boolean isEnded() {
    return !isInitialized() || (handledEndOfStream &&
            (applyDolbyPassthroughQuirk() || !hasPendingData()));// AMZN_CHANGE_ONELINE
  }

  @Override
  public boolean hasPendingData() {
    return isInitialized() && audioTrackPositionTracker.hasPendingData(getWrittenFrames());
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (configuration != null && !configuration.canApplyPlaybackParameters) {
      this.playbackParameters = PlaybackParameters.DEFAULT;
      return this.playbackParameters;
    }
    PlaybackParameters lastSetPlaybackParameters =
        afterDrainPlaybackParameters != null
            ? afterDrainPlaybackParameters
            : !playbackParametersCheckpoints.isEmpty()
                ? playbackParametersCheckpoints.getLast().playbackParameters
                : this.playbackParameters;
    if (!playbackParameters.equals(lastSetPlaybackParameters)) {
      if (isInitialized()) {
        // Drain the audio processors so we can determine the frame position at which the new
        // parameters apply.
        afterDrainPlaybackParameters = playbackParameters;
      } else {
        // Update the playback parameters now. They will be applied to the audio processors during
        // initialization.
        this.playbackParameters = playbackParameters;
      }
    }
    return this.playbackParameters;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    if (this.audioAttributes.equals(audioAttributes)) {
      return;
    }
    this.audioAttributes = audioAttributes;
    if (tunneling) {
      // The audio attributes are ignored in tunneling mode, so no need to reset.
      return;
    }
    flush();
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    log.i("calling setAudioSessionId = " + audioSessionId);
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      flush();
    }
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    if (this.auxEffectInfo.equals(auxEffectInfo)) {
      return;
    }
    int effectId = auxEffectInfo.effectId;
    float sendLevel = auxEffectInfo.sendLevel;
    if (audioTrack != null) {
      if (this.auxEffectInfo.effectId != effectId) {
        audioTrack.attachAuxEffect(effectId);
      }
      if (effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
        audioTrack.setAuxEffectSendLevel(sendLevel);
      }
    }
    this.auxEffectInfo = auxEffectInfo;
  }

  @Override
  public void enableTunnelingV21(int tunnelingAudioSessionId) {
    log.i("calling enableTunnelingV21 = " + tunnelingAudioSessionId);
    Assertions.checkState(Util.SDK_INT >= 21);
    if (!tunneling || audioSessionId != tunnelingAudioSessionId) {
      tunneling = true;
      audioSessionId = tunnelingAudioSessionId;
      flush();
    }
  }

  @Override
  public void disableTunneling() {
    if (tunneling) {
      tunneling = false;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
      flush();
    }
  }

  @Override
  public void setVolume(float volume) {
    if (this.volume != volume) {
      log.i("setVolume: volume = " + volume);
      this.volume = volume;
      setVolumeInternal();
    }
  }

  private void setVolumeInternal() {
    if (!isInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setVolumeInternalV21(audioTrack, volume);
    } else {
      setVolumeInternalV3(audioTrack, volume);
    }
  }

  @Override
  public void pause() {
    log.i("calling pause");
    playing = false;
    if (isInitialized() && audioTrackPositionTracker.pause()) {
      audioTrack.pause();
    }
  }

  @Override
  public void flush() {
    log.i("calling flush/reset");
    if (isInitialized()) {
      submittedPcmBytes = 0;
      submittedEncodedFrames = 0;
      writtenPcmBytes = 0;
      writtenEncodedFrames = 0;
      framesPerEncodedSample = 0;
      if (afterDrainPlaybackParameters != null) {
        playbackParameters = afterDrainPlaybackParameters;
        afterDrainPlaybackParameters = null;
      } else if (!playbackParametersCheckpoints.isEmpty()) {
        playbackParameters = playbackParametersCheckpoints.getLast().playbackParameters;
      }
      playbackParametersCheckpoints.clear();
      playbackParametersOffsetUs = 0;
      playbackParametersPositionUs = 0;
      trimmingAudioProcessor.resetTrimmedFrameCount();
      flushAudioProcessors();
      inputBuffer = null;
      outputBuffer = null;
      stoppedAudioTrack = false;
      handledEndOfStream = false;
      drainingAudioProcessorIndex = C.INDEX_UNSET;
      avSyncHeader = null;
      bytesUntilNextAvSync = 0;
      startMediaTimeState = START_NOT_SET;
      if (audioTrackPositionTracker.isPlaying()) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final AudioTrack toRelease = audioTrack;
      audioTrack = null;
      if (pendingConfiguration != null) {
        configuration = pendingConfiguration;
        pendingConfiguration = null;
      }
      audioTrackPositionTracker.reset();
      releasingConditionVariable.close();
      new Thread() {
        @Override
        public void run() {
          try {
            log.i("audioTrack.flush");
            toRelease.flush();
            log.i("audioTrack.release");
            toRelease.release();
          } finally {
            releasingConditionVariable.open();
          }
        }
      }.start();
    }
  }

  @Override
  public void reset() {
    log.i("calling reset");
    flush();
    releaseKeepSessionIdAudioTrack();
    for (AudioProcessor audioProcessor : toIntPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    for (AudioProcessor audioProcessor : toFloatPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    playing = false;
  }

  /**
   * Releases {@link #keepSessionIdAudioTrack} asynchronously, if it is non-{@code null}.
   */
  private void releaseKeepSessionIdAudioTrack() {
    if (keepSessionIdAudioTrack == null) {
      return;
    }

    // AudioTrack.release can take some time, so we call it on a background thread.
    final AudioTrack toRelease = keepSessionIdAudioTrack;
    keepSessionIdAudioTrack = null;
    new Thread() {
      @Override
      public void run() {
        log.i("audioTrack.release");
        toRelease.release();
      }
    }.start();
  }

  private void applyPlaybackParameters(
      PlaybackParameters playbackParameters, long presentationTimeUs) {
    PlaybackParameters newPlaybackParameters =
        configuration.canApplyPlaybackParameters
            ? audioProcessorChain.applyPlaybackParameters(playbackParameters)
            : PlaybackParameters.DEFAULT;
    // Store the position and corresponding media time from which the parameters will apply.
    playbackParametersCheckpoints.add(
        new PlaybackParametersCheckpoint(
            newPlaybackParameters,
            /* mediaTimeUs= */ Math.max(0, presentationTimeUs),
            /* positionUs= */ configuration.framesToDurationUs(getWrittenFrames())));
    setupAudioProcessors();
  }

  private long applySpeedup(long positionUs) {
    @Nullable PlaybackParametersCheckpoint checkpoint = null;
    while (!playbackParametersCheckpoints.isEmpty()
        && positionUs >= playbackParametersCheckpoints.getFirst().positionUs) {
      checkpoint = playbackParametersCheckpoints.remove();
    }
    if (checkpoint != null) {
      // We are playing (or about to play) media with the new playback parameters, so update them.
      playbackParameters = checkpoint.playbackParameters;
      playbackParametersPositionUs = checkpoint.positionUs;
      playbackParametersOffsetUs = checkpoint.mediaTimeUs - startMediaTimeUs;
    }

    if (playbackParameters.speed == 1f) {
      return positionUs + playbackParametersOffsetUs - playbackParametersPositionUs;
    }

    if (playbackParametersCheckpoints.isEmpty()) {
      return playbackParametersOffsetUs
          + audioProcessorChain.getMediaDuration(positionUs - playbackParametersPositionUs);
    }

    // We are playing data at a previous playback speed, so fall back to multiplying by the speed.
    return playbackParametersOffsetUs
        + Util.getMediaDurationForPlayoutDuration(
            positionUs - playbackParametersPositionUs, playbackParameters.speed);
  }

  private long applySkipping(long positionUs) {
    return positionUs
        + configuration.framesToDurationUs(audioProcessorChain.getSkippedOutputFrameCount());
  }

  private boolean isInitialized() {
    return audioTrack != null;
  }

  private long getSubmittedFrames() {
    return configuration.isInputPcm
        ? (submittedPcmBytes / configuration.inputPcmFrameSize)
        : submittedEncodedFrames;
  }

  private long getWrittenFrames() {
    return configuration.isInputPcm
        ? (writtenPcmBytes / configuration.outputPcmFrameSize)
        : writtenEncodedFrames;
  }

  private static AudioTrack initializeKeepSessionIdAudioTrack(int audioSessionId) {
    int sampleRate = 4000; // Equal to private AudioTrack.MIN_SAMPLE_RATE.
    int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
    @C.PcmEncoding int encoding = C.ENCODING_PCM_16BIT;
    int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
    return new AudioTrack(C.STREAM_TYPE_DEFAULT, sampleRate, channelConfig, encoding, bufferSize,
        MODE_STATIC, audioSessionId);
  }

  private static int getChannelConfig(int channelCount, boolean isInputPcm) {
    if (Util.SDK_INT <= 28 && !isInputPcm) {
      // In passthrough mode the channel count used to configure the audio track doesn't affect how
      // the stream is handled, except that some devices do overly-strict channel configuration
      // checks. Therefore we override the channel count so that a known-working channel
      // configuration is chosen in all cases. See [Internal: b/29116190].
      if (channelCount == 7) {
        channelCount = 8;
      } else if (channelCount == 3 || channelCount == 4 || channelCount == 5) {
        channelCount = 6;
      }
    }

    // Workaround for Nexus Player not reporting support for mono passthrough.
    // (See [Internal: b/34268671].)
    if (Util.SDK_INT <= 26 && "fugu".equals(Util.DEVICE) && !isInputPcm && channelCount == 1) {
      channelCount = 2;
    }

    return Util.getAudioTrackChannelConfig(channelCount);
  }

  private static int getMaximumEncodedRateBytesPerSecond(@C.Encoding int encoding) {
    switch (encoding) {
      case C.ENCODING_AC3:
        return 640 * 1000 / 8;
      case C.ENCODING_E_AC3:
      case C.ENCODING_E_AC3_JOC:
        return 6144 * 1000 / 8;
      case C.ENCODING_AC4:
        return 2688 * 1000 / 8;
      case C.ENCODING_DTS:
        // DTS allows an 'open' bitrate, but we assume the maximum listed value: 1536 kbit/s.
        return 1536 * 1000 / 8;
      case C.ENCODING_DTS_HD:
        return 18000 * 1000 / 8;
      case C.ENCODING_DOLBY_TRUEHD:
        return 24500 * 1000 / 8;
      case C.ENCODING_INVALID:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_A_LAW:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_PCM_MU_LAW:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }

  private static int getFramesPerEncodedSample(@C.Encoding int encoding, ByteBuffer buffer) {
    if (encoding == C.ENCODING_DTS || encoding == C.ENCODING_DTS_HD) {
      return DtsUtil.parseDtsAudioSampleCount(buffer);
    } else if (encoding == C.ENCODING_AC3) {
      return Ac3Util.getAc3SyncframeAudioSampleCount();
    } else if (encoding == C.ENCODING_E_AC3 || encoding == C.ENCODING_E_AC3_JOC) {
      return Ac3Util.parseEAc3SyncframeAudioSampleCount(buffer);
    } else if (encoding == C.ENCODING_AC4) {
      return Ac4Util.parseAc4SyncframeAudioSampleCount(buffer);
    } else if (encoding == C.ENCODING_DOLBY_TRUEHD) {
      int syncframeOffset = Ac3Util.findTrueHdSyncframeOffset(buffer);
      return syncframeOffset == C.INDEX_UNSET
          ? 0
          : (Ac3Util.parseTrueHdSyncframeAudioSampleCount(buffer, syncframeOffset)
              * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT);
    } else {
      throw new IllegalStateException("Unexpected audio encoding: " + encoding);
    }
  }

  @TargetApi(21)
  private static int writeNonBlockingV21(AudioTrack audioTrack, ByteBuffer buffer, int size) {
    return audioTrack.write(buffer, size, WRITE_NON_BLOCKING);
  }

  @TargetApi(21)
  private int writeNonBlockingWithAvSyncV21(AudioTrack audioTrack, ByteBuffer buffer, int size,
      long presentationTimeUs) {
    if (Util.SDK_INT >= 26) {
      // The underlying platform AudioTrack writes AV sync headers directly.
      return audioTrack.write(buffer, size, WRITE_NON_BLOCKING, presentationTimeUs * 1000);
    }
    if (avSyncHeader == null) {
      avSyncHeader = ByteBuffer.allocate(16);
      avSyncHeader.order(ByteOrder.BIG_ENDIAN);
      avSyncHeader.putInt(0x55550001);
    }
    if (bytesUntilNextAvSync == 0) {
      avSyncHeader.putInt(4, size);
      avSyncHeader.putLong(8, presentationTimeUs * 1000);
      avSyncHeader.position(0);
      bytesUntilNextAvSync = size;
    }
    int avSyncHeaderBytesRemaining = avSyncHeader.remaining();
    if (avSyncHeaderBytesRemaining > 0) {
      int result = audioTrack.write(avSyncHeader, avSyncHeaderBytesRemaining, WRITE_NON_BLOCKING);
      if (result < 0) {
        bytesUntilNextAvSync = 0;
        return result;
      }
      if (result < avSyncHeaderBytesRemaining) {
        return 0;
      }
    }
    int result = writeNonBlockingV21(audioTrack, buffer, size);
    if (result < 0) {
      bytesUntilNextAvSync = 0;
      return result;
    }
    bytesUntilNextAvSync -= result;
    return result;
  }

  @TargetApi(21)
  private static void setVolumeInternalV21(AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  @SuppressWarnings("deprecation")
  private static void setVolumeInternalV3(AudioTrack audioTrack, float volume) {
    audioTrack.setStereoVolume(volume, volume);
  }

  private void playPendingData() {
    if (!stoppedAudioTrack) {
      stoppedAudioTrack = true;
      // The audio processors have drained, so drain the underlying audio track.
      // AMZN_CHANGE_BEGIN
      if (!applyDolbyPassthroughQuirk()) {
        audioTrackPositionTracker.handleEndOfStream(getWrittenFrames());
      }
      // AMZN_CHANGE_END
      audioTrack.stop();
      bytesUntilNextAvSync = 0;
    }
  }

  /** Stores playback parameters with the position and media time at which they apply. */
  private static final class PlaybackParametersCheckpoint {

    private final PlaybackParameters playbackParameters;
    private final long mediaTimeUs;
    private final long positionUs;

    private PlaybackParametersCheckpoint(PlaybackParameters playbackParameters, long mediaTimeUs,
        long positionUs) {
      this.playbackParameters = playbackParameters;
      this.mediaTimeUs = mediaTimeUs;
      this.positionUs = positionUs;
    }

  }

  private final class PositionTrackerListener implements AudioTrackPositionTracker.Listener {

    @Override
    public void onPositionFramesMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (frame position mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      log.w(message);
    }

    @Override
    public void onSystemTimeUsMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (system clock mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      log.w(message);
    }

    @Override
    public void onInvalidLatency(long latencyUs) {
      log.w("Ignoring impossibly large audio latency: " + latencyUs);
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs) {
      if (listener != null) {
        long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
        listener.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }
  }

  /** Stores configuration relating to the audio format. */
  private static final class Configuration {

    public final boolean isInputPcm;
    public final int inputPcmFrameSize;
    public final int inputSampleRate;
    public final int outputPcmFrameSize;
    public final int outputSampleRate;
    public final int outputChannelConfig;
    @C.Encoding public final int outputEncoding;
    public final int bufferSize;
    public final boolean processingEnabled;
    public final boolean canApplyPlaybackParameters;
    public final AudioProcessor[] availableAudioProcessors;

    public Configuration(
        boolean isInputPcm,
        int inputPcmFrameSize,
        int inputSampleRate,
        int outputPcmFrameSize,
        int outputSampleRate,
        int outputChannelConfig,
        int outputEncoding,
        int specifiedBufferSize,
        boolean processingEnabled,
        boolean canApplyPlaybackParameters,
        AudioProcessor[] availableAudioProcessors) {
      this.isInputPcm = isInputPcm;
      this.inputPcmFrameSize = inputPcmFrameSize;
      this.inputSampleRate = inputSampleRate;
      this.outputPcmFrameSize = outputPcmFrameSize;
      this.outputSampleRate = outputSampleRate;
      this.outputChannelConfig = outputChannelConfig;
      this.outputEncoding = outputEncoding;
      this.bufferSize = specifiedBufferSize != 0 ? specifiedBufferSize : getDefaultBufferSize();
      this.processingEnabled = processingEnabled;
      this.canApplyPlaybackParameters = canApplyPlaybackParameters;
      this.availableAudioProcessors = availableAudioProcessors;
    }

    public boolean canReuseAudioTrack(Configuration audioTrackConfiguration) {
      return audioTrackConfiguration.outputEncoding == outputEncoding
          && audioTrackConfiguration.outputSampleRate == outputSampleRate
          && audioTrackConfiguration.outputChannelConfig == outputChannelConfig;
    }

    public long inputFramesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / inputSampleRate;
    }

    public long framesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate;
    }

    public long durationUsToFrames(long durationUs) {
      return (durationUs * outputSampleRate) / C.MICROS_PER_SECOND;
    }

    public boolean applyDolbyPassthroughQuirk() {
        return (!isInputPcm && isLegacyPassthroughQuirkEnabled);
    }
    public AudioTrack buildAudioTrack(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId)
        throws InitializationException {
      AudioTrack audioTrack;
      if (Util.SDK_INT >= 21) {
        audioTrack = createAudioTrackV21(tunneling, audioAttributes, audioSessionId);
      } else {
        // AMZN_CHANGE_BEGIN
        int streamType = Util.getStreamTypeForAudioUsage(audioAttributes.usage);
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
          if (applyDolbyPassthroughQuirk()) {
            audioTrack = new DolbyPassthroughAudioTrack(
                            streamType,
                            outputSampleRate,
                            outputChannelConfig,
                            outputEncoding,
                            bufferSize,
                            MODE_STREAM);
          } else {
            audioTrack =
                    new AudioTrack(
                            streamType,
                            outputSampleRate,
                            outputChannelConfig,
                            outputEncoding,
                            bufferSize,
                            MODE_STREAM);
          }
        } else {
          // Re-attach to the same audio session.
          if (applyDolbyPassthroughQuirk()) {
            audioTrack = new DolbyPassthroughAudioTrack(
                            streamType,
                            outputSampleRate,
                            outputChannelConfig,
                            outputEncoding,
                            bufferSize,
                            MODE_STREAM, audioSessionId);
          } else {
            audioTrack =
                    new AudioTrack(
                            streamType,
                            outputSampleRate,
                            outputChannelConfig,
                            outputEncoding,
                            bufferSize,
                            MODE_STREAM,
                            audioSessionId);
          }
        }
        // AMZN_CHANGE_END
      }
      int state = audioTrack.getState();
      if (state != STATE_INITIALIZED) {
        try {
          audioTrack.release();
        } catch (Exception e) {
          // The track has already failed to initialize, so it wouldn't be that surprising if
          // release were to fail too. Swallow the exception.
        }
        throw new InitializationException(state, outputSampleRate, outputChannelConfig, bufferSize);
      }
      return audioTrack;
    }

    @TargetApi(21)
    private AudioTrack createAudioTrackV21(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId) {
      android.media.AudioAttributes attributes;
      if (tunneling) {
        attributes =
            new android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build();
      } else {
        attributes = audioAttributes.getAudioAttributesV21();
      }
      AudioFormat format =
          new AudioFormat.Builder()
              .setChannelMask(outputChannelConfig)
              .setEncoding(outputEncoding)
              .setSampleRate(outputSampleRate)
              .build();

     // AMZN_CHANGE_BEGIN
     audioSessionId = audioSessionId != C.AUDIO_SESSION_ID_UNSET ? audioSessionId
         : AudioManager.AUDIO_SESSION_ID_GENERATE;
     if (applyDolbyPassthroughQuirk()) {
       return new DolbyPassthroughAudioTrack(attributes, format, bufferSize, MODE_STREAM,
               audioSessionId);
     } else {
       return new AudioTrack(attributes, format, bufferSize, MODE_STREAM,
               audioSessionId);
 
     }
     // AMZN_CHANGE_END
    }

    private int getDefaultBufferSize() {
      if (isInputPcm) {
        int minBufferSize =
            AudioTrack.getMinBufferSize(outputSampleRate, outputChannelConfig, outputEncoding);
        Assertions.checkState(minBufferSize != ERROR_BAD_VALUE);
        int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
        int minAppBufferSize =
            (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * outputPcmFrameSize;
        int maxAppBufferSize =
            (int)
                Math.max(
                    minBufferSize, durationUsToFrames(MAX_BUFFER_DURATION_US) * outputPcmFrameSize);
        return Util.constrainValue(multipliedBufferSize, minAppBufferSize, maxAppBufferSize);
      } else {
        int rate = getMaximumEncodedRateBytesPerSecond(outputEncoding);
        if (outputEncoding == C.ENCODING_AC3) {
          rate *= AC3_BUFFER_MULTIPLICATION_FACTOR;
        }
        return (int) (PASSTHROUGH_BUFFER_DURATION_US * rate / C.MICROS_PER_SECOND);
      }
    }
  }
}
