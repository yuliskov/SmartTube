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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ColorParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TtmlDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class TtmlDecoderTest {

  private static final String INLINE_ATTRIBUTES_TTML_FILE = "ttml/inline_style_attributes.xml";
  private static final String INHERIT_STYLE_TTML_FILE = "ttml/inherit_style.xml";
  private static final String INHERIT_STYLE_OVERRIDE_TTML_FILE =
      "ttml/inherit_and_override_style.xml";
  private static final String INHERIT_GLOBAL_AND_PARENT_TTML_FILE =
      "ttml/inherit_global_and_parent.xml";
  private static final String INHERIT_MULTIPLE_STYLES_TTML_FILE =
      "ttml/inherit_multiple_styles.xml";
  private static final String CHAIN_MULTIPLE_STYLES_TTML_FILE = "ttml/chain_multiple_styles.xml";
  private static final String MULTIPLE_REGIONS_TTML_FILE = "ttml/multiple_regions.xml";
  private static final String NO_UNDERLINE_LINETHROUGH_TTML_FILE =
      "ttml/no_underline_linethrough.xml";
  private static final String FONT_SIZE_TTML_FILE = "ttml/font_size.xml";
  private static final String FONT_SIZE_MISSING_UNIT_TTML_FILE = "ttml/font_size_no_unit.xml";
  private static final String FONT_SIZE_INVALID_TTML_FILE = "ttml/font_size_invalid.xml";
  private static final String FONT_SIZE_EMPTY_TTML_FILE = "ttml/font_size_empty.xml";
  private static final String FRAME_RATE_TTML_FILE = "ttml/frame_rate.xml";
  private static final String BITMAP_REGION_FILE = "ttml/bitmap_percentage_region.xml";
  private static final String BITMAP_PIXEL_REGION_FILE = "ttml/bitmap_pixel_region.xml";
  private static final String BITMAP_UNSUPPORTED_REGION_FILE = "ttml/bitmap_unsupported_region.xml";

  @Test
  public void testInlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;
    assertThat(firstPStyle.getFontColor()).isEqualTo(ColorParser.parseTtmlColor("yellow"));
    assertThat(firstPStyle.getBackgroundColor()).isEqualTo(ColorParser.parseTtmlColor("blue"));
    assertThat(firstPStyle.getFontFamily()).isEqualTo("serif");
    assertThat(firstPStyle.getStyle()).isEqualTo(TtmlStyle.STYLE_BOLD_ITALIC);
    assertThat(firstPStyle.isUnderline()).isTrue();
  }

  @Test
  public void testInheritInlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertSpans(
        subtitle,
        20,
        "text 2",
        "sansSerif",
        TtmlStyle.STYLE_ITALIC,
        0xFF00FFFF,
        ColorParser.parseTtmlColor("lime"),
        false,
        true,
        null);
  }

  /**
   * Regression test for devices on JellyBean where some named colors are not correctly defined on
   * framework level. Tests that <i>lime</i> resolves to <code>#FF00FF00</code> not <code>#00FF00
   * </code>.
   *
   * @see <a
   *     href="https://github.com/android/platform_frameworks_base/blob/jb-mr2-release/graphics/java/android/graphics/Color.java#L414">
   *     JellyBean Color</a> <a
   *     href="https://github.com/android/platform_frameworks_base/blob/kitkat-mr2.2-release/graphics/java/android/graphics/Color.java#L414">
   *     Kitkat Color</a>
   * @throws IOException thrown if reading subtitle file fails.
   */
  @Test
  public void testLime() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertSpans(
        subtitle,
        20,
        "text 2",
        "sansSerif",
        TtmlStyle.STYLE_ITALIC,
        0xFF00FFFF,
        0xFF00FF00,
        false,
        true,
        null);
  }

  @Test
  public void testInheritGlobalStyle() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    assertSpans(
        subtitle,
        10,
        "text 1",
        "serif",
        TtmlStyle.STYLE_BOLD_ITALIC,
        0xFF0000FF,
        0xFFFFFF00,
        true,
        false,
        null);
  }

  @Test
  public void testInheritGlobalStyleOverriddenByInlineAttributes()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_OVERRIDE_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    assertSpans(
        subtitle,
        10,
        "text 1",
        "serif",
        TtmlStyle.STYLE_BOLD_ITALIC,
        0xFF0000FF,
        0xFFFFFF00,
        true,
        false,
        null);
    assertSpans(
        subtitle,
        20,
        "text 2",
        "sansSerif",
        TtmlStyle.STYLE_ITALIC,
        0xFFFF0000,
        0xFFFFFF00,
        true,
        false,
        null);
  }

  @Test
  public void testInheritGlobalAndParent() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    assertSpans(
        subtitle,
        10,
        "text 1",
        "sansSerif",
        TtmlStyle.STYLE_NORMAL,
        0xFFFF0000,
        ColorParser.parseTtmlColor("lime"),
        false,
        true,
        Layout.Alignment.ALIGN_CENTER);
    assertSpans(
        subtitle,
        20,
        "text 2",
        "serif",
        TtmlStyle.STYLE_BOLD_ITALIC,
        0xFF0000FF,
        0xFFFFFF00,
        true,
        true,
        Layout.Alignment.ALIGN_CENTER);
  }

  @Test
  public void testInheritMultipleStyles() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);
    assertSpans(
        subtitle,
        10,
        "text 1",
        "sansSerif",
        TtmlStyle.STYLE_BOLD_ITALIC,
        0xFF0000FF,
        0xFFFFFF00,
        false,
        true,
        null);
  }

  @Test
  public void testInheritMultipleStylesWithoutLocalAttributes()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);
    assertSpans(
        subtitle,
        20,
        "text 2",
        "sansSerif",
        TtmlStyle.STYLE_BOLD_ITALIC,
        0xFF0000FF,
        0xFF000000,
        false,
        true,
        null);
  }

  @Test
  public void testMergeMultipleStylesWithParentStyle()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);
    assertSpans(
        subtitle,
        30,
        "text 2.5",
        "sansSerifInline",
        TtmlStyle.STYLE_ITALIC,
        0xFFFF0000,
        0xFFFFFF00,
        true,
        true,
        null);
  }

  @Test
  public void testMultipleRegions() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(MULTIPLE_REGIONS_TTML_FILE);
    List<Cue> cues = subtitle.getCues(1000000);
    assertThat(cues).hasSize(2);
    Cue cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("lorem");
    assertThat(cue.position).isEqualTo(10f / 100f);
    assertThat(cue.line).isEqualTo(10f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cue = cues.get(1);
    assertThat(cue.text.toString()).isEqualTo("amet");
    assertThat(cue.position).isEqualTo(60f / 100f);
    assertThat(cue.line).isEqualTo(10f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cues = subtitle.getCues(5000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("ipsum");
    assertThat(cue.position).isEqualTo(40f / 100f);
    assertThat(cue.line).isEqualTo(40f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cues = subtitle.getCues(9000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("dolor");
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    // TODO: Should be as below, once https://github.com/google/ExoPlayer/issues/2953 is fixed.
    // assertEquals(10f / 100f, cue.position);
    // assertEquals(80f / 100f, cue.line);
    // assertEquals(1f, cue.size);

    cues = subtitle.getCues(21000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("She first said this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
    assertThat(cue.size).isEqualTo(35f / 100f);
    cues = subtitle.getCues(25000000);
    cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("She first said this\nThen this");
    cues = subtitle.getCues(29000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("She first said this\nThen this\nFinally this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
  }

  @Test
  public void testEmptyStyleAttribute() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fourthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 3);

    assertThat(queryChildrenForTag(fourthDiv, TtmlNode.TAG_P, 0).getStyleIds()).isNull();
  }

  @Test
  public void testNonexistingStyleId() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fifthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 4);

    assertThat(queryChildrenForTag(fifthDiv, TtmlNode.TAG_P, 0).getStyleIds()).hasLength(1);
  }

  @Test
  public void testNonExistingAndExistingStyleIdWithRedundantSpaces()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode sixthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 5);

    String[] styleIds = queryChildrenForTag(sixthDiv, TtmlNode.TAG_P, 0).getStyleIds();
    assertThat(styleIds).hasLength(2);
  }

  @Test
  public void testMultipleChaining() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(CHAIN_MULTIPLE_STYLES_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);

    Map<String, TtmlStyle> globalStyles = subtitle.getGlobalStyles();

    TtmlStyle style = globalStyles.get("s2");
    assertThat(style.getFontFamily()).isEqualTo("serif");
    assertThat(style.getBackgroundColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getFontColor()).isEqualTo(0xFF000000);
    assertThat(style.getStyle()).isEqualTo(TtmlStyle.STYLE_BOLD_ITALIC);
    assertThat(style.isLinethrough()).isTrue();

    style = globalStyles.get("s3");
    // only difference: color must be RED
    assertThat(style.getFontColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getFontFamily()).isEqualTo("serif");
    assertThat(style.getBackgroundColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getStyle()).isEqualTo(TtmlStyle.STYLE_BOLD_ITALIC);
    assertThat(style.isLinethrough()).isTrue();
  }

  @Test
  public void testNoUnderline() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertWithMessage("noUnderline from inline attribute expected")
        .that(style.isUnderline())
        .isFalse();
  }

  @Test
  public void testNoLinethrough() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertWithMessage("noLineThrough from inline attribute expected in second pNode")
        .that(style.isLinethrough())
        .isFalse();
  }

  @Test
  public void testFontSizeSpans() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(10);

    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertThat(cues).hasSize(1);
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("text 1");
    assertAbsoluteFontSize(spannable, 32);

    cues = subtitle.getCues(20 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(cues.get(0).text)).isEqualTo("text 2");
    assertRelativeFontSize(spannable, 2.2f);

    cues = subtitle.getCues(30 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(cues.get(0).text)).isEqualTo("text 3");
    assertRelativeFontSize(spannable, 1.5f);

    cues = subtitle.getCues(40 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(cues.get(0).text)).isEqualTo("two values");
    assertAbsoluteFontSize(spannable, 16);

    cues = subtitle.getCues(50 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(cues.get(0).text)).isEqualTo("leading dot");
    assertRelativeFontSize(spannable, 0.5f);
  }

  @Test
  public void testFontSizeWithMissingUnitIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_MISSING_UNIT_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertThat(cues).hasSize(1);
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("no unit");
    assertThat(spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)).hasLength(0);
    assertThat(spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class)).hasLength(0);
  }

  @Test
  public void testFontSizeWithInvalidValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_INVALID_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);

    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertThat(cues).hasSize(1);
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("invalid");
    assertThat(spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)).hasLength(0);
    assertThat(spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class)).hasLength(0);

    cues = subtitle.getCues(20 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("invalid");
    assertThat(spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)).hasLength(0);
    assertThat(spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class)).hasLength(0);

    cues = subtitle.getCues(30 * 1000000);
    assertThat(cues).hasSize(1);
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("invalid dot");
    assertThat(spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)).hasLength(0);
    assertThat(spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class)).hasLength(0);
  }

  @Test
  public void testFontSizeWithEmptyValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_EMPTY_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertThat(cues).hasSize(1);
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertThat(String.valueOf(spannable)).isEqualTo("empty");
    assertThat(spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)).hasLength(0);
    assertThat(spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class)).hasLength(0);
  }

  @Test
  public void testFrameRate() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FRAME_RATE_TTML_FILE);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertThat(subtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(subtitle.getEventTime(1)).isEqualTo(1_010_000);
    assertThat((double) subtitle.getEventTime(2)).isWithin(1000).of(1_001_000_000);
    assertThat((double) subtitle.getEventTime(3)).isWithin(2000).of(2_002_000_000);
  }

  @Test
  public void testBitmapPercentageRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_REGION_FILE);

    List<Cue> cues = subtitle.getCues(1000000);
    assertThat(cues).hasSize(1);
    Cue cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);

    cues = subtitle.getCues(4000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(21f / 100f);
    assertThat(cue.line).isEqualTo(35f / 100f);
    assertThat(cue.size).isEqualTo(57f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(6f / 100f);

    cues = subtitle.getCues(7500000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);
  }

  @Test
  public void testBitmapPixelRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_PIXEL_REGION_FILE);

    List<Cue> cues = subtitle.getCues(1000000);
    assertThat(cues).hasSize(1);
    Cue cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(307f / 1280f);
    assertThat(cue.line).isEqualTo(562f / 720f);
    assertThat(cue.size).isEqualTo(653f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(86f / 720f);

    cues = subtitle.getCues(4000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(269f / 1280f);
    assertThat(cue.line).isEqualTo(612f / 720f);
    assertThat(cue.size).isEqualTo(730f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(43f / 720f);
  }

  @Test
  public void testBitmapUnsupportedRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_UNSUPPORTED_REGION_FILE);

    List<Cue> cues = subtitle.getCues(1000000);
    assertThat(cues).hasSize(1);
    Cue cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);

    cues = subtitle.getCues(4000000);
    assertThat(cues).hasSize(1);
    cue = cues.get(0);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);
  }

  private void assertSpans(
      TtmlSubtitle subtitle,
      int second,
      String text,
      String font,
      int fontStyle,
      int backgroundColor,
      int color,
      boolean isUnderline,
      boolean isLinethrough,
      Layout.Alignment alignment) {

    long timeUs = second * 1000000L;
    List<Cue> cues = subtitle.getCues(timeUs);

    assertThat(cues).hasSize(1);
    assertThat(String.valueOf(cues.get(0).text)).isEqualTo(text);
    assertWithMessage("single cue expected for timeUs: " + timeUs).that(cues.size()).isEqualTo(1);
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;

    assertFont(spannable, font);
    assertStyle(spannable, fontStyle);
    assertUnderline(spannable, isUnderline);
    assertStrikethrough(spannable, isLinethrough);
    assertUnderline(spannable, isUnderline);
    assertBackground(spannable, backgroundColor);
    assertForeground(spannable, color);
    assertAlignment(spannable, alignment);
  }

  private void assertAbsoluteFontSize(Spannable spannable, int absoluteFontSize) {
    AbsoluteSizeSpan[] absoluteSizeSpans =
        spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class);
    assertThat(absoluteSizeSpans).hasLength(1);
    assertThat(absoluteSizeSpans[0].getSize()).isEqualTo(absoluteFontSize);
  }

  private void assertRelativeFontSize(Spannable spannable, float relativeFontSize) {
    RelativeSizeSpan[] relativeSizeSpans =
        spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class);
    assertThat(relativeSizeSpans).hasLength(1);
    assertThat(relativeSizeSpans[0].getSizeChange()).isEqualTo(relativeFontSize);
  }

  private void assertFont(Spannable spannable, String font) {
    TypefaceSpan[] typefaceSpans = spannable.getSpans(0, spannable.length(), TypefaceSpan.class);
    assertThat(typefaceSpans[typefaceSpans.length - 1].getFamily()).isEqualTo(font);
  }

  private void assertStyle(Spannable spannable, int fontStyle) {
    StyleSpan[] styleSpans = spannable.getSpans(0, spannable.length(), StyleSpan.class);
    assertThat(styleSpans[styleSpans.length - 1].getStyle()).isEqualTo(fontStyle);
  }

  private void assertUnderline(Spannable spannable, boolean isUnderline) {
    UnderlineSpan[] underlineSpans = spannable.getSpans(0, spannable.length(), UnderlineSpan.class);
    assertWithMessage(isUnderline ? "must be underlined" : "must not be underlined")
        .that(underlineSpans)
        .hasLength(isUnderline ? 1 : 0);
  }

  private void assertStrikethrough(Spannable spannable, boolean isStrikethrough) {
    StrikethroughSpan[] striketroughSpans =
        spannable.getSpans(0, spannable.length(), StrikethroughSpan.class);
    assertWithMessage(isStrikethrough ? "must be strikethrough" : "must not be strikethrough")
        .that(striketroughSpans)
        .hasLength(isStrikethrough ? 1 : 0);
  }

  private void assertBackground(Spannable spannable, int backgroundColor) {
    BackgroundColorSpan[] backgroundColorSpans =
        spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
    if (backgroundColor != 0) {
      assertThat(backgroundColorSpans[backgroundColorSpans.length - 1].getBackgroundColor())
          .isEqualTo(backgroundColor);
    } else {
      assertThat(backgroundColorSpans).hasLength(0);
    }
  }

  private void assertForeground(Spannable spannable, int foregroundColor) {
    ForegroundColorSpan[] foregroundColorSpans =
        spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class);
    assertThat(foregroundColorSpans[foregroundColorSpans.length - 1].getForegroundColor())
        .isEqualTo(foregroundColor);
  }

  private void assertAlignment(Spannable spannable, Layout.Alignment alignment) {
    if (alignment != null) {
      AlignmentSpan.Standard[] alignmentSpans =
          spannable.getSpans(0, spannable.length(), AlignmentSpan.Standard.class);
      assertThat(alignmentSpans).hasLength(1);
      assertThat(alignmentSpans[0].getAlignment()).isEqualTo(alignment);
    } else {
      assertThat(spannable.getSpans(0, spannable.length(), AlignmentSpan.Standard.class))
          .hasLength(0);
    }
  }

  private TtmlNode queryChildrenForTag(TtmlNode node, String tag, int pos) {
    int count = 0;
    for (int i = 0; i < node.getChildCount(); i++) {
      if (tag.equals(node.getChild(i).tag)) {
        if (pos == count++) {
          return node.getChild(i);
        }
      }
    }
    throw new IllegalStateException("tag not found");
  }

  private TtmlSubtitle getSubtitle(String file) throws IOException, SubtitleDecoderException {
    TtmlDecoder ttmlDecoder = new TtmlDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), file);
    return ttmlDecoder.decode(bytes, bytes.length, false);
  }
}
