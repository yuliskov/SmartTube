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
package com.google.android.exoplayer2.ext.vp9;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link LibvpxVideoRenderer}. */
@RunWith(AndroidJUnit4.class)
public class VpxPlaybackTest {

  private static final String BEAR_URI = "asset:///bear-vp9.webm";
  private static final String BEAR_ODD_DIMENSIONS_URI = "asset:///bear-vp9-odd-dimensions.webm";
  private static final String ROADTRIP_10BIT_URI = "asset:///roadtrip-vp92-10bit.webm";
  private static final String INVALID_BITSTREAM_URI = "asset:///invalid-bitstream.webm";

  private static final String TAG = "VpxPlaybackTest";

  @Before
  public void setUp() {
    if (!VpxLibrary.isAvailable()) {
      fail("Vpx library not available.");
    }
  }

  @Test
  public void testBasicPlayback() throws Exception {
    playUri(BEAR_URI);
  }

  @Test
  public void testOddDimensionsPlayback() throws Exception {
    playUri(BEAR_ODD_DIMENSIONS_URI);
  }

  @Test
  public void test10BitProfile2Playback() throws Exception {
    if (VpxLibrary.isHighBitDepthSupported()) {
      Log.d(TAG, "High Bit Depth supported.");
      playUri(ROADTRIP_10BIT_URI);
      return;
    }
    Log.d(TAG, "High Bit Depth not supported.");
  }

  @Test
  public void testInvalidBitstream() {
    try {
      playUri(INVALID_BITSTREAM_URI);
      fail();
    } catch (Exception e) {
      assertThat(e.getCause()).isNotNull();
      assertThat(e.getCause()).isInstanceOf(VpxDecoderException.class);
    }
  }

  private void playUri(String uri) throws Exception {
    TestPlaybackRunnable testPlaybackRunnable =
        new TestPlaybackRunnable(Uri.parse(uri), ApplicationProvider.getApplicationContext());
    Thread thread = new Thread(testPlaybackRunnable);
    thread.start();
    thread.join();
    if (testPlaybackRunnable.playbackException != null) {
      throw testPlaybackRunnable.playbackException;
    }
  }

  private static class TestPlaybackRunnable implements Player.EventListener, Runnable {

    private final Context context;
    private final Uri uri;

    private ExoPlayer player;
    private ExoPlaybackException playbackException;

    public TestPlaybackRunnable(Uri uri, Context context) {
      this.uri = uri;
      this.context = context;
    }

    @Override
    public void run() {
      Looper.prepare();
      LibvpxVideoRenderer videoRenderer = new LibvpxVideoRenderer(0);
      DefaultTrackSelector trackSelector = new DefaultTrackSelector();
      player = ExoPlayerFactory.newInstance(context, new Renderer[] {videoRenderer}, trackSelector);
      player.addListener(this);
      MediaSource mediaSource =
          new ProgressiveMediaSource.Factory(
                  new DefaultDataSourceFactory(context, "ExoPlayerExtVp9Test"),
                  MatroskaExtractor.FACTORY)
              .createMediaSource(uri);
      player
          .createMessage(videoRenderer)
          .setType(LibvpxVideoRenderer.MSG_SET_OUTPUT_BUFFER_RENDERER)
          .setPayload(new VpxVideoSurfaceView(context))
          .send();
      player.prepare(mediaSource);
      player.setPlayWhenReady(true);
      Looper.loop();
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == Player.STATE_ENDED
          || (playbackState == Player.STATE_IDLE && playbackException != null)) {
        player.release();
        Looper.myLooper().quit();
      }
    }
  }

}
