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

import android.graphics.PointF;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CanvasRenderer}. */
@RunWith(AndroidJUnit4.class)
public class CanvasRendererTest {

  private static final float JUST_BELOW_45_DEGREES = (float) (Math.PI / 4 - 1.0E-08);
  private static final float JUST_ABOVE_45_DEGREES = (float) (Math.PI / 4 + 1.0E-08);
  private static final float TOLERANCE = .00001f;

  @Test
  public void testClicksOnCanvas() {
    assertClick(translateClick(JUST_BELOW_45_DEGREES, JUST_BELOW_45_DEGREES), 0, 0);
    assertClick(translateClick(JUST_BELOW_45_DEGREES, -JUST_BELOW_45_DEGREES), 0, 100);
    assertClick(translateClick(0, 0), 50, 50);
    assertClick(translateClick(-JUST_BELOW_45_DEGREES, JUST_BELOW_45_DEGREES), 100, 0);
    assertClick(translateClick(-JUST_BELOW_45_DEGREES, -JUST_BELOW_45_DEGREES), 100, 100);
  }

  @Test
  public void testClicksNotOnCanvas() {
    assertThat(translateClick(JUST_ABOVE_45_DEGREES, JUST_ABOVE_45_DEGREES)).isNull();
    assertThat(translateClick(JUST_ABOVE_45_DEGREES, -JUST_ABOVE_45_DEGREES)).isNull();
    assertThat(translateClick(-JUST_ABOVE_45_DEGREES, JUST_ABOVE_45_DEGREES)).isNull();
    assertThat(translateClick(-JUST_ABOVE_45_DEGREES, -JUST_ABOVE_45_DEGREES)).isNull();
    assertThat(translateClick((float) (Math.PI / 2), 0)).isNull();
    assertThat(translateClick(0, (float) Math.PI)).isNull();
  }

  private static PointF translateClick(float yaw, float pitch) {
    return CanvasRenderer.internalTranslateClick(
        yaw,
        pitch,
        /* xUnit= */ -1,
        /* yUnit= */ -1,
        /* widthUnit= */ 2,
        /* heightUnit= */ 2,
        /* widthPixel= */ 100,
        /* heightPixel= */ 100);
  }

  private static void assertClick(@Nullable PointF actual, float expectedX, float expectedY) {
    assertThat(actual).isNotNull();
    assertThat(actual.x).isWithin(TOLERANCE).of(expectedX);
    assertThat(actual.y).isWithin(TOLERANCE).of(expectedY);
  }
}
