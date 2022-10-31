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

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.video.spherical.Projection;
import java.nio.FloatBuffer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility class to render spherical meshes for video or images. Call {@link #init()} on the GL
 * thread when ready.
 */
/* package */ final class ProjectionRenderer {

  /**
   * Returns whether {@code projection} is supported. At least it should have left mesh and there
   * should be only one sub mesh per mesh.
   */
  public static boolean isSupported(Projection projection) {
    Projection.Mesh leftMesh = projection.leftMesh;
    Projection.Mesh rightMesh = projection.rightMesh;
    return leftMesh.getSubMeshCount() == 1
        && leftMesh.getSubMesh(0).textureId == Projection.SubMesh.VIDEO_TEXTURE_ID
        && rightMesh.getSubMeshCount() == 1
        && rightMesh.getSubMesh(0).textureId == Projection.SubMesh.VIDEO_TEXTURE_ID;
  }

  // Basic vertex & fragment shaders to render a mesh with 3D position & 2D texture data.
  private static final String[] VERTEX_SHADER_CODE =
      new String[] {
        "uniform mat4 uMvpMatrix;",
        "uniform mat3 uTexMatrix;",
        "attribute vec4 aPosition;",
        "attribute vec2 aTexCoords;",
        "varying vec2 vTexCoords;",

        // Standard transformation.
        "void main() {",
        "  gl_Position = uMvpMatrix * aPosition;",
        "  vTexCoords = (uTexMatrix * vec3(aTexCoords, 1)).xy;",
        "}"
      };
  private static final String[] FRAGMENT_SHADER_CODE =
      new String[] {
        // This is required since the texture data is GL_TEXTURE_EXTERNAL_OES.
        "#extension GL_OES_EGL_image_external : require",
        "precision mediump float;",

        // Standard texture rendering shader.
        "uniform samplerExternalOES uTexture;",
        "varying vec2 vTexCoords;",
        "void main() {",
        "  gl_FragColor = texture2D(uTexture, vTexCoords);",
        "}"
      };

  // Texture transform matrices.
  private static final float[] TEX_MATRIX_WHOLE = {
    1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 1.0f
  };
  private static final float[] TEX_MATRIX_TOP = {
    1.0f, 0.0f, 0.0f, 0.0f, -0.5f, 0.0f, 0.0f, 0.5f, 1.0f
  };
  private static final float[] TEX_MATRIX_BOTTOM = {
    1.0f, 0.0f, 0.0f, 0.0f, -0.5f, 0.0f, 0.0f, 1.0f, 1.0f
  };
  private static final float[] TEX_MATRIX_LEFT = {
    0.5f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 1.0f
  };
  private static final float[] TEX_MATRIX_RIGHT = {
    0.5f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.5f, 1.0f, 1.0f
  };

  private int stereoMode;
  private @Nullable MeshData leftMeshData;
  private @Nullable MeshData rightMeshData;

  // Program related GL items. These are only valid if program != 0.
  private int program;
  private int mvpMatrixHandle;
  private int uTexMatrixHandle;
  private int positionHandle;
  private int texCoordsHandle;
  private int textureHandle;

  /**
   * Sets a {@link Projection} to be used.
   *
   * @param projection Contains the projection data to be rendered.
   * @see #isSupported(Projection)
   */
  public void setProjection(Projection projection) {
    if (!isSupported(projection)) {
      return;
    }
    stereoMode = projection.stereoMode;
    leftMeshData = new MeshData(projection.leftMesh.getSubMesh(0));
    rightMeshData =
        projection.singleMesh ? leftMeshData : new MeshData(projection.rightMesh.getSubMesh(0));
  }

  /** Initializes of the GL components. */
  /* package */ void init() {
    program = GlUtil.compileProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
    mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix");
    uTexMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix");
    positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    texCoordsHandle = GLES20.glGetAttribLocation(program, "aTexCoords");
    textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
  }

  /**
   * Renders the mesh. If the projection hasn't been set, does nothing. This must be called on the
   * GL thread.
   *
   * @param textureId GL_TEXTURE_EXTERNAL_OES used for this mesh.
   * @param mvpMatrix The Model View Projection matrix.
   * @param rightEye Whether the right eye view should be drawn. If {@code false}, the left eye view
   *     is drawn.
   */
  /* package */ void draw(int textureId, float[] mvpMatrix, boolean rightEye) {
    MeshData meshData = rightEye ? rightMeshData : leftMeshData;
    if (meshData == null) {
      return;
    }

    // Configure shader.
    GLES20.glUseProgram(program);
    checkGlError();

    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glEnableVertexAttribArray(texCoordsHandle);
    checkGlError();

    float[] texMatrix;
    if (stereoMode == C.STEREO_MODE_TOP_BOTTOM) {
      texMatrix = rightEye ? TEX_MATRIX_BOTTOM : TEX_MATRIX_TOP;
    } else if (stereoMode == C.STEREO_MODE_LEFT_RIGHT) {
      texMatrix = rightEye ? TEX_MATRIX_RIGHT : TEX_MATRIX_LEFT;
    } else {
      texMatrix = TEX_MATRIX_WHOLE;
    }
    GLES20.glUniformMatrix3fv(uTexMatrixHandle, 1, false, texMatrix, 0);

    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glUniform1i(textureHandle, 0);
    checkGlError();

    // Load position data.
    GLES20.glVertexAttribPointer(
        positionHandle,
        Projection.POSITION_COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        Projection.POSITION_COORDS_PER_VERTEX * C.BYTES_PER_FLOAT,
        meshData.vertexBuffer);
    checkGlError();

    // Load texture data.
    GLES20.glVertexAttribPointer(
        texCoordsHandle,
        Projection.TEXTURE_COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        Projection.TEXTURE_COORDS_PER_VERTEX * C.BYTES_PER_FLOAT,
        meshData.textureBuffer);
    checkGlError();

    // Render.
    GLES20.glDrawArrays(meshData.drawMode, 0, meshData.vertexCount);
    checkGlError();

    GLES20.glDisableVertexAttribArray(positionHandle);
    GLES20.glDisableVertexAttribArray(texCoordsHandle);
  }

  /** Cleans up the GL resources. */
  /* package */ void shutdown() {
    if (program != 0) {
      GLES20.glDeleteProgram(program);
    }
  }

  private static class MeshData {
    private final int vertexCount;
    private final FloatBuffer vertexBuffer;
    private final FloatBuffer textureBuffer;
    @Projection.DrawMode private final int drawMode;

    public MeshData(Projection.SubMesh subMesh) {
      vertexCount = subMesh.getVertexCount();
      vertexBuffer = GlUtil.createBuffer(subMesh.vertices);
      textureBuffer = GlUtil.createBuffer(subMesh.textureCoords);
      switch (subMesh.mode) {
        case Projection.DRAW_MODE_TRIANGLES_STRIP:
          drawMode = GLES20.GL_TRIANGLE_STRIP;
          break;
        case Projection.DRAW_MODE_TRIANGLES_FAN:
          drawMode = GLES20.GL_TRIANGLE_FAN;
          break;
        case Projection.DRAW_MODE_TRIANGLES:
        default:
          drawMode = GLES20.GL_TRIANGLES;
          break;
      }
    }
  }
}
