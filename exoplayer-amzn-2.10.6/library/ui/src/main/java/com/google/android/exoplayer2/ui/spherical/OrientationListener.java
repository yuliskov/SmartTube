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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import androidx.annotation.BinderThread;
import android.view.Display;
import android.view.Surface;
import com.google.android.exoplayer2.video.spherical.FrameRotationQueue;

/**
 * Listens for orientation sensor events, converts event data to rotation matrix and roll value, and
 * notifies its own listeners.
 */
/* package */ final class OrientationListener implements SensorEventListener {
  /** A listener for orientation changes. */
  public interface Listener {
    /**
     * Called on device orientation change.
     *
     * @param deviceOrientationMatrix A 4x4 matrix defining device orientation.
     * @param deviceRoll Device roll value, in radians. The range of values is -&pi;/2 to &pi;/2.
     */
    void onOrientationChange(float[] deviceOrientationMatrix, float deviceRoll);
  }

  private final float[] deviceOrientationMatrix4x4 = new float[16];
  private final float[] tempMatrix4x4 = new float[16];
  private final float[] recenterMatrix4x4 = new float[16];
  private final float[] angles = new float[3];
  private final Display display;
  private final Listener[] listeners;
  private boolean recenterMatrixComputed;

  public OrientationListener(Display display, Listener... listeners) {
    this.display = display;
    this.listeners = listeners;
  }

  @Override
  @BinderThread
  public void onSensorChanged(SensorEvent event) {
    SensorManager.getRotationMatrixFromVector(deviceOrientationMatrix4x4, event.values);
    rotateAroundZ(deviceOrientationMatrix4x4, display.getRotation());
    float roll = extractRoll(deviceOrientationMatrix4x4);
    // Rotation vector sensor assumes Y is parallel to the ground.
    rotateYtoSky(deviceOrientationMatrix4x4);
    recenter(deviceOrientationMatrix4x4);
    notifyListeners(deviceOrientationMatrix4x4, roll);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Do nothing.
  }

  private void notifyListeners(float[] deviceOrientationMatrix, float roll) {
    for (Listener listener : listeners) {
      listener.onOrientationChange(deviceOrientationMatrix, roll);
    }
  }

  private void recenter(float[] matrix) {
    if (!recenterMatrixComputed) {
      FrameRotationQueue.computeRecenterMatrix(recenterMatrix4x4, matrix);
      recenterMatrixComputed = true;
    }
    System.arraycopy(matrix, 0, tempMatrix4x4, 0, tempMatrix4x4.length);
    Matrix.multiplyMM(matrix, 0, tempMatrix4x4, 0, recenterMatrix4x4, 0);
  }

  private float extractRoll(float[] matrix) {
    // Remapping is required since we need the calculated roll of the phone to be independent of the
    // phone's pitch & yaw.
    SensorManager.remapCoordinateSystem(
        matrix, SensorManager.AXIS_X, SensorManager.AXIS_MINUS_Z, tempMatrix4x4);
    SensorManager.getOrientation(tempMatrix4x4, angles);
    return angles[2];
  }

  private void rotateAroundZ(float[] matrix, int rotation) {
    int xAxis;
    int yAxis;
    switch (rotation) {
      case Surface.ROTATION_270:
        xAxis = SensorManager.AXIS_MINUS_Y;
        yAxis = SensorManager.AXIS_X;
        break;
      case Surface.ROTATION_180:
        xAxis = SensorManager.AXIS_MINUS_X;
        yAxis = SensorManager.AXIS_MINUS_Y;
        break;
      case Surface.ROTATION_90:
        xAxis = SensorManager.AXIS_Y;
        yAxis = SensorManager.AXIS_MINUS_X;
        break;
      case Surface.ROTATION_0:
        return;
      default:
        throw new IllegalStateException();
    }
    System.arraycopy(matrix, 0, tempMatrix4x4, 0, tempMatrix4x4.length);
    SensorManager.remapCoordinateSystem(tempMatrix4x4, xAxis, yAxis, matrix);
  }

  private static void rotateYtoSky(float[] matrix) {
    Matrix.rotateM(matrix, 0, 90, 1, 0, 0);
  }
}
