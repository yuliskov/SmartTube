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

import android.opengl.Matrix;
import com.google.android.exoplayer2.util.TimedValueQueue;

/**
 * This class serves multiple purposes:
 *
 * <ul>
 *   <li>Queues the rotation metadata extracted from camera motion track.
 *   <li>Converts the metadata to rotation matrices in OpenGl coordinate system.
 *   <li>Recenters the rotations to componsate the yaw of the initial rotation.
 * </ul>
 */
public final class FrameRotationQueue {
  private final float[] recenterMatrix;
  private final float[] rotationMatrix;
  private final TimedValueQueue<float[]> rotations;
  private boolean recenterMatrixComputed;

  public FrameRotationQueue() {
    recenterMatrix = new float[16];
    rotationMatrix = new float[16];
    rotations = new TimedValueQueue<>();
  }

  /**
   * Sets a rotation for a given timestamp.
   *
   * @param timestampUs Timestamp of the rotation.
   * @param angleAxis Angle axis orientation in radians representing the rotation from camera
   *     coordinate system to world coordinate system.
   */
  public void setRotation(long timestampUs, float[] angleAxis) {
    rotations.add(timestampUs, angleAxis);
  }

  /** Removes all of the rotations and forces rotations to be recentered. */
  public void reset() {
    rotations.clear();
    recenterMatrixComputed = false;
  }

  /**
   * Copies the rotation matrix with the greatest timestamp which is less than or equal to the given
   * timestamp to {@code matrix}. Removes all older rotations and the returned one from the queue.
   * Does nothing if there is no such rotation.
   *
   * @param matrix The rotation matrix.
   * @param timestampUs The time in microseconds to query the rotation.
   * @return Whether a rotation matrix is copied to {@code matrix}.
   */
  public boolean pollRotationMatrix(float[] matrix, long timestampUs) {
    float[] rotation = rotations.pollFloor(timestampUs);
    if (rotation == null) {
      return false;
    }
    // TODO [Internal: b/113315546]: Slerp between the floor and ceil rotation.
    getRotationMatrixFromAngleAxis(rotationMatrix, rotation);
    if (!recenterMatrixComputed) {
      computeRecenterMatrix(recenterMatrix, rotationMatrix);
      recenterMatrixComputed = true;
    }
    Matrix.multiplyMM(matrix, 0, recenterMatrix, 0, rotationMatrix, 0);
    return true;
  }

  /**
   * Computes a recentering matrix from the given angle-axis rotation only accounting for yaw. Roll
   * and tilt will not be compensated.
   *
   * @param recenterMatrix The recenter matrix.
   * @param rotationMatrix The rotation matrix.
   */
  public static void computeRecenterMatrix(float[] recenterMatrix, float[] rotationMatrix) {
    // The re-centering matrix is computed as follows:
    // recenter.row(2) = temp.col(2).transpose();
    // recenter.row(0) = recenter.row(1).cross(recenter.row(2)).normalized();
    // recenter.row(2) = recenter.row(0).cross(recenter.row(1)).normalized();
    //             | temp[10]  0   -temp[8]    0|
    //             | 0         1    0          0|
    // recenter =  | temp[8]   0    temp[10]   0|
    //             | 0         0    0          1|
    Matrix.setIdentityM(recenterMatrix, 0);
    float normRowSqr =
        rotationMatrix[10] * rotationMatrix[10] + rotationMatrix[8] * rotationMatrix[8];
    float normRow = (float) Math.sqrt(normRowSqr);
    recenterMatrix[0] = rotationMatrix[10] / normRow;
    recenterMatrix[2] = rotationMatrix[8] / normRow;
    recenterMatrix[8] = -rotationMatrix[8] / normRow;
    recenterMatrix[10] = rotationMatrix[10] / normRow;
  }

  private static void getRotationMatrixFromAngleAxis(float[] matrix, float[] angleAxis) {
    // Convert coordinates to OpenGL coordinates.
    // CAMM motion metadata: +x right, +y down, and +z forward.
    // OpenGL: +x right, +y up, -z forwards
    float x = angleAxis[0];
    float y = -angleAxis[1];
    float z = -angleAxis[2];
    float angleRad = Matrix.length(x, y, z);
    if (angleRad != 0) {
      float angleDeg = (float) Math.toDegrees(angleRad);
      Matrix.setRotateM(matrix, 0, angleDeg, x / angleRad, y / angleRad, z / angleRad);
    } else {
      Matrix.setIdentityM(matrix, 0);
    }
  }
}
