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
package com.google.android.exoplayer2.video.spherical;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Projection}. */
@RunWith(AndroidJUnit4.class)
public class ProjectionTest {
  private static final float EPSILON = .00001f;

  // Default 360 sphere.
  private static final float RADIUS = 1;
  private static final int LATITUDES = 12;
  private static final int LONGITUDES = 24;
  private static final float VERTICAL_FOV_DEGREES = 180;
  private static final float HORIZONTAL_FOV_DEGREES = 360;

  @Test
  public void testSphericalMesh() throws Exception {
    // Only the first param is important in this test.
    Projection projection =
        Projection.createEquirectangular(
            RADIUS,
            LATITUDES,
            LONGITUDES,
            VERTICAL_FOV_DEGREES,
            HORIZONTAL_FOV_DEGREES,
            C.STEREO_MODE_MONO);

    Projection.SubMesh subMesh = projection.leftMesh.getSubMesh(0);
    assertThat(subMesh.getVertexCount()).isGreaterThan(LATITUDES * LONGITUDES);

    float[] data = subMesh.vertices;
    for (int i = 0; i < data.length; ) {
      float x = data[i++];
      float y = data[i++];
      float z = data[i++];
      assertEquals(RADIUS, Math.sqrt(x * x + y * y + z * z), EPSILON);
    }
  }

  @Test
  public void testArgumentValidation() {
    checkIllegalArgumentException(0, 1, 1, 1, 1);
    checkIllegalArgumentException(1, 0, 1, 1, 1);
    checkIllegalArgumentException(1, 1, 0, 1, 1);
    checkIllegalArgumentException(1, 1, 1, 0, 1);
    checkIllegalArgumentException(1, 1, 1, 181, 1);
    checkIllegalArgumentException(1, 1, 1, 1, 0);
    checkIllegalArgumentException(1, 1, 1, 1, 361);
  }

  private void checkIllegalArgumentException(
      float radius,
      int latitudes,
      int longitudes,
      float verticalFovDegrees,
      float horizontalFovDegrees) {
    try {
      Projection.createEquirectangular(
          radius,
          latitudes,
          longitudes,
          verticalFovDegrees,
          horizontalFovDegrees,
          C.STEREO_MODE_MONO);
      fail();
    } catch (IllegalArgumentException e) {
      // Do nothing. Expected.
    }
  }
}
