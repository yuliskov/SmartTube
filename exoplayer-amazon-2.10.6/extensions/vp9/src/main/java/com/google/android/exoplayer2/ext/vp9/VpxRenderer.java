/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.vp9;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import com.google.android.exoplayer2.util.GlUtil;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLSurfaceView.Renderer implementation that can render YUV Frames returned by libvpx after
 * decoding. It does the YUV to RGB color conversion in the Fragment Shader.
 */
/* package */ class VpxRenderer implements GLSurfaceView.Renderer {

  private static final float[] kColorConversion601 = {
    1.164f, 1.164f, 1.164f,
    0.0f, -0.392f, 2.017f,
    1.596f, -0.813f, 0.0f,
  };

  private static final float[] kColorConversion709 = {
    1.164f, 1.164f, 1.164f,
    0.0f, -0.213f, 2.112f,
    1.793f, -0.533f, 0.0f,
  };

  private static final float[] kColorConversion2020 = {
    1.168f, 1.168f, 1.168f,
    0.0f, -0.188f, 2.148f,
    1.683f, -0.652f, 0.0f,
  };

  private static final String VERTEX_SHADER =
      "varying vec2 interp_tc;\n"
      + "attribute vec4 in_pos;\n"
      + "attribute vec2 in_tc;\n"
      + "void main() {\n"
      + "  gl_Position = in_pos;\n"
      + "  interp_tc = in_tc;\n"
      + "}\n";
  private static final String[] TEXTURE_UNIFORMS = {"y_tex", "u_tex", "v_tex"};
  private static final String FRAGMENT_SHADER =
      "precision mediump float;\n"
      + "varying vec2 interp_tc;\n"
      + "uniform sampler2D y_tex;\n"
      + "uniform sampler2D u_tex;\n"
      + "uniform sampler2D v_tex;\n"
      + "uniform mat3 mColorConversion;\n"
      + "void main() {\n"
      + "  vec3 yuv;\n"
      + "  yuv.x = texture2D(y_tex, interp_tc).r - 0.0625;\n"
      + "  yuv.y = texture2D(u_tex, interp_tc).r - 0.5;\n"
      + "  yuv.z = texture2D(v_tex, interp_tc).r - 0.5;\n"
      + "  gl_FragColor = vec4(mColorConversion * yuv, 1.0);\n"
      + "}\n";

  private static final FloatBuffer TEXTURE_VERTICES =
      GlUtil.createBuffer(new float[] {-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f});
  private final int[] yuvTextures = new int[3];
  private final AtomicReference<VpxOutputBuffer> pendingOutputBufferReference;

  // Kept in a field rather than a local variable so that it doesn't get garbage collected before
  // glDrawArrays uses it.
  @SuppressWarnings("FieldCanBeLocal")
  private FloatBuffer textureCoords;
  private int program;
  private int texLocation;
  private int colorMatrixLocation;
  private int previousWidth;
  private int previousStride;

  private VpxOutputBuffer renderedOutputBuffer; // Accessed only from the GL thread.

  public VpxRenderer() {
    previousWidth = -1;
    previousStride = -1;
    pendingOutputBufferReference = new AtomicReference<>();
  }

  /**
   * Set a frame to be rendered. This should be followed by a call to
   * VpxVideoSurfaceView.requestRender() to actually render the frame.
   *
   * @param outputBuffer OutputBuffer containing the YUV Frame to be rendered
   */
  public void setFrame(VpxOutputBuffer outputBuffer) {
    VpxOutputBuffer oldPendingOutputBuffer = pendingOutputBufferReference.getAndSet(outputBuffer);
    if (oldPendingOutputBuffer != null) {
      // The old pending output buffer will never be used for rendering, so release it now.
      oldPendingOutputBuffer.release();
    }
  }

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    program = GlUtil.compileProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    GLES20.glUseProgram(program);
    int posLocation = GLES20.glGetAttribLocation(program, "in_pos");
    GLES20.glEnableVertexAttribArray(posLocation);
    GLES20.glVertexAttribPointer(
        posLocation, 2, GLES20.GL_FLOAT, false, 0, TEXTURE_VERTICES);
    texLocation = GLES20.glGetAttribLocation(program, "in_tc");
    GLES20.glEnableVertexAttribArray(texLocation);
    GlUtil.checkGlError();
    colorMatrixLocation = GLES20.glGetUniformLocation(program, "mColorConversion");
    GlUtil.checkGlError();
    setupTextures();
    GlUtil.checkGlError();
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 unused) {
    VpxOutputBuffer pendingOutputBuffer = pendingOutputBufferReference.getAndSet(null);
    if (pendingOutputBuffer == null && renderedOutputBuffer == null) {
      // There is no output buffer to render at the moment.
      return;
    }
    if (pendingOutputBuffer != null) {
      if (renderedOutputBuffer != null) {
        renderedOutputBuffer.release();
      }
      renderedOutputBuffer = pendingOutputBuffer;
    }
    VpxOutputBuffer outputBuffer = renderedOutputBuffer;
    // Set color matrix. Assume BT709 if the color space is unknown.
    float[] colorConversion = kColorConversion709;
    switch (outputBuffer.colorspace) {
      case VpxOutputBuffer.COLORSPACE_BT601:
        colorConversion = kColorConversion601;
        break;
      case VpxOutputBuffer.COLORSPACE_BT2020:
        colorConversion = kColorConversion2020;
        break;
      case VpxOutputBuffer.COLORSPACE_BT709:
      default:
        break; // Do nothing
    }
    GLES20.glUniformMatrix3fv(colorMatrixLocation, 1, false, colorConversion, 0);

    for (int i = 0; i < 3; i++) {
      int h = (i == 0) ? outputBuffer.height : (outputBuffer.height + 1) / 2;
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
      GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
          outputBuffer.yuvStrides[i], h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE,
          outputBuffer.yuvPlanes[i]);
    }
    // Set cropping of stride if either width or stride has changed.
    if (previousWidth != outputBuffer.width || previousStride != outputBuffer.yuvStrides[0]) {
      float crop = (float) outputBuffer.width / outputBuffer.yuvStrides[0];
      // This buffer is consumed during each call to glDrawArrays. It needs to be a member variable
      // rather than a local variable to ensure that it doesn't get garbage collected.
      textureCoords =
          GlUtil.createBuffer(new float[] {0.0f, 0.0f, 0.0f, 1.0f, crop, 0.0f, crop, 1.0f});
      GLES20.glVertexAttribPointer(
          texLocation, 2, GLES20.GL_FLOAT, false, 0, textureCoords);
      previousWidth = outputBuffer.width;
      previousStride = outputBuffer.yuvStrides[0];
    }
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError();
  }

  private void setupTextures() {
    GLES20.glGenTextures(3, yuvTextures, 0);
    for (int i = 0; i < 3; i++)  {
      GLES20.glUniform1i(GLES20.glGetUniformLocation(program, TEXTURE_UNIFORMS[i]), i);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
    GlUtil.checkGlError();
  }
}
