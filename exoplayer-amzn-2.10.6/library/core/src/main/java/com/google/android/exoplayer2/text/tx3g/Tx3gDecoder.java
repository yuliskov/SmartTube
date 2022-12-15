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
package com.google.android.exoplayer2.text.tx3g;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.charset.Charset;
import java.util.List;

/**
 * A {@link SimpleSubtitleDecoder} for tx3g.
 * <p>
 * Currently supports parsing of a single text track with embedded styles.
 */
public final class Tx3gDecoder extends SimpleSubtitleDecoder {

  private static final char BOM_UTF16_BE = '\uFEFF';
  private static final char BOM_UTF16_LE = '\uFFFE';

  private static final int TYPE_STYL = Util.getIntegerCodeForString("styl");
  private static final int TYPE_TBOX = Util.getIntegerCodeForString("tbox");
  private static final String TX3G_SERIF = "Serif";

  private static final int SIZE_ATOM_HEADER = 8;
  private static final int SIZE_SHORT = 2;
  private static final int SIZE_BOM_UTF16 = 2;
  private static final int SIZE_STYLE_RECORD = 12;

  private static final int FONT_FACE_BOLD = 0x0001;
  private static final int FONT_FACE_ITALIC = 0x0002;
  private static final int FONT_FACE_UNDERLINE = 0x0004;

  private static final int SPAN_PRIORITY_LOW = 0xFF << Spanned.SPAN_PRIORITY_SHIFT;
  private static final int SPAN_PRIORITY_HIGH = 0;

  private static final int DEFAULT_FONT_FACE = 0;
  private static final int DEFAULT_COLOR = Color.WHITE;
  private static final String DEFAULT_FONT_FAMILY = C.SANS_SERIF_NAME;
  private static final float DEFAULT_VERTICAL_PLACEMENT = 0.85f;

  private final ParsableByteArray parsableByteArray;

  private boolean customVerticalPlacement;
  private int defaultFontFace;
  private int defaultColorRgba;
  private String defaultFontFamily;
  private float defaultVerticalPlacement;
  private int calculatedVideoTrackHeight;

  /**
   * Sets up a new {@link Tx3gDecoder} with default values.
   *
   * @param initializationData Sample description atom ('stsd') data with default subtitle styles.
   */
  public Tx3gDecoder(List<byte[]> initializationData) {
    super("Tx3gDecoder");
    parsableByteArray = new ParsableByteArray();

    if (initializationData != null && initializationData.size() == 1
        && (initializationData.get(0).length == 48 || initializationData.get(0).length == 53)) {
      byte[] initializationBytes = initializationData.get(0);
      defaultFontFace = initializationBytes[24];
      defaultColorRgba = ((initializationBytes[26] & 0xFF) << 24)
          | ((initializationBytes[27] & 0xFF) << 16)
          | ((initializationBytes[28] & 0xFF) << 8)
          | (initializationBytes[29] & 0xFF);
      String fontFamily =
          Util.fromUtf8Bytes(initializationBytes, 43, initializationBytes.length - 43);
      defaultFontFamily = TX3G_SERIF.equals(fontFamily) ? C.SERIF_NAME : C.SANS_SERIF_NAME;
      //font size (initializationBytes[25]) is 5% of video height
      calculatedVideoTrackHeight = 20 * initializationBytes[25];
      customVerticalPlacement = (initializationBytes[0] & 0x20) != 0;
      if (customVerticalPlacement) {
        int requestedVerticalPlacement = ((initializationBytes[10] & 0xFF) << 8)
            | (initializationBytes[11] & 0xFF);
        defaultVerticalPlacement = (float) requestedVerticalPlacement / calculatedVideoTrackHeight;
        defaultVerticalPlacement = Util.constrainValue(defaultVerticalPlacement, 0.0f, 0.95f);
      } else {
        defaultVerticalPlacement = DEFAULT_VERTICAL_PLACEMENT;
      }
    } else {
      defaultFontFace = DEFAULT_FONT_FACE;
      defaultColorRgba = DEFAULT_COLOR;
      defaultFontFamily = DEFAULT_FONT_FAMILY;
      customVerticalPlacement = false;
      defaultVerticalPlacement = DEFAULT_VERTICAL_PLACEMENT;
    }
  }

