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
package com.google.android.exoplayer2.text.subrip;

import androidx.annotation.Nullable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link SimpleSubtitleDecoder} for SubRip.
 */
public final class SubripDecoder extends SimpleSubtitleDecoder {

  // Fractional positions for use when alignment tags are present.
  /* package */ static final float START_FRACTION = 0.08f;
  /* package */ static final float END_FRACTION = 1 - START_FRACTION;
  /* package */ static final float MID_FRACTION = 0.5f;

  private static final String TAG = "SubripDecoder";

  private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+),(\\d+)";
  private static final Pattern SUBRIP_TIMING_LINE =
      Pattern.compile("\\s*(" + SUBRIP_TIMECODE + ")\\s*-->\\s*(" + SUBRIP_TIMECODE + ")?\\s*");

  private static final Pattern SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}");
  private static final String SUBRIP_ALIGNMENT_TAG = "\\{\\\\an[1-9]\\}";

  // Alignment tags for SSA V4+.
  private static final String ALIGN_BOTTOM_LEFT = "{\\an1}";
  private static final String ALIGN_BOTTOM_MID = "{\\an2}";
  private static final String ALIGN_BOTTOM_RIGHT = "{\\an3}";
  private static final String ALIGN_MID_LEFT = "{\\an4}";
  private static final String ALIGN_MID_MID = "{\\an5}";
  private static final String ALIGN_MID_RIGHT = "{\\an6}";
  private static final String ALIGN_TOP_LEFT = "{\\an7}";
  private static final String ALIGN_TOP_MID = "{\\an8}";
  private static final String ALIGN_TOP_RIGHT = "{\\an9}";

  private final StringBuilder textBuilder;
  private final ArrayList<String> tags;

  public SubripDecoder() {
    super("SubripDecoder");
    textBuilder = new StringBuilder();
    tags = new ArrayList<>();
  }

  @Override
  protected SubripSubtitle decode(byte[] bytes, int length, boolean reset) {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    ParsableByteArray subripData = new ParsableByteArray(bytes, length);
    String currentLine;

    while ((currentLine = subripData.readLine()) != null) {
      if (currentLine.length() == 0) {
        // Skip blank lines.
        continue;
      }

      // Parse the index line as a sanity check.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping invalid index: " + currentLine);
        continue;
      }

      // Read and parse the timing line.
      boolean haveEndTimecode = false;
      currentLine = subripData.readLine();
      if (currentLine == null) {
        Log.w(TAG, "Unexpected end");
        break;
      }

      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.matches()) {
        cueTimesUs.add(parseTimecode(matcher, 1));
        if (!TextUtils.isEmpty(matcher.group(6))) {
          haveEndTimecode = true;
          cueTimesUs.add(parseTimecode(matcher, 6));
        }
      } else {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      }

      // Read and parse the text and tags.
      textBuilder.setLength(0);
      tags.clear();
      currentLine = subripData.readLine();
      while (!TextUtils.isEmpty(currentLine)) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(processLine(currentLine, tags));
        currentLine = subripData.readLine();
      }

      Spanned text = Html.fromHtml(textBuilder.toString());

      String alignmentTag = null;
      for (int i = 0; i < tags.size(); i++) {
        String tag = tags.get(i);
        if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {
          alignmentTag = tag;
          // Subsequent alignment tags should be ignored.
          break;
        }
      }
      cues.add(buildCue(text, alignmentTag));

      if (haveEndTimecode) {
        cues.add(Cue.EMPTY);
      }
    }

    Cue[] cuesArray = new Cue[cues.size()];
    cues.toArray(cuesArray);
    long[] cueTimesUsArray = cueTimesUs.toArray();
    return new SubripSubtitle(cuesArray, cueTimesUsArray);
  }

  /**
   * Trims and removes tags from the given line. The removed tags are added to {@code tags}.
   *
   * @param line The line to process.
   * @param tags A list to which removed tags will be added.
   * @return The processed line.
   */
  private String processLine(String line, ArrayList<String> tags) {
    line = line.trim();

    int removedCharacterCount = 0;
    StringBuilder processedLine = new StringBuilder(line);
    Matcher matcher = SUBRIP_TAG_PATTERN.matcher(line);
    while (matcher.find()) {
      String tag = matcher.group();
      tags.add(tag);
      int start = matcher.start() - removedCharacterCount;
      int tagLength = tag.length();
      processedLine.replace(start, /* end= */ start + tagLength, /* str= */ "");
      removedCharacterCount += tagLength;
    }

    return processedLine.toString();
  }

  /**
   * Build a {@link Cue} based on the given text and alignment tag.
   *
   * @param text The text.
   * @param alignmentTag The alignment tag, or {@code null} if no alignment tag is available.
   * @return Built cue
   */
  private Cue buildCue(Spanned text, @Nullable String alignmentTag) {
    if (alignmentTag == null) {
      return new Cue(text);
    }

    // Horizontal alignment.
    @Cue.AnchorType int positionAnchor;
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_MID_LEFT:
      case ALIGN_TOP_LEFT:
        positionAnchor = Cue.ANCHOR_TYPE_START;
        break;
      case ALIGN_BOTTOM_RIGHT:
      case ALIGN_MID_RIGHT:
      case ALIGN_TOP_RIGHT:
        positionAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_BOTTOM_MID:
      case ALIGN_MID_MID:
      case ALIGN_TOP_MID:
      default:
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
    }

    // Vertical alignment.
    @Cue.AnchorType int lineAnchor;
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_BOTTOM_MID:
      case ALIGN_BOTTOM_RIGHT:
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_TOP_LEFT:
      case ALIGN_TOP_MID:
      case ALIGN_TOP_RIGHT:
        lineAnchor = Cue.ANCHOR_TYPE_START;
        break;
      case ALIGN_MID_LEFT:
      case ALIGN_MID_MID:
      case ALIGN_MID_RIGHT:
      default:
        lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
    }

    return new Cue(
        text,
        /* textAlignment= */ null,
        getFractionalPositionForAnchorType(lineAnchor),
        Cue.LINE_TYPE_FRACTION,
        lineAnchor,
        getFractionalPositionForAnchorType(positionAnchor),
        positionAnchor,
        Cue.DIMEN_UNSET);
  }

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    long timestampMs = Long.parseLong(matcher.group(groupOffset + 1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 4));
    return timestampMs * 1000;
  }

  /* package */ static float getFractionalPositionForAnchorType(@Cue.AnchorType int anchorType) {
    switch (anchorType) {
      case Cue.ANCHOR_TYPE_START:
        return SubripDecoder.START_FRACTION;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return SubripDecoder.MID_FRACTION;
      case Cue.ANCHOR_TYPE_END:
        return SubripDecoder.END_FRACTION;
      case Cue.TYPE_UNSET:
      default:
        // Should never happen.
        throw new IllegalArgumentException();
    }
  }
}
