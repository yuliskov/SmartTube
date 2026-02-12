/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A time bar that shows a current position, buffered position, duration and ad markers.
 *
 * <p>A DefaultTimeBar can be customized by setting attributes, as outlined below.
 *
 * <h3>Attributes</h3>
 *
 * The following attributes can be set on a DefaultTimeBar when used in a layout XML file:
 *
 * <p>
 *
 * <ul>
 *   <li><b>{@code bar_height}</b> - Dimension for the height of the time bar.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_BAR_HEIGHT_DP}
 *       </ul>
 *   <li><b>{@code touch_target_height}</b> - Dimension for the height of the area in which touch
 *       interactions with the time bar are handled. If no height is specified, this also determines
 *       the height of the view.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_TOUCH_TARGET_HEIGHT_DP}
 *       </ul>
 *   <li><b>{@code ad_marker_width}</b> - Dimension for the width of any ad markers shown on the
 *       bar. Ad markers are superimposed on the time bar to show the times at which ads will play.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_AD_MARKER_WIDTH_DP}
 *       </ul>
 *   <li><b>{@code scrubber_enabled_size}</b> - Dimension for the diameter of the circular scrubber
 *       handle when scrubbing is enabled but not in progress. Set to zero if no scrubber handle
 *       should be shown.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_SCRUBBER_ENABLED_SIZE_DP}
 *       </ul>
 *   <li><b>{@code scrubber_disabled_size}</b> - Dimension for the diameter of the circular scrubber
 *       handle when scrubbing isn't enabled. Set to zero if no scrubber handle should be shown.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_SCRUBBER_DISABLED_SIZE_DP}
 *       </ul>
 *   <li><b>{@code scrubber_dragged_size}</b> - Dimension for the diameter of the circular scrubber
 *       handle when scrubbing is in progress. Set to zero if no scrubber handle should be shown.
 *       <ul>
 *         <li>Default: {@link #DEFAULT_SCRUBBER_DRAGGED_SIZE_DP}
 *       </ul>
 *   <li><b>{@code scrubber_drawable}</b> - Optional reference to a drawable to draw for the
 *       scrubber handle. If set, this overrides the default behavior, which is to draw a circle for
 *       the scrubber handle.
 *   <li><b>{@code played_color}</b> - Color for the portion of the time bar representing media
 *       before the current playback position.
 *       <ul>
 *         <li>Corresponding method: {@link #setPlayedColor(int)}
 *         <li>Default: {@link #DEFAULT_PLAYED_COLOR}
 *       </ul>
 *   <li><b>{@code scrubber_color}</b> - Color for the scrubber handle.
 *       <ul>
 *         <li>Corresponding method: {@link #setScrubberColor(int)}
 *         <li>Default: {@link #DEFAULT_SCRUBBER_COLOR}
 *       </ul>
 *   <li><b>{@code buffered_color}</b> - Color for the portion of the time bar after the current
 *       played position up to the current buffered position.
 *       <ul>
 *         <li>Corresponding method: {@link #setBufferedColor(int)}
 *         <li>Default: {@link #DEFAULT_BUFFERED_COLOR}
 *       </ul>
 *   <li><b>{@code unplayed_color}</b> - Color for the portion of the time bar after the current
 *       buffered position.
 *       <ul>
 *         <li>Corresponding method: {@link #setUnplayedColor(int)}
 *         <li>Default: {@link #DEFAULT_UNPLAYED_COLOR}
 *       </ul>
 *   <li><b>{@code ad_marker_color}</b> - Color for unplayed ad markers.
 *       <ul>
 *         <li>Corresponding method: {@link #setAdMarkerColor(int)}
 *         <li>Default: {@link #DEFAULT_AD_MARKER_COLOR}
 *       </ul>
 *   <li><b>{@code played_ad_marker_color}</b> - Color for played ad markers.
 *       <ul>
 *         <li>Corresponding method: {@link #setPlayedAdMarkerColor(int)}
 *         <li>Default: {@link #DEFAULT_PLAYED_AD_MARKER_COLOR}
 *       </ul>
 * </ul>
 */
public class DefaultTimeBar extends View implements TimeBar {

  /**
   * Default height for the time bar, in dp.
   */
  public static final int DEFAULT_BAR_HEIGHT_DP = 4;
  /**
   * Default height for the touch target, in dp.
   */
  public static final int DEFAULT_TOUCH_TARGET_HEIGHT_DP = 26;
  /**
   * Default width for ad markers, in dp.
   */
  public static final int DEFAULT_AD_MARKER_WIDTH_DP = 4;
  /**
   * Default diameter for the scrubber when enabled, in dp.
   */
  public static final int DEFAULT_SCRUBBER_ENABLED_SIZE_DP = 12;
  /**
   * Default diameter for the scrubber when disabled, in dp.
   */
  public static final int DEFAULT_SCRUBBER_DISABLED_SIZE_DP = 0;
  /**
   * Default diameter for the scrubber when dragged, in dp.
   */
  public static final int DEFAULT_SCRUBBER_DRAGGED_SIZE_DP = 16;
  /**
   * Default color for the played portion of the time bar.
   */
  public static final int DEFAULT_PLAYED_COLOR = 0xFFFFFFFF;
  /** Default color for the played portion of the time bar. */
  public static final int DEFAULT_UNPLAYED_COLOR = 0x33FFFFFF;
  /** Default color for the buffered portion of the time bar. */
  public static final int DEFAULT_BUFFERED_COLOR = 0xCCFFFFFF;
  /** Default color for the scrubber handle. */
  public static final int DEFAULT_SCRUBBER_COLOR = 0xFFFFFFFF;
  /** Default color for ad markers. */
  public static final int DEFAULT_AD_MARKER_COLOR = 0xB2FFFF00;
  /** Default color for played ad markers. */
  public static final int DEFAULT_PLAYED_AD_MARKER_COLOR = 0x33FFFF00;

  /**
   * The threshold in dps above the bar at which touch events trigger fine scrub mode.
   */
  private static final int FINE_SCRUB_Y_THRESHOLD_DP = -50;
  /**
   * The ratio by which times are reduced in fine scrub mode.
   */
  private static final int FINE_SCRUB_RATIO = 3;
  /**
   * The time after which the scrubbing listener is notified that scrubbing has stopped after
   * performing an incremental scrub using key input.
   */
  private static final long STOP_SCRUBBING_TIMEOUT_MS = 1000;
  private static final int DEFAULT_INCREMENT_COUNT = 20;

  /**
   * The name of the Android SDK view that most closely resembles this custom view. Used as the
   * class name for accessibility.
   */
  private static final String ACCESSIBILITY_CLASS_NAME = "android.widget.SeekBar";

  private final Rect seekBounds;
  private final Rect progressBar;
  private final Rect bufferedBar;
  private final Rect scrubberBar;
  private final Paint playedPaint;
  private final Paint bufferedPaint;
  private final Paint unplayedPaint;
  private final Paint adMarkerPaint;
  private final Paint playedAdMarkerPaint;
  private final Paint scrubberPaint;
  private final @Nullable Drawable scrubberDrawable;
  private final int barHeight;
  private final int touchTargetHeight;
  private final int adMarkerWidth;
  private final int scrubberEnabledSize;
  private final int scrubberDisabledSize;
  private final int scrubberDraggedSize;
  private final int scrubberPadding;
  private final int fineScrubYThreshold;
  private final StringBuilder formatBuilder;
  private final Formatter formatter;
  private final Runnable stopScrubbingRunnable;
  private final CopyOnWriteArraySet<OnScrubListener> listeners;
  private final int[] locationOnScreen;
  private final Point touchPosition;
  private final float density;

  private int keyCountIncrement;
  private long keyTimeIncrement;
  private int lastCoarseScrubXPosition;

  private boolean scrubbing;
  private long scrubPosition;
  private long duration;
  private long position;
  private long bufferedPosition;
  private int adGroupCount;
  private @Nullable long[] adGroupTimesMs;
  private @Nullable boolean[] playedAdGroups;

  public DefaultTimeBar(Context context) {
    this(context, null);
  }

  public DefaultTimeBar(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, attrs);
  }

  // Suppress warnings due to usage of View methods in the constructor.
  @SuppressWarnings("nullness:method.invocation.invalid")
  public DefaultTimeBar(
      Context context,
      @Nullable AttributeSet attrs,
      int defStyleAttr,
      @Nullable AttributeSet timebarAttrs) {
    super(context, attrs, defStyleAttr);
    seekBounds = new Rect();
    progressBar = new Rect();
    bufferedBar = new Rect();
    scrubberBar = new Rect();
    playedPaint = new Paint();
    bufferedPaint = new Paint();
    unplayedPaint = new Paint();
    adMarkerPaint = new Paint();
    playedAdMarkerPaint = new Paint();
    scrubberPaint = new Paint();
    scrubberPaint.setAntiAlias(true);
    listeners = new CopyOnWriteArraySet<>();
    locationOnScreen = new int[2];
    touchPosition = new Point();

    // Calculate the dimensions and paints for drawn elements.
    Resources res = context.getResources();
    DisplayMetrics displayMetrics = res.getDisplayMetrics();
    density = displayMetrics.density;
    fineScrubYThreshold = dpToPx(density, FINE_SCRUB_Y_THRESHOLD_DP);
    int defaultBarHeight = dpToPx(density, DEFAULT_BAR_HEIGHT_DP);
    int defaultTouchTargetHeight = dpToPx(density, DEFAULT_TOUCH_TARGET_HEIGHT_DP);
    int defaultAdMarkerWidth = dpToPx(density, DEFAULT_AD_MARKER_WIDTH_DP);
    int defaultScrubberEnabledSize = dpToPx(density, DEFAULT_SCRUBBER_ENABLED_SIZE_DP);
    int defaultScrubberDisabledSize = dpToPx(density, DEFAULT_SCRUBBER_DISABLED_SIZE_DP);
    int defaultScrubberDraggedSize = dpToPx(density, DEFAULT_SCRUBBER_DRAGGED_SIZE_DP);
    if (timebarAttrs != null) {
      TypedArray a =
          context.getTheme().obtainStyledAttributes(timebarAttrs, R.styleable.DefaultTimeBar, 0, 0);
      try {
        scrubberDrawable = a.getDrawable(R.styleable.DefaultTimeBar_scrubber_drawable);
        if (scrubberDrawable != null) {
          setDrawableLayoutDirection(scrubberDrawable);
          defaultTouchTargetHeight =
              Math.max(scrubberDrawable.getMinimumHeight(), defaultTouchTargetHeight);
        }
        barHeight = a.getDimensionPixelSize(R.styleable.DefaultTimeBar_bar_height,
            defaultBarHeight);
        touchTargetHeight = a.getDimensionPixelSize(R.styleable.DefaultTimeBar_touch_target_height,
            defaultTouchTargetHeight);
        adMarkerWidth = a.getDimensionPixelSize(R.styleable.DefaultTimeBar_ad_marker_width,
            defaultAdMarkerWidth);
        scrubberEnabledSize = a.getDimensionPixelSize(
            R.styleable.DefaultTimeBar_scrubber_enabled_size, defaultScrubberEnabledSize);
        scrubberDisabledSize = a.getDimensionPixelSize(
            R.styleable.DefaultTimeBar_scrubber_disabled_size, defaultScrubberDisabledSize);
        scrubberDraggedSize = a.getDimensionPixelSize(
            R.styleable.DefaultTimeBar_scrubber_dragged_size, defaultScrubberDraggedSize);
        int playedColor = a.getInt(R.styleable.DefaultTimeBar_played_color, DEFAULT_PLAYED_COLOR);
        int scrubberColor =
            a.getInt(R.styleable.DefaultTimeBar_scrubber_color, DEFAULT_SCRUBBER_COLOR);
        int bufferedColor =
            a.getInt(R.styleable.DefaultTimeBar_buffered_color, DEFAULT_BUFFERED_COLOR);
        int unplayedColor =
            a.getInt(R.styleable.DefaultTimeBar_unplayed_color, DEFAULT_UNPLAYED_COLOR);
        int adMarkerColor = a.getInt(R.styleable.DefaultTimeBar_ad_marker_color,
            DEFAULT_AD_MARKER_COLOR);
        int playedAdMarkerColor =
            a.getInt(
                R.styleable.DefaultTimeBar_played_ad_marker_color, DEFAULT_PLAYED_AD_MARKER_COLOR);
        playedPaint.setColor(playedColor);
        scrubberPaint.setColor(scrubberColor);
        bufferedPaint.setColor(bufferedColor);
        unplayedPaint.setColor(unplayedColor);
        adMarkerPaint.setColor(adMarkerColor);
        playedAdMarkerPaint.setColor(playedAdMarkerColor);
      } finally {
        a.recycle();
      }
    } else {
      barHeight = defaultBarHeight;
      touchTargetHeight = defaultTouchTargetHeight;
      adMarkerWidth = defaultAdMarkerWidth;
      scrubberEnabledSize = defaultScrubberEnabledSize;
      scrubberDisabledSize = defaultScrubberDisabledSize;
      scrubberDraggedSize = defaultScrubberDraggedSize;
      playedPaint.setColor(DEFAULT_PLAYED_COLOR);
      scrubberPaint.setColor(DEFAULT_SCRUBBER_COLOR);
      bufferedPaint.setColor(DEFAULT_BUFFERED_COLOR);
      unplayedPaint.setColor(DEFAULT_UNPLAYED_COLOR);
      adMarkerPaint.setColor(DEFAULT_AD_MARKER_COLOR);
      playedAdMarkerPaint.setColor(DEFAULT_PLAYED_AD_MARKER_COLOR);
      scrubberDrawable = null;
    }
    formatBuilder = new StringBuilder();
    formatter = new Formatter(formatBuilder, Locale.getDefault());
    stopScrubbingRunnable = () -> stopScrubbing(/* canceled= */ false);
    if (scrubberDrawable != null) {
      scrubberPadding = (scrubberDrawable.getMinimumWidth() + 1) / 2;
    } else {
      scrubberPadding =
          (Math.max(scrubberDisabledSize, Math.max(scrubberEnabledSize, scrubberDraggedSize)) + 1)
              / 2;
    }
    duration = C.TIME_UNSET;
    keyTimeIncrement = C.TIME_UNSET;
    keyCountIncrement = DEFAULT_INCREMENT_COUNT;
    setFocusable(true);
    if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
  }

  /**
   * Sets the color for the portion of the time bar representing media before the playback position.
   *
   * @param playedColor The color for the portion of the time bar representing media before the
   *     playback position.
   */
  public void setPlayedColor(@ColorInt int playedColor) {
    playedPaint.setColor(playedColor);
    invalidate(seekBounds);
  }

  /**
   * Sets the color for the scrubber handle.
   *
   * @param scrubberColor The color for the scrubber handle.
   */
  public void setScrubberColor(@ColorInt int scrubberColor) {
    scrubberPaint.setColor(scrubberColor);
    invalidate(seekBounds);
  }

  /**
   * Sets the color for the portion of the time bar after the current played position up to the
   * current buffered position.
   *
   * @param bufferedColor The color for the portion of the time bar after the current played
   *     position up to the current buffered position.
   */
  public void setBufferedColor(@ColorInt int bufferedColor) {
    bufferedPaint.setColor(bufferedColor);
    invalidate(seekBounds);
  }

  /**
   * Sets the color for the portion of the time bar after the current played position.
   *
   * @param unplayedColor The color for the portion of the time bar after the current played
   *     position.
   */
  public void setUnplayedColor(@ColorInt int unplayedColor) {
    unplayedPaint.setColor(unplayedColor);
    invalidate(seekBounds);
  }

  /**
   * Sets the color for unplayed ad markers.
   *
   * @param adMarkerColor The color for unplayed ad markers.
   */
  public void setAdMarkerColor(@ColorInt int adMarkerColor) {
    adMarkerPaint.setColor(adMarkerColor);
    invalidate(seekBounds);
  }

  /**
   * Sets the color for played ad markers.
   *
   * @param playedAdMarkerColor The color for played ad markers.
   */
  public void setPlayedAdMarkerColor(@ColorInt int playedAdMarkerColor) {
    playedAdMarkerPaint.setColor(playedAdMarkerColor);
    invalidate(seekBounds);
  }

  // TimeBar implementation.

  @Override
  public void addListener(OnScrubListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(OnScrubListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void setKeyTimeIncrement(long time) {
    Assertions.checkArgument(time > 0);
    keyCountIncrement = C.INDEX_UNSET;
    keyTimeIncrement = time;
  }

  @Override
  public void setKeyCountIncrement(int count) {
    Assertions.checkArgument(count > 0);
    keyCountIncrement = count;
    keyTimeIncrement = C.TIME_UNSET;
  }

  @Override
  public void setPosition(long position) {
    this.position = position;
    setContentDescription(getProgressText());
    update();
  }

  @Override
  public void setBufferedPosition(long bufferedPosition) {
    this.bufferedPosition = bufferedPosition;
    update();
  }

  @Override
  public void setDuration(long duration) {
    this.duration = duration;
    if (scrubbing && duration == C.TIME_UNSET) {
      stopScrubbing(/* canceled= */ true);
    }
    update();
  }

  @Override
  public long getPreferredUpdateDelay() {
    int timeBarWidthDp = pxToDp(density, progressBar.width());
    return timeBarWidthDp == 0 || duration == 0 || duration == C.TIME_UNSET
        ? Long.MAX_VALUE
        : duration / timeBarWidthDp;
  }

  @Override
  public void setAdGroupTimesMs(@Nullable long[] adGroupTimesMs, @Nullable boolean[] playedAdGroups,
      int adGroupCount) {
    Assertions.checkArgument(adGroupCount == 0
        || (adGroupTimesMs != null && playedAdGroups != null));
    this.adGroupCount = adGroupCount;
    this.adGroupTimesMs = adGroupTimesMs;
    this.playedAdGroups = playedAdGroups;
    update();
  }

  // View methods.

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (scrubbing && !enabled) {
      stopScrubbing(/* canceled= */ true);
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    canvas.save();
    drawTimeBar(canvas);
    drawPlayhead(canvas);
    canvas.restore();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled() || duration <= 0) {
      return false;
    }
    Point touchPosition = resolveRelativeTouchPosition(event);
    int x = touchPosition.x;
    int y = touchPosition.y;
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (isInSeekBar(x, y)) {
          positionScrubber(x);
          startScrubbing(getScrubberPosition());
          update();
          invalidate();
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        if (scrubbing) {
          if (y < fineScrubYThreshold) {
            int relativeX = x - lastCoarseScrubXPosition;
            positionScrubber(lastCoarseScrubXPosition + relativeX / FINE_SCRUB_RATIO);
          } else {
            lastCoarseScrubXPosition = x;
            positionScrubber(x);
          }
          updateScrubbing(getScrubberPosition());
          update();
          invalidate();
          return true;
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (scrubbing) {
          stopScrubbing(/* canceled= */ event.getAction() == MotionEvent.ACTION_CANCEL);
          return true;
        }
        break;
      default:
        // Do nothing.
    }
    return false;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isEnabled()) {
      long positionIncrement = getPositionIncrement();
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          positionIncrement = -positionIncrement;
          // Fall through.
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          if (scrubIncrementally(positionIncrement)) {
            removeCallbacks(stopScrubbingRunnable);
            postDelayed(stopScrubbingRunnable, STOP_SCRUBBING_TIMEOUT_MS);
            return true;
          }
          break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
          if (scrubbing) {
            stopScrubbing(/* canceled= */ false);
            return true;
          }
          break;
        default:
          // Do nothing.
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onFocusChanged(
      boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    if (scrubbing && !gainFocus) {
      stopScrubbing(/* canceled= */ false);
    }
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    updateDrawableState();
  }

  @Override
  public void jumpDrawablesToCurrentState() {
    super.jumpDrawablesToCurrentState();
    if (scrubberDrawable != null) {
      scrubberDrawable.jumpToCurrentState();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int height = heightMode == MeasureSpec.UNSPECIFIED ? touchTargetHeight
        : heightMode == MeasureSpec.EXACTLY ? heightSize : Math.min(touchTargetHeight, heightSize);
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    updateDrawableState();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int width = right - left;
    int height = bottom - top;
    int barY = (height - touchTargetHeight) / 2;
    int seekLeft = getPaddingLeft();
    int seekRight = width - getPaddingRight();
    int progressY = barY + (touchTargetHeight - barHeight) / 2;
    seekBounds.set(seekLeft, barY, seekRight, barY + touchTargetHeight);
    progressBar.set(seekBounds.left + scrubberPadding, progressY,
        seekBounds.right - scrubberPadding, progressY + barHeight);
    update();
  }

  @Override
  public void onRtlPropertiesChanged(int layoutDirection) {
    if (scrubberDrawable != null && setDrawableLayoutDirection(scrubberDrawable, layoutDirection)) {
      invalidate();
    }
  }

  @Override
  public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
    super.onInitializeAccessibilityEvent(event);
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED) {
      event.getText().add(getProgressText());
    }
    event.setClassName(ACCESSIBILITY_CLASS_NAME);
  }

  @TargetApi(21)
  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(ACCESSIBILITY_CLASS_NAME);
    info.setContentDescription(getProgressText());
    if (duration <= 0) {
      return;
    }
    if (Util.SDK_INT >= 21) {
      info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD);
      info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD);
    } else {
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }
  }

  @Override
  public boolean performAccessibilityAction(int action, @Nullable Bundle args) {
    if (super.performAccessibilityAction(action, args)) {
      return true;
    }
    if (duration <= 0) {
      return false;
    }
    if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      if (scrubIncrementally(-getPositionIncrement())) {
        stopScrubbing(/* canceled= */ false);
      }
    } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
      if (scrubIncrementally(getPositionIncrement())) {
        stopScrubbing(/* canceled= */ false);
      }
    } else {
      return false;
    }
    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    return true;
  }

  // Internal methods.

  private void startScrubbing(long scrubPosition) {
    this.scrubPosition = scrubPosition;
    scrubbing = true;
    setPressed(true);
    ViewParent parent = getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
    for (OnScrubListener listener : listeners) {
      listener.onScrubStart(this, scrubPosition);
    }
  }

  private void updateScrubbing(long scrubPosition) {
    if (this.scrubPosition == scrubPosition) {
      return;
    }
    this.scrubPosition = scrubPosition;
    for (OnScrubListener listener : listeners) {
      listener.onScrubMove(this, scrubPosition);
    }
  }

  private void stopScrubbing(boolean canceled) {
    removeCallbacks(stopScrubbingRunnable);
    scrubbing = false;
    setPressed(false);
    ViewParent parent = getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(false);
    }
    invalidate();
    for (OnScrubListener listener : listeners) {
      listener.onScrubStop(this, scrubPosition, canceled);
    }
  }

  /**
   * Incrementally scrubs the position by {@code positionChange}.
   *
   * @param positionChange The change in the scrubber position, in milliseconds. May be negative.
   * @return Returns whether the scrubber position changed.
   */
  private boolean scrubIncrementally(long positionChange) {
    if (duration <= 0) {
      return false;
    }
    long previousPosition = scrubbing ? scrubPosition : position;
    long scrubPosition = Util.constrainValue(previousPosition + positionChange, 0, duration);
    if (scrubPosition == previousPosition) {
      return false;
    }
    if (!scrubbing) {
      startScrubbing(scrubPosition);
    } else {
      updateScrubbing(scrubPosition);
    }
    update();
    return true;
  }

  private void update() {
    bufferedBar.set(progressBar);
    scrubberBar.set(progressBar);
    long newScrubberTime = scrubbing ? scrubPosition : position;
    if (duration > 0) {
      int bufferedPixelWidth = (int) ((progressBar.width() * bufferedPosition) / duration);
      bufferedBar.right = Math.min(progressBar.left + bufferedPixelWidth, progressBar.right);
      int scrubberPixelPosition = (int) ((progressBar.width() * newScrubberTime) / duration);
      scrubberBar.right = Math.min(progressBar.left + scrubberPixelPosition, progressBar.right);
    } else {
      bufferedBar.right = progressBar.left;
      scrubberBar.right = progressBar.left;
    }
    invalidate(seekBounds);
  }

  private void positionScrubber(float xPosition) {
    scrubberBar.right = Util.constrainValue((int) xPosition, progressBar.left, progressBar.right);
  }

  private Point resolveRelativeTouchPosition(MotionEvent motionEvent) {
    getLocationOnScreen(locationOnScreen);
    touchPosition.set(
        ((int) motionEvent.getRawX()) - locationOnScreen[0],
        ((int) motionEvent.getRawY()) - locationOnScreen[1]);
    return touchPosition;
  }

  private long getScrubberPosition() {
    if (progressBar.width() <= 0 || duration == C.TIME_UNSET) {
      return 0;
    }
    return (scrubberBar.width() * duration) / progressBar.width();
  }

  private boolean isInSeekBar(float x, float y) {
    return seekBounds.contains((int) x, (int) y);
  }

  private void drawTimeBar(Canvas canvas) {
    int progressBarHeight = progressBar.height();
    int barTop = progressBar.centerY() - progressBarHeight / 2;
    int barBottom = barTop + progressBarHeight;
    if (duration <= 0) {
      canvas.drawRect(progressBar.left, barTop, progressBar.right, barBottom, unplayedPaint);
      return;
    }
    int bufferedLeft = bufferedBar.left;
    int bufferedRight = bufferedBar.right;
    int progressLeft = Math.max(Math.max(progressBar.left, bufferedRight), scrubberBar.right);
    if (progressLeft < progressBar.right) {
      canvas.drawRect(progressLeft, barTop, progressBar.right, barBottom, unplayedPaint);
    }
    bufferedLeft = Math.max(bufferedLeft, scrubberBar.right);
    if (bufferedRight > bufferedLeft) {
      canvas.drawRect(bufferedLeft, barTop, bufferedRight, barBottom, bufferedPaint);
    }
    if (scrubberBar.width() > 0) {
      canvas.drawRect(scrubberBar.left, barTop, scrubberBar.right, barBottom, playedPaint);
    }
    if (adGroupCount == 0) {
      return;
    }
    long[] adGroupTimesMs = Assertions.checkNotNull(this.adGroupTimesMs);
    boolean[] playedAdGroups = Assertions.checkNotNull(this.playedAdGroups);
    int adMarkerOffset = adMarkerWidth / 2;
    for (int i = 0; i < adGroupCount; i++) {
      long adGroupTimeMs = Util.constrainValue(adGroupTimesMs[i], 0, duration);
      int markerPositionOffset =
          (int) (progressBar.width() * adGroupTimeMs / duration) - adMarkerOffset;
      int markerLeft = progressBar.left + Math.min(progressBar.width() - adMarkerWidth,
          Math.max(0, markerPositionOffset));
      Paint paint = playedAdGroups[i] ? playedAdMarkerPaint : adMarkerPaint;
      canvas.drawRect(markerLeft, barTop, markerLeft + adMarkerWidth, barBottom, paint);
    }
  }

  private void drawPlayhead(Canvas canvas) {
    if (duration <= 0) {
      return;
    }
    int playheadX = Util.constrainValue(scrubberBar.right, scrubberBar.left, progressBar.right);
    int playheadY = scrubberBar.centerY();
    if (scrubberDrawable == null) {
      int scrubberSize = (scrubbing || isFocused()) ? scrubberDraggedSize
          : (isEnabled() ? scrubberEnabledSize : scrubberDisabledSize);
      int playheadRadius = scrubberSize / 2;
      canvas.drawCircle(playheadX, playheadY, playheadRadius, scrubberPaint);
    } else {
      int scrubberDrawableWidth = scrubberDrawable.getIntrinsicWidth();
      int scrubberDrawableHeight = scrubberDrawable.getIntrinsicHeight();
      scrubberDrawable.setBounds(
          playheadX - scrubberDrawableWidth / 2,
          playheadY - scrubberDrawableHeight / 2,
          playheadX + scrubberDrawableWidth / 2,
          playheadY + scrubberDrawableHeight / 2);
      scrubberDrawable.draw(canvas);
    }
  }

  private void updateDrawableState() {
    if (scrubberDrawable != null && scrubberDrawable.isStateful()
        && scrubberDrawable.setState(getDrawableState())) {
      invalidate();
    }
  }

  private String getProgressText() {
    return Util.getStringForTime(formatBuilder, formatter, position);
  }

  private long getPositionIncrement() {
    return keyTimeIncrement == C.TIME_UNSET
        ? (duration == C.TIME_UNSET ? 0 : (duration / keyCountIncrement)) : keyTimeIncrement;
  }

  private boolean setDrawableLayoutDirection(Drawable drawable) {
    return Util.SDK_INT >= 23 && setDrawableLayoutDirection(drawable, getLayoutDirection());
  }

  private static boolean setDrawableLayoutDirection(Drawable drawable, int layoutDirection) {
    return Util.SDK_INT >= 23 && drawable.setLayoutDirection(layoutDirection);
  }

  private static int dpToPx(float density, int dps) {
    return (int) (dps * density + 0.5f);
  }

  private static int pxToDp(float density, int px) {
    return (int) (px / density);
  }
}
