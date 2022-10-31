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
import static android.graphics.Color.RED;
import static android.graphics.Color.YELLOW;
import static com.google.android.exoplayer2.text.ttml.TtmlRenderUtil.resolveStyle;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_BOLD;
import static com.google.android.exoplayer2.text.ttml.TtmlStyle.STYLE_BOLD_ITALIC;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TtmlRenderUtil}. */
@RunWith(AndroidJUnit4.class)
public final class TtmlRenderUtilTest {

  @Test
  public void testResolveStyleNoStyleAtAll() {
    assertThat(resolveStyle(null, null, null)).isNull();
  }

  @Test
  public void testResolveStyleSingleReferentialStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0"};

    assertThat(TtmlRenderUtil.resolveStyle(null, styleIds, globalStyles))
        .isSameAs(globalStyles.get("s0"));
  }

  @Test
  public void testResolveStyleMultipleReferentialStyles() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0", "s1"};

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(null, styleIds, globalStyles);
    assertThat(resolved).isNotSameAs(globalStyles.get("s0"));
    assertThat(resolved).isNotSameAs(globalStyles.get("s1"));
    assertThat(resolved.getId()).isNull();

    // inherited from s0
    assertThat(resolved.getBackgroundColor()).isEqualTo(BLACK);
    // inherited from s1
    assertThat(resolved.getFontColor()).isEqualTo(RED);
    // merged from s0 and s1
    assertThat(resolved.getStyle()).isEqualTo(STYLE_BOLD_ITALIC);
  }

  @Test
  public void testResolveMergeSingleReferentialStyleIntoInlineStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0"};
    TtmlStyle style = new TtmlStyle();
    style.setBackgroundColor(Color.YELLOW);

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    assertThat(resolved).isSameAs(style);

    // inline attribute not overridden
    assertThat(resolved.getBackgroundColor()).isEqualTo(YELLOW);
    // inherited from referential style
    assertThat(resolved.getStyle()).isEqualTo(STYLE_BOLD);
  }

  @Test
  public void testResolveMergeMultipleReferentialStylesIntoInlineStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0", "s1"};
    TtmlStyle style = new TtmlStyle();
    style.setBackgroundColor(Color.YELLOW);

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    assertThat(resolved).isSameAs(style);

    // inline attribute not overridden
    assertThat(resolved.getBackgroundColor()).isEqualTo(YELLOW);
    // inherited from both referential style
    assertThat(resolved.getStyle()).isEqualTo(STYLE_BOLD_ITALIC);
  }

  @Test
  public void testResolveStyleOnlyInlineStyle() {
    TtmlStyle inlineStyle = new TtmlStyle();
    assertThat(TtmlRenderUtil.resolveStyle(inlineStyle, null, null)).isSameAs(inlineStyle);
  }

  private static Map<String, TtmlStyle> getGlobalStyles() {
    Map<String, TtmlStyle> globalStyles = new HashMap<>();

    TtmlStyle s0 = new TtmlStyle();
    s0.setId("s0");
    s0.setBackgroundColor(Color.BLACK);
    s0.setBold(true);
    globalStyles.put(s0.getId(), s0);

    TtmlStyle s1 = new TtmlStyle();
    s1.setId("s1");
    s1.setBackgroundColor(Color.RED);
    s1.setFontColor(Color.RED);
    s1.setItalic(true);
    globalStyles.put(s1.getId(), s1);

    return globalStyles;
  }

}
