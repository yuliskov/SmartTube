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

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.CompanionAd;
import com.google.ads.interactivemedia.v3.api.UiElement;
import java.util.List;
import java.util.Set;

/** A fake ad for testing. */
/* package */ final class FakeAd implements Ad {

  private final boolean skippable;
  private final AdPodInfo adPodInfo;

  public FakeAd(boolean skippable, int podIndex, int totalAds, int adPosition) {
    this.skippable = skippable;
    adPodInfo =
        new AdPodInfo() {
          @Override
          public int getTotalAds() {
            return totalAds;
          }

          @Override
          public int getAdPosition() {
            return adPosition;
          }

          @Override
          public int getPodIndex() {
            return podIndex;
          }

          @Override
          public boolean isBumper() {
            throw new UnsupportedOperationException();
          }

          @Override
          public double getMaxDuration() {
            throw new UnsupportedOperationException();
          }

          @Override
          public double getTimeOffset() {
            throw new UnsupportedOperationException();
          }
        };
  }

  @Override
  public int getVastMediaWidth() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getVastMediaHeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getVastMediaBitrate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSkippable() {
    return skippable;
  }

  @Override
  public AdPodInfo getAdPodInfo() {
    return adPodInfo;
  }

  @Override
  public String getAdId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCreativeId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCreativeAdId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getUniversalAdIdValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getUniversalAdIdRegistry() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAdSystem() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getAdWrapperIds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getAdWrapperSystems() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getAdWrapperCreativeIds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isLinear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getSkipTimeOffset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isUiDisabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDescription() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getTitle() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getContentType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAdvertiserName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSurveyUrl() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDealId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getWidth() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getHeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getTraffickingParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getDuration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<UiElement> getUiElements() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<CompanionAd> getCompanionAds() {
    throw new UnsupportedOperationException();
  }
}
