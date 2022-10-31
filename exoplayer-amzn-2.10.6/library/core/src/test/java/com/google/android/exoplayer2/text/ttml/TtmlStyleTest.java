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

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_BOLD;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_BOLD_ITALIC;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_ITALIC;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_NORMAL;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.UNSPECIFIED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TtmlStyle}. */
@RunWith(AndroidJUnit4.class)
public final class TtmlStyleTest {

  private static final String FONT_FAMILY = "serif";
  private static final String ID = "id";
  public static final int FOREGROUND_COLOR = Color.WHITE;
  public static final int BACKGROUND_COLOR = Color.BLACK;
  private TtmlStyle style;

  @Before
  public void setUp() throws Exception {
    style = new TtmlStyle();
  }

  @Test
  public void testInheritStyle() {
    style.inherit(createAncestorStyle());
    assertWithMessage("id must not be inherited").that(style.getId()).isNull();
    assertThat(style.isUnderline()).isTrue();
    assertThat(style.isLinethrough()).isTrue();
    assertThat(style.getStyle()).isEqualTo(STYLE_BOLD_ITALIC);
    assertThat(style.getFontFamily()).isEqualTo(FONT_FAMILY);
    assertThat(style.getFontColor()).isEqualTo(WHITE);
    assertWithMessage("do not inherit backgroundColor").that(style.hasBackgroundColor()).isFalse();
  }

  @Test
  public void testChainStyle() {
    style.chain(createAncestorStyle());
    assertWithMessage("id must not be inherited").that(style.getId()).isNull();
    assertThat(style.isUnderline()).isTrue();
    assertThat(style.isLinethrough()).isTrue();
    assertThat(style.getStyle()).isEqualTo(STYLE_BOLD_ITALIC);
    assertThat(style.getFontFamily()).isEqualTo(FONT_FAMILY);
    assertThat(style.getFontColor()).isEqualTo(FOREGROUND_COLOR);
    // do inherit backgroundColor when chaining
    assertWithMessage("do not inherit backgroundColor when chaining")
        .that(style.getBackgroundColor()).isEqualTo(BACKGROUND_COLOR);
  }

  private static TtmlStyle createAncestorStyle() {
    TtmlStyle ancestor = new TtmlStyle();
    ancestor.setId(ID);
    ancestor.setItalic(true);
    ancestor.setBold(true);
    ancestor.setBackgroundColor(BACKGROUND_COLOR);
    ancestor.setFontColor(FOREGROUND_COLOR);
    ancestor.setLinethrough(true);
    ancestor.setUnderline(true);
    ancestor.setFontFamily(FONT_FAMILY);
    return ancestor;
  }

  @Test
  public void testStyle() {
    assertThat(style.getStyle()).isEqualTo(UNSPECIFIED);
    style.setItalic(true);
    assertThat(style.getStyle()).isEqualTo(STYLE_ITALIC);
    style.setBold(true);
    assertThat(style.getStyle()).isEqualTo(STYLE_BOLD_ITALIC);
    style.setItalic(false);
    assertThat(style.getStyle()).isEqualTo(STYLE_BOLD);
    style.setBold(false);
    assertThat(style.getStyle()).isEqualTo(STYLE_NORMAL);
  }

  @Test
  public void testLinethrough() {
    assertThat(style.isLinethrough()).isFalse();
    style.setLinethrough(true);
    assertThat(style.isLinethrough()).isTrue();
    style.setLinethrough(false);
    assertThat(style.isLinethrough()).isFalse();
  }

  @Test
  public void testUnderline() {
    assertThat(style.isUnderline()).isFalse();
    style.setUnderline(true);
    assertThat(style.isUnderline()).isTrue();
    style.setUnderline(false);
    assertThat(style.isUnderline()).isFalse();
  }

  @Test
  public void testFontFamily() {
    assertThat(style.getFontFamily()).isNull();
    style.setFontFamily(FONT_FAMILY);
    assertThat(style.getFontFamily()).isEqualTo(FONT_FAMILY);
    style.setFontFamily(null);
    assertThat(style.getFontFamily()).isNull();
  }

  @Test
  public void testColor() {
    assertThat(style.hasFontColor()).isFalse();
    style.setFontColor(Color.BLACK);
    assertThat(style.getFontColor()).isEqualTo(BLACK);
    assertThat(style.hasFontColor()).isTrue();
  }

  @Test
  public void testBackgroundColor() {
    assertThat(style.hasBackgroundColor()).isFalse();
    style.setBackgroundColor(Color.BLACK);
    assertThat(style.getBackgroundColor()).isEqualTo(BLACK);
    assertThat(style.hasBackgroundColor()).isTrue();
  }

  @Test
  public void testId() {
    assertThat(style.getId()).isNull();
    style.setId(ID);
    assertThat(style.getId()).isEqualTo(ID);
    style.setId(null);
    assertThat(style.getId()).isNull();
  }

}
