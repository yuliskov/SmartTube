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
package com.google.android.exoplayer2.text;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.annotation.IntDef;
import android.text.Layout.Alignment;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains information about a specific cue, including textual content and formatting data.
 */
public class Cue {

  /** The empty cue. */
  public static final Cue EMPTY = new Cue("");

  /** An unset position or width. */
  public static final float DIMEN_UNSET = Float.MIN_VALUE;

  /**
   * The type of anchor, which may be unset. One of {@link #TYPE_UNSET}, {@link #ANCHOR_TYPE_START},
   * {@link #ANCHOR_TYPE_MIDDLE} or {@link #ANCHOR_TYPE_END}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_UNSET, ANCHOR_TYPE_START, ANCHOR_TYPE_MIDDLE, ANCHOR_TYPE_END})
  public @interface AnchorType {}

  /**
   * An unset anchor or line type value.
   */
  public static final int TYPE_UNSET = Integer.MIN_VALUE;

  /**
   * Anchors the left (for horizontal positions) or top (for vertical positions) edge of the cue
   * box.
   */
  public static final int ANCHOR_TYPE_START = 0;

  /**
   * Anchors the middle of the cue box.
   */
  public static final int ANCHOR_TYPE_MIDDLE = 1;

  /**
   * Anchors the right (for horizontal positions) or bottom (for vertical positions) edge of the cue
   * box.
   */
  public static final int ANCHOR_TYPE_END = 2;

  /**
   * The type of line, which may be unset. One of {@link #TYPE_UNSET}, {@link #LINE_TYPE_FRACTION}
   * or {@link #LINE_TYPE_NUMBER}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_UNSET, LINE_TYPE_FRACTION, LINE_TYPE_NUMBER})
  public @interface LineType {}

  /**
   * Value for {@link #lineType} when {@link #line} is a fractional position.
   */
  public static final int LINE_TYPE_FRACTION = 0;

  /**
   * Value for {@link #lineType} when {@link #line} is a line number.
   */
  public static final int LINE_TYPE_NUMBER = 1;

  /**
   * The type of default text size for this cue, which may be unset. One of {@link #TYPE_UNSET},
   * {@link #TEXT_SIZE_TYPE_FRACTIONAL}, {@link #TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING} or {@link
   * #TEXT_SIZE_TYPE_ABSOLUTE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TYPE_UNSET,
    TEXT_SIZE_TYPE_FRACTIONAL,
    TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING,
    TEXT_SIZE_TYPE_ABSOLUTE
  })
  public @interface TextSizeType {}

  /** Text size is measured as a fraction of the viewport size minus the view padding. */
  public static final int TEXT_SIZE_TYPE_FRACTIONAL = 0;

  /** Text size is measured as a fraction of the viewport size, ignoring the view padding */
  public static final int TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING = 1;

  /** Text size is measured in number of pixels. */
  public static final int TEXT_SIZE_TYPE_ABSOLUTE = 2;

  /**
   * The cue text, or null if this is an image cue. Note the {@link CharSequence} may be decorated
   * with styling spans.
   */
  public final CharSequence text;

  /**
   * The alignment of the cue text within the cue box, or null if the alignment is undefined.
   */
  public final Alignment textAlignment;

  /**
   * The cue image, or null if this is a text cue.
   */
  public final Bitmap bitmap;

  /**
   * The position of the {@link #lineAnchor} of the cue box within the viewport in the direction
   * orthogonal to the writing direction, or {@link #DIMEN_UNSET}. When set, the interpretation of
   * the value depends on the value of {@link #lineType}.
   * <p>
   * For horizontal text and {@link #lineType} equal to {@link #LINE_TYPE_FRACTION}, this is the
   * fractional vertical position relative to the top of the viewport.
   */
  public final float line;

  /**
   * The type of the {@link #line} value.
   *
   * <p>{@link #LINE_TYPE_FRACTION} indicates that {@link #line} is a fractional position within the
   * viewport.
   *
   * <p>{@link #LINE_TYPE_NUMBER} indicates that {@link #line} is a line number, where the size of
   * each line is taken to be the size of the first line of the cue. When {@link #line} is greater
   * than or equal to 0 lines count from the start of the viewport, with 0 indicating zero offset
   * from the start edge. When {@link #line} is negative lines count from the end of the viewport,
   * with -1 indicating zero offset from the end edge. For horizontal text the line spacing is the
   * height of the first line of the cue, and the start and end of the viewport are the top and
   * bottom respectively.
   *
   * <p>Note that it's particularly important to consider the effect of {@link #lineAnchor} when
   * using {@link #LINE_TYPE_NUMBER}. {@code (line == 0 && lineAnchor == ANCHOR_TYPE_START)}
   * positions a (potentially multi-line) cue at the very top of the viewport. {@code (line == -1 &&
   * lineAnchor == ANCHOR_TYPE_END)} positions a (potentially multi-line) cue at the very bottom of
   * the viewport. {@code (line == 0 && lineAnchor == ANCHOR_TYPE_END)} and {@code (line == -1 &&
   * lineAnchor == ANCHOR_TYPE_START)} position cues entirely outside of the viewport. {@code (line
   * == 1 && lineAnchor == ANCHOR_TYPE_END)} positions a cue so that only the last line is visible
   * at the top of the viewport. {@code (line == -2 && lineAnchor == ANCHOR_TYPE_START)} position a
   * cue so that only its first line is visible at the bottom of the viewport.
   */
  public final @LineType int lineType;

