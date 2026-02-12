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
package com.google.android.exoplayer2.ext.ima;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.view.View;
import android.view.ViewGroup;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdError.AdErrorCode;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AdsLoader} using the IMA SDK. All methods must be called on the main thread.
 *
 * <p>The player instance that will play the loaded ads must be set before playback using {@link
 * #setPlayer(Player)}. If the ads loader is no longer required, it must be released by calling
 * {@link #release()}.
 *
 * <p>The IMA SDK can take into account video control overlay views when calculating ad viewability.
 * For more details see {@link AdDisplayContainer#registerVideoControlsOverlay(View)} and {@link
 * AdViewProvider#getAdOverlayViews()}.
 */
public final class ImaAdsLoader
    implements Player.EventListener,
        AdsLoader,
        VideoAdPlayer,
        ContentProgressProvider,
        AdErrorListener,
        AdsLoadedListener,
        AdEventListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ima");
  }

  /** Builder for {@link ImaAdsLoader}. */
  public static final class Builder {

    private final Context context;

    @Nullable private ImaSdkSettings imaSdkSettings;
    @Nullable private AdEventListener adEventListener;
    @Nullable private Set<UiElement> adUiElements;
    private int vastLoadTimeoutMs;
    private int mediaLoadTimeoutMs;
    private int mediaBitrate;
    private boolean focusSkipButtonWhenAvailable;
    private ImaFactory imaFactory;

    /**
     * Creates a new builder for {@link ImaAdsLoader}.
     *
     * @param context The context;
     */
    public Builder(Context context) {
      this.context = Assertions.checkNotNull(context);
      vastLoadTimeoutMs = TIMEOUT_UNSET;
      mediaLoadTimeoutMs = TIMEOUT_UNSET;
      mediaBitrate = BITRATE_UNSET;
      focusSkipButtonWhenAvailable = true;
      imaFactory = new DefaultImaFactory();
    }

    /**
     * Sets the IMA SDK settings. The provided settings instance's player type and version fields
     * may be overwritten.
     *
     * <p>If this method is not called the default settings will be used.
     *
     * @param imaSdkSettings The {@link ImaSdkSettings}.
     * @return This builder, for convenience.
     */
    public Builder setImaSdkSettings(ImaSdkSettings imaSdkSettings) {
      this.imaSdkSettings = Assertions.checkNotNull(imaSdkSettings);
      return this;
    }

    /**
     * Sets a listener for ad events that will be passed to {@link
     * AdsManager#addAdEventListener(AdEventListener)}.
     *
     * @param adEventListener The ad event listener.
     * @return This builder, for convenience.
     */
    public Builder setAdEventListener(AdEventListener adEventListener) {
      this.adEventListener = Assertions.checkNotNull(adEventListener);
      return this;
    }

    /**
     * Sets the ad UI elements to be rendered by the IMA SDK.
     *
     * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setUiElements(Set)
     */
    public Builder setAdUiElements(Set<UiElement> adUiElements) {
      this.adUiElements = new HashSet<>(Assertions.checkNotNull(adUiElements));
      return this;
    }

    /**
     * Sets the VAST load timeout, in milliseconds.
     *
     * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
     * @return This builder, for convenience.
     * @see AdsRequest#setVastLoadTimeout(float)
     */
    public Builder setVastLoadTimeoutMs(int vastLoadTimeoutMs) {
      Assertions.checkArgument(vastLoadTimeoutMs > 0);
      this.vastLoadTimeoutMs = vastLoadTimeoutMs;
      return this;
    }

    /**
     * Sets the ad media load timeout, in milliseconds.
     *
     * @param mediaLoadTimeoutMs The ad media load timeout, in milliseconds.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setLoadVideoTimeout(int)
     */
    public Builder setMediaLoadTimeoutMs(int mediaLoadTimeoutMs) {
      Assertions.checkArgument(mediaLoadTimeoutMs > 0);
      this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
      return this;
    }

    /**
     * Sets the media maximum recommended bitrate for ads, in bps.
     *
     * @param bitrate The media maximum recommended bitrate for ads, in bps.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setBitrateKbps(int)
     */
    public Builder setMaxMediaBitrate(int bitrate) {
      Assertions.checkArgument(bitrate > 0);
      this.mediaBitrate = bitrate;
      return this;
    }

    /**
     * Sets whether to focus the skip button (when available) on Android TV devices. The default
     * setting is {@code true}.
     *
     * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
     *     Android TV devices.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setFocusSkipButtonWhenAvailable(boolean)
     */
    public Builder setFocusSkipButtonWhenAvailable(boolean focusSkipButtonWhenAvailable) {
      this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
      return this;
    }

    @VisibleForTesting
    /* package */ Builder setImaFactory(ImaFactory imaFactory) {
      this.imaFactory = Assertions.checkNotNull(imaFactory);
      return this;
    }

    /**
     * Returns a new {@link ImaAdsLoader} for the specified ad tag.
     *
     * @param adTagUri The URI of a compatible ad tag to load. See
     *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
     *     information on compatible ad tags.
     * @return The new {@link ImaAdsLoader}.
     */
    public ImaAdsLoader buildForAdTag(Uri adTagUri) {
      return new ImaAdsLoader(
          context,
          adTagUri,
          imaSdkSettings,
          null,
          vastLoadTimeoutMs,
          mediaLoadTimeoutMs,
          mediaBitrate,
          focusSkipButtonWhenAvailable,
          adUiElements,
          adEventListener,
          imaFactory);
    }

    /**
     * Returns a new {@link ImaAdsLoader} with the specified sideloaded ads response.
     *
     * @param adsResponse The sideloaded VAST, VMAP, or ad rules response to be used instead of
     *     making a request via an ad tag URL.
     * @return The new {@link ImaAdsLoader}.
     */
    public ImaAdsLoader buildForAdsResponse(String adsResponse) {
      return new ImaAdsLoader(
          context,
          null,
          imaSdkSettings,
          adsResponse,
          vastLoadTimeoutMs,
          mediaLoadTimeoutMs,
          mediaBitrate,
          focusSkipButtonWhenAvailable,
          adUiElements,
          adEventListener,
          imaFactory);
    }
  }

  private static final boolean DEBUG = false;
  private static final String TAG = "ImaAdsLoader";

  /**
   * Whether to enable preloading of ads in {@link AdsRenderingSettings}.
   */
  private static final boolean ENABLE_PRELOADING = true;

  private static final String IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima";
  private static final String IMA_SDK_SETTINGS_PLAYER_VERSION = ExoPlayerLibraryInfo.VERSION;

  /** The value used in {@link VideoProgressUpdate}s to indicate an unset duration. */
  private static final long IMA_DURATION_UNSET = -1L;

  /**
   * Threshold before the end of content at which IMA is notified that content is complete if the
   * player buffers, in milliseconds.
   */
  private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;

  /** The maximum duration before an ad break that IMA may start preloading the next ad. */
  private static final long MAXIMUM_PRELOAD_DURATION_MS = 8000;

  private static final int TIMEOUT_UNSET = -1;
  private static final int BITRATE_UNSET = -1;

  /** The state of ad playback. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED})
  private @interface ImaAdState {}
  /**
   * The ad playback state when IMA is not playing an ad.
   */
  private static final int IMA_AD_STATE_NONE = 0;
  /**
   * The ad playback state when IMA has called {@link #playAd()} and not {@link #pauseAd()}.
   */
  private static final int IMA_AD_STATE_PLAYING = 1;
  /**
   * The ad playback state when IMA has called {@link #pauseAd()} while playing an ad.
   */
  private static final int IMA_AD_STATE_PAUSED = 2;

  private final @Nullable Uri adTagUri;
  private final @Nullable String adsResponse;
  private final int vastLoadTimeoutMs;
  private final int mediaLoadTimeoutMs;
  private final boolean focusSkipButtonWhenAvailable;
  private final int mediaBitrate;
  private final @Nullable Set<UiElement> adUiElements;
  private final @Nullable AdEventListener adEventListener;
  private final ImaFactory imaFactory;
  private final Timeline.Period period;
  private final List<VideoAdPlayerCallback> adCallbacks;
  private final AdDisplayContainer adDisplayContainer;
  private final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;

  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  private Object pendingAdRequestContext;
  private List<String> supportedMimeTypes;
  @Nullable private EventListener eventListener;
  @Nullable private Player player;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;
  private int lastVolumePercentage;

  private AdsManager adsManager;
  private boolean initializedAdsManager;
  private AdLoadException pendingAdLoadError;
  private Timeline timeline;
  private long contentDurationMs;
  private int podIndexOffset;
  private AdPlaybackState adPlaybackState;

  // Fields tracking IMA's state.

  /** The expected ad group index that IMA should load next. */
  private int expectedAdGroupIndex;
  /** The index of the current ad group that IMA is loading. */
  private int adGroupIndex;
  /** Whether IMA has sent an ad event to pause content since the last resume content event. */
  private boolean imaPausedContent;
  /** The current ad playback state. */
  private @ImaAdState int imaAdState;
  /**
   * Whether {@link com.google.ads.interactivemedia.v3.api.AdsLoader#contentComplete()} has been
   * called since starting ad playback.
   */
  private boolean sentContentComplete;

  // Fields tracking the player/loader state.

  /** Whether the player is playing an ad. */
  private boolean playingAd;
  /**
   * If the player is playing an ad, stores the ad index in its ad group. {@link C#INDEX_UNSET}
   * otherwise.
   */
  private int playingAdIndexInAdGroup;
  /**
   * Whether there's a pending ad preparation error which IMA needs to be notified of when it
   * transitions from playing content to playing the ad.
   */
  private boolean shouldNotifyAdPrepareError;
  /**
   * If a content period has finished but IMA has not yet called {@link #playAd()}, stores the value
   * of {@link SystemClock#elapsedRealtime()} when the content stopped playing. This can be used to
   * determine a fake, increasing content position. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressElapsedRealtimeMs;
  /**
   * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
   * content progress should increase. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressOffsetMs;
  /** Stores the pending content position when a seek operation was intercepted to play an ad. */
  private long pendingContentPositionMs;
  /** Whether {@link #getContentProgress()} has sent {@link #pendingContentPositionMs} to IMA. */
  private boolean sentPendingContentPositionMs;

  /**
   * Creates a new IMA ads loader.
   *
   * <p>If you need to customize the ad request, use {@link ImaAdsLoader.Builder} instead.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   */
  public ImaAdsLoader(Context context, Uri adTagUri) {
    this(
        context,
        adTagUri,
        /* imaSdkSettings= */ null,
        /* adsResponse= */ null,
        /* vastLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaBitrate= */ BITRATE_UNSET,
        /* focusSkipButtonWhenAvailable= */ true,
        /* adUiElements= */ null,
        /* adEventListener= */ null,
        /* imaFactory= */ new DefaultImaFactory());
  }

  /**
   * Creates a new IMA ads loader.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @param imaSdkSettings {@link ImaSdkSettings} used to configure the IMA SDK, or {@code null} to
   *     use the default settings. If set, the player type and version fields may be overwritten.
   * @deprecated Use {@link ImaAdsLoader.Builder}.
   */
  @Deprecated
  public ImaAdsLoader(Context context, Uri adTagUri, ImaSdkSettings imaSdkSettings) {
    this(
        context,
        adTagUri,
        imaSdkSettings,
        /* adsResponse= */ null,
        /* vastLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaBitrate= */ BITRATE_UNSET,
        /* focusSkipButtonWhenAvailable= */ true,
        /* adUiElements= */ null,
        /* adEventListener= */ null,
        /* imaFactory= */ new DefaultImaFactory());
  }

  private ImaAdsLoader(
      Context context,
      @Nullable Uri adTagUri,
      @Nullable ImaSdkSettings imaSdkSettings,
      @Nullable String adsResponse,
      int vastLoadTimeoutMs,
      int mediaLoadTimeoutMs,
      int mediaBitrate,
      boolean focusSkipButtonWhenAvailable,
      @Nullable Set<UiElement> adUiElements,
      @Nullable AdEventListener adEventListener,
      ImaFactory imaFactory) {
    Assertions.checkArgument(adTagUri != null || adsResponse != null);
    this.adTagUri = adTagUri;
    this.adsResponse = adsResponse;
    this.vastLoadTimeoutMs = vastLoadTimeoutMs;
    this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
    this.mediaBitrate = mediaBitrate;
    this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
    this.adUiElements = adUiElements;
    this.adEventListener = adEventListener;
    this.imaFactory = imaFactory;
    if (imaSdkSettings == null) {
      imaSdkSettings = imaFactory.createImaSdkSettings();
      if (DEBUG) {
        imaSdkSettings.setDebugMode(true);
      }
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
    period = new Timeline.Period();
    adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
    adDisplayContainer = imaFactory.createAdDisplayContainer();
    adDisplayContainer.setPlayer(/* videoAdPlayer= */ this);
    adsLoader = imaFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    adsLoader.addAdErrorListener(/* adErrorListener= */ this);
    adsLoader.addAdsLoadedListener(/* adsLoadedListener= */ this);
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    adGroupIndex = C.INDEX_UNSET;
    contentDurationMs = C.TIME_UNSET;
    timeline = Timeline.EMPTY;
  }

  /**
   * Returns the underlying {@code com.google.ads.interactivemedia.v3.api.AdsLoader} wrapped by
   * this instance.
   */
  public com.google.ads.interactivemedia.v3.api.AdsLoader getAdsLoader() {
    return adsLoader;
  }

  /**
   * Returns the {@link AdDisplayContainer} used by this loader.
   *
   * <p>Note: any video controls overlays registered via {@link
   * AdDisplayContainer#registerVideoControlsOverlay(View)} will be unregistered automatically when
   * the media source detaches from this instance. It is therefore necessary to re-register views
   * each time the ads loader is reused. Alternatively, provide overlay views via the {@link
   * AdsLoader.AdViewProvider} when creating the media source to benefit from automatic
   * registration.
   */
  public AdDisplayContainer getAdDisplayContainer() {
    return adDisplayContainer;
  }

  /**
   * Sets the slots for displaying companion ads. Individual slots can be created using {@link
   * ImaSdkFactory#createCompanionAdSlot()}.
   *
   * @param companionSlots Slots for displaying companion ads.
   * @see AdDisplayContainer#setCompanionSlots(Collection)
   * @deprecated Use {@code getAdDisplayContainer().setCompanionSlots(...)}.
   */
  @Deprecated
  public void setCompanionSlots(Collection<CompanionAdSlot> companionSlots) {
    adDisplayContainer.setCompanionSlots(companionSlots);
  }

  /**
   * Requests ads, if they have not already been requested. Must be called on the main thread.
   *
   * <p>Ads will be requested automatically when the player is prepared if this method has not been
   * called, so it is only necessary to call this method if you want to request ads before preparing
   * the player.
   *
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   */
  public void requestAds(ViewGroup adViewGroup) {
    if (adPlaybackState != null || adsManager != null || pendingAdRequestContext != null) {
      // Ads have already been requested.
      return;
    }
    adDisplayContainer.setAdContainer(adViewGroup);
    pendingAdRequestContext = new Object();
    AdsRequest request = imaFactory.createAdsRequest();
    if (adTagUri != null) {
      request.setAdTagUrl(adTagUri.toString());
    } else /* adsResponse != null */ {
      request.setAdsResponse(adsResponse);
    }
    if (vastLoadTimeoutMs != TIMEOUT_UNSET) {
      request.setVastLoadTimeout(vastLoadTimeoutMs);
    }
    request.setContentProgressProvider(this);
    request.setUserRequestContext(pendingAdRequestContext);
    adsLoader.requestAds(request);
  }

  // AdsLoader implementation.

  @Override
  public void setPlayer(@Nullable Player player) {
    Assertions.checkState(Looper.getMainLooper() == Looper.myLooper());
    Assertions.checkState(
        player == null || player.getApplicationLooper() == Looper.getMainLooper());
    nextPlayer = player;
    wasSetPlayerCalled = true;
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
    List<String> supportedMimeTypes = new ArrayList<>();
    for (@C.ContentType int contentType : contentTypes) {
      if (contentType == C.TYPE_DASH) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_MPD);
      } else if (contentType == C.TYPE_HLS) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8);
      } else if (contentType == C.TYPE_OTHER) {
        supportedMimeTypes.addAll(
            Arrays.asList(
                MimeTypes.VIDEO_MP4,
                MimeTypes.VIDEO_WEBM,
                MimeTypes.VIDEO_H263,
                MimeTypes.AUDIO_MP4,
                MimeTypes.AUDIO_MPEG));
      } else if (contentType == C.TYPE_SS) {
        // IMA does not support Smooth Streaming ad media.
      }
    }
    this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes);
  }

  @Override
  public void start(EventListener eventListener, AdViewProvider adViewProvider) {
    Assertions.checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    player = nextPlayer;
    if (player == null) {
      return;
    }
    this.eventListener = eventListener;
    lastVolumePercentage = 0;
    lastAdProgress = null;
    lastContentProgress = null;
    ViewGroup adViewGroup = adViewProvider.getAdViewGroup();
    adDisplayContainer.setAdContainer(adViewGroup);
    View[] adOverlayViews = adViewProvider.getAdOverlayViews();
    for (View view : adOverlayViews) {
      adDisplayContainer.registerVideoControlsOverlay(view);
    }
    player.addListener(this);
    maybeNotifyPendingAdLoadError();
    if (adPlaybackState != null) {
      // Pass the ad playback state to the player, and resume ads if necessary.
      eventListener.onAdPlaybackState(adPlaybackState);
      if (imaPausedContent && player.getPlayWhenReady()) {
        adsManager.resume();
      }
    } else if (adsManager != null) {
      adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
      updateAdPlaybackState();
    } else {
      // Ads haven't loaded yet, so request them.
      requestAds(adViewGroup);
    }
  }

  @Override
  public void stop() {
    if (player == null) {
      return;
    }
    if (adsManager != null && imaPausedContent) {
      adPlaybackState =
          adPlaybackState.withAdResumePositionUs(
              playingAd ? C.msToUs(player.getCurrentPosition()) : 0);
      adsManager.pause();
    }
    lastVolumePercentage = getVolume();
    lastAdProgress = getAdProgress();
    lastContentProgress = getContentProgress();
    adDisplayContainer.unregisterAllVideoControlsOverlays();
    player.removeListener(this);
    player = null;
    eventListener = null;
  }

  @Override
  public void release() {
    pendingAdRequestContext = null;
    if (adsManager != null) {
      adsManager.destroy();
      adsManager = null;
    }
    adsLoader.removeAdsLoadedListener(/* adsLoadedListener= */ this);
    adsLoader.removeAdErrorListener(/* adErrorListener= */ this);
    imaPausedContent = false;
    imaAdState = IMA_AD_STATE_NONE;
    pendingAdLoadError = null;
    adPlaybackState = AdPlaybackState.NONE;
    updateAdPlaybackState();
  }

  @Override
  public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
    if (player == null) {
      return;
    }
    try {
      handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception);
    } catch (Exception e) {
      maybeNotifyInternalError("handlePrepareError", e);
    }
  }

  // com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener implementation.

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
    if (!Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.getUserRequestContext())) {
      adsManager.destroy();
      return;
    }
    pendingAdRequestContext = null;
    this.adsManager = adsManager;
    adsManager.addAdErrorListener(this);
    adsManager.addAdEventListener(this);
    if (adEventListener != null) {
      adsManager.addAdEventListener(adEventListener);
    }
    if (player != null) {
      // If a player is attached already, start playback immediately.
      try {
        adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
        updateAdPlaybackState();
      } catch (Exception e) {
        maybeNotifyInternalError("onAdsManagerLoaded", e);
      }
    }
  }

  // AdEvent.AdEventListener implementation.

  @Override
  public void onAdEvent(AdEvent adEvent) {
    AdEventType adEventType = adEvent.getType();
    if (DEBUG) {
      Log.d(TAG, "onAdEvent: " + adEventType);
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring AdEvent after release: " + adEvent);
      return;
    }
    try {
      handleAdEvent(adEvent);
    } catch (Exception e) {
      maybeNotifyInternalError("onAdEvent", e);
    }
  }

  // AdErrorEvent.AdErrorListener implementation.

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    AdError error = adErrorEvent.getError();
    if (DEBUG) {
      Log.d(TAG, "onAdError", error);
    }
    if (adsManager == null) {
      // No ads were loaded, so allow playback to start without any ads.
      pendingAdRequestContext = null;
      adPlaybackState = new AdPlaybackState();
      updateAdPlaybackState();
    } else if (isAdGroupLoadError(error)) {
      try {
        handleAdGroupLoadError(error);
      } catch (Exception e) {
        maybeNotifyInternalError("onAdError", e);
      }
    }
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdLoadException.createForAllAds(error);
    }
    maybeNotifyPendingAdLoadError();
  }

  // ContentProgressProvider implementation.

  @Override
  public VideoProgressUpdate getContentProgress() {
    if (player == null) {
      return lastContentProgress;
    }
    boolean hasContentDuration = contentDurationMs != C.TIME_UNSET;
    long contentPositionMs;
    if (pendingContentPositionMs != C.TIME_UNSET) {
      sentPendingContentPositionMs = true;
      contentPositionMs = pendingContentPositionMs;
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
      long elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs;
      contentPositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs;
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    } else if (imaAdState == IMA_AD_STATE_NONE && !playingAd && hasContentDuration) {
      contentPositionMs = player.getCurrentPosition();
      // Update the expected ad group index for the current content position. The update is delayed
      // until MAXIMUM_PRELOAD_DURATION_MS before the ad so that an ad group load error delivered
      // just after an ad group isn't incorrectly attributed to the next ad group.
      int nextAdGroupIndex =
          adPlaybackState.getAdGroupIndexAfterPositionUs(
              C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
      if (nextAdGroupIndex != expectedAdGroupIndex && nextAdGroupIndex != C.INDEX_UNSET) {
        long nextAdGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[nextAdGroupIndex]);
        if (nextAdGroupTimeMs == C.TIME_END_OF_SOURCE) {
          nextAdGroupTimeMs = contentDurationMs;
        }
        if (nextAdGroupTimeMs - contentPositionMs < MAXIMUM_PRELOAD_DURATION_MS) {
          expectedAdGroupIndex = nextAdGroupIndex;
        }
      }
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
    long contentDurationMs = hasContentDuration ? this.contentDurationMs : IMA_DURATION_UNSET;
    return new VideoProgressUpdate(contentPositionMs, contentDurationMs);
  }

  // VideoAdPlayer implementation.

  @Override
  public VideoProgressUpdate getAdProgress() {
    if (player == null) {
      return lastAdProgress;
    } else if (imaAdState != IMA_AD_STATE_NONE && playingAd) {
      long adDuration = player.getDuration();
      return adDuration == C.TIME_UNSET ? VideoProgressUpdate.VIDEO_TIME_NOT_READY
          : new VideoProgressUpdate(player.getCurrentPosition(), adDuration);
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
  }

  @Override
  public int getVolume() {
    if (player == null) {
      return lastVolumePercentage;
    }

    Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      return (int) (audioComponent.getVolume() * 100);
    }

    // Check for a selected track using an audio renderer.
    TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
    for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
      if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
        return 100;
      }
    }
    return 0;
  }

  @Override
  public void loadAd(String adUriString) {
    try {
      if (DEBUG) {
        Log.d(TAG, "loadAd in ad group " + adGroupIndex);
      }
      if (adsManager == null) {
        Log.w(TAG, "Ignoring loadAd after release");
        return;
      }
      if (adGroupIndex == C.INDEX_UNSET) {
        Log.w(
            TAG,
            "Unexpected loadAd without LOADED event; assuming ad group index is actually "
                + expectedAdGroupIndex);
        adGroupIndex = expectedAdGroupIndex;
        adsManager.start();
      }
      int adIndexInAdGroup = getAdIndexInAdGroupToLoad(adGroupIndex);
      if (adIndexInAdGroup == C.INDEX_UNSET) {
        Log.w(TAG, "Unexpected loadAd in an ad group with no remaining unavailable ads");
        return;
      }
      adPlaybackState =
          adPlaybackState.withAdUri(adGroupIndex, adIndexInAdGroup, Uri.parse(adUriString));
      updateAdPlaybackState();
    } catch (Exception e) {
      maybeNotifyInternalError("loadAd", e);
    }
  }

  @Override
  public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.add(videoAdPlayerCallback);
  }

  @Override
  public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.remove(videoAdPlayerCallback);
  }

  @Override
  public void playAd() {
    if (DEBUG) {
      Log.d(TAG, "playAd");
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring playAd after release");
      return;
    }
    switch (imaAdState) {
      case IMA_AD_STATE_PLAYING:
        // IMA does not always call stopAd before resuming content.
        // See [Internal: b/38354028, b/63320878].
        Log.w(TAG, "Unexpected playAd without stopAd");
        break;
      case IMA_AD_STATE_NONE:
        // IMA is requesting to play the ad, so stop faking the content position.
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
        fakeContentProgressOffsetMs = C.TIME_UNSET;
        imaAdState = IMA_AD_STATE_PLAYING;
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onPlay();
        }
        if (shouldNotifyAdPrepareError) {
          shouldNotifyAdPrepareError = false;
          for (int i = 0; i < adCallbacks.size(); i++) {
            adCallbacks.get(i).onError();
          }
        }
        break;
      case IMA_AD_STATE_PAUSED:
        imaAdState = IMA_AD_STATE_PLAYING;
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onResume();
        }
        break;
      default:
        throw new IllegalStateException();
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected playAd while detached");
    } else if (!player.getPlayWhenReady()) {
      adsManager.pause();
    }
  }

  @Override
  public void stopAd() {
    if (DEBUG) {
      Log.d(TAG, "stopAd");
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring stopAd after release");
      return;
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected stopAd while detached");
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      Log.w(TAG, "Unexpected stopAd");
      return;
    }
    try {
      stopAdInternal();
    } catch (Exception e) {
      maybeNotifyInternalError("stopAd", e);
    }
  }

  @Override
  public void pauseAd() {
    if (DEBUG) {
      Log.d(TAG, "pauseAd");
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // This method is called after content is resumed.
      return;
    }
    imaAdState = IMA_AD_STATE_PAUSED;
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onPause();
    }
  }

  @Override
  public void resumeAd() {
    // This method is never called. See [Internal: b/18931719].
    maybeNotifyInternalError("resumeAd", new IllegalStateException("Unexpected call to resumeAd"));
  }

  // Player.EventListener implementation.

  @Override
  public void onTimelineChanged(
      Timeline timeline, @Nullable Object manifest, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    long contentDurationUs = timeline.getPeriod(0, period).durationUs;
    contentDurationMs = C.usToMs(contentDurationUs);
    if (contentDurationUs != C.TIME_UNSET) {
      adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
    }
    if (!initializedAdsManager && adsManager != null) {
      initializedAdsManager = true;
      initializeAdsManager();
    }
    onPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (adsManager == null) {
      return;
    }

    if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
      adsManager.pause();
      return;
    }

    if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
      adsManager.resume();
      return;
    }

    if (imaAdState == IMA_AD_STATE_NONE && playbackState == Player.STATE_BUFFERING
        && playWhenReady) {
      checkForContentComplete();
    } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onEnded();
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlayerStateChanged");
      }
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    if (imaAdState != IMA_AD_STATE_NONE) {
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError();
      }
    }
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    if (adsManager == null) {
      return;
    }
    if (!playingAd && !player.isPlayingAd()) {
      checkForContentComplete();
      if (sentContentComplete) {
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
          if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
          }
        }
        updateAdPlaybackState();
      } else if (!timeline.isEmpty()) {
        long positionMs = player.getCurrentPosition();
        timeline.getPeriod(0, period);
        int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
        if (newAdGroupIndex != C.INDEX_UNSET) {
          sentPendingContentPositionMs = false;
          pendingContentPositionMs = positionMs;
          if (newAdGroupIndex != adGroupIndex) {
            shouldNotifyAdPrepareError = false;
          }
        }
      }
    }
    updateImaStateForPlayerState();
  }

  // Internal methods.

  private void initializeAdsManager() {
    AdsRenderingSettings adsRenderingSettings = imaFactory.createAdsRenderingSettings();
    adsRenderingSettings.setEnablePreloading(ENABLE_PRELOADING);
    adsRenderingSettings.setMimeTypes(supportedMimeTypes);
    if (mediaLoadTimeoutMs != TIMEOUT_UNSET) {
      adsRenderingSettings.setLoadVideoTimeout(mediaLoadTimeoutMs);
    }
    if (mediaBitrate != BITRATE_UNSET) {
      adsRenderingSettings.setBitrateKbps(mediaBitrate / 1000);
    }
    adsRenderingSettings.setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable);
    if (adUiElements != null) {
      adsRenderingSettings.setUiElements(adUiElements);
    }

    // Skip ads based on the start position as required.
    long[] adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
    long contentPositionMs = player.getContentPosition();
    int adGroupIndexForPosition =
        adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    if (adGroupIndexForPosition > 0 && adGroupIndexForPosition != C.INDEX_UNSET) {
      // Skip any ad groups before the one at or immediately before the playback position.
      for (int i = 0; i < adGroupIndexForPosition; i++) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
      }
      // Play ads after the midpoint between the ad to play and the one before it, to avoid issues
      // with rounding one of the two ad times.
      long adGroupForPositionTimeUs = adGroupTimesUs[adGroupIndexForPosition];
      long adGroupBeforeTimeUs = adGroupTimesUs[adGroupIndexForPosition - 1];
      double midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforeTimeUs) / 2d;
      adsRenderingSettings.setPlayAdsAfterTime(midpointTimeUs / C.MICROS_PER_SECOND);
    }

    // IMA indexes any remaining midroll ad pods from 1. A preroll (if present) has index 0.
    // Store an index offset as we want to index all ads (including skipped ones) from 0.
    if (adGroupIndexForPosition == 0 && adGroupTimesUs[0] == 0) {
      // We are playing a preroll.
      podIndexOffset = 0;
    } else if (adGroupIndexForPosition == C.INDEX_UNSET) {
      // There's no ad to play which means there's no preroll.
      podIndexOffset = -1;
    } else {
      // We are playing a midroll and any ads before it were skipped.
      podIndexOffset = adGroupIndexForPosition - 1;
    }

    if (adGroupIndexForPosition != C.INDEX_UNSET && hasMidrollAdGroups(adGroupTimesUs)) {
      // Provide the player's initial position to trigger loading and playing the ad.
      pendingContentPositionMs = contentPositionMs;
    }

    adsManager.init(adsRenderingSettings);
    updateAdPlaybackState();
    if (DEBUG) {
      Log.d(TAG, "Initialized with ads rendering settings: " + adsRenderingSettings);
    }
  }

  private void handleAdEvent(AdEvent adEvent) {
    Ad ad = adEvent.getAd();
    switch (adEvent.getType()) {
      case LOADED:
        // The ad position is not always accurate when using preloading. See [Internal: b/62613240].
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        int podIndex = adPodInfo.getPodIndex();
        adGroupIndex =
            podIndex == -1 ? (adPlaybackState.adGroupCount - 1) : (podIndex + podIndexOffset);
        int adPosition = adPodInfo.getAdPosition();
        int adCount = adPodInfo.getTotalAds();
        adsManager.start();
        if (DEBUG) {
          Log.d(TAG, "Loaded ad " + adPosition + " of " + adCount + " in group " + adGroupIndex);
        }
        int oldAdCount = adPlaybackState.adGroups[adGroupIndex].count;
        if (adCount != oldAdCount) {
          if (oldAdCount == C.LENGTH_UNSET) {
            adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, adCount);
            updateAdPlaybackState();
          } else {
            // IMA sometimes unexpectedly decreases the ad count in an ad group.
            Log.w(TAG, "Unexpected ad count in LOADED, " + adCount + ", expected " + oldAdCount);
          }
        }
        if (adGroupIndex != expectedAdGroupIndex) {
          Log.w(
              TAG,
              "Expected ad group index "
                  + expectedAdGroupIndex
                  + ", actual ad group index "
                  + adGroupIndex);
          expectedAdGroupIndex = adGroupIndex;
        }
        break;
      case CONTENT_PAUSE_REQUESTED:
        // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
        // before sending CONTENT_RESUME_REQUESTED.
        imaPausedContent = true;
        pauseContentInternal();
        break;
      case TAPPED:
        if (eventListener != null) {
          eventListener.onAdTapped();
        }
        break;
      case CLICKED:
        if (eventListener != null) {
          eventListener.onAdClicked();
        }
        break;
      case CONTENT_RESUME_REQUESTED:
        imaPausedContent = false;
        resumeContentInternal();
        break;
      case LOG:
        Map<String, String> adData = adEvent.getAdData();
        String message = "AdEvent: " + adData;
        Log.i(TAG, message);
        if ("adLoadError".equals(adData.get("type"))) {
          handleAdGroupLoadError(new IOException(message));
        }
        break;
      case STARTED:
      case ALL_ADS_COMPLETED:
      default:
        break;
    }
  }

  private void updateImaStateForPlayerState() {
    boolean wasPlayingAd = playingAd;
    int oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup;
    playingAd = player.isPlayingAd();
    playingAdIndexInAdGroup = playingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
    boolean adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup;
    if (adFinished) {
      // IMA is waiting for the ad playback to finish so invoke the callback now.
      // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onEnded();
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity");
      }
    }
    if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
      int adGroupIndex = player.getCurrentAdGroupIndex();
      // IMA hasn't called playAd yet, so fake the content position.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
    }
  }

  private void resumeContentInternal() {
    if (imaAdState != IMA_AD_STATE_NONE) {
      imaAdState = IMA_AD_STATE_NONE;
      if (DEBUG) {
        Log.d(TAG, "Unexpected CONTENT_RESUME_REQUESTED without stopAd");
      }
    }
    if (adGroupIndex != C.INDEX_UNSET) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex);
      adGroupIndex = C.INDEX_UNSET;
      updateAdPlaybackState();
    }
  }

  private void pauseContentInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
  }

  private void stopAdInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    int adIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
    // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0);
    updateAdPlaybackState();
    if (!playingAd) {
      adGroupIndex = C.INDEX_UNSET;
    }
  }

  private void handleAdGroupLoadError(Exception error) {
    int adGroupIndex =
        this.adGroupIndex == C.INDEX_UNSET ? expectedAdGroupIndex : this.adGroupIndex;
    if (adGroupIndex == C.INDEX_UNSET) {
      // Drop the error, as we don't know which ad group it relates to.
      return;
    }
    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
    if (adGroup.count == C.LENGTH_UNSET) {
      adPlaybackState =
          adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.length));
      adGroup = adPlaybackState.adGroups[adGroupIndex];
    }
    for (int i = 0; i < adGroup.count; i++) {
      if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
        if (DEBUG) {
          Log.d(TAG, "Removing ad " + i + " in ad group " + adGroupIndex);
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i);
      }
    }
    updateAdPlaybackState();
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex);
    }
    pendingContentPositionMs = C.TIME_UNSET;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
  }

  private void handleAdPrepareError(int adGroupIndex, int adIndexInAdGroup, Exception exception) {
    if (DEBUG) {
      Log.d(
          TAG, "Prepare error for ad " + adIndexInAdGroup + " in group " + adGroupIndex, exception);
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring ad prepare error after release");
      return;
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // Send IMA a content position at the ad group so that it will try to play it, at which point
      // we can notify that it failed to load.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
      shouldNotifyAdPrepareError = true;
    } else {
      // We're already playing an ad.
      if (adIndexInAdGroup > playingAdIndexInAdGroup) {
        // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
        // which means that the ad after will load (if any).
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded();
        }
      }
      playingAdIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError();
      }
    }
    adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
    updateAdPlaybackState();
  }

  private void checkForContentComplete() {
    if (contentDurationMs != C.TIME_UNSET && pendingContentPositionMs == C.TIME_UNSET
        && player.getContentPosition() + END_OF_CONTENT_POSITION_THRESHOLD_MS >= contentDurationMs
        && !sentContentComplete) {
      adsLoader.contentComplete();
      if (DEBUG) {
        Log.d(TAG, "adsLoader.contentComplete");
      }
      sentContentComplete = true;
      // After sending content complete IMA will not poll the content position, so set the expected
      // ad group index.
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentDurationMs));
    }
  }

  private void updateAdPlaybackState() {
    // Ignore updates while detached. When a player is attached it will receive the latest state.
    if (eventListener != null) {
      eventListener.onAdPlaybackState(adPlaybackState);
    }
  }

  /**
   * Returns the next ad index in the specified ad group to load, or {@link C#INDEX_UNSET} if all
   * ads in the ad group have loaded.
   */
  private int getAdIndexInAdGroupToLoad(int adGroupIndex) {
    @AdState int[] states = adPlaybackState.adGroups[adGroupIndex].states;
    int adIndexInAdGroup = 0;
    // IMA loads ads in order.
    while (adIndexInAdGroup < states.length
        && states[adIndexInAdGroup] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
      adIndexInAdGroup++;
    }
    return adIndexInAdGroup == states.length ? C.INDEX_UNSET : adIndexInAdGroup;
  }

  private void maybeNotifyPendingAdLoadError() {
    if (pendingAdLoadError != null && eventListener != null) {
      eventListener.onAdLoadError(pendingAdLoadError, new DataSpec(adTagUri));
      pendingAdLoadError = null;
    }
  }

  private void maybeNotifyInternalError(String name, Exception cause) {
    String message = "Internal error in " + name;
    Log.e(TAG, message, cause);
    // We can't recover from an unexpected error in general, so skip all remaining ads.
    if (adPlaybackState == null) {
      adPlaybackState = AdPlaybackState.NONE;
    } else {
      for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
      }
    }
    updateAdPlaybackState();
    if (eventListener != null) {
      eventListener.onAdLoadError(
          AdLoadException.createForUnexpected(new RuntimeException(message, cause)),
          new DataSpec(adTagUri));
    }
  }

  private static long[] getAdGroupTimesUs(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      // If no cue points are specified, there is a preroll ad.
      return new long[] {0};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = (long) (C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return adGroupTimesUs;
  }

  private static boolean isAdGroupLoadError(AdError adError) {
    // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
    // a single ad, ad group or the whole timeline.
    return adError.getErrorCode() == AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
        || adError.getErrorCode() == AdErrorCode.UNKNOWN_ERROR;
  }

  private static boolean hasMidrollAdGroups(long[] adGroupTimesUs) {
    int count = adGroupTimesUs.length;
    if (count == 1) {
      return adGroupTimesUs[0] != 0 && adGroupTimesUs[0] != C.TIME_END_OF_SOURCE;
    } else if (count == 2) {
      return adGroupTimesUs[0] != 0 || adGroupTimesUs[1] != C.TIME_END_OF_SOURCE;
    } else {
      // There's at least one midroll ad group, as adGroupTimesUs is never empty.
      return true;
    }
  }

  /** Factory for objects provided by the IMA SDK. */
  @VisibleForTesting
  /* package */ interface ImaFactory {
    /** @see ImaSdkSettings */
    ImaSdkSettings createImaSdkSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRenderingSettings() */
    AdsRenderingSettings createAdsRenderingSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdDisplayContainer() */
    AdDisplayContainer createAdDisplayContainer();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRequest() */
    AdsRequest createAdsRequest();
    /** @see ImaSdkFactory#createAdsLoader(Context, ImaSdkSettings, AdDisplayContainer) */
    com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer);
  }

  /** Default {@link ImaFactory} for non-test usage, which delegates to {@link ImaSdkFactory}. */
  private static final class DefaultImaFactory implements ImaFactory {
    @Override
    public ImaSdkSettings createImaSdkSettings() {
      return ImaSdkFactory.getInstance().createImaSdkSettings();
    }

    @Override
    public AdsRenderingSettings createAdsRenderingSettings() {
      return ImaSdkFactory.getInstance().createAdsRenderingSettings();
    }

    @Override
    public AdDisplayContainer createAdDisplayContainer() {
      return ImaSdkFactory.getInstance().createAdDisplayContainer();
    }

    @Override
    public AdsRequest createAdsRequest() {
      return ImaSdkFactory.getInstance().createAdsRequest();
    }

    @Override
    public com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
      return ImaSdkFactory.getInstance()
          .createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }
  }
}
