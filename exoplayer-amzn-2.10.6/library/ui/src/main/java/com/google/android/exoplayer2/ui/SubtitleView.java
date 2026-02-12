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
package com.google.android.exoplayer2.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * A view for displaying subtitle {@link Cue}s.
 */
public final class SubtitleView extends View implements TextOutput {

  /**
   * The default fractional text size.
   *
   * @see #setFractionalTextSize(float, boolean)
   */
  public static final float DEFAULT_TEXT_SIZE_FRACTION = 0.0533f;

  /**
   * The default bottom padding to apply when {@link Cue#line} is {@link Cue#DIMEN_UNSET}, as a
   * fraction of the viewport height.
   *
   * @see #setBottomPaddingFraction(float)
   */
  public static final float DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f;

  private final List<SubtitlePainter> painters;

  @Nullable private List<Cue> cues;
  @Cue.TextSizeType private int textSizeType;
  private float textSize;
  private boolean applyEmbeddedStyles;
  private boolean applyEmbeddedFontSizes;
  private CaptionStyleCompat style;
  private float bottomPaddingFraction;

  public SubtitleView(Context context) {
    this(context, /* attrs= */ null);
  }

  public SubtitleView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    painters = new ArrayList<>();
    textSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    textSize = DEFAULT_TEXT_SIZE_FRACTION;
    applyEmbeddedStyles = true;
    applyEmbeddedFontSizes = true;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;
  }

  @Override
  public void onCues(List<Cue> cues) {
    setCues(cues);
  }

  /**
   * Sets the cues to be displayed by the view.
   *
   * @param cues The cues to display, or null to clear the cues.
   */
  public void setCues(@Nullable List<Cue> cues) {
    // MOD: fix overlapped subs
    // All cues are rendered simultaneously, so remain only one of them
    if (cues != null && cues.size() > 1) {
      cues = cues.subList(cues.size() - 1, cues.size());
    }

    if (this.cues == cues) {
      return;
    }
    this.cues = cues;
    // Ensure we have sufficient painters.
    int cueCount = (cues == null) ? 0 : cues.size();
    while (painters.size() < cueCount) {
      painters.add(new SubtitlePainter(getContext()));
    }
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Set the text size to a given unit and value.
   * <p>
   * See {@link TypedValue} for the possible dimension units.
   *
   * @param unit The desired dimension unit.
   * @param size The desired size in the given units.
   */
  public void setFixedTextSize(int unit, float size) {
    Context context = getContext();
    Resources resources;
    if (context == null) {
      resources = Resources.getSystem();
    } else {
      resources = context.getResources();
    }
    setTextSize(
        Cue.TEXT_SIZE_TYPE_ABSOLUTE,
        TypedValue.applyDimension(unit, size, resources.getDisplayMetrics()));
  }

  /**
   * Sets the text size to one derived from {@link CaptioningManager#getFontScale()}, or to a
   * default size before API level 19.
   */
  public void setUserDefaultTextSize() {
    float fontScale = Util.SDK_INT >= 19 && !isInEditMode() ? getUserCaptionFontScaleV19() : 1f;
    setFractionalTextSize(DEFAULT_TEXT_SIZE_FRACTION * fontScale);
  }

  /**
   * Sets the text size to be a fraction of the view's remaining height after its top and bottom
   * padding have been subtracted.
   * <p>
   * Equivalent to {@code #setFractionalTextSize(fractionOfHeight, false)}.
   *
   * @param fractionOfHeight A fraction between 0 and 1.
   */
  public void setFractionalTextSize(float fractionOfHeight) {
    setFractionalTextSize(fractionOfHeight, false);
  }

  /**
   * Sets the text size to be a fraction of the height of this view.
   *
   * @param fractionOfHeight A fraction between 0 and 1.
   * @param ignorePadding Set to true if {@code fractionOfHeight} should be interpreted as a
   *     fraction of this view's height ignoring any top and bottom padding. Set to false if
   *     {@code fractionOfHeight} should be interpreted as a fraction of this view's remaining
   *     height after the top and bottom padding has been subtracted.
   */
  public void setFractionalTextSize(float fractionOfHeight, boolean ignorePadding) {
    setTextSize(
        ignorePadding
            ? Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING
            : Cue.TEXT_SIZE_TYPE_FRACTIONAL,
        fractionOfHeight);
  }

  private void setTextSize(@Cue.TextSizeType int textSizeType, float textSize) {
    if (this.textSizeType == textSizeType && this.textSize == textSize) {
      return;
    }
    this.textSizeType = textSizeType;
    this.textSize = textSize;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets whether styling embedded within the cues should be applied. Enabled by default.
   * Overrides any setting made with {@link SubtitleView#setApplyEmbeddedFontSizes}.
   *
   * @param applyEmbeddedStyles Whether styling embedded within the cues should be applied.
   */
  public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
    if (this.applyEmbeddedStyles == applyEmbeddedStyles
        && this.applyEmbeddedFontSizes == applyEmbeddedStyles) {
      return;
    }
    this.applyEmbeddedStyles = applyEmbeddedStyles;
    this.applyEmbeddedFontSizes = applyEmbeddedStyles;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets whether font sizes embedded within the cues should be applied. Enabled by default.
   * Only takes effect if {@link SubtitleView#setApplyEmbeddedStyles} is set to true.
   *
   * @param applyEmbeddedFontSizes Whether font sizes embedded within the cues should be applied.
   */
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    if (this.applyEmbeddedFontSizes == applyEmbeddedFontSizes) {
      return;
    }
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets the caption style to be equivalent to the one returned by
   * {@link CaptioningManager#getUserStyle()}, or to a default style before API level 19.
   */
  public void setUserDefaultStyle() {
    setStyle(
        Util.SDK_INT >= 19 && isCaptionManagerEnabled() && !isInEditMode()
            ? getUserCaptionStyleV19()
            : CaptionStyleCompat.DEFAULT);
  }

  /**
   * Sets the caption style.
   *
   * @param style A style for the view.
   */
  public void setStyle(CaptionStyleCompat style) {
    if (this.style == style) {
      return;
    }
    this.style = style;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets the bottom padding fraction to apply when {@link Cue#line} is {@link Cue#DIMEN_UNSET},
   * as a fraction of the view's remaining height after its top and bottom padding have been
   * subtracted.
   * <p>
   * Note that this padding is applied in addition to any standard view padding.
   *
   * @param bottomPaddingFraction The bottom padding fraction.
   */
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    if (this.bottomPaddingFraction == bottomPaddingFraction) {
      return;
    }
    this.bottomPaddingFraction = bottomPaddingFraction;
    // Invalidate to trigger drawing.
    invalidate();
  }

  @Override
  public void dispatchDraw(Canvas canvas) {
    List<Cue> cues = this.cues;
    if (cues == null || cues.isEmpty()) {
      return;
    }

    int rawViewHeight = getHeight();

    // Calculate the cue box bounds relative to the canvas after padding is taken into account.
    int left = getPaddingLeft();
    int top = getPaddingTop();
    int right = getWidth() - getPaddingRight();
    int bottom = rawViewHeight - getPaddingBottom();
    if (bottom <= top || right <= left) {
      // No space to draw subtitles.
      return;
    }
    int viewHeightMinusPadding = bottom - top;

    float defaultViewTextSizePx =
        resolveTextSize(textSizeType, textSize, rawViewHeight, viewHeightMinusPadding);
    if (defaultViewTextSizePx <= 0) {
      // Text has no height.
      return;
    }

    int cueCount = cues.size();
    for (int i = 0; i < cueCount; i++) {
      Cue cue = cues.get(i);
      float cueTextSizePx = resolveCueTextSize(cue, rawViewHeight, viewHeightMinusPadding);
      SubtitlePainter painter = painters.get(i);
      painter.draw(
          cue,
          applyEmbeddedStyles,
          applyEmbeddedFontSizes,
          style,
          defaultViewTextSizePx,
          cueTextSizePx,
          bottomPaddingFraction,
          canvas,
          left,
          top,
          right,
          bottom);
    }
  }

  private float resolveCueTextSize(Cue cue, int rawViewHeight, int viewHeightMinusPadding) {
    if (cue.textSizeType == Cue.TYPE_UNSET || cue.textSize == Cue.DIMEN_UNSET) {
      return 0;
    }
    float defaultCueTextSizePx =
        resolveTextSize(cue.textSizeType, cue.textSize, rawViewHeight, viewHeightMinusPadding);
    return Math.max(defaultCueTextSizePx, 0);
  }

  private float resolveTextSize(
      @Cue.TextSizeType int textSizeType,
      float textSize,
      int rawViewHeight,
      int viewHeightMinusPadding) {
    switch (textSizeType) {
      case Cue.TEXT_SIZE_TYPE_ABSOLUTE:
        return textSize;
      case Cue.TEXT_SIZE_TYPE_FRACTIONAL:
        return textSize * viewHeightMinusPadding;
      case Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING:
        return textSize * rawViewHeight;
      case Cue.TYPE_UNSET:
      default:
        return Cue.DIMEN_UNSET;
    }
  }

  @TargetApi(19)
  private boolean isCaptionManagerEnabled() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.isEnabled();
  }

  @TargetApi(19)
  private float getUserCaptionFontScaleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.getFontScale();
  }

  @TargetApi(19)
  private CaptionStyleCompat getUserCaptionStyleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getContext().getSystemService(Context.CAPTIONING_SERVICE);
    return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
  }

}
