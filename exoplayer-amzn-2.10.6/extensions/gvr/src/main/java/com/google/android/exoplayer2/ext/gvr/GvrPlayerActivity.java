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

package com.google.android.exoplayer2.ext.gvr;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.BinderThread;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.spherical.GlViewGroup;
import com.google.android.exoplayer2.ui.spherical.PointerRenderer;
import com.google.android.exoplayer2.ui.spherical.SceneRenderer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.vr.ndk.base.DaydreamApi;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;
import javax.microedition.khronos.egl.EGLConfig;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Base activity for VR 360 video playback. */
public abstract class GvrPlayerActivity extends GvrActivity {

  private static final int EXIT_FROM_VR_REQUEST_CODE = 42;

  private final Handler mainHandler;

  @Nullable private Player player;
  @MonotonicNonNull private GlViewGroup glView;
  @MonotonicNonNull private ControllerManager controllerManager;
  @MonotonicNonNull private SurfaceTexture surfaceTexture;
  @MonotonicNonNull private Surface surface;
  @MonotonicNonNull private SceneRenderer scene;
  @MonotonicNonNull private PlayerControlView playerControl;

  public GvrPlayerActivity() {
    mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setScreenAlwaysOn(true);

    GvrView gvrView = new GvrView(this);
    // Since videos typically have fewer pixels per degree than the phones, reducing the render
    // target scaling factor reduces the work required to render the scene.
    gvrView.setRenderTargetScale(.5f);

    // If a custom theme isn't specified, the Context's theme is used. For VR Activities, this is
    // the old Android default theme rather than a modern theme. Override this with a custom theme.
    Context theme = new ContextThemeWrapper(this, R.style.VrTheme);
    glView = new GlViewGroup(theme, R.layout.vr_ui);

    playerControl = Assertions.checkNotNull(glView.findViewById(R.id.controller));
    playerControl.setShowVrButton(true);
    playerControl.setVrButtonListener(v -> exit());

    PointerRenderer pointerRenderer = new PointerRenderer();
    scene = new SceneRenderer();
    Renderer renderer = new Renderer(scene, glView, pointerRenderer);

    // Attach glView to gvrView in order to properly handle UI events.
    gvrView.addView(glView, 0);

    // Standard GvrView configuration
    gvrView.setEGLConfigChooser(
        8, 8, 8, 8, // RGBA bits.
        16, // Depth bits.
        0); // Stencil bits.
    gvrView.setRenderer(renderer);
    setContentView(gvrView);

    // Most Daydream phones can render a 4k video at 60fps in sustained performance mode. These
    // options can be tweaked along with the render target scale.
    if (gvrView.setAsyncReprojectionEnabled(true)) {
      AndroidCompat.setSustainedPerformanceMode(this, true);
    }

    // Handle the user clicking on the 'X' in the top left corner. Since this is done when the user
    // has taken the headset out of VR, it should launch the app's exit flow directly rather than
    // using the transition flow.
    gvrView.setOnCloseButtonListener(this::finish);

    ControllerManager.EventListener listener =
        new ControllerManager.EventListener() {
          @Override
          public void onApiStatusChanged(int status) {
            // Do nothing.
          }

          @Override
          public void onRecentered() {
            // TODO if in cardboard mode call gvrView.recenterHeadTracker();
            glView.post(() -> Util.castNonNull(playerControl).show());
          }
        };
    controllerManager = new ControllerManager(this, listener);

    Controller controller = controllerManager.getController();
    ControllerEventListener controllerEventListener =
        new ControllerEventListener(controller, pointerRenderer, glView);
    controller.setEventListener(controllerEventListener);
  }

  /**
   * Sets the {@link Player} to use.
   *
   * @param newPlayer The {@link Player} to use, or {@code null} to detach the current player.
   */
  protected void setPlayer(@Nullable Player newPlayer) {
    Assertions.checkNotNull(scene);
    if (player == newPlayer) {
      return;
    }
    if (player != null) {
      Player.VideoComponent videoComponent = player.getVideoComponent();
      if (videoComponent != null) {
        if (surface != null) {
          videoComponent.clearVideoSurface(surface);
        }
        videoComponent.clearVideoFrameMetadataListener(scene);
        videoComponent.clearCameraMotionListener(scene);
      }
    }
    player = newPlayer;
    if (player != null) {
      Player.VideoComponent videoComponent = player.getVideoComponent();
      if (videoComponent != null) {
        videoComponent.setVideoFrameMetadataListener(scene);
        videoComponent.setCameraMotionListener(scene);
        videoComponent.setVideoSurface(surface);
      }
    }
    Assertions.checkNotNull(playerControl).setPlayer(player);
  }

  /**
   * Sets the default stereo mode. If the played video doesn't contain a stereo mode the default one
   * is used.
   *
   * @param stereoMode A {@link C.StereoMode} value.
   */
  protected void setDefaultStereoMode(@C.StereoMode int stereoMode) {
    Assertions.checkNotNull(scene).setDefaultStereoMode(stereoMode);
  }

