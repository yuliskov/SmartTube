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

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import java.util.List;
import java.util.Map;

/** Fake {@link AdsRequest} implementation for tests. */
public final class FakeAdsRequest implements AdsRequest {

  private String adTagUrl;
  private String adsResponse;
  private Object userRequestContext;
  private AdDisplayContainer adDisplayContainer;
  private ContentProgressProvider contentProgressProvider;

  @Override
  public void setAdTagUrl(String adTagUrl) {
    this.adTagUrl = adTagUrl;
  }

  @Override
  public String getAdTagUrl() {
    return adTagUrl;
  }

  @Override
  public void setExtraParameter(String s, String s1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getExtraParameter(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> getExtraParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setUserRequestContext(Object userRequestContext) {
    this.userRequestContext = userRequestContext;
  }

  @Override
  public Object getUserRequestContext() {
    return userRequestContext;
  }

  @Override
  public AdDisplayContainer getAdDisplayContainer() {
    return adDisplayContainer;
  }

  @Override
  public void setAdDisplayContainer(AdDisplayContainer adDisplayContainer) {
    this.adDisplayContainer = adDisplayContainer;
  }

  @Override
  public ContentProgressProvider getContentProgressProvider() {
    return contentProgressProvider;
  }

  @Override
  public void setContentProgressProvider(ContentProgressProvider contentProgressProvider) {
    this.contentProgressProvider = contentProgressProvider;
  }

  @Override
  public String getAdsResponse() {
    return adsResponse;
  }

  @Override
  public void setAdsResponse(String adsResponse) {
    this.adsResponse = adsResponse;
  }

  @Override
  public void setAdWillAutoPlay(boolean b) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAdWillPlayMuted(boolean b) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContentDuration(float v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContentKeywords(List<String> list) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContentTitle(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVastLoadTimeout(float v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLiveStreamPrefetchSeconds(float v) {
    throw new UnsupportedOperationException();
  }
}
