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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Annotation;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.misc.PaddingBackgroundColorSpan;
import com.liskovsoft.sharedutils.misc.RoundedBackgroundSpan;

/**
 * Paints subtitle {@link Cue}s.
 */
/* package */ final class SubtitlePainter {

  private static final String TAG = "SubtitlePainter";

  /**
   * Ratio of inner padding to font size.
   */
  private static final float INNER_PADDING_RATIO = 0.125f;

  // Styled dimensions.
  private final float outlineWidth;
  private final float shadowRadius;
  private final float shadowOffset;
  private final float spacingMult;
  private final float spacingAdd;

  private final TextPaint textPaint;
  private final TextPaint translationLinePaint;
  private final Paint paint;

  // Previous input variables.
  private CharSequence cueText;
  private Alignment cueTextAlignment;
  private Bitmap cueBitmap;
  private float cueLine;
  @Cue.LineType
  private int cueLineType;
  @Cue.AnchorType
  private int cueLineAnchor;
  private float cuePosition;
  @Cue.AnchorType
  private int cuePositionAnchor;
  private float cueSize;
  private float cueBitmapHeight;
  private boolean applyEmbeddedStyles;
  private boolean applyEmbeddedFontSizes;
  private int foregroundColor;
  private int backgroundColor;
  private int windowColor;
  private int edgeColor;
  @CaptionStyleCompat.EdgeType
  private int edgeType;
  private float defaultTextSizePx;
  private float cueTextSizePx;
  private float bottomPaddingFraction;
  private int parentLeft;
  private int parentTop;
  private int parentRight;
  private int parentBottom;

  // Derived drawing variables.
  private StaticLayout textLayout;
  /** Non-null when {@link #trySetupSmartTubeDualSubtitleLayouts} handles the cue. */
  private StaticLayout translationLayout;
  private int translationGapPx;
  private int textLeft;
  private int textTop;
  private int textPaddingX;
  private Rect bitmapRect;

  @SuppressWarnings("ResourceType")
  public SubtitlePainter(Context context) {
    int[] viewAttr = {android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier};
    TypedArray styledAttributes = context.obtainStyledAttributes(null, viewAttr, 0, 0);
    spacingAdd = styledAttributes.getDimensionPixelSize(0, 0);
    spacingMult = styledAttributes.getFloat(1, 1);
    styledAttributes.recycle();

    Resources resources = context.getResources();
    DisplayMetrics displayMetrics = resources.getDisplayMetrics();
    int twoDpInPx = Math.round((2f * displayMetrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
    outlineWidth = twoDpInPx;
    shadowRadius = twoDpInPx;
    shadowOffset = twoDpInPx;

    textPaint = new TextPaint();
    textPaint.setAntiAlias(true);
    textPaint.setSubpixelText(true);

    translationLinePaint = new TextPaint();
    translationLinePaint.setAntiAlias(true);
    translationLinePaint.setSubpixelText(true);

    paint = new Paint();
    paint.setAntiAlias(true);
    paint.setStyle(Style.FILL);
  }

  /**
   * Draws the provided {@link Cue} into a canvas with the specified styling.
   *
   * <p>A call to this method is able to use cached results of calculations made during the previous
   * call, and so an instance of this class is able to optimize repeated calls to this method in
   * which the same parameters are passed.
   *
   * @param cue The cue to draw.
   * @param applyEmbeddedStyles Whether styling embedded within the cue should be applied.
   * @param applyEmbeddedFontSizes If {@code applyEmbeddedStyles} is true, defines whether font
   *     sizes embedded within the cue should be applied. Otherwise, it is ignored.
   * @param style The style to use when drawing the cue text.
   * @param defaultTextSizePx The default text size to use when drawing the text, in pixels.
   * @param cueTextSizePx The embedded text size of this cue, in pixels.
   * @param bottomPaddingFraction The bottom padding fraction to apply when {@link Cue#line} is
   *     {@link Cue#DIMEN_UNSET}, as a fraction of the viewport height
   * @param canvas The canvas into which to draw.
   * @param cueBoxLeft The left position of the enclosing cue box.
   * @param cueBoxTop The top position of the enclosing cue box.
   * @param cueBoxRight The right position of the enclosing cue box.
   * @param cueBoxBottom The bottom position of the enclosing cue box.
   */
  public void draw(
      Cue cue,
      boolean applyEmbeddedStyles,
      boolean applyEmbeddedFontSizes,
      CaptionStyleCompat style,
      float defaultTextSizePx,
      float cueTextSizePx,
      float bottomPaddingFraction,
      Canvas canvas,
      int cueBoxLeft,
      int cueBoxTop,
      int cueBoxRight,
      int cueBoxBottom) {
    boolean isTextCue = cue.bitmap == null;
    int windowColor = Color.BLACK;
    if (isTextCue) {
      if (TextUtils.isEmpty(cue.text)) {
        // Nothing to draw.
        return;
      }
      windowColor = (cue.windowColorSet && applyEmbeddedStyles)
          ? cue.windowColor : style.windowColor;
    }
    if (areCharSequencesEqual(this.cueText, cue.text)
        && Util.areEqual(this.cueTextAlignment, cue.textAlignment)
        && this.cueBitmap == cue.bitmap
        && this.cueLine == cue.line
        && this.cueLineType == cue.lineType
        && Util.areEqual(this.cueLineAnchor, cue.lineAnchor)
        && this.cuePosition == cue.position
        && Util.areEqual(this.cuePositionAnchor, cue.positionAnchor)
        && this.cueSize == cue.size
        && this.cueBitmapHeight == cue.bitmapHeight
        && this.applyEmbeddedStyles == applyEmbeddedStyles
        && this.applyEmbeddedFontSizes == applyEmbeddedFontSizes
        && this.foregroundColor == style.foregroundColor
        && this.backgroundColor == style.backgroundColor
        && this.windowColor == windowColor
        && this.edgeType == style.edgeType
        && this.edgeColor == style.edgeColor
        && Util.areEqual(this.textPaint.getTypeface(), style.typeface)
        && this.defaultTextSizePx == defaultTextSizePx
        && this.cueTextSizePx == cueTextSizePx
        && this.bottomPaddingFraction == bottomPaddingFraction
        && this.parentLeft == cueBoxLeft
        && this.parentTop == cueBoxTop
        && this.parentRight == cueBoxRight
        && this.parentBottom == cueBoxBottom) {
      // We can use the cached layout.
      drawLayout(canvas, isTextCue);
      return;
    }

    this.cueText = cue.text;
    this.cueTextAlignment = cue.textAlignment;
    this.cueBitmap = cue.bitmap;
    this.cueLine = cue.line;
    this.cueLineType = cue.lineType;
    this.cueLineAnchor = cue.lineAnchor;
    this.cuePosition = cue.position;
    this.cuePositionAnchor = cue.positionAnchor;
    this.cueSize = cue.size;
    this.cueBitmapHeight = cue.bitmapHeight;
    this.applyEmbeddedStyles = applyEmbeddedStyles;
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    this.foregroundColor = style.foregroundColor;
    this.backgroundColor = style.backgroundColor;
    this.windowColor = windowColor;
    this.edgeType = style.edgeType;
    this.edgeColor = style.edgeColor;
    this.textPaint.setTypeface(style.typeface);
    this.defaultTextSizePx = defaultTextSizePx;
    this.cueTextSizePx = cueTextSizePx;
    this.bottomPaddingFraction = bottomPaddingFraction;
    this.parentLeft = cueBoxLeft;
    this.parentTop = cueBoxTop;
    this.parentRight = cueBoxRight;
    this.parentBottom = cueBoxBottom;

    if (isTextCue) {
      setupTextLayout();
    } else {
      setupBitmapLayout();
    }
    drawLayout(canvas, isTextCue);
  }

  private void setupTextLayout() {
    translationLayout = null;
    translationGapPx = 0;

    int parentWidth = parentRight - parentLeft;
    int parentHeight = parentBottom - parentTop;

    textPaint.setTextSize(defaultTextSizePx);
    int textPaddingX = (int) (defaultTextSizePx * INNER_PADDING_RATIO + 0.5f);

    int availableWidth = parentWidth - textPaddingX * 2;
    if (cueSize != Cue.DIMEN_UNSET) {
      availableWidth = (int) (availableWidth * cueSize);
    }
    if (availableWidth <= 0) {
      Log.w(TAG, "Skipped drawing subtitle cue (insufficient space)");
      return;
    }

    if (trySetupSmartTubeDualSubtitleLayouts(parentWidth, parentHeight, availableWidth, textPaddingX)) {
      return;
    }

    CharSequence cueText = this.cueText;
    // Remove embedded styling or font size if requested.
    if (!applyEmbeddedStyles) {
      if (!isSmartTubeDualMergedSubtitleCue(cueText)) {
        cueText = cueText.toString(); // Equivalent to erasing all spans.
      }
    } else if (!applyEmbeddedFontSizes) {
      SpannableStringBuilder newCueText = new SpannableStringBuilder(cueText);
      int cueLength = newCueText.length();
      AbsoluteSizeSpan[] absSpans = newCueText.getSpans(0, cueLength, AbsoluteSizeSpan.class);
      RelativeSizeSpan[] relSpans = newCueText.getSpans(0, cueLength, RelativeSizeSpan.class);
      for (AbsoluteSizeSpan absSpan : absSpans) {
        newCueText.removeSpan(absSpan);
      }
      for (RelativeSizeSpan relSpan : relSpans) {
        newCueText.removeSpan(relSpan);
      }
      cueText = newCueText;
    } else {
      // Apply embedded styles & font size.
      if (cueTextSizePx > 0) {
        // Use a SpannableStringBuilder encompassing the whole cue text to apply the default
        // cueTextSizePx.
        SpannableStringBuilder newCueText = new SpannableStringBuilder(cueText);
        newCueText.setSpan(
            new AbsoluteSizeSpan((int) cueTextSizePx),
            /* start= */ 0,
            /* end= */ newCueText.length(),
            Spanned.SPAN_PRIORITY);
        cueText = newCueText;
      }
    }

    if (Color.alpha(backgroundColor) > 0) {
      SpannableStringBuilder newCueText = new SpannableStringBuilder(cueText);
      // MOD: add subs bg padding
      //newCueText.setSpan(
      //    new BackgroundColorSpan(backgroundColor), 0, newCueText.length(), Spanned.SPAN_PRIORITY);
      newCueText.setSpan(
              new PaddingBackgroundColorSpan(backgroundColor), 0, newCueText.length(), Spanned.SPAN_PRIORITY);
      //newCueText.setSpan(
      //        new RoundedBackgroundSpan(backgroundColor), 0, newCueText.length(), Spanned.SPAN_PRIORITY);
      cueText = newCueText;
    }

    Alignment textAlignment = cueTextAlignment == null ? Alignment.ALIGN_CENTER : cueTextAlignment;
    textLayout = new StaticLayout(cueText, textPaint, availableWidth, textAlignment, spacingMult,
        spacingAdd, true);
    // MOD: same height for multiline and single line subs (use 3 lines height)
    //int textHeight = textLayout.getLineBaseline(0) * 3; // base line has same height across single and multi line
    int textHeight = textLayout.getHeight();
    int textWidth = 0;
    int lineCount = textLayout.getLineCount();
    for (int i = 0; i < lineCount; i++) {
      textWidth = Math.max((int) Math.ceil(textLayout.getLineWidth(i)), textWidth);
    }
    if (cueSize != Cue.DIMEN_UNSET && textWidth < availableWidth) {
      textWidth = availableWidth;
    }
    textWidth += textPaddingX * 2;

    int textLeft;
    int textRight;
    if (cuePosition != Cue.DIMEN_UNSET) {
      int anchorPosition = Math.round(parentWidth * cuePosition) + parentLeft;
      textLeft = cuePositionAnchor == Cue.ANCHOR_TYPE_END ? anchorPosition - textWidth
          : cuePositionAnchor == Cue.ANCHOR_TYPE_MIDDLE ? (anchorPosition * 2 - textWidth) / 2
              : anchorPosition;
      textLeft = Math.max(textLeft, parentLeft);
      textRight = Math.min(textLeft + textWidth, parentRight);
    } else {
      textLeft = (parentWidth - textWidth) / 2 + parentLeft;
      textRight = textLeft + textWidth;
    }

    textWidth = textRight - textLeft;
    if (textWidth <= 0) {
      Log.w(TAG, "Skipped drawing subtitle cue (invalid horizontal positioning)");
      return;
    }

    int textTop;
    if (cueLine != Cue.DIMEN_UNSET) {
      int anchorPosition;
      if (cueLineType == Cue.LINE_TYPE_FRACTION) {
        anchorPosition = Math.round(parentHeight * cueLine) + parentTop;
      } else {
        // cueLineType == Cue.LINE_TYPE_NUMBER
        int firstLineHeight = textLayout.getLineBottom(0) - textLayout.getLineTop(0);
        if (cueLine >= 0) {
          anchorPosition = Math.round(cueLine * firstLineHeight) + parentTop;
        } else {
          anchorPosition = Math.round((cueLine + 1) * firstLineHeight) + parentBottom;
        }
      }
      textTop = cueLineAnchor == Cue.ANCHOR_TYPE_END ? anchorPosition - textHeight
          : cueLineAnchor == Cue.ANCHOR_TYPE_MIDDLE ? (anchorPosition * 2 - textHeight) / 2
              : anchorPosition;
      if (textTop + textHeight > parentBottom) {
        textTop = parentBottom - textHeight;
      } else if (textTop < parentTop) {
        textTop = parentTop;
      }
    } else {
      textTop = parentBottom - textHeight - (int) (parentHeight * bottomPaddingFraction);
    }

    // Update the derived drawing variables.
    this.textLayout = new StaticLayout(cueText, textPaint, textWidth, textAlignment, spacingMult,
        spacingAdd, true);
    this.textLeft = textLeft;
    this.textTop = textTop;
    this.textPaddingX = textPaddingX;
  }

  /**
   * SmartTube dual subtitles: draw the translated line with its own {@link TextPaint} so size,
   * color, and italic are visible regardless of {@link StaticLayout} span behavior on device.
   */
  private boolean trySetupSmartTubeDualSubtitleLayouts(
      int parentWidth, int parentHeight, int availableWidth, int textPaddingX) {
    CharSequence raw = this.cueText;
    if (!isSmartTubeDualMergedSubtitleCue(raw)) {
      return false;
    }
    int translationStartIndex = dualSubtitleTranslationStart(raw);
    if (translationStartIndex <= 0
        || translationStartIndex > raw.length()
        || raw.charAt(translationStartIndex - 1) != '\n') {
      return false;
    }
    String line1 = raw.subSequence(0, translationStartIndex - 1).toString();
    String line2 = raw.subSequence(translationStartIndex, raw.length()).toString();
    if (line2.isEmpty()) {
      return false;
    }

    Alignment textAlignment = cueTextAlignment == null ? Alignment.ALIGN_CENTER : cueTextAlignment;

    CharSequence primaryText = line1;
    CharSequence translationText = line2;
    if (Color.alpha(backgroundColor) > 0) {
      SpannableStringBuilder b1 = new SpannableStringBuilder(line1);
      b1.setSpan(
          new PaddingBackgroundColorSpan(backgroundColor),
          0,
          b1.length(),
          Spanned.SPAN_PRIORITY);
      primaryText = b1;
      SpannableStringBuilder b2 = new SpannableStringBuilder(line2);
      b2.setSpan(
          new PaddingBackgroundColorSpan(backgroundColor),
          0,
          b2.length(),
          Spanned.SPAN_PRIORITY);
      translationText = b2;
    }

    translationLinePaint.setAntiAlias(textPaint.isAntiAlias());
    translationLinePaint.setSubpixelText(textPaint.isSubpixelText());
    translationLinePaint.setLetterSpacing(textPaint.getLetterSpacing());
    translationLinePaint.setTextScaleX(textPaint.getTextScaleX());
    translationLinePaint.setTextSize(
        defaultTextSizePx * DualSubtitleCueMarkers.TRANSLATION_RELATIVE_SIZE);
    translationLinePaint.setColor(DualSubtitleCueMarkers.TRANSLATION_COLOR);
    translationLinePaint.setTypeface(
        Typeface.create(
            textPaint.getTypeface() != null ? textPaint.getTypeface() : Typeface.DEFAULT,
            Typeface.ITALIC));

    textLayout =
        new StaticLayout(
            primaryText, textPaint, availableWidth, textAlignment, spacingMult, spacingAdd, true);
    int firstLineHeightForAnchor = textLayout.getLineBottom(0) - textLayout.getLineTop(0);

    translationLayout =
        new StaticLayout(
            translationText,
            translationLinePaint,
            availableWidth,
            textAlignment,
            spacingMult,
            spacingAdd,
            true);

    translationGapPx =
        Math.round(defaultTextSizePx * DualSubtitleCueMarkers.TRANSLATION_LINE_GAP_FRACTION);

    int textHeight =
        textLayout.getHeight() + translationGapPx + translationLayout.getHeight();
    int textWidth = 0;
    for (int i = 0; i < textLayout.getLineCount(); i++) {
      textWidth = Math.max((int) Math.ceil(textLayout.getLineWidth(i)), textWidth);
    }
    for (int i = 0; i < translationLayout.getLineCount(); i++) {
      textWidth = Math.max((int) Math.ceil(translationLayout.getLineWidth(i)), textWidth);
    }
    if (cueSize != Cue.DIMEN_UNSET && textWidth < availableWidth) {
      textWidth = availableWidth;
    }
    textWidth += textPaddingX * 2;

    int textLeft;
    int textRight;
    if (cuePosition != Cue.DIMEN_UNSET) {
      int anchorPosition = Math.round(parentWidth * cuePosition) + parentLeft;
      textLeft =
          cuePositionAnchor == Cue.ANCHOR_TYPE_END
              ? anchorPosition - textWidth
              : cuePositionAnchor == Cue.ANCHOR_TYPE_MIDDLE
                  ? (anchorPosition * 2 - textWidth) / 2
                  : anchorPosition;
      textLeft = Math.max(textLeft, parentLeft);
      textRight = Math.min(textLeft + textWidth, parentRight);
    } else {
      textLeft = (parentWidth - textWidth) / 2 + parentLeft;
      textRight = textLeft + textWidth;
    }

    textWidth = textRight - textLeft;
    if (textWidth <= 0) {
      Log.w(TAG, "Skipped drawing subtitle cue (invalid horizontal positioning)");
      translationLayout = null;
      translationGapPx = 0;
      textLayout = null;
      return false;
    }

    int textTop;
    if (cueLine != Cue.DIMEN_UNSET) {
      int anchorPosition;
      if (cueLineType == Cue.LINE_TYPE_FRACTION) {
        anchorPosition = Math.round(parentHeight * cueLine) + parentTop;
      } else {
        if (cueLine >= 0) {
          anchorPosition = Math.round(cueLine * firstLineHeightForAnchor) + parentTop;
        } else {
          anchorPosition =
              Math.round((cueLine + 1) * firstLineHeightForAnchor) + parentBottom;
        }
      }
      textTop =
          cueLineAnchor == Cue.ANCHOR_TYPE_END
              ? anchorPosition - textHeight
              : cueLineAnchor == Cue.ANCHOR_TYPE_MIDDLE
                  ? (anchorPosition * 2 - textHeight) / 2
                  : anchorPosition;
      if (textTop + textHeight > parentBottom) {
        textTop = parentBottom - textHeight;
      } else if (textTop < parentTop) {
        textTop = parentTop;
      }
    } else {
      textTop = parentBottom - textHeight - (int) (parentHeight * bottomPaddingFraction);
    }

    this.textLayout =
        new StaticLayout(
            primaryText, textPaint, textWidth, textAlignment, spacingMult, spacingAdd, true);
    this.translationLayout =
        new StaticLayout(
            translationText,
            translationLinePaint,
            textWidth,
            textAlignment,
            spacingMult,
            spacingAdd,
            true);
    this.textLeft = textLeft;
    this.textTop = textTop;
    this.textPaddingX = textPaddingX;
    return true;
  }

  private void setupBitmapLayout() {
    int parentWidth = parentRight - parentLeft;
    int parentHeight = parentBottom - parentTop;
    float anchorX = parentLeft + (parentWidth * cuePosition);
    float anchorY = parentTop + (parentHeight * cueLine);
    int width = Math.round(parentWidth * cueSize);
    int height = cueBitmapHeight != Cue.DIMEN_UNSET ? Math.round(parentHeight * cueBitmapHeight)
        : Math.round(width * ((float) cueBitmap.getHeight() / cueBitmap.getWidth()));
    int x =
        Math.round(
            cuePositionAnchor == Cue.ANCHOR_TYPE_END
                ? (anchorX - width)
                : cuePositionAnchor == Cue.ANCHOR_TYPE_MIDDLE ? (anchorX - (width / 2)) : anchorX);
    int y =
        Math.round(
            cueLineAnchor == Cue.ANCHOR_TYPE_END
                ? (anchorY - height)
                : cueLineAnchor == Cue.ANCHOR_TYPE_MIDDLE ? (anchorY - (height / 2)) : anchorY);
    bitmapRect = new Rect(x, y, x + width, y + height);
  }

  private void drawLayout(Canvas canvas, boolean isTextCue) {
    if (isTextCue) {
      drawTextLayout(canvas);
    } else {
      drawBitmapLayout(canvas);
    }
  }

  private void drawTextLayout(Canvas canvas) {
    StaticLayout layout = textLayout;
    if (layout == null) {
      return;
    }

    int saveCount = canvas.save();
    canvas.translate(textLeft, textTop);

    int blockHeight = layout.getHeight();
    int blockWidth = layout.getWidth();
    if (translationLayout != null) {
      blockHeight += translationGapPx + translationLayout.getHeight();
      blockWidth = Math.max(blockWidth, translationLayout.getWidth());
    }

    if (Color.alpha(windowColor) > 0) {
      paint.setColor(windowColor);
      canvas.drawRect(-textPaddingX, 0, blockWidth + textPaddingX, blockHeight, paint);
    }

    drawLayoutEdgeAndFill(canvas, layout, textPaint, foregroundColor);

    if (translationLayout != null) {
      canvas.translate(0, layout.getHeight() + translationGapPx);
      drawLayoutEdgeAndFill(
          canvas,
          translationLayout,
          translationLinePaint,
          DualSubtitleCueMarkers.TRANSLATION_COLOR);
    }

    textPaint.setShadowLayer(0, 0, 0, 0);
    translationLinePaint.setShadowLayer(0, 0, 0, 0);

    canvas.restoreToCount(saveCount);
  }

  /** Caption edge + fill for one {@link StaticLayout} using {@code fillColor} for the main pass. */
  private void drawLayoutEdgeAndFill(
      Canvas canvas, StaticLayout layout, TextPaint tp, int fillColor) {
    if (edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
      tp.setStrokeJoin(Join.ROUND);
      tp.setStrokeWidth(outlineWidth);
      tp.setColor(edgeColor);
      tp.setStyle(Style.FILL_AND_STROKE);
      layout.draw(canvas);
    } else if (edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
      tp.setShadowLayer(shadowRadius, shadowOffset, shadowOffset, edgeColor);
    } else if (edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED
        || edgeType == CaptionStyleCompat.EDGE_TYPE_DEPRESSED) {
      boolean raised = edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED;
      int colorUp = raised ? Color.WHITE : edgeColor;
      int colorDown = raised ? edgeColor : Color.WHITE;
      float offset = shadowRadius / 2f;
      tp.setColor(fillColor);
      tp.setStyle(Style.FILL);
      tp.setShadowLayer(shadowRadius, -offset, -offset, colorUp);
      layout.draw(canvas);
      tp.setShadowLayer(shadowRadius, offset, offset, colorDown);
    }

    tp.setColor(fillColor);
    tp.setStyle(Style.FILL);
    layout.draw(canvas);
    tp.setShadowLayer(0, 0, 0, 0);
  }

  private void drawBitmapLayout(Canvas canvas) {
    canvas.drawBitmap(cueBitmap, null, bitmapRect, null);
  }

  private static int dualSubtitleTranslationStart(CharSequence cueText) {
    if (!(cueText instanceof Spanned)) {
      return -1;
    }
    Spanned sp = (Spanned) cueText;
    Annotation[] annotations = sp.getSpans(0, sp.length(), Annotation.class);
    for (Annotation a : annotations) {
      if (DualSubtitleCueMarkers.ANNOTATION_KEY.equals(a.getKey())
          && DualSubtitleCueMarkers.ANNOTATION_VALUE.equals(a.getValue())) {
        return sp.getSpanStart(a);
      }
    }
    return -1;
  }

  /**
   * Dual subtitles merge original + translation in one {@link CharSequence} with spans on the
   * translation line and an {@link Annotation} marker; those must survive even when the view
   * disables embedded WebVTT/TTML styling.
   */
  private static boolean isSmartTubeDualMergedSubtitleCue(CharSequence cueText) {
    if (!(cueText instanceof Spanned)) {
      return false;
    }
    Spanned sp = (Spanned) cueText;
    Annotation[] annotations = sp.getSpans(0, sp.length(), Annotation.class);
    for (Annotation a : annotations) {
      if (DualSubtitleCueMarkers.ANNOTATION_KEY.equals(a.getKey())
          && DualSubtitleCueMarkers.ANNOTATION_VALUE.equals(a.getValue())) {
        return true;
      }
    }
    return false;
  }

  /**
   * This method is used instead of {@link TextUtils#equals(CharSequence, CharSequence)} because the
   * latter only checks the text of each sequence, and does not check for equality of styling that
   * may be embedded within the {@link CharSequence}s.
   */
  @SuppressWarnings("UndefinedEquals")
  private static boolean areCharSequencesEqual(CharSequence first, CharSequence second) {
    // Some CharSequence implementations don't perform a cheap referential equality check in their
    // equals methods, so we perform one explicitly here.
    return first == second || (first != null && first.equals(second));
  }

}
