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
package com.google.android.exoplayer2.util;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.RED;
import static android.graphics.Color.WHITE;
import static android.graphics.Color.argb;
import static android.graphics.Color.parseColor;
import static com.google.android.exoplayer2.util.ColorParser.parseTtmlColor;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ColorParser}. */
@RunWith(AndroidJUnit4.class)
public final class ColorParserTest {

  // Negative tests.

  @Test(expected = IllegalArgumentException.class)
  public void testParseUnknownColor() {
    ColorParser.parseTtmlColor("colorOfAnElectron");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParseNull() {
    ColorParser.parseTtmlColor(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParseEmpty() {
    ColorParser.parseTtmlColor("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRgbColorParsingRgbValuesNegative() {
    ColorParser.parseTtmlColor("rgb(-4, 55, 209)");
  }

  // Positive tests.

  @Test
  public void testHexCodeParsing() {
    assertThat(parseTtmlColor("#FFFFFF")).isEqualTo(WHITE);
    assertThat(parseTtmlColor("#FFFFFFFF")).isEqualTo(WHITE);
    assertThat(parseTtmlColor("#123456")).isEqualTo(parseColor("#FF123456"));
    // Hex colors in ColorParser are RGBA, where-as {@link Color#parseColor} takes ARGB.
    assertThat(parseTtmlColor("#FFFFFF00")).isEqualTo(parseColor("#00FFFFFF"));
    assertThat(parseTtmlColor("#12345678")).isEqualTo(parseColor("#78123456"));
  }

  @Test
  public void testRgbColorParsing() {
    assertThat(parseTtmlColor("rgb(255,255,255)")).isEqualTo(WHITE);
    // Spaces are ignored.
    assertThat(parseTtmlColor("   rgb (      255, 255, 255)")).isEqualTo(WHITE);
  }

  @Test
  public void testRgbColorParsingRgbValuesOutOfBounds() {
    int outOfBounds = ColorParser.parseTtmlColor("rgb(999, 999, 999)");
    int color = Color.rgb(999, 999, 999);
    // Behave like the framework does.
    assertThat(outOfBounds).isEqualTo(color);
  }

  @Test
  public void testRgbaColorParsing() {
    assertThat(parseTtmlColor("rgba(255,255,255,255)")).isEqualTo(WHITE);
    assertThat(parseTtmlColor("rgba(255,255,255,255)"))
        .isEqualTo(argb(255, 255, 255, 255));
    assertThat(parseTtmlColor("rgba(0, 0, 0, 255)")).isEqualTo(BLACK);
    assertThat(parseTtmlColor("rgba(0, 0, 255, 0)"))
        .isEqualTo(argb(0, 0, 0, 255));
    assertThat(parseTtmlColor("rgba(255, 0, 0, 255)")).isEqualTo(RED);
    assertThat(parseTtmlColor("rgba(255, 0, 255, 0)"))
        .isEqualTo(argb(0, 255, 0, 255));
    assertThat(parseTtmlColor("rgba(255, 0, 0, 205)"))
        .isEqualTo(argb(205, 255, 0, 0));
  }

}
