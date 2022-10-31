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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Helper class to run an ExoPlayer test. */
public final class ExoPlayerTestRunner implements Player.EventListener, ActionSchedule.Callback {

  /**
   * Builder to set-up a {@link ExoPlayerTestRunner}. Default fake implementations will be used for
   * unset test properties.
   */
  public static final class Builder {

    /**
     * A generic video {@link Format} which can be used to set up media sources and renderers.
     */
    public static final Format VIDEO_FORMAT = Format.createVideoSampleFormat(null,
        MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
        null, null);

    /**
     * A generic audio {@link Format} which can be used to set up media sources and renderers.
     */
    public static final Format AUDIO_FORMAT = Format.createAudioSampleFormat(null,
        MimeTypes.AUDIO_AAC, null, Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);

    private Clock clock;
    private Timeline timeline;
    private Object manifest;
    private MediaSource mediaSource;
    private DefaultTrackSelector trackSelector;
    private LoadControl loadControl;
    private BandwidthMeter bandwidthMeter;
    private Format[] supportedFormats;
    private Renderer[] renderers;
    private RenderersFactory renderersFactory;
    private ActionSchedule actionSchedule;
    private Player.EventListener eventListener;
    private AnalyticsListener analyticsListener;
    private Integer expectedPlayerEndedCount;

    /**
     * Sets a {@link Timeline} to be used by a {@link FakeMediaSource} in the test runner. The
     * default value is a seekable, non-dynamic {@link FakeTimeline} with a duration of
     * {@link FakeTimeline.TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US}. Setting the
     * timeline is not allowed after a call to {@link #setMediaSource(MediaSource)}.
     *
     * @param timeline A {@link Timeline} to be used by a {@link FakeMediaSource} in the test
     *     runner.
     * @return This builder.
     */
    public Builder setTimeline(Timeline timeline) {
      assertThat(mediaSource).isNull();
      this.timeline = timeline;
      return this;
    }

    /**
     * Sets a manifest to be used by a {@link FakeMediaSource} in the test runner. The default value
     * is null. Setting the manifest is not allowed after a call to
     * {@link #setMediaSource(MediaSource)}.
     *
     * @param manifest A manifest to be used by a {@link FakeMediaSource} in the test runner.
     * @return This builder.
     */
    public Builder setManifest(Object manifest) {
      assertThat(mediaSource).isNull();
      this.manifest = manifest;
      return this;
    }

    /**
     * Sets a {@link MediaSource} to be used by the test runner. The default value is a
     * {@link FakeMediaSource} with the timeline and manifest provided by
     * {@link #setTimeline(Timeline)} and {@link #setManifest(Object)}. Setting the media source is
     * not allowed after calls to {@link #setTimeline(Timeline)} and/or
     * {@link #setManifest(Object)}.
     *
     * @param mediaSource A {@link MediaSource} to be used by the test runner.
     * @return This builder.
     */
    public Builder setMediaSource(MediaSource mediaSource) {
      assertThat(timeline).isNull();
      assertThat(manifest).isNull();
      this.mediaSource = mediaSource;
      return this;
    }

    /**
     * Sets a {@link DefaultTrackSelector} to be used by the test runner. The default value is a
     * {@link DefaultTrackSelector} in its initial configuration.
     *
     * @param trackSelector A {@link DefaultTrackSelector} to be used by the test runner.
     * @return This builder.
     */
    public Builder setTrackSelector(DefaultTrackSelector trackSelector) {
      this.trackSelector = trackSelector;
      return this;
    }

    /**
     * Sets a {@link LoadControl} to be used by the test runner. The default value is a
     * {@link DefaultLoadControl}.
     *
     * @param loadControl A {@link LoadControl} to be used by the test runner.
     * @return This builder.
     */
    public Builder setLoadControl(LoadControl loadControl) {
      this.loadControl = loadControl;
      return this;
    }

