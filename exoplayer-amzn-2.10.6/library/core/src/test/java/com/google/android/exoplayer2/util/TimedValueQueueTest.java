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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TimedValueQueue}. */
@RunWith(AndroidJUnit4.class)
public class TimedValueQueueTest {

  private TimedValueQueue<String> queue;

  @Before
  public void setUp() throws Exception {
    queue = new TimedValueQueue<>();
  }

  @Test
  public void testAddAndPollValues() {
    queue.add(0, "a");
    queue.add(1, "b");
    queue.add(2, "c");
    assertThat(queue.poll(0)).isEqualTo("a");
    assertThat(queue.poll(1)).isEqualTo("b");
    assertThat(queue.poll(2)).isEqualTo("c");
  }

  @Test
  public void testBufferCapacityIncreasesAutomatically() {
    queue = new TimedValueQueue<>(1);
    for (int i = 0; i < 20; i++) {
      queue.add(i, "" + i);
      if ((i & 1) == 1) {
        assertThat(queue.poll(0)).isEqualTo("" + (i / 2));
      }
    }
    assertThat(queue.size()).isEqualTo(10);
  }

  @Test
  public void testTimeDiscontinuityClearsValues() {
    queue.add(1, "b");
    queue.add(2, "c");
    queue.add(0, "a");
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.poll(0)).isEqualTo("a");
  }

  @Test
  public void testTimeDiscontinuityOnFullBufferClearsValues() {
    queue = new TimedValueQueue<>(2);
    queue.add(1, "b");
    queue.add(3, "c");
    queue.add(2, "a");
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.poll(2)).isEqualTo("a");
  }

  @Test
  public void testPollReturnsClosestValue() {
    queue.add(0, "a");
    queue.add(3, "b");
    assertThat(queue.poll(2)).isEqualTo("b");
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  public void testPollRemovesPreviousValues() {
    queue.add(0, "a");
    queue.add(1, "b");
    queue.add(2, "c");
    assertThat(queue.poll(1)).isEqualTo("b");
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  public void testPollFloorReturnsClosestPreviousValue() {
    queue.add(0, "a");
    queue.add(3, "b");
    assertThat(queue.pollFloor(2)).isEqualTo("a");
    assertThat(queue.pollFloor(2)).isEqualTo(null);
    assertThat(queue.pollFloor(3)).isEqualTo("b");
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  public void testPollFloorRemovesPreviousValues() {
    queue.add(0, "a");
    queue.add(1, "b");
    queue.add(2, "c");
    assertThat(queue.pollFloor(1)).isEqualTo("b");
    assertThat(queue.size()).isEqualTo(1);
  }
}