  /**
   * The cue box anchor positioned by {@link #line}. One of {@link #ANCHOR_TYPE_START}, {@link
   * #ANCHOR_TYPE_MIDDLE}, {@link #ANCHOR_TYPE_END} and {@link #TYPE_UNSET}.
   *
   * <p>For the normal case of horizontal text, {@link #ANCHOR_TYPE_START}, {@link
   * #ANCHOR_TYPE_MIDDLE} and {@link #ANCHOR_TYPE_END} correspond to the top, middle and bottom of
   * the cue box respectively.
   */
  public final @AnchorType int lineAnchor;

  /**
   * The fractional position of the {@link #positionAnchor} of the cue box within the viewport in
   * the direction orthogonal to {@link #line}, or {@link #DIMEN_UNSET}.
   * <p>
   * For horizontal text, this is the horizontal position relative to the left of the viewport. Note
   * that positioning is relative to the left of the viewport even in the case of right-to-left
   * text.
   */
  public final float position;

  /**
   * The cue box anchor positioned by {@link #position}. One of {@link #ANCHOR_TYPE_START}, {@link
   * #ANCHOR_TYPE_MIDDLE}, {@link #ANCHOR_TYPE_END} and {@link #TYPE_UNSET}.
   *
   * <p>For the normal case of horizontal text, {@link #ANCHOR_TYPE_START}, {@link
   * #ANCHOR_TYPE_MIDDLE} and {@link #ANCHOR_TYPE_END} correspond to the left, middle and right of
   * the cue box respectively.
   */
  public final @AnchorType int positionAnchor;

  /**
   * The size of the cue box in the writing direction specified as a fraction of the viewport size
   * in that direction, or {@link #DIMEN_UNSET}.
   */
  public final float size;

  /**
   * The bitmap height as a fraction of the of the viewport size, or {@link #DIMEN_UNSET} if the
   * bitmap should be displayed at its natural height given the bitmap dimensions and the specified
   * {@link #size}.
   */
  public final float bitmapHeight;

  /**
   * Specifies whether or not the {@link #windowColor} property is set.
   */
  public final boolean windowColorSet;

  /**
   * The fill color of the window.
   */
  public final int windowColor;

  /**
   * The default text size type for this cue's text, or {@link #TYPE_UNSET} if this cue has no
   * default text size.
   */
  public final @TextSizeType int textSizeType;

  /**
   * The default text size for this cue's text, or {@link #DIMEN_UNSET} if this cue has no default
   * text size.
   */
  public final float textSize;

  /**
   * Creates an image cue.
   *
   * @param bitmap See {@link #bitmap}.
   * @param horizontalPosition The position of the horizontal anchor within the viewport, expressed
   *     as a fraction of the viewport width.
   * @param horizontalPositionAnchor The horizontal anchor. One of {@link #ANCHOR_TYPE_START},
   *     {@link #ANCHOR_TYPE_MIDDLE}, {@link #ANCHOR_TYPE_END} and {@link #TYPE_UNSET}.
   * @param verticalPosition The position of the vertical anchor within the viewport, expressed as a
   *     fraction of the viewport height.
   * @param verticalPositionAnchor The vertical anchor. One of {@link #ANCHOR_TYPE_START}, {@link
   *     #ANCHOR_TYPE_MIDDLE}, {@link #ANCHOR_TYPE_END} and {@link #TYPE_UNSET}.
   * @param width The width of the cue as a fraction of the viewport width.
   * @param height The height of the cue as a fraction of the viewport height, or {@link
   *     #DIMEN_UNSET} if the bitmap should be displayed at its natural height for the specified
   *     {@code width}.
   */
  public Cue(
      Bitmap bitmap,
      float horizontalPosition,
      @AnchorType int horizontalPositionAnchor,
      float verticalPosition,
      @AnchorType int verticalPositionAnchor,
      float width,
      float height) {
    this(
        /* text= */ null,
        /* textAlignment= */ null,
        bitmap,
        verticalPosition,
        /* lineType= */ LINE_TYPE_FRACTION,
        verticalPositionAnchor,
        horizontalPosition,
        horizontalPositionAnchor,
        /* textSizeType= */ TYPE_UNSET,
        /* textSize= */ DIMEN_UNSET,
        width,
        height,
        /* windowColorSet= */ false,
        /* windowColor= */ Color.BLACK);
  }