    /**
     * Sets the {@link BandwidthMeter} to be used by the test runner. The default value is a {@link
     * DefaultBandwidthMeter} in its default configuration.
     *
     * @param bandwidthMeter The {@link BandwidthMeter} to be used by the test runner.
     * @return This builder.
     */
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      this.bandwidthMeter = bandwidthMeter;
      return this;
    }

    /**
     * Sets a list of {@link Format}s to be used by a {@link FakeMediaSource} to create media
     * periods and for setting up a {@link FakeRenderer}. The default value is a single
     * {@link #VIDEO_FORMAT}. Note that this parameter doesn't have any influence if both a media
     * source with {@link #setMediaSource(MediaSource)} and renderers with
     * {@link #setRenderers(Renderer...)} or {@link #setRenderersFactory(RenderersFactory)} are set.
     *
     * @param supportedFormats A list of supported {@link Format}s.
     * @return This builder.
     */
    public Builder setSupportedFormats(Format... supportedFormats) {
      this.supportedFormats = supportedFormats;
      return this;
    }

    /**
     * Sets the {@link Renderer}s to be used by the test runner. The default value is a single
     * {@link FakeRenderer} supporting the formats set by {@link #setSupportedFormats(Format...)}.
     * Setting the renderers is not allowed after a call to
     * {@link #setRenderersFactory(RenderersFactory)}.
     *
     * @param renderers A list of {@link Renderer}s to be used by the test runner.
     * @return This builder.
     */
    public Builder setRenderers(Renderer... renderers) {
      assertThat(renderersFactory).isNull();
      this.renderers = renderers;
      return this;
    }