  @CallSuper
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent unused) {
    if (requestCode == EXIT_FROM_VR_REQUEST_CODE && resultCode == RESULT_OK) {
      finish();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Util.castNonNull(controllerManager).start();
  }

  @Override
  protected void onPause() {
    Util.castNonNull(controllerManager).stop();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    setPlayer(null);
    releaseSurface(surfaceTexture, surface);
    super.onDestroy();
  }

  /** Tries to exit gracefully from VR using a VR transition dialog. */
  @SuppressWarnings("nullness:argument.type.incompatible")
  protected void exit() {
    // This needs to use GVR's exit transition to avoid disorienting the user.
    DaydreamApi api = DaydreamApi.create(this);
    if (api != null) {
      api.exitFromVr(this, EXIT_FROM_VR_REQUEST_CODE, null);
      // Eventually, the Activity's onActivityResult will be called.
      api.close();
    } else {
      finish();
    }
  }

  /** Toggles PlayerControl visibility. */
  @UiThread
  protected void togglePlayerControlVisibility() {
    if (Assertions.checkNotNull(playerControl).isVisible()) {
      playerControl.hide();
    } else {
      playerControl.show();
    }
  }

  // Called on GL thread.
  private void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
    mainHandler.post(
        () -> {
          SurfaceTexture oldSurfaceTexture = this.surfaceTexture;
          Surface oldSurface = this.surface;
          this.surfaceTexture = surfaceTexture;
          this.surface = new Surface(surfaceTexture);
          if (player != null) {
            Player.VideoComponent videoComponent = player.getVideoComponent();
            if (videoComponent != null) {
              videoComponent.setVideoSurface(surface);
            }
          }
          releaseSurface(oldSurfaceTexture, oldSurface);
        });
  }

  private static void releaseSurface(
      @Nullable SurfaceTexture oldSurfaceTexture, @Nullable Surface oldSurface) {
    if (oldSurfaceTexture != null) {
      oldSurfaceTexture.release();
    }
    if (oldSurface != null) {
      oldSurface.release();
    }
  }

  private class Renderer implements GvrView.StereoRenderer {
    private static final float Z_NEAR = .1f;
    private static final float Z_FAR = 100;

    private final float[] viewProjectionMatrix = new float[16];
    private final SceneRenderer scene;
    private final GlViewGroup glView;
    private final PointerRenderer pointerRenderer;

    public Renderer(SceneRenderer scene, GlViewGroup glView, PointerRenderer pointerRenderer) {
      this.scene = scene;
      this.glView = glView;
      this.pointerRenderer = pointerRenderer;
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {}

    @Override
    public void onDrawEye(Eye eye) {
      Matrix.multiplyMM(
          viewProjectionMatrix, 0, eye.getPerspective(Z_NEAR, Z_FAR), 0, eye.getEyeView(), 0);
      scene.drawFrame(viewProjectionMatrix, eye.getType() == Eye.Type.RIGHT);
      if (glView.isVisible()) {
        glView.getRenderer().draw(viewProjectionMatrix);
        pointerRenderer.draw(viewProjectionMatrix);
      }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {}

    @Override
    public void onSurfaceCreated(EGLConfig config) {
      onSurfaceTextureAvailable(scene.init());
      glView.getRenderer().init();
      pointerRenderer.init();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {}

    @Override
    public void onRendererShutdown() {
      glView.getRenderer().shutdown();
      pointerRenderer.shutdown();
      scene.shutdown();
    }
  }

  private class ControllerEventListener extends Controller.EventListener {

    private final Controller controller;
    private final PointerRenderer pointerRenderer;
    private final GlViewGroup glView;
    private final float[] controllerOrientationMatrix;
    private boolean clickButtonDown;
    private boolean appButtonDown;

    public ControllerEventListener(
        Controller controller, PointerRenderer pointerRenderer, GlViewGroup glView) {
      this.controller = controller;
      this.pointerRenderer = pointerRenderer;
      this.glView = glView;
      controllerOrientationMatrix = new float[16];
    }

    @Override
    @BinderThread
    public void onUpdate() {
      controller.update();
      controller.orientation.toRotationMatrix(controllerOrientationMatrix);
      pointerRenderer.setControllerOrientation(controllerOrientationMatrix);

      if (clickButtonDown || controller.clickButtonState) {
        int action;
        if (clickButtonDown != controller.clickButtonState) {
          clickButtonDown = controller.clickButtonState;
          action = clickButtonDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
        } else {
          action = MotionEvent.ACTION_MOVE;
        }
        glView.post(
            () -> {
              float[] angles = controller.orientation.toYawPitchRollRadians(new float[3]);
              boolean clickedOnView = glView.simulateClick(action, angles[0], angles[1]);
              if (action == MotionEvent.ACTION_DOWN && !clickedOnView) {
                togglePlayerControlVisibility();
              }
            });
      } else if (!appButtonDown && controller.appButtonState) {
        glView.post(GvrPlayerActivity.this::togglePlayerControlVisibility);
      }
      appButtonDown = controller.appButtonState;
    }
  }
}
