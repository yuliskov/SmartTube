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

import static com.google.android.exoplayer2.util.GlUtil.checkGlError;

import android.opengl.GLES20;
import android.opengl.Matrix;
import com.google.android.exoplayer2.util.GlUtil;
import java.nio.FloatBuffer;

/** Renders a pointer. */
public final class PointerRenderer {
  // The pointer quad is 2 * SIZE units.
  private static final float SIZE = .01f;
  private static final float DISTANCE = 1;

  // Standard vertex shader.
  private static final String[] VERTEX_SHADER_CODE =
      new String[] {
        "uniform mat4 uMvpMatrix;",
        "attribute vec3 aPosition;",
        "varying vec2 vCoords;",

        // Pass through normalized vertex coordinates.
        "void main() {",
        "  gl_Position = uMvpMatrix * vec4(aPosition, 1);",
        "  vCoords = aPosition.xy / vec2(" + SIZE + ", " + SIZE + ");",
        "}"
      };

  // Procedurally render a ring on the quad between the specified radii.
  private static final String[] FRAGMENT_SHADER_CODE =
      new String[] {
        "precision mediump float;",
        "varying vec2 vCoords;",

        // Simple ring shader that is white between the radii and transparent elsewhere.
        "void main() {",
        "  float r = length(vCoords);",
        // Blend the edges of the ring at .55 +/- .05 and .85 +/- .05.
        "  float alpha = smoothstep(0.5, 0.6, r) * (1.0 - smoothstep(0.8, 0.9, r));",
        "  if (alpha == 0.0) {",
        "    discard;",
        "  } else {",
        "    gl_FragColor = vec4(alpha);",
        "  }",
        "}"
      };

  // Simple quad mesh.
  private static final int COORDS_PER_VERTEX = 3;
  private static final float[] VERTEX_DATA = {
    -SIZE, -SIZE, -DISTANCE, SIZE, -SIZE, -DISTANCE, -SIZE, SIZE, -DISTANCE, SIZE, SIZE, -DISTANCE,
  };
  private final FloatBuffer vertexBuffer;

  // The pointer doesn't have a real modelMatrix. Its distance is baked into the mesh and it
  // uses a rotation matrix when rendered.
  private final float[] modelViewProjectionMatrix;
  // This is accessed on the binder & GL Threads.
  private final float[] controllerOrientationMatrix;

  // Program-related GL items. These are only valid if program != 0.
  private int program = 0;
  private int mvpMatrixHandle;
  private int positionHandle;

  public PointerRenderer() {
    vertexBuffer = GlUtil.createBuffer(VERTEX_DATA);
    modelViewProjectionMatrix = new float[16];
    controllerOrientationMatrix = new float[16];
    Matrix.setIdentityM(controllerOrientationMatrix, 0);
  }

  /** Finishes initialization of this object on the GL thread. */
  public void init() {
    if (program != 0) {
      return;
    }

    program = GlUtil.compileProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
    mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix");
    positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    checkGlError();
  }

  /**
   * Renders the pointer.
   *
   * @param viewProjectionMatrix Scene's view projection matrix.
   */
  public void draw(float[] viewProjectionMatrix) {
    // Configure shader.
    GLES20.glUseProgram(program);
    checkGlError();

    synchronized (controllerOrientationMatrix) {
      Matrix.multiplyMM(
          modelViewProjectionMatrix, 0, viewProjectionMatrix, 0, controllerOrientationMatrix, 0);
    }
    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
    checkGlError();

    // Render quad.
    GLES20.glEnableVertexAttribArray(positionHandle);
    checkGlError();

    GLES20.glVertexAttribPointer(
        positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, /* stride= */ 0, vertexBuffer);
    checkGlError();

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_DATA.length / COORDS_PER_VERTEX);
    checkGlError();

    GLES20.glDisableVertexAttribArray(positionHandle);
  }

  /** Frees GL resources. */
  public void shutdown() {
    if (program != 0) {
      GLES20.glDeleteProgram(program);
    }
  }

  /** Updates the pointer's position with the latest Controller pose. */
  public void setControllerOrientation(float[] rotationMatrix) {
    synchronized (controllerOrientationMatrix) {
      System.arraycopy(rotationMatrix, 0, controllerOrientationMatrix, 0, rotationMatrix.length);
    }
  }
}
