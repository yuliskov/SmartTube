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

import android.opengl.Matrix;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link FrameRotationQueue}. */
@RunWith(AndroidJUnit4.class)
public class FrameRotationQueueTest {

  private FrameRotationQueue frameRotationQueue;
  private float[] rotationMatrix;

  @Before
  public void setUp() throws Exception {
    frameRotationQueue = new FrameRotationQueue();
    rotationMatrix = new float[16];
  }

  @Test
  public void testGetRotationMatrixReturnsNull_whenEmpty() throws Exception {
    assertThat(frameRotationQueue.pollRotationMatrix(rotationMatrix, 0)).isFalse();
  }

  @Test
  public void testGetRotationMatrixReturnsNotNull_whenNotEmpty() throws Exception {
    frameRotationQueue.setRotation(0, new float[] {1, 2, 3});
    assertThat(frameRotationQueue.pollRotationMatrix(rotationMatrix, 0)).isTrue();
    assertThat(rotationMatrix).hasLength(16);
  }

  @Test
  public void testConvertsAngleAxisToRotationMatrix() throws Exception {
    doTestAngleAxisToRotationMatrix(/* angleRadian= */ 0, /* x= */ 1, /* y= */ 0, /* z= */ 0);
    frameRotationQueue.reset();
    doTestAngleAxisToRotationMatrix(/* angleRadian= */ 1, /* x= */ 1, /* y= */ 0, /* z= */ 0);
    frameRotationQueue.reset();
    doTestAngleAxisToRotationMatrix(/* angleRadian= */ 1, /* x= */ 0, /* y= */ 0, /* z= */ 1);
    // Don't reset frameRotationQueue as we use recenter matrix from previous calls.
    doTestAngleAxisToRotationMatrix(/* angleRadian= */ -1, /* x= */ 0, /* y= */ 1, /* z= */ 0);
    doTestAngleAxisToRotationMatrix(/* angleRadian= */ 1, /* x= */ 1, /* y= */ 1, /* z= */ 1);
  }

  @Test
  public void testRecentering_justYaw() throws Exception {
    float[] actualMatrix =
        getRotationMatrixFromAngleAxis(
            /* angleRadian= */ (float) Math.PI, /* x= */ 0, /* y= */ 1, /* z= */ 0);
    float[] expectedMatrix = new float[16];
    Matrix.setIdentityM(expectedMatrix, 0);
    assertEquals(actualMatrix, expectedMatrix);
  }

  @Test
  public void testRecentering_yawAndPitch() throws Exception {
    float[] matrix =
        getRotationMatrixFromAngleAxis(
            /* angleRadian= */ (float) Math.PI, /* x= */ 1, /* y= */ 1, /* z= */ 0);
    assertMultiplication(
        /* xr= */ 0, /* yr= */ 0, /* zr= */ 1, matrix, /* x= */ 0, /* y= */ 0, /* z= */ 1);
  }

  @Test
  public void testRecentering_yawAndPitch2() throws Exception {
    float[] matrix =
        getRotationMatrixFromAngleAxis(
            /* angleRadian= */ (float) Math.PI / 2, /* x= */ 1, /* y= */ 1, /* z= */ 0);
    float sqrt2 = (float) Math.sqrt(2);
    assertMultiplication(
        /* xr= */ sqrt2, /* yr= */ 0, /* zr= */ 0, matrix, /* x= */ 1, /* y= */ -1, /* z= */ 0);
  }

  @Test
  public void testRecentering_yawAndPitchAndRoll() throws Exception {
    float[] matrix =
        getRotationMatrixFromAngleAxis(
            /* angleRadian= */ (float) Math.PI * 2 / 3, /* x= */ 1, /* y= */ 1, /* z= */ 1);
    assertMultiplication(
        /* xr= */ 0, /* yr= */ 0, /* zr= */ 1, matrix, /* x= */ 0, /* y= */ 0, /* z= */ 1);
  }

  private void doTestAngleAxisToRotationMatrix(float angleRadian, int x, int y, int z) {
    float[] actualMatrix = getRotationMatrixFromAngleAxis(angleRadian, x, y, z);
    float[] expectedMatrix = createRotationMatrix(angleRadian, x, y, z);
    assertEquals(actualMatrix, expectedMatrix);
  }

  private float[] getRotationMatrixFromAngleAxis(float angleRadian, int x, int y, int z) {
    float length = Matrix.length(x, y, z);
    float factor = angleRadian / length;
    // Negate y and z to revert OpenGL coordinate system conversion.
    frameRotationQueue.setRotation(0, new float[] {x * factor, -y * factor, -z * factor});
    frameRotationQueue.pollRotationMatrix(rotationMatrix, 0);
    return rotationMatrix;
  }

  private static void assertMultiplication(
      float xr, float yr, float zr, float[] actualMatrix, float x, float y, float z) {
    float[] vector = new float[] {x, y, z, 0};
    float[] resultVec = new float[4];
    Matrix.multiplyMV(resultVec, 0, actualMatrix, 0, vector, 0);
    assertEquals(resultVec, new float[] {xr, yr, zr, 0});
  }

  private static float[] createRotationMatrix(float angleRadian, int x, int y, int z) {
    float[] expectedMatrix = new float[16];
    Matrix.setRotateM(expectedMatrix, 0, (float) Math.toDegrees(angleRadian), x, y, z);
    return expectedMatrix;
  }

  private static void assertEquals(float[] actual, float[] expected) {
    assertThat(actual).usingTolerance(1.0e-5).containsExactly(expected).inOrder();
  }
}
