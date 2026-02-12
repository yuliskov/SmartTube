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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.DefaultLoadControl.Builder;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultLoadControl}. */
@RunWith(AndroidJUnit4.class)
public class DefaultLoadControlTest {

  private static final float SPEED = 1f;
  private static final long MIN_BUFFER_US = C.msToUs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS);
  private static final long MAX_BUFFER_US = C.msToUs(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS);
  private static final int TARGET_BUFFER_BYTES = C.DEFAULT_BUFFER_SEGMENT_SIZE * 2;

  private Builder builder;
  private DefaultAllocator allocator;
  private DefaultLoadControl loadControl;

  @Before
  public void setUp() throws Exception {
    builder = new Builder();
    allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
  }

  @Test
  public void testShouldContinueLoading_untilMaxBufferExceeded() {
    createDefaultLoadControl();
    assertThat(loadControl.shouldContinueLoading(/* bufferedDurationUs= */ 0, SPEED)).isTrue();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, SPEED)).isTrue();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US - 1, SPEED)).isTrue();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US, SPEED)).isFalse();
  }

  @Test
  public void testShouldNotContinueLoadingOnceBufferingStopped_untilBelowMinBuffer() {
    createDefaultLoadControl();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US - 1, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US - 1, SPEED)).isTrue();
  }

  @Test
  public void testShouldContinueLoadingWithTargetBufferBytesReached_untilMinBufferReached() {
    createDefaultLoadControl();
    makeSureTargetBufferBytesReached();

    assertThat(loadControl.shouldContinueLoading(/* bufferedDurationUs= */ 0, SPEED)).isTrue();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US - 1, SPEED)).isTrue();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US, SPEED)).isFalse();
  }

  @Test
  public void testShouldNeverContinueLoading_ifMaxBufferReachedAndNotPrioritizeTimeOverSize() {
    builder.setPrioritizeTimeOverSizeThresholds(false);
    createDefaultLoadControl();
    // Put loadControl in buffering state.
    assertThat(loadControl.shouldContinueLoading(/* bufferedDurationUs= */ 0, SPEED)).isTrue();
    makeSureTargetBufferBytesReached();

    assertThat(loadControl.shouldContinueLoading(/* bufferedDurationUs= */ 0, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, SPEED)).isFalse();
    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US, SPEED)).isFalse();
  }

  @Test
  public void testShouldContinueLoadingWithMinBufferReached_inFastPlayback() {
    createDefaultLoadControl();

    // At normal playback speed, we stop buffering when the buffer reaches the minimum.
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, SPEED)).isFalse();

    // At double playback speed, we continue loading.
    assertThat(loadControl.shouldContinueLoading(MIN_BUFFER_US, /* playbackSpeed= */ 2f)).isTrue();
  }

  @Test
  public void testShouldNotContinueLoadingWithMaxBufferReached_inFastPlayback() {
    createDefaultLoadControl();

    assertThat(loadControl.shouldContinueLoading(MAX_BUFFER_US, /* playbackSpeed= */ 100f))
        .isFalse();
  }

  @Test
  public void testStartsPlayback_whenMinBufferSizeReached() {
    createDefaultLoadControl();
    assertThat(loadControl.shouldStartPlayback(MIN_BUFFER_US, SPEED, /* rebuffering= */ false))
        .isTrue();
  }

  private void createDefaultLoadControl() {
    builder.setAllocator(allocator);
    builder.setTargetBufferBytes(TARGET_BUFFER_BYTES);
    loadControl = builder.createDefaultLoadControl();
    loadControl.onTracksSelected(new Renderer[0], null, null);
  }

  private void makeSureTargetBufferBytesReached() {
    while (allocator.getTotalBytesAllocated() < TARGET_BUFFER_BYTES) {
      allocator.allocate();
    }
  }

}
