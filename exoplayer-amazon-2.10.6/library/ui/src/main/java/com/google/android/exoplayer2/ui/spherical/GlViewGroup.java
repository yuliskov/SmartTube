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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.exoplayer2.util.Assertions;

/** This View uses standard Android APIs to render its child Views to a texture. */
public final class GlViewGroup extends FrameLayout {

  private final CanvasRenderer canvasRenderer;

  /**
   * @param context The Context the view is running in, through which it can access the current
   *     theme, resources, etc.
   * @param layoutId ID for an XML layout resource to load (e.g., * <code>R.layout.main_page</code>)
   */
  public GlViewGroup(Context context, int layoutId) {
    super(context);
    this.canvasRenderer = new CanvasRenderer();

    LayoutInflater.from(context).inflate(layoutId, this);

    measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    Assertions.checkState(width > 0 && height > 0);
    canvasRenderer.setSize(width, height);
    setLayoutParams(new FrameLayout.LayoutParams(width, height));
  }

  /** Returns whether the view is currently visible. */
  @UiThread
  public boolean isVisible() {
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      if (getChildAt(i).getVisibility() == VISIBLE) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void dispatchDraw(Canvas notUsed) {
    Canvas glCanvas = canvasRenderer.lockCanvas();
    if (glCanvas == null) {
      // This happens if Android tries to draw this View before GL initialization completes. We need
      // to retry until the draw call happens after GL invalidation.
      postInvalidate();
      return;
    }

    // Clear the canvas first.
    glCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    // Have Android render the child views.
    super.dispatchDraw(glCanvas);
    // Commit the changes.
    canvasRenderer.unlockCanvasAndPost(glCanvas);
  }

  /**
   * Simulates a click on the view.
   *
   * @param action Click action.
   * @param yaw Yaw of the click's orientation in radians.
   * @param pitch Pitch of the click's orientation in radians.
   * @return Whether the click was simulated. If false then the view is not visible or the click was
   *     outside of its bounds.
   */
  @UiThread
  public boolean simulateClick(int action, float yaw, float pitch) {
    if (!isVisible()) {
      return false;
    }
    PointF point = canvasRenderer.translateClick(yaw, pitch);
    if (point == null) {
      return false;
    }
    long now = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(now, now, action, point.x, point.y, /* metaState= */ 1);
    dispatchTouchEvent(event);
    return true;
  }

  @AnyThread
  public CanvasRenderer getRenderer() {
    return canvasRenderer;
  }
}
