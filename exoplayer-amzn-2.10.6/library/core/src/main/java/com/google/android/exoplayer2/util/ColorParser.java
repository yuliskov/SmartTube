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

import android.text.TextUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for color expressions found in styling formats, e.g. TTML and CSS.
 *
 * @see <a href="https://w3c.github.io/webvtt/#styling">WebVTT CSS Styling</a>
 * @see <a href="https://www.w3.org/TR/ttml2/">Timed Text Markup Language 2 (TTML2) - 10.3.5</a>
 */
public final class ColorParser {

  private static final String RGB = "rgb";
  private static final String RGBA = "rgba";

  private static final Pattern RGB_PATTERN = Pattern.compile(
      "^rgb\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$");

  private static final Pattern RGBA_PATTERN_INT_ALPHA = Pattern.compile(
      "^rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$");

  private static final Pattern RGBA_PATTERN_FLOAT_ALPHA = Pattern.compile(
      "^rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d*\\.?\\d*?)\\)$");

  private static final Map<String, Integer> COLOR_MAP;

  /**
   * Parses a TTML color expression.
   *
   * @param colorExpression The color expression.
   * @return The parsed ARGB color.
   */
  public static int parseTtmlColor(String colorExpression) {
    return parseColorInternal(colorExpression, false);
  }

  /**
   * Parses a CSS color expression.
   *
   * @param colorExpression The color expression.
   * @return The parsed ARGB color.
   */
  public static int parseCssColor(String colorExpression) {
    return parseColorInternal(colorExpression, true);
  }

  private static int parseColorInternal(String colorExpression, boolean alphaHasFloatFormat) {
    Assertions.checkArgument(!TextUtils.isEmpty(colorExpression));
    colorExpression = colorExpression.replace(" ", "");
    if (colorExpression.charAt(0) == '#') {
      // Parse using Long to avoid failure when colorExpression is greater than #7FFFFFFF.
      int color = (int) Long.parseLong(colorExpression.substring(1), 16);
      if (colorExpression.length() == 7) {
        // Set the alpha value
        color |= 0xFF000000;
      } else if (colorExpression.length() == 9) {
        // We have #RRGGBBAA, but we need #AARRGGBB
        color = ((color & 0xFF) << 24) | (color >>> 8);
      } else {
        throw new IllegalArgumentException();
      }
      return color;
    } else if (colorExpression.startsWith(RGBA)) {
      Matcher matcher = (alphaHasFloatFormat ? RGBA_PATTERN_FLOAT_ALPHA : RGBA_PATTERN_INT_ALPHA)
          .matcher(colorExpression);
      if (matcher.matches()) {
        return argb(
          alphaHasFloatFormat ? (int) (255 * Float.parseFloat(matcher.group(4)))
              : Integer.parseInt(matcher.group(4), 10),
          Integer.parseInt(matcher.group(1), 10),
          Integer.parseInt(matcher.group(2), 10),
          Integer.parseInt(matcher.group(3), 10)
        );
      }
    } else if (colorExpression.startsWith(RGB)) {
      Matcher matcher = RGB_PATTERN.matcher(colorExpression);
      if (matcher.matches()) {
        return rgb(
          Integer.parseInt(matcher.group(1), 10),
          Integer.parseInt(matcher.group(2), 10),
          Integer.parseInt(matcher.group(3), 10)
        );
      }
    } else {
      // we use our own color map
      Integer color = COLOR_MAP.get(Util.toLowerInvariant(colorExpression));
      if (color != null) {
        return color;
      }
    }
    throw new IllegalArgumentException();
  }

  private static int argb(int alpha, int red, int green, int blue) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  private static int rgb(int red, int green, int blue) {
    return argb(0xFF, red, green, blue);
  }

