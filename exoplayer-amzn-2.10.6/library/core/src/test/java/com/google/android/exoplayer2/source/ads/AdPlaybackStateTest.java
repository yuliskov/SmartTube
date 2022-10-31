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
package com.google.android.exoplayer2.source.ads;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link AdPlaybackState}. */
@RunWith(AndroidJUnit4.class)
public final class AdPlaybackStateTest {

  private static final long[] TEST_AD_GROUP_TMES_US = new long[] {0, C.msToUs(10_000)};
  private static final Uri TEST_URI = Uri.EMPTY;

  private AdPlaybackState state;

  @Before
  public void setUp() {
    state = new AdPlaybackState(TEST_AD_GROUP_TMES_US);
  }

  @Test
  public void testSetAdCount() {
    assertThat(state.adGroups[0].count).isEqualTo(C.LENGTH_UNSET);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    assertThat(state.adGroups[0].count).isEqualTo(1);
  }

  @Test
  public void testSetAdUriBeforeAdCount() {
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    assertThat(state.adGroups[0].uris[0]).isNull();
    assertThat(state.adGroups[0].states[0]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].uris[1]).isSameAs(TEST_URI);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void testSetAdErrorBeforeAdCount() {
    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    assertThat(state.adGroups[0].uris[0]).isNull();
    assertThat(state.adGroups[0].states[0]).isEqualTo(AdPlaybackState.AD_STATE_ERROR);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
  }

  @Test
  public void testGetFirstAdIndexToPlayIsZero() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void testGetFirstAdIndexToPlaySkipsPlayedAd() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void testGetFirstAdIndexToPlaySkipsSkippedAd() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withSkippedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void testGetFirstAdIndexToPlaySkipsErrorAds() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(2);
  }

  @Test
  public void testGetNextAdIndexToPlaySkipsErrorAds() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1, TEST_URI);

    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);

    assertThat(state.adGroups[0].getNextAdIndexToPlay(0)).isEqualTo(2);
  }

  @Test
  public void testSetAdStateTwiceThrows() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    try {
      state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void testSkipAllWithoutAdCount() {
    state = state.withSkippedAdGroup(0);
    state = state.withSkippedAdGroup(1);
    assertThat(state.adGroups[0].count).isEqualTo(0);
    assertThat(state.adGroups[1].count).isEqualTo(0);
  }
}
