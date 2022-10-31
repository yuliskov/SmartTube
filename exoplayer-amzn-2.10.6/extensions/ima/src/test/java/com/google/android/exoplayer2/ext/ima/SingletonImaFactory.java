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

import android.content.Context;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;

/** {@link ImaAdsLoader.ImaFactory} that returns provided instances from each getter, for tests. */
final class SingletonImaFactory implements ImaAdsLoader.ImaFactory {

  private final ImaSdkSettings imaSdkSettings;
  private final AdsRenderingSettings adsRenderingSettings;
  private final AdDisplayContainer adDisplayContainer;
  private final AdsRequest adsRequest;
  private final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;

  public SingletonImaFactory(
      ImaSdkSettings imaSdkSettings,
      AdsRenderingSettings adsRenderingSettings,
      AdDisplayContainer adDisplayContainer,
      AdsRequest adsRequest,
      com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader) {
    this.imaSdkSettings = imaSdkSettings;
    this.adsRenderingSettings = adsRenderingSettings;
    this.adDisplayContainer = adDisplayContainer;
    this.adsRequest = adsRequest;
    this.adsLoader = adsLoader;
  }

  @Override
  public ImaSdkSettings createImaSdkSettings() {
    return imaSdkSettings;
  }

  @Override
  public AdsRenderingSettings createAdsRenderingSettings() {
    return adsRenderingSettings;
  }

  @Override
  public AdDisplayContainer createAdDisplayContainer() {
    return adDisplayContainer;
  }

  @Override
  public AdsRequest createAdsRequest() {
    return adsRequest;
  }

  @Override
  public AdsLoader createAdsLoader(
      Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
    return adsLoader;
  }
}