    /**
     * Sets the {@link RenderersFactory} to be used by the test runner. The default factory creates
     * all renderers set by {@link #setRenderers(Renderer...)}. Setting the renderer factory is not
     * allowed after a call to {@link #setRenderers(Renderer...)}.
     *
     * @param renderersFactory A {@link RenderersFactory} to be used by the test runner.
     * @return This builder.
     */
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      assertThat(renderers).isNull();
      this.renderersFactory = renderersFactory;
      return this;
    }

    /**
     * Sets the {@link Clock} to be used by the test runner. The default value is a {@link
     * AutoAdvancingFakeClock}.
     *
     * @param clock A {@link Clock} to be used by the test runner.
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets an {@link ActionSchedule} to be run by the test runner. The first action will be
     * executed immediately before {@link SimpleExoPlayer#prepare(MediaSource)}.
     *
     * @param actionSchedule An {@link ActionSchedule} to be used by the test runner.
     * @return This builder.
     */
    public Builder setActionSchedule(ActionSchedule actionSchedule) {
      this.actionSchedule = actionSchedule;
      return this;
    }

    /**
     * Sets an {@link Player.EventListener} to be registered to listen to player events.
     *
     * @param eventListener A {@link Player.EventListener} to be registered by the test runner to
     *     listen to player events.
     * @return This builder.
     */
    public Builder setEventListener(Player.EventListener eventListener) {
      this.eventListener = eventListener;
      return this;
    }

    /**
     * Sets an {@link AnalyticsListener} to be registered.
     *
     * @param analyticsListener An {@link AnalyticsListener} to be registered.
     * @return This builder.
     */
    public Builder setAnalyticsListener(AnalyticsListener analyticsListener) {
      this.analyticsListener = analyticsListener;
      return this;
    }

    /**
     * Sets the number of times the test runner is expected to reach the {@link Player#STATE_ENDED}
     * or {@link Player#STATE_IDLE}. The default is 1. This affects how long
     * {@link ExoPlayerTestRunner#blockUntilEnded(long)} waits.
     *
     * @param expectedPlayerEndedCount The number of times the player is expected to reach the ended
     *     or idle state.
     * @return This builder.
     */
    public Builder setExpectedPlayerEndedCount(int expectedPlayerEndedCount) {
      this.expectedPlayerEndedCount = expectedPlayerEndedCount;
      return this;
    }

    /**
     * Builds an {@link ExoPlayerTestRunner} using the provided values or their defaults.
     *
     * @param context The context.
     * @return The built {@link ExoPlayerTestRunner}.
     */
    public ExoPlayerTestRunner build(Context context) {
      if (supportedFormats == null) {
        supportedFormats = new Format[] {VIDEO_FORMAT};
      }
      if (trackSelector == null) {
        trackSelector = new DefaultTrackSelector();
      }
      if (bandwidthMeter == null) {
        bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
      }
      if (renderersFactory == null) {
        if (renderers == null) {
          renderers = new Renderer[] {new FakeRenderer(supportedFormats)};
        }
        renderersFactory =
            (eventHandler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput,
                drmSessionManager) -> renderers;
      }
      if (loadControl == null) {
        loadControl = new DefaultLoadControl();
      }
      if (clock == null) {
        clock = new AutoAdvancingFakeClock();
      }
      if (mediaSource == null) {
        if (timeline == null) {
          timeline = new FakeTimeline(1);
        }
        mediaSource = new FakeMediaSource(timeline, manifest, supportedFormats);
      }
      if (expectedPlayerEndedCount == null) {
        expectedPlayerEndedCount = 1;
      }
      return new ExoPlayerTestRunner(
          context,
          clock,
          mediaSource,
          renderersFactory,
          trackSelector,
          loadControl,
          bandwidthMeter,
          actionSchedule,
          eventListener,
          analyticsListener,
          expectedPlayerEndedCount);
    }
  }

  private final Context context;
  private final Clock clock;
  private final MediaSource mediaSource;
  private final RenderersFactory renderersFactory;
  private final DefaultTrackSelector trackSelector;
  private final LoadControl loadControl;
  private final BandwidthMeter bandwidthMeter;
  private final @Nullable ActionSchedule actionSchedule;
  private final @Nullable Player.EventListener eventListener;
  private final @Nullable AnalyticsListener analyticsListener;

  private final HandlerThread playerThread;
  private final HandlerWrapper handler;
  private final CountDownLatch endedCountDownLatch;
  private final CountDownLatch actionScheduleFinishedCountDownLatch;
  private final ArrayList<Timeline> timelines;
  private final ArrayList<Object> manifests;
  private final ArrayList<Integer> timelineChangeReasons;
  private final ArrayList<Integer> periodIndices;
  private final ArrayList<Integer> discontinuityReasons;

  private SimpleExoPlayer player;
  private Exception exception;
  private TrackGroupArray trackGroups;
  private boolean playerWasPrepared;

  private ExoPlayerTestRunner(
      Context context,
      Clock clock,
      MediaSource mediaSource,
      RenderersFactory renderersFactory,
      DefaultTrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      @Nullable ActionSchedule actionSchedule,
      @Nullable Player.EventListener eventListener,
      @Nullable AnalyticsListener analyticsListener,
      int expectedPlayerEndedCount) {
    this.context = context;
    this.clock = clock;
    this.mediaSource = mediaSource;
    this.renderersFactory = renderersFactory;
    this.trackSelector = trackSelector;
    this.loadControl = loadControl;
    this.bandwidthMeter = bandwidthMeter;
    this.actionSchedule = actionSchedule;
    this.eventListener = eventListener;
    this.analyticsListener = analyticsListener;
    this.timelines = new ArrayList<>();
    this.manifests = new ArrayList<>();
    this.timelineChangeReasons = new ArrayList<>();
    this.periodIndices = new ArrayList<>();
    this.discontinuityReasons = new ArrayList<>();
    this.endedCountDownLatch = new CountDownLatch(expectedPlayerEndedCount);
    this.actionScheduleFinishedCountDownLatch = new CountDownLatch(actionSchedule != null ? 1 : 0);
    this.playerThread = new HandlerThread("ExoPlayerTest thread");
    playerThread.start();
    this.handler = clock.createHandler(playerThread.getLooper(), /* callback= */ null);
  }

  // Called on the test thread to run the test.

  /**
   * Starts the test runner on its own thread. This will trigger the creation of the player, the
   * listener registration, the start of the action schedule, and the preparation of the player
   * with the provided media source.
   *
   * @return This test runner.
   */
  public ExoPlayerTestRunner start() {
    handler.post(
        () -> {
          try {
            player =
                new TestSimpleExoPlayer(
                    context, renderersFactory, trackSelector, loadControl, bandwidthMeter, clock);
            player.addListener(ExoPlayerTestRunner.this);
            if (eventListener != null) {
              player.addListener(eventListener);
            }
            if (analyticsListener != null) {
              player.addAnalyticsListener(analyticsListener);
            }
            player.setPlayWhenReady(true);
            if (actionSchedule != null) {
              actionSchedule.start(player, trackSelector, null, handler, ExoPlayerTestRunner.this);
            }
            player.prepare(mediaSource, /* resetPosition= */ false, /* resetState= */ false);
          } catch (Exception e) {
            handleException(e);
          }
        });
    return this;
  }

  /**
   * Blocks the current thread until the test runner finishes. A test is deemed to be finished when
   * the playback state transitioned to {@link Player#STATE_ENDED} or {@link Player#STATE_IDLE} for
   * the specified number of times. The test also finishes when an {@link ExoPlaybackException} is
   * thrown.
   *
   * @param timeoutMs The maximum time to wait for the test runner to finish. If this time elapsed
   *     the method will throw a {@link TimeoutException}.
   * @return This test runner.
   * @throws Exception If any exception occurred during playback, release, or due to a timeout.
   */
  public ExoPlayerTestRunner blockUntilEnded(long timeoutMs) throws Exception {
    if (!endedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      exception = new TimeoutException("Test playback timed out waiting for playback to end.");
    }
    release();
    // Throw any pending exception (from playback, timing out or releasing).
    if (exception != null) {
      throw exception;
    }
    return this;
  }

  /**
   * Blocks the current thread until the action schedule finished. This does not release the test
   * runner and the test must still call {@link #blockUntilEnded(long)}.
   *
   * @param timeoutMs The maximum time to wait for the action schedule to finish.
   * @return This test runner.
   * @throws TimeoutException If the action schedule did not finish within the specified timeout.
   * @throws InterruptedException If the test thread gets interrupted while waiting.
   */
  public ExoPlayerTestRunner blockUntilActionScheduleFinished(long timeoutMs)
      throws TimeoutException, InterruptedException {
    if (!actionScheduleFinishedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("Test playback timed out waiting for action schedule to finish.");
    }
    return this;
  }

  // Assertions called on the test thread after test finished.

  /**
   * Asserts that the timelines reported by
   * {@link Player.EventListener#onTimelineChanged(Timeline, Object, int)} are equal to the provided
   * timelines.
   *
   * @param timelines A list of expected {@link Timeline}s.
   */
  public void assertTimelinesEqual(Timeline... timelines) {
    assertThat(this.timelines).containsExactlyElementsIn(Arrays.asList(timelines)).inOrder();
  }

  /**
   * Asserts that the manifests reported by
   * {@link Player.EventListener#onTimelineChanged(Timeline, Object, int)} are equal to the provided
   * manifest.
   *
   * @param manifests A list of expected manifests.
   */
  public void assertManifestsEqual(Object... manifests) {
    assertThat(this.manifests).containsExactlyElementsIn(Arrays.asList(manifests)).inOrder();
  }

  /**
   * Asserts that the timeline change reasons reported by {@link
   * Player.EventListener#onTimelineChanged(Timeline, Object, int)} are equal to the provided
   * timeline change reasons.
   */
  public void assertTimelineChangeReasonsEqual(Integer... reasons) {
    assertThat(timelineChangeReasons).containsExactlyElementsIn(Arrays.asList(reasons)).inOrder();
  }

  /**
   * Asserts that the last track group array reported by
   * {@link Player.EventListener#onTracksChanged(TrackGroupArray, TrackSelectionArray)} is equal to
   * the provided track group array.
   *
   * @param trackGroupArray The expected {@link TrackGroupArray}.
   */
  public void assertTrackGroupsEqual(TrackGroupArray trackGroupArray) {
    assertThat(this.trackGroups).isEqualTo(trackGroupArray);
  }

  /**
   * Asserts that {@link Player.EventListener#onPositionDiscontinuity(int)} was not called.
   */
  public void assertNoPositionDiscontinuities() {
    assertThat(discontinuityReasons).isEmpty();
  }

  /**
   * Asserts that the discontinuity reasons reported by {@link
   * Player.EventListener#onPositionDiscontinuity(int)} are equal to the provided values.
   *
   * @param discontinuityReasons The expected discontinuity reasons.
   */
  public void assertPositionDiscontinuityReasonsEqual(Integer... discontinuityReasons) {
    assertThat(this.discontinuityReasons)
        .containsExactlyElementsIn(Arrays.asList(discontinuityReasons))
        .inOrder();
  }

  /**
   * Asserts that the indices of played periods is equal to the provided list of periods. A period
   * is considered to be played if it was the current period after a position discontinuity or a
   * media source preparation. When the same period is repeated automatically due to enabled repeat
   * modes, it is reported twice. Seeks within the current period are not reported.
   *
   * @param periodIndices A list of expected period indices.
   */
  public void assertPlayedPeriodIndices(Integer... periodIndices) {
    assertThat(this.periodIndices)
        .containsExactlyElementsIn(Arrays.asList(periodIndices))
        .inOrder();
  }

  // Private implementation details.

  private void release() throws InterruptedException {
    handler.post(
        () -> {
          try {
            if (player != null) {
              player.release();
            }
          } catch (Exception e) {
            handleException(e);
          } finally {
            playerThread.quit();
          }
        });
    playerThread.join();
  }

  private void handleException(Exception exception) {
    if (this.exception == null) {
      this.exception = exception;
    }
    while (endedCountDownLatch.getCount() > 0) {
      endedCountDownLatch.countDown();
    }
  }

  // Player.EventListener

  @Override
  public void onTimelineChanged(
      Timeline timeline, @Nullable Object manifest, @Player.TimelineChangeReason int reason) {
    timelines.add(timeline);
    manifests.add(manifest);
    timelineChangeReasons.add(reason);
    if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED) {
      periodIndices.add(player.getCurrentPeriodIndex());
    }
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    this.trackGroups = trackGroups;
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    playerWasPrepared |= playbackState != Player.STATE_IDLE;
    if (playbackState == Player.STATE_ENDED
        || (playbackState == Player.STATE_IDLE && playerWasPrepared)) {
      endedCountDownLatch.countDown();
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    handleException(error);
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    discontinuityReasons.add(reason);
    int currentIndex = player.getCurrentPeriodIndex();
    if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
        || periodIndices.isEmpty()
        || periodIndices.get(periodIndices.size() - 1) != currentIndex) {
      // Ignore seek or internal discontinuities within a period.
      periodIndices.add(currentIndex);
    }
  }

  // ActionSchedule.Callback

  @Override
  public void onActionScheduleFinished() {
    actionScheduleFinishedCountDownLatch.countDown();
  }

  /** SimpleExoPlayer implementation using a custom Clock. */
  private static final class TestSimpleExoPlayer extends SimpleExoPlayer {

    public TestSimpleExoPlayer(
        Context context,
        RenderersFactory renderersFactory,
        TrackSelector trackSelector,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        Clock clock) {
      super(
          context,
          renderersFactory,
          trackSelector,
          loadControl,
          /* drmSessionManager= */ null,
          bandwidthMeter,
          new AnalyticsCollector.Factory(),
          clock,
          Looper.myLooper());
    }
  }
}
