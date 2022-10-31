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

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Renders a canvas on a quad.
 *
 * <p>A CanvasRenderer can be created on any thread, but {@link #init()} needs to be called on the
 * GL thread before it can be rendered.
 */
public final class CanvasRenderer {

  private static final float WIDTH_UNIT = 0.8f;
  private static final float DISTANCE_UNIT = 1f;
  private static final float X_UNIT = -WIDTH_UNIT / 2;
  private static final float Y_UNIT = -0.3f;

  // Standard vertex shader that passes through the texture data.
  private static final String[] VERTEX_SHADER_CODE = {
    "uniform mat4 uMvpMatrix;",
    // 3D position data.
    "attribute vec3 aPosition;",
    // 2D UV vertices.
    "attribute vec2 aTexCoords;",
    "varying vec2 vTexCoords;",

    // Standard transformation.
    "void main() {",
    "  gl_Position = uMvpMatrix * vec4(aPosition, 1);",
    "  vTexCoords = aTexCoords;",
    "}"
  };

  private static final String[] FRAGMENT_SHADER_CODE = {
    // This is required since the texture data is GL_TEXTURE_EXTERNAL_OES.
    "#extension GL_OES_EGL_image_external : require",
    "precision mediump float;",
    "uniform samplerExternalOES uTexture;",
    "varying vec2 vTexCoords;",
    "void main() {",
    "  gl_FragColor = texture2D(uTexture, vTexCoords);",
    "}"
  };

  // The quad has 2 triangles built from 4 total vertices. Each vertex has 3 position and 2 texture
  // coordinates.
  private static final int POSITION_COORDS_PER_VERTEX = 3;
  private static final int TEXTURE_COORDS_PER_VERTEX = 2;
  private static final int COORDS_PER_VERTEX =
      POSITION_COORDS_PER_VERTEX + TEXTURE_COORDS_PER_VERTEX;
  private static final int VERTEX_STRIDE_BYTES = COORDS_PER_VERTEX * C.BYTES_PER_FLOAT;
  private static final int VERTEX_COUNT = 4;
  private static final float HALF_PI = (float) (Math.PI / 2);

  private final FloatBuffer vertexBuffer;
  private final AtomicBoolean surfaceDirty;

  private int width;
  private int height;
  private float heightUnit;

  // Program-related GL items. These are only valid if program != 0.
  private int program = 0;
  private int mvpMatrixHandle;
  private int positionHandle;
  private int textureCoordsHandle;
  private int textureHandle;
  private int textureId;

  // Components used to manage the Canvas that the View is rendered to. These are only valid after
  // GL initialization. The client of this class acquires a Canvas from the Surface, writes to it
  // and posts it. This marks the Surface as dirty. The GL code then updates the SurfaceTexture
  // when rendering only if it is dirty.
  @MonotonicNonNull private SurfaceTexture displaySurfaceTexture;
  @MonotonicNonNull private Surface displaySurface;

  public CanvasRenderer() {
    vertexBuffer = GlUtil.createBuffer(COORDS_PER_VERTEX * VERTEX_COUNT);
    surfaceDirty = new AtomicBoolean();
  }

  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
    heightUnit = WIDTH_UNIT * height / width;

    float[] vertexData = new float[COORDS_PER_VERTEX * VERTEX_COUNT];
    int vertexDataIndex = 0;
    for (int y = 0; y < 2; y++) {
      for (int x = 0; x < 2; x++) {
        vertexData[vertexDataIndex++] = X_UNIT + (WIDTH_UNIT * x);
        vertexData[vertexDataIndex++] = Y_UNIT + (heightUnit * y);
        vertexData[vertexDataIndex++] = -DISTANCE_UNIT;
        vertexData[vertexDataIndex++] = x;
        vertexData[vertexDataIndex++] = 1 - y;
      }
    }
    vertexBuffer.position(0);
    vertexBuffer.put(vertexData);
  }

  /**
   * Calls {@link Surface#lockCanvas(Rect)}.
   *
   * @return {@link Canvas} for the View to render to or {@code null} if {@link #init()} has not yet
   *     been called.
   */
  @Nullable
  public Canvas lockCanvas() {
    return displaySurface == null ? null : displaySurface.lockCanvas(/* inOutDirty= */ null);
  }

  /**
   * Calls {@link Surface#unlockCanvasAndPost(Canvas)} and marks the SurfaceTexture as dirty.
   *
   * @param canvas the canvas returned from {@link #lockCanvas()}
   */
  public void unlockCanvasAndPost(@Nullable Canvas canvas) {
    if (canvas == null || displaySurface == null) {
      // glInit() hasn't run yet.
      return;
    }
    displaySurface.unlockCanvasAndPost(canvas);
  }

  /** Finishes constructing this object on the GL Thread. */
  public void init() {
    if (program != 0) {
      return;
    }

    // Create the program.
    program = GlUtil.compileProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
    mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix");
    positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    textureCoordsHandle = GLES20.glGetAttribLocation(program, "aTexCoords");
    textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
    textureId = GlUtil.createExternalTexture();
    checkGlError();

    // Create the underlying SurfaceTexture with the appropriate size.
    displaySurfaceTexture = new SurfaceTexture(textureId);
    displaySurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> surfaceDirty.set(true));
    displaySurfaceTexture.setDefaultBufferSize(width, height);
    displaySurface = new Surface(displaySurfaceTexture);
  }

  /**
   * Renders the quad.
   *
   * @param viewProjectionMatrix Array of floats containing the quad's 4x4 perspective matrix in the
   *     {@link android.opengl.Matrix} format.
   */
  public void draw(float[] viewProjectionMatrix) {
    if (displaySurfaceTexture == null) {
      return;
    }

    GLES20.glUseProgram(program);
    checkGlError();

    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glEnableVertexAttribArray(textureCoordsHandle);
    checkGlError();

    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, viewProjectionMatrix, 0);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glUniform1i(textureHandle, 0);
    checkGlError();

    // Load position data.
    vertexBuffer.position(0);
    GLES20.glVertexAttribPointer(
        positionHandle,
        POSITION_COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        VERTEX_STRIDE_BYTES,
        vertexBuffer);
    checkGlError();

    // Load texture data.
    vertexBuffer.position(POSITION_COORDS_PER_VERTEX);
    GLES20.glVertexAttribPointer(
        textureCoordsHandle,
        TEXTURE_COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        VERTEX_STRIDE_BYTES,
        vertexBuffer);
    checkGlError();

    if (surfaceDirty.compareAndSet(true, false)) {
      // If the Surface has been written to, get the new data onto the SurfaceTexture.
      displaySurfaceTexture.updateTexImage();
    }

    // Render.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);
    checkGlError();

    GLES20.glDisableVertexAttribArray(positionHandle);
    GLES20.glDisableVertexAttribArray(textureCoordsHandle);
  }

  /** Frees GL resources. */
  public void shutdown() {
    if (program != 0) {
      GLES20.glDeleteProgram(program);
      GLES20.glDeleteTextures(1, new int[] {textureId}, 0);
    }

    if (displaySurfaceTexture != null) {
      displaySurfaceTexture.release();
    }
    if (displaySurface != null) {
      displaySurface.release();
    }
  }

  /**
   * Translates an orientation into pixel coordinates on the canvas.
   *
   * <p>This is a minimal hit detection system that works for this quad because it has no model
   * matrix. All the math is based on the fact that its size and distance are hard-coded into this
   * class. For a more complex 3D mesh, a general bounding box and ray collision system would be
   * required.
   *
   * @param yaw Yaw of the orientation in radians.
   * @param pitch Pitch of the orientation in radians.
   * @return A {@link PointF} which contains the translated coordinate, or null if the point is
   *     outside of the quad's bounds.
   */
  @Nullable
  public PointF translateClick(float yaw, float pitch) {
    return internalTranslateClick(
        yaw, pitch, X_UNIT, Y_UNIT, WIDTH_UNIT, heightUnit, width, height);
  }

  @Nullable
  /* package */ static PointF internalTranslateClick(
      float yaw,
      float pitch,
      float xUnit,
      float yUnit,
      float widthUnit,
      float heightUnit,
      int widthPixel,
      int heightPixel) {
    if (yaw >= HALF_PI || yaw <= -HALF_PI || pitch >= HALF_PI || pitch <= -HALF_PI) {
      return null;
    }
    double clickXUnit = Math.tan(yaw) * DISTANCE_UNIT - xUnit;
    double clickYUnit = Math.tan(pitch) * DISTANCE_UNIT - yUnit;
    if (clickXUnit < 0 || clickXUnit > widthUnit || clickYUnit < 0 || clickYUnit > heightUnit) {
      return null;
    }
    // Convert from the polar coordinates of the controller to the rectangular coordinates of the
    // View. Note the negative yaw and pitch used to generate Android-compliant x and y coordinates.
    float clickXPixel = (float) (widthPixel - clickXUnit * widthPixel / widthUnit);
    float clickYPixel = (float) (heightPixel - clickYUnit * heightPixel / heightUnit);
    return new PointF(clickXPixel, clickYPixel);
  }
}
