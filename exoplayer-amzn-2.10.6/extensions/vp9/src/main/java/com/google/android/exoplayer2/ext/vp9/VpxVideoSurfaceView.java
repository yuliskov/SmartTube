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

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * A GLSurfaceView extension that scales itself to the given aspect ratio.
 */
public class VpxVideoSurfaceView extends GLSurfaceView implements VpxOutputBufferRenderer {

  private final VpxRenderer renderer;

  public VpxVideoSurfaceView(Context context) {
    this(context, null);
  }

  public VpxVideoSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    renderer = new VpxRenderer();
    setPreserveEGLContextOnPause(true);
    setEGLContextClientVersion(2);
    setRenderer(renderer);
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  @Override
  public void setOutputBuffer(VpxOutputBuffer outputBuffer) {
    renderer.setFrame(outputBuffer);
    requestRender();
  }

}
