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
package com.google.android.exoplayer2.util;

import static android.opengl.GLU.gluErrorString;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** GL utility methods. */
public final class GlUtil {
  private static final String TAG = "GlUtil";

  /** Class only contains static methods. */
  private GlUtil() {}

  /**
   * If there is an OpenGl error, logs the error and if {@link
   * ExoPlayerLibraryInfo#GL_ASSERTIONS_ENABLED} is true throws a {@link RuntimeException}.
   */
  public static void checkGlError() {
    int lastError = GLES20.GL_NO_ERROR;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, "glError " + gluErrorString(error));
      lastError = error;
    }
    if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED && lastError != GLES20.GL_NO_ERROR) {
      throw new RuntimeException("glError " + gluErrorString(lastError));
    }
  }

  /**
   * Builds a GL shader program from vertex and fragment shader code.
   *
   * @param vertexCode GLES20 vertex shader program as arrays of strings. Strings are joined by
   *     adding a new line character in between each of them.
   * @param fragmentCode GLES20 fragment shader program as arrays of strings. Strings are joined by
   *     adding a new line character in between each of them.
   * @return GLES20 program id.
   */
  public static int compileProgram(String[] vertexCode, String[] fragmentCode) {
    return compileProgram(TextUtils.join("\n", vertexCode), TextUtils.join("\n", fragmentCode));
  }

  /**
   * Builds a GL shader program from vertex and fragment shader code.
   *
   * @param vertexCode GLES20 vertex shader program.
   * @param fragmentCode GLES20 fragment shader program.
   * @return GLES20 program id.
   */
  public static int compileProgram(String vertexCode, String fragmentCode) {
    int program = GLES20.glCreateProgram();
    checkGlError();

    // Add the vertex and fragment shaders.
    addShader(GLES20.GL_VERTEX_SHADER, vertexCode, program);
    addShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode, program);

    // Link and check for errors.
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[] {GLES20.GL_FALSE};
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      throwGlError("Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(program));
    }
    checkGlError();

    return program;
  }

  /**
   * Allocates a FloatBuffer with the given data.
   *
   * @param data Used to initialize the new buffer.
   */
  public static FloatBuffer createBuffer(float[] data) {
    return (FloatBuffer) createBuffer(data.length).put(data).flip();
  }

  /**
   * Allocates a FloatBuffer.
   *
   * @param capacity The new buffer's capacity, in floats.
   */
  public static FloatBuffer createBuffer(int capacity) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT);
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  public static int createExternalTexture() {
    int[] texId = new int[1];
    GLES20.glGenTextures(1, IntBuffer.wrap(texId));
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0]);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
    return texId[0];
  }

  private static void addShader(int type, String source, int program) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);

    int[] result = new int[] {GLES20.GL_FALSE};
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
    if (result[0] != GLES20.GL_TRUE) {
      throwGlError(GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
    }

    GLES20.glAttachShader(program, shader);
    GLES20.glDeleteShader(shader);
    checkGlError();
  }

  private static void throwGlError(String errorMsg) {
    Log.e(TAG, errorMsg);
    if (ExoPlayerLibraryInfo.GL_ASSERTIONS_ENABLED) {
      throw new RuntimeException(errorMsg);
    }
  }
}
