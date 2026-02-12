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

import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;

/** Fake {@link com.google.ads.interactivemedia.v3.api.AdsLoader} implementation for tests. */
public final class FakeAdsLoader implements com.google.ads.interactivemedia.v3.api.AdsLoader {

  private final ImaSdkSettings imaSdkSettings;
  private final AdsManager adsManager;
  private final ArrayList<AdsLoadedListener> adsLoadedListeners;
  private final ArrayList<AdErrorListener> adErrorListeners;

  public FakeAdsLoader(ImaSdkSettings imaSdkSettings, AdsManager adsManager) {
    this.imaSdkSettings = Assertions.checkNotNull(imaSdkSettings);
    this.adsManager = Assertions.checkNotNull(adsManager);
    adsLoadedListeners = new ArrayList<>();
    adErrorListeners = new ArrayList<>();
  }

  @Override
  public void contentComplete() {
    // Do nothing.
  }

  @Override
  public ImaSdkSettings getSettings() {
    return imaSdkSettings;
  }

  @Override
  public void requestAds(AdsRequest adsRequest) {
    for (AdsLoadedListener listener : adsLoadedListeners) {
      listener.onAdsManagerLoaded(
          new AdsManagerLoadedEvent() {
            @Override
            public AdsManager getAdsManager() {
              return adsManager;
            }

            @Override
            public StreamManager getStreamManager() {
              throw new UnsupportedOperationException();
            }

            @Override
            public Object getUserRequestContext() {
              return adsRequest.getUserRequestContext();
            }
          });
    }
  }

  @Override
  public String requestStream(StreamRequest streamRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addAdsLoadedListener(AdsLoadedListener adsLoadedListener) {
    adsLoadedListeners.add(adsLoadedListener);
  }

  @Override
  public void removeAdsLoadedListener(AdsLoadedListener adsLoadedListener) {
    adsLoadedListeners.remove(adsLoadedListener);
  }

  @Override
  public void addAdErrorListener(AdErrorListener adErrorListener) {
    adErrorListeners.add(adErrorListener);
  }

  @Override
  public void removeAdErrorListener(AdErrorListener adErrorListener) {
    adErrorListeners.remove(adErrorListener);
  }
}