  @Override
  protected Subtitle decode(byte[] bytes, int length, boolean reset)
      throws SubtitleDecoderException {
    parsableByteArray.reset(bytes, length);
    String cueTextString = readSubtitleText(parsableByteArray);
    if (cueTextString.isEmpty()) {
      return Tx3gSubtitle.EMPTY;
    }
    // Attach default styles.
    SpannableStringBuilder cueText = new SpannableStringBuilder(cueTextString);
    attachFontFace(cueText, defaultFontFace, DEFAULT_FONT_FACE, 0, cueText.length(),
        SPAN_PRIORITY_LOW);
    attachColor(cueText, defaultColorRgba, DEFAULT_COLOR, 0, cueText.length(),
        SPAN_PRIORITY_LOW);
    attachFontFamily(cueText, defaultFontFamily, DEFAULT_FONT_FAMILY, 0, cueText.length(),
        SPAN_PRIORITY_LOW);
    float verticalPlacement = defaultVerticalPlacement;
    // Find and attach additional styles.
    while (parsableByteArray.bytesLeft() >= SIZE_ATOM_HEADER) {
      int position = parsableByteArray.getPosition();
      int atomSize = parsableByteArray.readInt();
      int atomType = parsableByteArray.readInt();
      if (atomType == TYPE_STYL) {
        assertTrue(parsableByteArray.bytesLeft() >= SIZE_SHORT);
        int styleRecordCount = parsableByteArray.readUnsignedShort();
        for (int i = 0; i < styleRecordCount; i++) {
          applyStyleRecord(parsableByteArray, cueText);
        }
      } else if (atomType == TYPE_TBOX && customVerticalPlacement) {
        assertTrue(parsableByteArray.bytesLeft() >= SIZE_SHORT);
        int requestedVerticalPlacement = parsableByteArray.readUnsignedShort();
        verticalPlacement = (float) requestedVerticalPlacement / calculatedVideoTrackHeight;
        verticalPlacement = Util.constrainValue(verticalPlacement, 0.0f, 0.95f);
      }
      parsableByteArray.setPosition(position + atomSize);
    }
    return new Tx3gSubtitle(
        new Cue(
            cueText,
            /* textAlignment= */ null,
            verticalPlacement,
            Cue.LINE_TYPE_FRACTION,
            Cue.ANCHOR_TYPE_START,
            Cue.DIMEN_UNSET,
            Cue.TYPE_UNSET,
            Cue.DIMEN_UNSET));
  }

  private static String readSubtitleText(ParsableByteArray parsableByteArray)
      throws SubtitleDecoderException {
    assertTrue(parsableByteArray.bytesLeft() >= SIZE_SHORT);
    int textLength = parsableByteArray.readUnsignedShort();
    if (textLength == 0) {
      return "";
    }
    if (parsableByteArray.bytesLeft() >= SIZE_BOM_UTF16) {
      char firstChar = parsableByteArray.peekChar();
      if (firstChar == BOM_UTF16_BE || firstChar == BOM_UTF16_LE) {
        return parsableByteArray.readString(textLength, Charset.forName(C.UTF16_NAME));
      }
    }
    return parsableByteArray.readString(textLength, Charset.forName(C.UTF8_NAME));
  }

  private void applyStyleRecord(ParsableByteArray parsableByteArray,
      SpannableStringBuilder cueText) throws SubtitleDecoderException {
    assertTrue(parsableByteArray.bytesLeft() >= SIZE_STYLE_RECORD);
    int start = parsableByteArray.readUnsignedShort();
    int end = parsableByteArray.readUnsignedShort();
    parsableByteArray.skipBytes(2); // font identifier
    int fontFace = parsableByteArray.readUnsignedByte();
    parsableByteArray.skipBytes(1); // font size
    int colorRgba = parsableByteArray.readInt();
    attachFontFace(cueText, fontFace, defaultFontFace, start, end, SPAN_PRIORITY_HIGH);
    attachColor(cueText, colorRgba, defaultColorRgba, start, end, SPAN_PRIORITY_HIGH);
  }

  private static void attachFontFace(SpannableStringBuilder cueText, int fontFace,
      int defaultFontFace, int start, int end, int spanPriority) {
    if (fontFace != defaultFontFace) {
      final int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | spanPriority;
      boolean isBold = (fontFace & FONT_FACE_BOLD) != 0;
      boolean isItalic = (fontFace & FONT_FACE_ITALIC) != 0;
      if (isBold) {
        if (isItalic) {
          cueText.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, flags);
        } else {
          cueText.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        }
      } else if (isItalic) {
        cueText.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
      }
      boolean isUnderlined = (fontFace & FONT_FACE_UNDERLINE) != 0;
      if (isUnderlined) {
        cueText.setSpan(new UnderlineSpan(), start, end, flags);
      }
      if (!isUnderlined && !isBold && !isItalic) {
        cueText.setSpan(new StyleSpan(Typeface.NORMAL), start, end, flags);
      }
    }
  }

  private static void attachColor(SpannableStringBuilder cueText, int colorRgba,
      int defaultColorRgba, int start, int end, int spanPriority) {
    if (colorRgba != defaultColorRgba) {
      int colorArgb = ((colorRgba & 0xFF) << 24) | (colorRgba >>> 8);
      cueText.setSpan(new ForegroundColorSpan(colorArgb), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | spanPriority);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private static void attachFontFamily(SpannableStringBuilder cueText, String fontFamily,
      String defaultFontFamily, int start, int end, int spanPriority) {
    if (fontFamily != defaultFontFamily) {
      cueText.setSpan(new TypefaceSpan(fontFamily), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | spanPriority);
    }
  }

  private static void assertTrue(boolean checkValue) throws SubtitleDecoderException {
    if (!checkValue) {
      throw new SubtitleDecoderException("Unexpected subtitle format.");
    }
  }
}
