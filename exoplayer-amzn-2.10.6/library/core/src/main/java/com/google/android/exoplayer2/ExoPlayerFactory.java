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

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;

/**
 * A factory for {@link ExoPlayer} instances.
 */
public final class ExoPlayerFactory {

  private static @Nullable BandwidthMeter singletonBandwidthMeter;

  private ExoPlayerFactory() {}

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   * @deprecated Use {@link #newSimpleInstance(Context, RenderersFactory, TrackSelector,
   *     LoadControl, DrmSessionManager)}.
   */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context).setExtensionRendererMode(extensionRendererMode);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   * @param allowedVideoJoiningTimeMs The maximum duration for which a video renderer can attempt to
   *     seamlessly join an ongoing playback.
   * @deprecated Use {@link #newSimpleInstance(Context, RenderersFactory, TrackSelector,
   *     LoadControl, DrmSessionManager)}.
   */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionRendererMode)
            .setAllowedVideoJoiningTimeMs(allowedVideoJoiningTimeMs);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context) {
    return newSimpleInstance(context, new DefaultTrackSelector());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
    return newSimpleInstance(context, new DefaultRenderersFactory(context), trackSelector);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context, RenderersFactory renderersFactory, TrackSelector trackSelector) {
    return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context, TrackSelector trackSelector, LoadControl loadControl) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance. Available extension renderers are not used.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(
        context, renderersFactory, trackSelector, new DefaultLoadControl(), drmSessionManager);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        /* drmSessionManager= */ null,
        Util.getLooper());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager, Util.getLooper());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        new AnalyticsCollector.Factory(),
        Util.getLooper());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector.Factory analyticsCollectorFactory) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        analyticsCollectorFactory,
        Util.getLooper());
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      Looper looper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        new AnalyticsCollector.Factory(),
        looper);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector.Factory analyticsCollectorFactory,
      Looper looper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        getDefaultBandwidthMeter(context),
        analyticsCollectorFactory,
        looper);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector.Factory analyticsCollectorFactory,
      Looper looper) {
    return new SimpleExoPlayer(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        analyticsCollectorFactory,
        looper);
  }

  /**
   * Creates an {@link ExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector) {
    return newInstance(context, renderers, trackSelector, new DefaultLoadControl());
  }

  /**
   * Creates an {@link ExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    return newInstance(context, renderers, trackSelector, loadControl, Util.getLooper());
  }

  /**
   * Creates an {@link ExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      Looper looper) {
    return newInstance(
        context, renderers, trackSelector, loadControl, getDefaultBandwidthMeter(context), looper);
  }

  /**
   * Creates an {@link ExoPlayer} instance.
   *
   * @param context A {@link Context}.
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  @SuppressWarnings("unused")
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      Looper looper) {
    return new ExoPlayerImpl(
        renderers, trackSelector, loadControl, bandwidthMeter, Clock.DEFAULT, looper);
  }

  private static synchronized BandwidthMeter getDefaultBandwidthMeter(Context context) {
    if (singletonBandwidthMeter == null) {
      singletonBandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
    }
    return singletonBandwidthMeter;
  }
}
