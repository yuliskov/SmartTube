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
package com.google.android.exoplayer2.ui.spherical;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.MotionEvent;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TouchTracker}. */
@RunWith(AndroidJUnit4.class)
public class TouchTrackerTest {
  private static final float EPSILON = 0.00001f;
  private static final int SWIPE_PX = 100;
  private static final float PX_PER_DEGREES = 25;

  private TouchTracker tracker;
  private float yaw;
  private float pitch;
  private float[] dummyMatrix;

  private static void swipe(TouchTracker tracker, float x0, float y0, float x1, float y1) {
    tracker.onTouch(null, MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x0, y0, 0));
    tracker.onTouch(null, MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, x1, y1, 0));
    tracker.onTouch(null, MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, x1, y1, 0));
  }

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    tracker =
        new TouchTracker(
            context,
            scrollOffsetDegrees -> {
              pitch = scrollOffsetDegrees.y;
              yaw = scrollOffsetDegrees.x;
            },
            PX_PER_DEGREES);
    dummyMatrix = new float[16];
    tracker.onOrientationChange(dummyMatrix, 0);
  }

  @Test
  public void testTap() {
    // Tap is a noop.
    swipe(tracker, 0, 0, 0, 0);
    assertThat(yaw).isWithin(EPSILON).of(0);
    assertThat(pitch).isWithin(EPSILON).of(0);
  }

  @Test
  public void testBasicYaw() {
    swipe(tracker, 0, 0, SWIPE_PX, 0);
    assertThat(yaw).isWithin(EPSILON).of(-SWIPE_PX / PX_PER_DEGREES);
    assertThat(pitch).isWithin(EPSILON).of(0);
  }

  @Test
  public void testBigYaw() {
    swipe(tracker, 0, 0, -10 * SWIPE_PX, 0);
    assertThat(yaw).isEqualTo(10 * SWIPE_PX / PX_PER_DEGREES);
    assertThat(pitch).isWithin(EPSILON).of(0);
  }

  @Test
  public void testYawUnaffectedByPitch() {
    swipe(tracker, 0, 0, 0, SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(0);

    swipe(tracker, 0, 0, SWIPE_PX, SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(-SWIPE_PX / PX_PER_DEGREES);
  }

  @Test
  public void testBasicPitch() {
    swipe(tracker, 0, 0, 0, SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(0);
    assertThat(pitch).isWithin(EPSILON).of(SWIPE_PX / PX_PER_DEGREES);
  }

  @Test
  public void testPitchClipped() {
    // Big reverse pitch should be clipped.
    swipe(tracker, 0, 0, 0, -20 * SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(0);
    assertThat(pitch).isEqualTo(-TouchTracker.MAX_PITCH_DEGREES);

    // Big forward pitch should be clipped.
    swipe(tracker, 0, 0, 0, 50 * SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(0);
    assertThat(pitch).isEqualTo(TouchTracker.MAX_PITCH_DEGREES);
  }

  @Test
  public void testWithRoll90() {
    tracker.onOrientationChange(dummyMatrix, (float) Math.toRadians(90));

    // Y-axis should now control yaw.
    swipe(tracker, 0, 0, 0, 2 * SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(-2 * SWIPE_PX / PX_PER_DEGREES);

    // X-axis should now control reverse pitch.
    swipe(tracker, 0, 0, -3 * SWIPE_PX, 0);
    assertThat(pitch).isWithin(EPSILON).of(3 * SWIPE_PX / PX_PER_DEGREES);
  }

  @Test
  public void testWithRoll180() {
    tracker.onOrientationChange(dummyMatrix, (float) Math.toRadians(180));

    // X-axis should now control reverse yaw.
    swipe(tracker, 0, 0, -2 * SWIPE_PX, 0);
    assertThat(yaw).isWithin(EPSILON).of(-2 * SWIPE_PX / PX_PER_DEGREES);

    // Y-axis should now control reverse pitch.
    swipe(tracker, 0, 0, 0, -3 * SWIPE_PX);
    assertThat(pitch).isWithin(EPSILON).of(3 * SWIPE_PX / PX_PER_DEGREES);
  }

  @Test
  public void testWithRoll270() {
    tracker.onOrientationChange(dummyMatrix, (float) Math.toRadians(270));

    // Y-axis should now control reverse yaw.
    swipe(tracker, 0, 0, 0, -2 * SWIPE_PX);
    assertThat(yaw).isWithin(EPSILON).of(-2 * SWIPE_PX / PX_PER_DEGREES);

    // X-axis should now control pitch.
    swipe(tracker, 0, 0, 3 * SWIPE_PX, 0);
    assertThat(pitch).isWithin(EPSILON).of(3 * SWIPE_PX / PX_PER_DEGREES);
  }
}
