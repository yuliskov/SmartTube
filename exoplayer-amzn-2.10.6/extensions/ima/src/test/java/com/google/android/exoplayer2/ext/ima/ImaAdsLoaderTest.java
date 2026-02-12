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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.source.ads.SinglePeriodAdTimeline;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for {@link ImaAdsLoader}. */
@RunWith(AndroidJUnit4.class)
public class ImaAdsLoaderTest {

  private static final long CONTENT_DURATION_US = 10 * C.MICROS_PER_SECOND;
  private static final Timeline CONTENT_TIMELINE =
      new SinglePeriodTimeline(CONTENT_DURATION_US, /* isSeekable= */ true, /* isDynamic= */ false);
  private static final Uri TEST_URI = Uri.EMPTY;
  private static final long TEST_AD_DURATION_US = 5 * C.MICROS_PER_SECOND;
  private static final long[][] PREROLL_ADS_DURATIONS_US = new long[][] {{TEST_AD_DURATION_US}};
  private static final Float[] PREROLL_CUE_POINTS_SECONDS = new Float[] {0f};
  private static final FakeAd UNSKIPPABLE_AD =
      new FakeAd(/* skippable= */ false, /* podIndex= */ 0, /* totalAds= */ 1, /* adPosition= */ 1);

  private @Mock ImaSdkSettings imaSdkSettings;
  private @Mock AdsRenderingSettings adsRenderingSettings;
  private @Mock AdDisplayContainer adDisplayContainer;
  private @Mock AdsManager adsManager;
  private SingletonImaFactory testImaFactory;
  private ViewGroup adViewGroup;
  private View adOverlayView;
  private AdsLoader.AdViewProvider adViewProvider;
  private TestAdsLoaderListener adsLoaderListener;
  private FakePlayer fakeExoPlayer;
  private ImaAdsLoader imaAdsLoader;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FakeAdsRequest fakeAdsRequest = new FakeAdsRequest();
    FakeAdsLoader fakeAdsLoader = new FakeAdsLoader(imaSdkSettings, adsManager);
    testImaFactory =
        new SingletonImaFactory(
            imaSdkSettings,
            adsRenderingSettings,
            adDisplayContainer,
            fakeAdsRequest,
            fakeAdsLoader);
    adViewGroup = new FrameLayout(ApplicationProvider.getApplicationContext());
    adOverlayView = new View(ApplicationProvider.getApplicationContext());
    adViewProvider =
        new AdsLoader.AdViewProvider() {
          @Override
          public ViewGroup getAdViewGroup() {
            return adViewGroup;
          }

          @Override
          public View[] getAdOverlayViews() {
            return new View[] {adOverlayView};
          }
        };
  }

  @After
  public void teardown() {
    if (imaAdsLoader != null) {
      imaAdsLoader.release();
    }
  }

  @Test
  public void testBuilder_overridesPlayerType() {
    when(imaSdkSettings.getPlayerType()).thenReturn("test player type");
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);

    verify(imaSdkSettings).setPlayerType("google/exo.ext.ima");
  }

  @Test
  public void testStart_setsAdUiViewGroup() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    verify(adDisplayContainer, atLeastOnce()).setAdContainer(adViewGroup);
    verify(adDisplayContainer, atLeastOnce()).registerVideoControlsOverlay(adOverlayView);
  }

  @Test
  public void testStart_updatesAdPlaybackState() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs= */ 0)
                .withAdDurationsUs(PREROLL_ADS_DURATIONS_US)
                .withContentDurationUs(CONTENT_DURATION_US));
  }

  @Test
  public void testStartAfterRelease() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.release();
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
  }

  @Test
  public void testStartAndCallbacksAfterRelease() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.release();
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    fakeExoPlayer.setPlayingContentPosition(/* position= */ 0);
    fakeExoPlayer.setState(Player.STATE_READY, true);

    // If callbacks are invoked there is no crash.
    // Note: we can't currently call getContentProgress/getAdProgress as a VerifyError is thrown
    // when using Robolectric and accessing VideoProgressUpdate.VIDEO_TIME_NOT_READY, due to the IMA
    // SDK being proguarded.
    imaAdsLoader.requestAds(adViewGroup);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.LOADED, UNSKIPPABLE_AD));
    imaAdsLoader.loadAd(TEST_URI.toString());
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_PAUSE_REQUESTED, UNSKIPPABLE_AD));
    imaAdsLoader.playAd();
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.STARTED, UNSKIPPABLE_AD));
    imaAdsLoader.pauseAd();
    imaAdsLoader.stopAd();
    imaAdsLoader.onPlayerError(ExoPlaybackException.createForSource(new IOException()));
    imaAdsLoader.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_RESUME_REQUESTED, /* ad= */ null));
    imaAdsLoader.handlePrepareError(
        /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, new IOException());
  }

  @Test
  public void testPlayback_withPrerollAd_marksAdAsPlayed() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);

    // Load the preroll ad.
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.LOADED, UNSKIPPABLE_AD));
    imaAdsLoader.loadAd(TEST_URI.toString());
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_PAUSE_REQUESTED, UNSKIPPABLE_AD));

    // Play the preroll ad.
    imaAdsLoader.playAd();
    fakeExoPlayer.setPlayingAdPosition(
        /* adGroupIndex= */ 0,
        /* adIndexInAdGroup= */ 0,
        /* position= */ 0,
        /* contentPosition= */ 0);
    fakeExoPlayer.setState(Player.STATE_READY, true);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.STARTED, UNSKIPPABLE_AD));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.FIRST_QUARTILE, UNSKIPPABLE_AD));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.MIDPOINT, UNSKIPPABLE_AD));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.THIRD_QUARTILE, UNSKIPPABLE_AD));

    // Play the content.
    fakeExoPlayer.setPlayingContentPosition(0);
    imaAdsLoader.stopAd();
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_RESUME_REQUESTED, /* ad= */ null));

    // Verify that the preroll ad has been marked as played.
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs= */ 0)
                .withContentDurationUs(CONTENT_DURATION_US)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, /* uri= */ TEST_URI)
                .withAdDurationsUs(PREROLL_ADS_DURATIONS_US)
                .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
                .withAdResumePositionUs(/* adResumePositionUs= */ 0));
  }

  @Test
  public void testStop_unregistersAllVideoControlOverlays() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_ADS_DURATIONS_US, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    imaAdsLoader.requestAds(adViewGroup);
    imaAdsLoader.stop();

    InOrder inOrder = inOrder(adDisplayContainer);
    inOrder.verify(adDisplayContainer).registerVideoControlsOverlay(adOverlayView);
    inOrder.verify(adDisplayContainer).unregisterAllVideoControlsOverlays();
  }

  private void setupPlayback(Timeline contentTimeline, long[][] adDurationsUs, Float[] cuePoints) {
    fakeExoPlayer = new FakePlayer();
    adsLoaderListener = new TestAdsLoaderListener(fakeExoPlayer, contentTimeline, adDurationsUs);
    when(adsManager.getAdCuePoints()).thenReturn(Arrays.asList(cuePoints));
    imaAdsLoader =
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setImaFactory(testImaFactory)
            .setImaSdkSettings(imaSdkSettings)
            .buildForAdTag(TEST_URI);
    imaAdsLoader.setPlayer(fakeExoPlayer);
  }

  private static AdEvent getAdEvent(AdEventType adEventType, @Nullable Ad ad) {
    return new AdEvent() {
      @Override
      public AdEventType getType() {
        return adEventType;
      }

      @Override
      public @Nullable Ad getAd() {
        return ad;
      }

      @Override
      public Map<String, String> getAdData() {
        return Collections.emptyMap();
      }
    };
  }

  /** Ad loader event listener that forwards ad playback state to a fake player. */
  private static final class TestAdsLoaderListener implements AdsLoader.EventListener {

    private final FakePlayer fakeExoPlayer;
    private final Timeline contentTimeline;
    private final long[][] adDurationsUs;

    public AdPlaybackState adPlaybackState;

    public TestAdsLoaderListener(
        FakePlayer fakeExoPlayer, Timeline contentTimeline, long[][] adDurationsUs) {
      this.fakeExoPlayer = fakeExoPlayer;
      this.contentTimeline = contentTimeline;
      this.adDurationsUs = adDurationsUs;
    }

    @Override
    public void onAdPlaybackState(AdPlaybackState adPlaybackState) {
      adPlaybackState = adPlaybackState.withAdDurationsUs(adDurationsUs);
      this.adPlaybackState = adPlaybackState;
      fakeExoPlayer.updateTimeline(new SinglePeriodAdTimeline(contentTimeline, adPlaybackState));
    }

    @Override
    public void onAdLoadError(AdLoadException error, DataSpec dataSpec) {
      assertThat(error.type).isNotEqualTo(AdLoadException.TYPE_UNEXPECTED);
    }

    @Override
    public void onAdClicked() {
      // Do nothing.
    }

    @Override
    public void onAdTapped() {
      // Do nothing.
    }
  }
}
