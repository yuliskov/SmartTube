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
package com.google.android.exoplayer2.text.webvtt;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link WebvttDecoder}. */
@RunWith(AndroidJUnit4.class)
public class WebvttDecoderTest {

  private static final String TYPICAL_FILE = "webvtt/typical";
  private static final String TYPICAL_WITH_BAD_TIMESTAMPS = "webvtt/typical_with_bad_timestamps";
  private static final String TYPICAL_WITH_IDS_FILE = "webvtt/typical_with_identifiers";
  private static final String TYPICAL_WITH_COMMENTS_FILE = "webvtt/typical_with_comments";
  private static final String WITH_POSITIONING_FILE = "webvtt/with_positioning";
  private static final String WITH_BAD_CUE_HEADER_FILE = "webvtt/with_bad_cue_header";
  private static final String WITH_TAGS_FILE = "webvtt/with_tags";
  private static final String WITH_CSS_STYLES = "webvtt/with_css_styles";
  private static final String WITH_CSS_COMPLEX_SELECTORS = "webvtt/with_css_complex_selectors";
  private static final String WITH_BOM = "webvtt/with_bom";
  private static final String EMPTY_FILE = "webvtt/empty";

  @Test
  public void testDecodeEmpty() throws IOException {
    WebvttDecoder decoder = new WebvttDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY_FILE);
    try {
      decoder.decode(bytes, bytes.length, /* reset= */ false);
      fail();
    } catch (SubtitleDecoderException expected) {
      // Do nothing.
    }
  }

  @Test
  public void testDecodeTypical() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_FILE);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
  }

  @Test
  public void testDecodeWithBom() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_BOM);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
  }

  @Test
  public void testDecodeTypicalWithBadTimestamps() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_WITH_BAD_TIMESTAMPS);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
  }

  @Test
  public void testDecodeTypicalWithIds() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_WITH_IDS_FILE);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
  }

  @Test
  public void testDecodeTypicalWithComments() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(TYPICAL_WITH_COMMENTS_FILE);

    // test event count
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // test cues
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
  }

  @Test
  public void testDecodeWithTags() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_TAGS_FILE);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(8);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 4,
        /* startTimeUs= */ 4000000,
        /* endTimeUs= */ 5000000,
        "This is the third subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 6,
        /* startTimeUs= */ 6000000,
        /* endTimeUs= */ 7000000,
        "This is the <fourth> &subtitle.");
  }

  @Test
  public void testDecodeWithPositioning() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_POSITIONING_FILE);
    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);
    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.",
        Alignment.ALIGN_NORMAL,
        /* line= */ Cue.DIMEN_UNSET,
        /* lineType= */ Cue.TYPE_UNSET,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* position= */ 0.1f,
        /* positionAnchor= */ Cue.ANCHOR_TYPE_START,
        /* size= */ 0.35f);
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.",
        Alignment.ALIGN_OPPOSITE,
        /* line= */ Cue.DIMEN_UNSET,
        /* lineType= */ Cue.TYPE_UNSET,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* position= */ Cue.DIMEN_UNSET,
        /* positionAnchor= */ Cue.TYPE_UNSET,
        /* size= */ 0.35f);
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 4,
        /* startTimeUs= */ 4000000,
        /* endTimeUs= */ 5000000,
        "This is the third subtitle.",
        Alignment.ALIGN_CENTER,
        /* line= */ 0.45f,
        /* lineType= */ Cue.LINE_TYPE_FRACTION,
        /* lineAnchor= */ Cue.ANCHOR_TYPE_END,
        /* position= */ Cue.DIMEN_UNSET,
        /* positionAnchor= */ Cue.TYPE_UNSET,
        /* size= */ 0.35f);
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 6,
        /* startTimeUs= */ 6000000,
        /* endTimeUs= */ 7000000,
        "This is the fourth subtitle.",
        Alignment.ALIGN_CENTER,
        /* line= */ -11f,
        /* lineType= */ Cue.LINE_TYPE_NUMBER,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* position= */ Cue.DIMEN_UNSET,
        /* positionAnchor= */ Cue.TYPE_UNSET,
        /* size= */ Cue.DIMEN_UNSET);
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 8,
        /* startTimeUs= */ 7000000,
        /* endTimeUs= */ 8000000,
        "This is the fifth subtitle.",
        Alignment.ALIGN_OPPOSITE,
        /* line= */ Cue.DIMEN_UNSET,
        /* lineType= */ Cue.TYPE_UNSET,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* position= */ 0.1f,
        /* positionAnchor= */ Cue.ANCHOR_TYPE_END,
        /* size= */ 0.1f);
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 10,
        /* startTimeUs= */ 10000000,
        /* endTimeUs= */ 11000000,
        "This is the sixth subtitle.",
        Alignment.ALIGN_CENTER,
        /* line= */ 0.45f,
        /* lineType= */ Cue.LINE_TYPE_FRACTION,
        /* lineAnchor= */ Cue.ANCHOR_TYPE_END,
        /* position= */ Cue.DIMEN_UNSET,
        /* positionAnchor= */ Cue.TYPE_UNSET,
        /* size= */ 0.35f);
  }

  @Test
  public void testDecodeWithBadCueHeader() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_BAD_CUE_HEADER_FILE);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 4000000,
        /* endTimeUs= */ 5000000,
        "This is the third subtitle.");
  }

  @Test
  public void testWebvttWithCssStyle() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_CSS_STYLES);

    // Test event count.
    assertThat(subtitle.getEventTimeCount()).isEqualTo(8);

    // Test cues.
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 0,
        /* startTimeUs= */ 0,
        /* endTimeUs= */ 1234000,
        "This is the first subtitle.");
    assertCue(
        subtitle,
        /* eventTimeIndex= */ 2,
        /* startTimeUs= */ 2345000,
        /* endTimeUs= */ 3456000,
        "This is the second subtitle.");

    Spanned s1 = getUniqueSpanTextAt(subtitle, /* timeUs= */ 0);
    Spanned s2 = getUniqueSpanTextAt(subtitle, /* timeUs= */ 2345000);
    Spanned s3 = getUniqueSpanTextAt(subtitle, /* timeUs= */ 20000000);
    Spanned s4 = getUniqueSpanTextAt(subtitle, /* timeUs= */ 25000000);
    assertThat(s1.getSpans(/* start= */ 0, s1.length(), ForegroundColorSpan.class)).hasLength(1);
    assertThat(s1.getSpans(/* start= */ 0, s1.length(), BackgroundColorSpan.class)).hasLength(1);
    assertThat(s2.getSpans(/* start= */ 0, s2.length(), ForegroundColorSpan.class)).hasLength(2);
    assertThat(s3.getSpans(/* start= */ 10, s3.length(), UnderlineSpan.class)).hasLength(1);
    assertThat(s4.getSpans(/* start= */ 0, /* end= */ 16, BackgroundColorSpan.class)).hasLength(2);
    assertThat(s4.getSpans(/* start= */ 17, s4.length(), StyleSpan.class)).hasLength(1);
    assertThat(s4.getSpans(/* start= */ 17, s4.length(), StyleSpan.class)[0].getStyle())
        .isEqualTo(Typeface.BOLD);
  }

  @Test
  public void testWithComplexCssSelectors() throws IOException, SubtitleDecoderException {
    WebvttSubtitle subtitle = getSubtitleForTestAsset(WITH_CSS_COMPLEX_SELECTORS);
    Spanned text = getUniqueSpanTextAt(subtitle, /* timeUs= */ 0);
    assertThat(text.getSpans(/* start= */ 30, text.length(), ForegroundColorSpan.class))
        .hasLength(1);
    assertThat(
            text.getSpans(/* start= */ 30, text.length(), ForegroundColorSpan.class)[0]
                .getForegroundColor())
        .isEqualTo(0xFFEE82EE);
    assertThat(text.getSpans(/* start= */ 30, text.length(), TypefaceSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 30, text.length(), TypefaceSpan.class)[0].getFamily())
        .isEqualTo("courier");

    text = getUniqueSpanTextAt(subtitle, /* timeUs= */ 2000000);
    assertThat(text.getSpans(/* start= */ 5, text.length(), TypefaceSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 5, text.length(), TypefaceSpan.class)[0].getFamily())
        .isEqualTo("courier");

    text = getUniqueSpanTextAt(subtitle, /* timeUs= */ 2500000);
    assertThat(text.getSpans(/* start= */ 5, text.length(), StyleSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 5, text.length(), StyleSpan.class)[0].getStyle())
        .isEqualTo(Typeface.BOLD);
    assertThat(text.getSpans(/* start= */ 5, text.length(), TypefaceSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 5, text.length(), TypefaceSpan.class)[0].getFamily())
        .isEqualTo("courier");

    text = getUniqueSpanTextAt(subtitle, /* timeUs= */ 4000000);
    assertThat(text.getSpans(/* start= */ 6, /* end= */ 22, StyleSpan.class)).hasLength(0);
    assertThat(text.getSpans(/* start= */ 30, text.length(), StyleSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 30, text.length(), StyleSpan.class)[0].getStyle())
        .isEqualTo(Typeface.BOLD);

    text = getUniqueSpanTextAt(subtitle, /* timeUs= */ 5000000);
    assertThat(text.getSpans(/* start= */ 9, /* end= */ 17, StyleSpan.class)).hasLength(0);
    assertThat(text.getSpans(/* start= */ 19, text.length(), StyleSpan.class)).hasLength(1);
    assertThat(text.getSpans(/* start= */ 19, text.length(), StyleSpan.class)[0].getStyle())
        .isEqualTo(Typeface.ITALIC);
  }

  private WebvttSubtitle getSubtitleForTestAsset(String asset)
      throws IOException, SubtitleDecoderException {
    WebvttDecoder decoder = new WebvttDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), asset);
    return decoder.decode(bytes, bytes.length, /* reset= */ false);
  }

  private Spanned getUniqueSpanTextAt(WebvttSubtitle sub, long timeUs) {
    return (Spanned) sub.getCues(timeUs).get(0).text;
  }

  private static void assertCue(
      WebvttSubtitle subtitle, int eventTimeIndex, long startTimeUs, int endTimeUs, String text) {
    assertCue(
        subtitle,
        eventTimeIndex,
        startTimeUs,
        endTimeUs,
        text,
        /* textAlignment= */ null,
        /* line= */ Cue.DIMEN_UNSET,
        /* lineType= */ Cue.TYPE_UNSET,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* position= */ Cue.DIMEN_UNSET,
        /* positionAnchor= */ Cue.TYPE_UNSET,
        /* size= */ Cue.DIMEN_UNSET);
  }

  private static void assertCue(
      WebvttSubtitle subtitle,
      int eventTimeIndex,
      long startTimeUs,
      int endTimeUs,
      String text,
      Alignment textAlignment,
      float line,
      int lineType,
      int lineAnchor,
      float position,
      int positionAnchor,
      float size) {
    assertThat(subtitle.getEventTime(eventTimeIndex)).isEqualTo(startTimeUs);
    assertThat(subtitle.getEventTime(eventTimeIndex + 1)).isEqualTo(endTimeUs);
    List<Cue> cues = subtitle.getCues(subtitle.getEventTime(eventTimeIndex));
    assertThat(cues).hasSize(1);
    // Assert cue properties.
    Cue cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo(text);
    assertThat(cue.textAlignment).isEqualTo(textAlignment);
    assertThat(cue.line).isEqualTo(line);
    assertThat(cue.lineType).isEqualTo(lineType);
    assertThat(cue.lineAnchor).isEqualTo(lineAnchor);
    assertThat(cue.position).isEqualTo(position);
    assertThat(cue.positionAnchor).isEqualTo(positionAnchor);
    assertThat(cue.size).isEqualTo(size);
  }
}