  /**
   * Creates a text cue whose {@link #textAlignment} is null, whose type parameters are set to
   * {@link #TYPE_UNSET} and whose dimension parameters are set to {@link #DIMEN_UNSET}.
   *
   * @param text See {@link #text}.
   */
  public Cue(CharSequence text) {
    this(
        text,
        /* textAlignment= */ null,
        /* line= */ DIMEN_UNSET,
        /* lineType= */ TYPE_UNSET,
        /* lineAnchor= */ TYPE_UNSET,
        /* position= */ DIMEN_UNSET,
        /* positionAnchor= */ TYPE_UNSET,
        /* size= */ DIMEN_UNSET);
  }

  /**
   * Creates a text cue.
   *
   * @param text See {@link #text}.
   * @param textAlignment See {@link #textAlignment}.
   * @param line See {@link #line}.
   * @param lineType See {@link #lineType}.
   * @param lineAnchor See {@link #lineAnchor}.
   * @param position See {@link #position}.
   * @param positionAnchor See {@link #positionAnchor}.
   * @param size See {@link #size}.
   */
  public Cue(
      CharSequence text,
      Alignment textAlignment,
      float line,
      @LineType int lineType,
      @AnchorType int lineAnchor,
      float position,
      @AnchorType int positionAnchor,
      float size) {
    this(
        text,
        textAlignment,
        line,
        lineType,
        lineAnchor,
        position,
        positionAnchor,
        size,
        /* windowColorSet= */ false,
        /* windowColor= */ Color.BLACK);
  }

  /**
   * Creates a text cue.
   *
   * @param text See {@link #text}.
   * @param textAlignment See {@link #textAlignment}.
   * @param line See {@link #line}.
   * @param lineType See {@link #lineType}.
   * @param lineAnchor See {@link #lineAnchor}.
   * @param position See {@link #position}.
   * @param positionAnchor See {@link #positionAnchor}.
   * @param size See {@link #size}.
   * @param textSizeType See {@link #textSizeType}.
   * @param textSize See {@link #textSize}.
   */
  public Cue(
      CharSequence text,
      Alignment textAlignment,
      float line,
      @LineType int lineType,
      @AnchorType int lineAnchor,
      float position,
      @AnchorType int positionAnchor,
      float size,
      @TextSizeType int textSizeType,
      float textSize) {
    this(
        text,
        textAlignment,
        /* bitmap= */ null,
        line,
        lineType,
        lineAnchor,
        position,
        positionAnchor,
        textSizeType,
        textSize,
        size,
        /* bitmapHeight= */ DIMEN_UNSET,
        /* windowColorSet= */ false,
        /* windowColor= */ Color.BLACK);
  }

  /**
   * Creates a text cue.
   *
   * @param text See {@link #text}.
   * @param textAlignment See {@link #textAlignment}.
   * @param line See {@link #line}.
   * @param lineType See {@link #lineType}.
   * @param lineAnchor See {@link #lineAnchor}.
   * @param position See {@link #position}.
   * @param positionAnchor See {@link #positionAnchor}.
   * @param size See {@link #size}.
   * @param windowColorSet See {@link #windowColorSet}.
   * @param windowColor See {@link #windowColor}.
   */
  public Cue(
      CharSequence text,
      Alignment textAlignment,
      float line,
      @LineType int lineType,
      @AnchorType int lineAnchor,
      float position,
      @AnchorType int positionAnchor,
      float size,
      boolean windowColorSet,
      int windowColor) {
    this(
        text,
        textAlignment,
        /* bitmap= */ null,
        line,
        lineType,
        lineAnchor,
        position,
        positionAnchor,
        /* textSizeType= */ TYPE_UNSET,
        /* textSize= */ DIMEN_UNSET,
        size,
        /* bitmapHeight= */ DIMEN_UNSET,
        windowColorSet,
        windowColor);
  }

  private Cue(
      CharSequence text,
      Alignment textAlignment,
      Bitmap bitmap,
      float line,
      @LineType int lineType,
      @AnchorType int lineAnchor,
      float position,
      @AnchorType int positionAnchor,
      @TextSizeType int textSizeType,
      float textSize,
      float size,
      float bitmapHeight,
      boolean windowColorSet,
      int windowColor) {
    this.text = text;
    this.textAlignment = textAlignment;
    this.bitmap = bitmap;
    this.line = line;
    this.lineType = lineType;
    this.lineAnchor = lineAnchor;
    this.position = position;
    this.positionAnchor = positionAnchor;
    this.size = size;
    this.bitmapHeight = bitmapHeight;
    this.windowColorSet = windowColorSet;
    this.windowColor = windowColor;
    this.textSizeType = textSizeType;
    this.textSize = textSize;
  }

}
