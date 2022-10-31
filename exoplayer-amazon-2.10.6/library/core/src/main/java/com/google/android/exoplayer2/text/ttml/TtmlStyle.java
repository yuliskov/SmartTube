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
package com.google.android.exoplayer2.text.ttml;

import android.graphics.Typeface;
import androidx.annotation.IntDef;
import android.text.Layout;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Style object of a <code>TtmlNode</code>
 */
/* package */ final class TtmlStyle {

  public static final int UNSPECIFIED = -1;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {UNSPECIFIED, STYLE_NORMAL, STYLE_BOLD, STYLE_ITALIC, STYLE_BOLD_ITALIC})
  public @interface StyleFlags {}

  public static final int STYLE_NORMAL = Typeface.NORMAL;
  public static final int STYLE_BOLD = Typeface.BOLD;
  public static final int STYLE_ITALIC = Typeface.ITALIC;
  public static final int STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNSPECIFIED, FONT_SIZE_UNIT_PIXEL, FONT_SIZE_UNIT_EM, FONT_SIZE_UNIT_PERCENT})
  public @interface FontSizeUnit {}

  public static final int FONT_SIZE_UNIT_PIXEL = 1;
  public static final int FONT_SIZE_UNIT_EM = 2;
  public static final int FONT_SIZE_UNIT_PERCENT = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNSPECIFIED, OFF, ON})
  private @interface OptionalBoolean {}

  private static final int OFF = 0;
  private static final int ON = 1;

  private String fontFamily;
  private int fontColor;
  private boolean hasFontColor;
  private int backgroundColor;
  private boolean hasBackgroundColor;
  @OptionalBoolean private int linethrough;
  @OptionalBoolean private int underline;
  @OptionalBoolean private int bold;
  @OptionalBoolean private int italic;
  @FontSizeUnit private int fontSizeUnit;
  private float fontSize;
  private String id;
  private TtmlStyle inheritableStyle;
  private Layout.Alignment textAlign;

  public TtmlStyle() {
    linethrough = UNSPECIFIED;
    underline = UNSPECIFIED;
    bold = UNSPECIFIED;
    italic = UNSPECIFIED;
    fontSizeUnit = UNSPECIFIED;
  }

  /**
   * Returns the style or {@link #UNSPECIFIED} when no style information is given.
   *
   * @return {@link #UNSPECIFIED}, {@link #STYLE_NORMAL}, {@link #STYLE_BOLD}, {@link #STYLE_BOLD}
   *     or {@link #STYLE_BOLD_ITALIC}.
   */
  @StyleFlags public int getStyle() {
    if (bold == UNSPECIFIED && italic == UNSPECIFIED) {
      return UNSPECIFIED;
    }
    return (bold == ON ? STYLE_BOLD : STYLE_NORMAL)
        | (italic == ON ? STYLE_ITALIC : STYLE_NORMAL);
  }

  public boolean isLinethrough() {
    return linethrough == ON;
  }

  public TtmlStyle setLinethrough(boolean linethrough) {
    Assertions.checkState(inheritableStyle == null);
    this.linethrough = linethrough ? ON : OFF;
    return this;
  }

  public boolean isUnderline() {
    return underline == ON;
  }

  public TtmlStyle setUnderline(boolean underline) {
    Assertions.checkState(inheritableStyle == null);
    this.underline = underline ? ON : OFF;
    return this;
  }

  public TtmlStyle setBold(boolean bold) {
    Assertions.checkState(inheritableStyle == null);
    this.bold = bold ? ON : OFF;
    return this;
  }

  public TtmlStyle setItalic(boolean italic) {
    Assertions.checkState(inheritableStyle == null);
    this.italic = italic ? ON : OFF;
    return this;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public TtmlStyle setFontFamily(String fontFamily) {
    Assertions.checkState(inheritableStyle == null);
    this.fontFamily = fontFamily;
    return this;
  }

  public int getFontColor() {
    if (!hasFontColor) {
      throw new IllegalStateException("Font color has not been defined.");
    }
    return fontColor;
  }

  public TtmlStyle setFontColor(int fontColor) {
    Assertions.checkState(inheritableStyle == null);
    this.fontColor = fontColor;
    hasFontColor = true;
    return this;
  }

  public boolean hasFontColor() {
    return hasFontColor;
  }

  public int getBackgroundColor() {
    if (!hasBackgroundColor) {
      throw new IllegalStateException("Background color has not been defined.");
    }
    return backgroundColor;
  }

  public TtmlStyle setBackgroundColor(int backgroundColor) {
    this.backgroundColor = backgroundColor;
    hasBackgroundColor = true;
    return this;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }

  /**
   * Inherits from an ancestor style. Properties like <i>tts:backgroundColor</i> which
   * are not inheritable are not inherited as well as properties which are already set locally
   * are never overridden.
   *
   * @param ancestor the ancestor style to inherit from
   */
  public TtmlStyle inherit(TtmlStyle ancestor) {
    return inherit(ancestor, false);
  }

  /**
   * Chains this style to referential style. Local properties which are already set
   * are never overridden.
   *
   * @param ancestor the referential style to inherit from
   */
  public TtmlStyle chain(TtmlStyle ancestor) {
    return inherit(ancestor, true);
  }

  private TtmlStyle inherit(TtmlStyle ancestor, boolean chaining) {
    if (ancestor != null) {
      if (!hasFontColor && ancestor.hasFontColor) {
        setFontColor(ancestor.fontColor);
      }
      if (bold == UNSPECIFIED) {
        bold = ancestor.bold;
      }
      if (italic == UNSPECIFIED) {
        italic = ancestor.italic;
      }
      if (fontFamily == null) {
        fontFamily = ancestor.fontFamily;
      }
      if (linethrough == UNSPECIFIED) {
        linethrough = ancestor.linethrough;
      }
      if (underline == UNSPECIFIED) {
        underline = ancestor.underline;
      }
      if (textAlign == null) {
        textAlign = ancestor.textAlign;
      }
      if (fontSizeUnit == UNSPECIFIED) {
        fontSizeUnit = ancestor.fontSizeUnit;
        fontSize = ancestor.fontSize;
      }
      // attributes not inherited as of http://www.w3.org/TR/ttml1/
      if (chaining && !hasBackgroundColor && ancestor.hasBackgroundColor) {
        setBackgroundColor(ancestor.backgroundColor);
      }
    }
    return this;
  }

  public TtmlStyle setId(String id) {
    this.id = id;
    return this;
  }

  public String getId() {
    return id;
  }

  public Layout.Alignment getTextAlign() {
    return textAlign;
  }

  public TtmlStyle setTextAlign(Layout.Alignment textAlign) {
    this.textAlign = textAlign;
    return this;
  }

  public TtmlStyle setFontSize(float fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  public TtmlStyle setFontSizeUnit(int fontSizeUnit) {
    this.fontSizeUnit = fontSizeUnit;
    return this;
  }

  @FontSizeUnit public int getFontSizeUnit() {
    return fontSizeUnit;
  }

  public float getFontSize() {
    return fontSize;
  }

}
