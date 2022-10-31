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

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing WebVTT data.
 */
public final class WebvttParserUtil {

  private static final Pattern COMMENT = Pattern.compile("^NOTE((\u0020|\u0009).*)?$");
  private static final String WEBVTT_HEADER = "WEBVTT";

  private WebvttParserUtil() {}

  /**
   * Reads and validates the first line of a WebVTT file.
   *
   * @param input The input from which the line should be read.
   * @throws ParserException If the line isn't the start of a valid WebVTT file.
   */
  public static void validateWebvttHeaderLine(ParsableByteArray input) throws ParserException {
    int startPosition = input.getPosition();
    if (!isWebvttHeaderLine(input)) {
      input.setPosition(startPosition);
      throw new ParserException("Expected WEBVTT. Got " + input.readLine());
    }
  }

  /**
   * Returns whether the given input is the first line of a WebVTT file.
   *
   * @param input The input from which the line should be read.
   */
  public static boolean isWebvttHeaderLine(ParsableByteArray input) {
    String line = input.readLine();
    return line != null && line.startsWith(WEBVTT_HEADER);
  }

  /**
   * Parses a WebVTT timestamp.
   *
   * @param timestamp The timestamp string.
   * @return The parsed timestamp in microseconds.
   * @throws NumberFormatException If the timestamp could not be parsed.
   */
  public static long parseTimestampUs(String timestamp) throws NumberFormatException {
    long value = 0;
    String[] parts = Util.splitAtFirst(timestamp, "\\.");
    String[] subparts = Util.split(parts[0], ":");
    for (String subpart : subparts) {
      value = (value * 60) + Long.parseLong(subpart);
    }
    value *= 1000;
    if (parts.length == 2) {
      value += Long.parseLong(parts[1]);
    }
    return value * 1000;
  }

  /**
   * Parses a percentage string.
   *
   * @param s The percentage string.
   * @return The parsed value, where 1.0 represents 100%.
   * @throws NumberFormatException If the percentage could not be parsed.
   */
  public static float parsePercentage(String s) throws NumberFormatException {
    if (!s.endsWith("%")) {
      throw new NumberFormatException("Percentages must end with %");
    }
    return Float.parseFloat(s.substring(0, s.length() - 1)) / 100;
  }

  /**
   * Reads lines up to and including the next WebVTT cue header.
   *
   * @param input The input from which lines should be read.
   * @return A {@link Matcher} for the WebVTT cue header, or null if the end of the input was
   *     reached without a cue header being found. In the case that a cue header is found, groups 1,
   *     2 and 3 of the returned matcher contain the start time, end time and settings list.
   */
  public static Matcher findNextCueHeader(ParsableByteArray input) {
    String line;
    while ((line = input.readLine()) != null) {
      if (COMMENT.matcher(line).matches()) {
        // Skip until the end of the comment block.
        while ((line = input.readLine()) != null && !line.isEmpty()) {}
      } else {
        Matcher cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(line);
        if (cueHeaderMatcher.matches()) {
          return cueHeaderMatcher;
        }
      }
    }
    return null;
  }

}
