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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Tx3gDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class Tx3gDecoderTest {

  private static final String NO_SUBTITLE = "tx3g/no_subtitle";
  private static final String SAMPLE_JUST_TEXT = "tx3g/sample_just_text";
  private static final String SAMPLE_WITH_STYL = "tx3g/sample_with_styl";
  private static final String SAMPLE_WITH_STYL_ALL_DEFAULTS = "tx3g/sample_with_styl_all_defaults";
  private static final String SAMPLE_UTF16_BE_NO_STYL = "tx3g/sample_utf16_be_no_styl";
  private static final String SAMPLE_UTF16_LE_NO_STYL = "tx3g/sample_utf16_le_no_styl";
  private static final String SAMPLE_WITH_MULTIPLE_STYL = "tx3g/sample_with_multiple_styl";
  private static final String SAMPLE_WITH_OTHER_EXTENSION = "tx3g/sample_with_other_extension";
  private static final String SAMPLE_WITH_TBOX = "tx3g/sample_with_tbox";
  private static final String INITIALIZATION = "tx3g/initialization";
  private static final String INITIALIZATION_ALL_DEFAULTS = "tx3g/initialization_all_defaults";

  @Test
  public void testDecodeNoSubtitle() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), NO_SUBTITLE);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getCues(0)).isEmpty();
  }

  @Test
  public void testDecodeJustText() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_JUST_TEXT);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(0);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeWithStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(3);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.BOLD_ITALIC);
    findSpan(text, 0, 6, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeWithStylAllDefaults() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL_ALL_DEFAULTS);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(0);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeUtf16BeNoStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_UTF16_BE_NO_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("你好");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(0);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeUtf16LeNoStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_UTF16_LE_NO_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("你好");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(0);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeWithMultipleStyl() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_MULTIPLE_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("Line 2\nLine 3");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(4);
    StyleSpan styleSpan = findSpan(text, 0, 5, StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.ITALIC);
    findSpan(text, 7, 12, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 5, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    colorSpan = findSpan(text, 7, 12, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testDecodeWithOtherExtension() throws IOException, SubtitleDecoderException {
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.emptyList());
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), SAMPLE_WITH_OTHER_EXTENSION);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(2);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.BOLD);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  @Test
  public void testInitializationDecodeWithStyl() throws IOException, SubtitleDecoderException {
    byte[] initBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INITIALIZATION);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(5);
    StyleSpan styleSpan = findSpan(text, 0, text.length(), StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.BOLD_ITALIC);
    findSpan(text, 0, text.length(), UnderlineSpan.class);
    TypefaceSpan typefaceSpan = findSpan(text, 0, text.length(), TypefaceSpan.class);
    assertThat(typefaceSpan.getFamily()).isEqualTo(C.SERIF_NAME);
    ForegroundColorSpan colorSpan = findSpan(text, 0, text.length(), ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.RED);
    colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1f);
  }

  @Test
  public void testInitializationDecodeWithTbox() throws IOException, SubtitleDecoderException {
    byte[] initBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INITIALIZATION);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_TBOX);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(4);
    StyleSpan styleSpan = findSpan(text, 0, text.length(), StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.BOLD_ITALIC);
    findSpan(text, 0, text.length(), UnderlineSpan.class);
    TypefaceSpan typefaceSpan = findSpan(text, 0, text.length(), TypefaceSpan.class);
    assertThat(typefaceSpan.getFamily()).isEqualTo(C.SERIF_NAME);
    ForegroundColorSpan colorSpan = findSpan(text, 0, text.length(), ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.RED);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.1875f);
  }

  @Test
  public void testInitializationAllDefaultsDecodeWithStyl()
      throws IOException, SubtitleDecoderException {
    byte[] initBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), INITIALIZATION_ALL_DEFAULTS);
    Tx3gDecoder decoder = new Tx3gDecoder(Collections.singletonList(initBytes));
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SAMPLE_WITH_STYL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    SpannedString text = new SpannedString(subtitle.getCues(0).get(0).text);
    assertThat(text.toString()).isEqualTo("CC Test");
    assertThat(text.getSpans(0, text.length(), Object.class)).hasLength(3);
    StyleSpan styleSpan = findSpan(text, 0, 6, StyleSpan.class);
    assertThat(styleSpan.getStyle()).isEqualTo(Typeface.BOLD_ITALIC);
    findSpan(text, 0, 6, UnderlineSpan.class);
    ForegroundColorSpan colorSpan = findSpan(text, 0, 6, ForegroundColorSpan.class);
    assertThat(colorSpan.getForegroundColor()).isEqualTo(Color.GREEN);
    assertFractionalLinePosition(subtitle.getCues(0).get(0), 0.85f);
  }

  private static <T> T findSpan(
      SpannedString testObject, int expectedStart, int expectedEnd, Class<T> expectedType) {
    T[] spans = testObject.getSpans(0, testObject.length(), expectedType);
    for (T span : spans) {
      if (testObject.getSpanStart(span) == expectedStart
          && testObject.getSpanEnd(span) == expectedEnd) {
        return span;
      }
    }
    fail("Span not found.");
    return null;
  }

  private static void assertFractionalLinePosition(Cue cue, float expectedFraction) {
    assertThat(cue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(cue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_START);
    assertThat(Math.abs(expectedFraction - cue.line) < 1e-6).isTrue();
  }
}
