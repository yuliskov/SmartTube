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
package com.google.android.exoplayer2.source.dash.manifest;

import java.util.Locale;

/**
 * A template from which URLs can be built.
 * <p>
 * URLs are built according to the substitution rules defined in ISO/IEC 23009-1:2014 5.3.9.4.4.
 */
public final class UrlTemplate {

  private static final String REPRESENTATION = "RepresentationID";
  private static final String NUMBER = "Number";
  private static final String BANDWIDTH = "Bandwidth";
  private static final String TIME = "Time";
  private static final String ESCAPED_DOLLAR = "$$";
  private static final String DEFAULT_FORMAT_TAG = "%01d";

  private static final int REPRESENTATION_ID = 1;
  private static final int NUMBER_ID = 2;
  private static final int BANDWIDTH_ID = 3;
  private static final int TIME_ID = 4;

  private final String[] urlPieces;
  private final int[] identifiers;
  private final String[] identifierFormatTags;
  private final int identifierCount;

  /**
   * Compile an instance from the provided template string.
   *
   * @param template The template.
   * @return The compiled instance.
   * @throws IllegalArgumentException If the template string is malformed.
   */
  public static UrlTemplate compile(String template) {
    // These arrays are sizes assuming each of the four possible identifiers will be present at
    // most once in the template, which seems like a reasonable assumption.
    String[] urlPieces = new String[5];
    int[] identifiers = new int[4];
    String[] identifierFormatTags = new String[4];
    int identifierCount = parseTemplate(template, urlPieces, identifiers, identifierFormatTags);
    return new UrlTemplate(urlPieces, identifiers, identifierFormatTags, identifierCount);
  }

  /**
   * Internal constructor. Use {@link #compile(String)} to build instances of this class.
   */
  private UrlTemplate(String[] urlPieces, int[] identifiers, String[] identifierFormatTags,
      int identifierCount) {
    this.urlPieces = urlPieces;
    this.identifiers = identifiers;
    this.identifierFormatTags = identifierFormatTags;
    this.identifierCount = identifierCount;
  }

  /**
   * Constructs a Uri from the template, substituting in the provided arguments.
   *
   * <p>Arguments whose corresponding identifiers are not present in the template will be ignored.
   *
   * @param representationId The representation identifier.
   * @param segmentNumber The segment number.
   * @param bandwidth The bandwidth.
   * @param time The time as specified by the segment timeline.
   * @return The built Uri.
   */
  public String buildUri(String representationId, long segmentNumber, int bandwidth, long time) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < identifierCount; i++) {
      builder.append(urlPieces[i]);
      if (identifiers[i] == REPRESENTATION_ID) {
        builder.append(representationId);
      } else if (identifiers[i] == NUMBER_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags[i], segmentNumber));
      } else if (identifiers[i] == BANDWIDTH_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags[i], bandwidth));
      } else if (identifiers[i] == TIME_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags[i], time));
      }
    }
    builder.append(urlPieces[identifierCount]);
    return builder.toString();
  }

  /**
   * Parses {@code template}, placing the decomposed components into the provided arrays.
   * <p>
   * If the return value is N, {@code urlPieces} will contain (N+1) strings that must be
   * interleaved with N arguments in order to construct a url. The N identifiers that correspond to
   * the required arguments, together with the tags that define their required formatting, are
   * returned in {@code identifiers} and {@code identifierFormatTags} respectively.
   *
   * @param template The template to parse.
   * @param urlPieces A holder for pieces of url parsed from the template.
   * @param identifiers A holder for identifiers parsed from the template.
   * @param identifierFormatTags A holder for format tags corresponding to the parsed identifiers.
   * @return The number of identifiers in the template url.
   * @throws IllegalArgumentException If the template string is malformed.
   */
  private static int parseTemplate(String template, String[] urlPieces, int[] identifiers,
      String[] identifierFormatTags) {
    urlPieces[0] = "";
    int templateIndex = 0;
    int identifierCount = 0;
    while (templateIndex < template.length()) {
      int dollarIndex = template.indexOf("$", templateIndex);
      if (dollarIndex == -1) {
        urlPieces[identifierCount] += template.substring(templateIndex);
        templateIndex = template.length();
      } else if (dollarIndex != templateIndex) {
        urlPieces[identifierCount] += template.substring(templateIndex, dollarIndex);
        templateIndex = dollarIndex;
      } else if (template.startsWith(ESCAPED_DOLLAR, templateIndex)) {
        urlPieces[identifierCount] += "$";
        templateIndex += 2;
      } else {
        int secondIndex = template.indexOf("$", templateIndex + 1);
        String identifier = template.substring(templateIndex + 1, secondIndex);
        if (identifier.equals(REPRESENTATION)) {
          identifiers[identifierCount] = REPRESENTATION_ID;
        } else {
          int formatTagIndex = identifier.indexOf("%0");
          String formatTag = DEFAULT_FORMAT_TAG;
          if (formatTagIndex != -1) {
            formatTag = identifier.substring(formatTagIndex);
            // Allowed conversions are decimal integer (which is the only conversion allowed by the
            // DASH specification) and hexadecimal integer (due to existing content that uses it).
            // Else we assume that the conversion is missing, and that it should be decimal integer.
            if (!formatTag.endsWith("d") && !formatTag.endsWith("x")) {
              formatTag += "d";
            }
            identifier = identifier.substring(0, formatTagIndex);
          }
          switch (identifier) {
            case NUMBER:
              identifiers[identifierCount] = NUMBER_ID;
              break;
            case BANDWIDTH:
              identifiers[identifierCount] = BANDWIDTH_ID;
              break;
            case TIME:
              identifiers[identifierCount] = TIME_ID;
              break;
            default:
              throw new IllegalArgumentException("Invalid template: " + template);
          }
          identifierFormatTags[identifierCount] = formatTag;
        }
        identifierCount++;
        urlPieces[identifierCount] = "";
        templateIndex = secondIndex + 1;
      }
    }
    return identifierCount;
  }

}