  static {
    COLOR_MAP = new HashMap<>();
    COLOR_MAP.put("aliceblue", 0xFFF0F8FF);
    COLOR_MAP.put("antiquewhite", 0xFFFAEBD7);
    COLOR_MAP.put("aqua", 0xFF00FFFF);
    COLOR_MAP.put("aquamarine", 0xFF7FFFD4);
    COLOR_MAP.put("azure", 0xFFF0FFFF);
    COLOR_MAP.put("beige", 0xFFF5F5DC);
    COLOR_MAP.put("bisque", 0xFFFFE4C4);
    COLOR_MAP.put("black", 0xFF000000);
    COLOR_MAP.put("blanchedalmond", 0xFFFFEBCD);
    COLOR_MAP.put("blue", 0xFF0000FF);
    COLOR_MAP.put("blueviolet", 0xFF8A2BE2);
    COLOR_MAP.put("brown", 0xFFA52A2A);
    COLOR_MAP.put("burlywood", 0xFFDEB887);
    COLOR_MAP.put("cadetblue", 0xFF5F9EA0);
    COLOR_MAP.put("chartreuse", 0xFF7FFF00);
    COLOR_MAP.put("chocolate", 0xFFD2691E);
    COLOR_MAP.put("coral", 0xFFFF7F50);
    COLOR_MAP.put("cornflowerblue", 0xFF6495ED);
    COLOR_MAP.put("cornsilk", 0xFFFFF8DC);
    COLOR_MAP.put("crimson", 0xFFDC143C);
    COLOR_MAP.put("cyan", 0xFF00FFFF);
    COLOR_MAP.put("darkblue", 0xFF00008B);
    COLOR_MAP.put("darkcyan", 0xFF008B8B);
    COLOR_MAP.put("darkgoldenrod", 0xFFB8860B);
    COLOR_MAP.put("darkgray", 0xFFA9A9A9);
    COLOR_MAP.put("darkgreen", 0xFF006400);
    COLOR_MAP.put("darkgrey", 0xFFA9A9A9);
    COLOR_MAP.put("darkkhaki", 0xFFBDB76B);
    COLOR_MAP.put("darkmagenta", 0xFF8B008B);
    COLOR_MAP.put("darkolivegreen", 0xFF556B2F);
    COLOR_MAP.put("darkorange", 0xFFFF8C00);
    COLOR_MAP.put("darkorchid", 0xFF9932CC);
    COLOR_MAP.put("darkred", 0xFF8B0000);
    COLOR_MAP.put("darksalmon", 0xFFE9967A);
    COLOR_MAP.put("darkseagreen", 0xFF8FBC8F);
    COLOR_MAP.put("darkslateblue", 0xFF483D8B);
    COLOR_MAP.put("darkslategray", 0xFF2F4F4F);
    COLOR_MAP.put("darkslategrey", 0xFF2F4F4F);
    COLOR_MAP.put("darkturquoise", 0xFF00CED1);
    COLOR_MAP.put("darkviolet", 0xFF9400D3);
    COLOR_MAP.put("deeppink", 0xFFFF1493);
    COLOR_MAP.put("deepskyblue", 0xFF00BFFF);
    COLOR_MAP.put("dimgray", 0xFF696969);
    COLOR_MAP.put("dimgrey", 0xFF696969);
    COLOR_MAP.put("dodgerblue", 0xFF1E90FF);
    COLOR_MAP.put("firebrick", 0xFFB22222);
    COLOR_MAP.put("floralwhite", 0xFFFFFAF0);
    COLOR_MAP.put("forestgreen", 0xFF228B22);
    COLOR_MAP.put("fuchsia", 0xFFFF00FF);
    COLOR_MAP.put("gainsboro", 0xFFDCDCDC);
    COLOR_MAP.put("ghostwhite", 0xFFF8F8FF);
    COLOR_MAP.put("gold", 0xFFFFD700);
    COLOR_MAP.put("goldenrod", 0xFFDAA520);
    COLOR_MAP.put("gray", 0xFF808080);
    COLOR_MAP.put("green", 0xFF008000);
    COLOR_MAP.put("greenyellow", 0xFFADFF2F);
    COLOR_MAP.put("grey", 0xFF808080);
    COLOR_MAP.put("honeydew", 0xFFF0FFF0);
    COLOR_MAP.put("hotpink", 0xFFFF69B4);
    COLOR_MAP.put("indianred", 0xFFCD5C5C);
    COLOR_MAP.put("indigo", 0xFF4B0082);
    COLOR_MAP.put("ivory", 0xFFFFFFF0);
    COLOR_MAP.put("khaki", 0xFFF0E68C);
    COLOR_MAP.put("lavender", 0xFFE6E6FA);
    COLOR_MAP.put("lavenderblush", 0xFFFFF0F5);
    COLOR_MAP.put("lawngreen", 0xFF7CFC00);
    COLOR_MAP.put("lemonchiffon", 0xFFFFFACD);
    COLOR_MAP.put("lightblue", 0xFFADD8E6);
    COLOR_MAP.put("lightcoral", 0xFFF08080);
    COLOR_MAP.put("lightcyan", 0xFFE0FFFF);
    COLOR_MAP.put("lightgoldenrodyellow", 0xFFFAFAD2);
    COLOR_MAP.put("lightgray", 0xFFD3D3D3);
    COLOR_MAP.put("lightgreen", 0xFF90EE90);
    COLOR_MAP.put("lightgrey", 0xFFD3D3D3);
    COLOR_MAP.put("lightpink", 0xFFFFB6C1);
    COLOR_MAP.put("lightsalmon", 0xFFFFA07A);
    COLOR_MAP.put("lightseagreen", 0xFF20B2AA);
    COLOR_MAP.put("lightskyblue", 0xFF87CEFA);
    COLOR_MAP.put("lightslategray", 0xFF778899);
    COLOR_MAP.put("lightslategrey", 0xFF778899);
    COLOR_MAP.put("lightsteelblue", 0xFFB0C4DE);
    COLOR_MAP.put("lightyellow", 0xFFFFFFE0);
    COLOR_MAP.put("lime", 0xFF00FF00);
    COLOR_MAP.put("limegreen", 0xFF32CD32);
    COLOR_MAP.put("linen", 0xFFFAF0E6);
    COLOR_MAP.put("magenta", 0xFFFF00FF);
    COLOR_MAP.put("maroon", 0xFF800000);
    COLOR_MAP.put("mediumaquamarine", 0xFF66CDAA);
    COLOR_MAP.put("mediumblue", 0xFF0000CD);
    COLOR_MAP.put("mediumorchid", 0xFFBA55D3);
    COLOR_MAP.put("mediumpurple", 0xFF9370DB);
    COLOR_MAP.put("mediumseagreen", 0xFF3CB371);
    COLOR_MAP.put("mediumslateblue", 0xFF7B68EE);
    COLOR_MAP.put("mediumspringgreen", 0xFF00FA9A);
    COLOR_MAP.put("mediumturquoise", 0xFF48D1CC);
    COLOR_MAP.put("mediumvioletred", 0xFFC71585);
    COLOR_MAP.put("midnightblue", 0xFF191970);
    COLOR_MAP.put("mintcream", 0xFFF5FFFA);
    COLOR_MAP.put("mistyrose", 0xFFFFE4E1);
    COLOR_MAP.put("moccasin", 0xFFFFE4B5);
    COLOR_MAP.put("navajowhite", 0xFFFFDEAD);
    COLOR_MAP.put("navy", 0xFF000080);
    COLOR_MAP.put("oldlace", 0xFFFDF5E6);
    COLOR_MAP.put("olive", 0xFF808000);
    COLOR_MAP.put("olivedrab", 0xFF6B8E23);
    COLOR_MAP.put("orange", 0xFFFFA500);
    COLOR_MAP.put("orangered", 0xFFFF4500);
    COLOR_MAP.put("orchid", 0xFFDA70D6);
    COLOR_MAP.put("palegoldenrod", 0xFFEEE8AA);
    COLOR_MAP.put("palegreen", 0xFF98FB98);
    COLOR_MAP.put("paleturquoise", 0xFFAFEEEE);
    COLOR_MAP.put("palevioletred", 0xFFDB7093);
    COLOR_MAP.put("papayawhip", 0xFFFFEFD5);
    COLOR_MAP.put("peachpuff", 0xFFFFDAB9);
    COLOR_MAP.put("peru", 0xFFCD853F);
    COLOR_MAP.put("pink", 0xFFFFC0CB);
    COLOR_MAP.put("plum", 0xFFDDA0DD);
    COLOR_MAP.put("powderblue", 0xFFB0E0E6);
    COLOR_MAP.put("purple", 0xFF800080);
    COLOR_MAP.put("rebeccapurple", 0xFF663399);
    COLOR_MAP.put("red", 0xFFFF0000);
    COLOR_MAP.put("rosybrown", 0xFFBC8F8F);
    COLOR_MAP.put("royalblue", 0xFF4169E1);
    COLOR_MAP.put("saddlebrown", 0xFF8B4513);
    COLOR_MAP.put("salmon", 0xFFFA8072);
    COLOR_MAP.put("sandybrown", 0xFFF4A460);
    COLOR_MAP.put("seagreen", 0xFF2E8B57);
    COLOR_MAP.put("seashell", 0xFFFFF5EE);
    COLOR_MAP.put("sienna", 0xFFA0522D);
    COLOR_MAP.put("silver", 0xFFC0C0C0);
    COLOR_MAP.put("skyblue", 0xFF87CEEB);
    COLOR_MAP.put("slateblue", 0xFF6A5ACD);
    COLOR_MAP.put("slategray", 0xFF708090);
    COLOR_MAP.put("slategrey", 0xFF708090);
    COLOR_MAP.put("snow", 0xFFFFFAFA);
    COLOR_MAP.put("springgreen", 0xFF00FF7F);
    COLOR_MAP.put("steelblue", 0xFF4682B4);
    COLOR_MAP.put("tan", 0xFFD2B48C);
    COLOR_MAP.put("teal", 0xFF008080);
    COLOR_MAP.put("thistle", 0xFFD8BFD8);
    COLOR_MAP.put("tomato", 0xFFFF6347);
    COLOR_MAP.put("transparent", 0x00000000);
    COLOR_MAP.put("turquoise", 0xFF40E0D0);
    COLOR_MAP.put("violet", 0xFFEE82EE);
    COLOR_MAP.put("wheat", 0xFFF5DEB3);
    COLOR_MAP.put("white", 0xFFFFFFFF);
    COLOR_MAP.put("whitesmoke", 0xFFF5F5F5);
    COLOR_MAP.put("yellow", 0xFFFFFF00);
    COLOR_MAP.put("yellowgreen", 0xFF9ACD32);
  }

  private ColorParser() {
    // Prevent instantiation.
  }
}
