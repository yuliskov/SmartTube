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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A package internal representation of TTML node.
 */
/* package */ final class TtmlNode {

  public static final String TAG_TT = "tt";
  public static final String TAG_HEAD = "head";
  public static final String TAG_BODY = "body";
  public static final String TAG_DIV = "div";
  public static final String TAG_P = "p";
  public static final String TAG_SPAN = "span";
  public static final String TAG_BR = "br";
  public static final String TAG_STYLE = "style";
  public static final String TAG_STYLING = "styling";
  public static final String TAG_LAYOUT = "layout";
  public static final String TAG_REGION = "region";
  public static final String TAG_METADATA = "metadata";
  public static final String TAG_IMAGE = "image";
  public static final String TAG_DATA = "data";
  public static final String TAG_INFORMATION = "information";

  public static final String ANONYMOUS_REGION_ID = "";
  public static final String ATTR_ID = "id";
  public static final String ATTR_TTS_ORIGIN = "origin";
  public static final String ATTR_TTS_EXTENT = "extent";
  public static final String ATTR_TTS_DISPLAY_ALIGN = "displayAlign";
  public static final String ATTR_TTS_BACKGROUND_COLOR = "backgroundColor";
  public static final String ATTR_TTS_FONT_STYLE = "fontStyle";
  public static final String ATTR_TTS_FONT_SIZE = "fontSize";
  public static final String ATTR_TTS_FONT_FAMILY = "fontFamily";
  public static final String ATTR_TTS_FONT_WEIGHT = "fontWeight";
  public static final String ATTR_TTS_COLOR = "color";
  public static final String ATTR_TTS_TEXT_DECORATION = "textDecoration";
  public static final String ATTR_TTS_TEXT_ALIGN = "textAlign";

  public static final String LINETHROUGH = "linethrough";
  public static final String NO_LINETHROUGH = "nolinethrough";
  public static final String UNDERLINE = "underline";
  public static final String NO_UNDERLINE = "nounderline";
  public static final String ITALIC = "italic";
  public static final String BOLD = "bold";

  public static final String LEFT = "left";
  public static final String CENTER = "center";
  public static final String RIGHT = "right";
  public static final String START = "start";
  public static final String END = "end";

  @Nullable public final String tag;
  @Nullable public final String text;
  public final boolean isTextNode;
  public final long startTimeUs;
  public final long endTimeUs;
  @Nullable public final TtmlStyle style;
  @Nullable private final String[] styleIds;
  public final String regionId;
  @Nullable public final String imageId;

  private final HashMap<String, Integer> nodeStartsByRegion;
  private final HashMap<String, Integer> nodeEndsByRegion;

  private List<TtmlNode> children;

  public static TtmlNode buildTextNode(String text) {
    return new TtmlNode(
        /* tag= */ null,
        TtmlRenderUtil.applyTextElementSpacePolicy(text),
        /* startTimeUs= */ C.TIME_UNSET,
        /* endTimeUs= */ C.TIME_UNSET,
        /* style= */ null,
        /* styleIds= */ null,
        ANONYMOUS_REGION_ID,
        /* imageId= */ null);
  }

  public static TtmlNode buildNode(
      @Nullable String tag,
      long startTimeUs,
      long endTimeUs,
      @Nullable TtmlStyle style,
      @Nullable String[] styleIds,
      String regionId,
      @Nullable String imageId) {
    return new TtmlNode(
        tag, /* text= */ null, startTimeUs, endTimeUs, style, styleIds, regionId, imageId);
  }

  private TtmlNode(
      @Nullable String tag,
      @Nullable String text,
      long startTimeUs,
      long endTimeUs,
      @Nullable TtmlStyle style,
      @Nullable String[] styleIds,
      String regionId,
      @Nullable String imageId) {
    this.tag = tag;
    this.text = text;
    this.imageId = imageId;
    this.style = style;
    this.styleIds = styleIds;
    this.isTextNode = text != null;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.regionId = Assertions.checkNotNull(regionId);
    nodeStartsByRegion = new HashMap<>();
    nodeEndsByRegion = new HashMap<>();
  }

  public boolean isActive(long timeUs) {
    return (startTimeUs == C.TIME_UNSET && endTimeUs == C.TIME_UNSET)
        || (startTimeUs <= timeUs && endTimeUs == C.TIME_UNSET)
        || (startTimeUs == C.TIME_UNSET && timeUs < endTimeUs)
        || (startTimeUs <= timeUs && timeUs < endTimeUs);
  }

  public void addChild(TtmlNode child) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(child);
  }

  public TtmlNode getChild(int index) {
    if (children == null) {
      throw new IndexOutOfBoundsException();
    }
    return children.get(index);
  }

  public int getChildCount() {
    return children == null ? 0 : children.size();
  }

  public long[] getEventTimesUs() {
    TreeSet<Long> eventTimeSet = new TreeSet<>();
    getEventTimes(eventTimeSet, false);
    long[] eventTimes = new long[eventTimeSet.size()];
    int i = 0;
    for (long eventTimeUs : eventTimeSet) {
      eventTimes[i++] = eventTimeUs;
    }
    return eventTimes;
  }

  private void getEventTimes(TreeSet<Long> out, boolean descendsPNode) {
    boolean isPNode = TAG_P.equals(tag);
    boolean isDivNode = TAG_DIV.equals(tag);
    if (descendsPNode || isPNode || (isDivNode && imageId != null)) {
      if (startTimeUs != C.TIME_UNSET) {
        out.add(startTimeUs);
      }
      if (endTimeUs != C.TIME_UNSET) {
        out.add(endTimeUs);
      }
    }
    if (children == null) {
      return;
    }
    for (int i = 0; i < children.size(); i++) {
      children.get(i).getEventTimes(out, descendsPNode || isPNode);
    }
  }

  public String[] getStyleIds() {
    return styleIds;
  }

  public List<Cue> getCues(
      long timeUs,
      Map<String, TtmlStyle> globalStyles,
      Map<String, TtmlRegion> regionMap,
      Map<String, String> imageMap) {

    List<Pair<String, String>> regionImageOutputs = new ArrayList<>();
    traverseForImage(timeUs, regionId, regionImageOutputs);

    TreeMap<String, SpannableStringBuilder> regionTextOutputs = new TreeMap<>();
    traverseForText(timeUs, false, regionId, regionTextOutputs);
    traverseForStyle(timeUs, globalStyles, regionTextOutputs);

    List<Cue> cues = new ArrayList<>();

    // Create image based cues.
    for (Pair<String, String> regionImagePair : regionImageOutputs) {
      String encodedBitmapData = imageMap.get(regionImagePair.second);
      if (encodedBitmapData == null) {
        // Image reference points to an invalid image. Do nothing.
        continue;
      }

      byte[] bitmapData = Base64.decode(encodedBitmapData, Base64.DEFAULT);
      Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, /* offset= */ 0, bitmapData.length);
      TtmlRegion region = regionMap.get(regionImagePair.first);

      cues.add(
          new Cue(
              bitmap,
              region.position,
              Cue.ANCHOR_TYPE_START,
              region.line,
              region.lineAnchor,
              region.width,
              region.height));
    }

    // Create text based cues.
    for (Entry<String, SpannableStringBuilder> entry : regionTextOutputs.entrySet()) {
      TtmlRegion region = regionMap.get(entry.getKey());
      cues.add(
          new Cue(
              cleanUpText(entry.getValue()),
              /* textAlignment= */ null,
              region.line,
              region.lineType,
              region.lineAnchor,
              region.position,
              /* positionAnchor= */ Cue.TYPE_UNSET,
              region.width,
              region.textSizeType,
              region.textSize));
    }

    return cues;
  }

  private void traverseForImage(
      long timeUs, String inheritedRegion, List<Pair<String, String>> regionImageList) {
    String resolvedRegionId = ANONYMOUS_REGION_ID.equals(regionId) ? inheritedRegion : regionId;
    if (isActive(timeUs) && TAG_DIV.equals(tag) && imageId != null) {
      regionImageList.add(new Pair<>(resolvedRegionId, imageId));
      return;
    }
    for (int i = 0; i < getChildCount(); ++i) {
      getChild(i).traverseForImage(timeUs, resolvedRegionId, regionImageList);
    }
  }

  private void traverseForText(
      long timeUs,
      boolean descendsPNode,
      String inheritedRegion,
      Map<String, SpannableStringBuilder> regionOutputs) {
    nodeStartsByRegion.clear();
    nodeEndsByRegion.clear();
    if (TAG_METADATA.equals(tag)) {
      // Ignore metadata tag.
      return;
    }

    String resolvedRegionId = ANONYMOUS_REGION_ID.equals(regionId) ? inheritedRegion : regionId;

    if (isTextNode && descendsPNode) {
      getRegionOutput(resolvedRegionId, regionOutputs).append(text);
    } else if (TAG_BR.equals(tag) && descendsPNode) {
      getRegionOutput(resolvedRegionId, regionOutputs).append('\n');
    } else if (isActive(timeUs)) {
      // This is a container node, which can contain zero or more children.
      for (Entry<String, SpannableStringBuilder> entry : regionOutputs.entrySet()) {
        nodeStartsByRegion.put(entry.getKey(), entry.getValue().length());
      }

      boolean isPNode = TAG_P.equals(tag);
      for (int i = 0; i < getChildCount(); i++) {
        getChild(i).traverseForText(timeUs, descendsPNode || isPNode, resolvedRegionId,
            regionOutputs);
      }
      if (isPNode) {
        TtmlRenderUtil.endParagraph(getRegionOutput(resolvedRegionId, regionOutputs));
      }

      for (Entry<String, SpannableStringBuilder> entry : regionOutputs.entrySet()) {
        nodeEndsByRegion.put(entry.getKey(), entry.getValue().length());
      }
    }
  }

  private static SpannableStringBuilder getRegionOutput(
      String resolvedRegionId, Map<String, SpannableStringBuilder> regionOutputs) {
    if (!regionOutputs.containsKey(resolvedRegionId)) {
      regionOutputs.put(resolvedRegionId, new SpannableStringBuilder());
    }
    return regionOutputs.get(resolvedRegionId);
  }

  private void traverseForStyle(
      long timeUs,
      Map<String, TtmlStyle> globalStyles,
      Map<String, SpannableStringBuilder> regionOutputs) {
    if (!isActive(timeUs)) {
      return;
    }
    for (Entry<String, Integer> entry : nodeEndsByRegion.entrySet()) {
      String regionId = entry.getKey();
      int start = nodeStartsByRegion.containsKey(regionId) ? nodeStartsByRegion.get(regionId) : 0;
      int end = entry.getValue();
      if (start != end) {
        SpannableStringBuilder regionOutput = regionOutputs.get(regionId);
        applyStyleToOutput(globalStyles, regionOutput, start, end);
      }
    }
    for (int i = 0; i < getChildCount(); ++i) {
      getChild(i).traverseForStyle(timeUs, globalStyles, regionOutputs);
    }
  }

  private void applyStyleToOutput(
      Map<String, TtmlStyle> globalStyles,
      SpannableStringBuilder regionOutput,
      int start,
      int end) {
    TtmlStyle resolvedStyle = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    if (resolvedStyle != null) {
      TtmlRenderUtil.applyStylesToSpan(regionOutput, start, end, resolvedStyle);
    }
  }

  private SpannableStringBuilder cleanUpText(SpannableStringBuilder builder) {
    // Having joined the text elements, we need to do some final cleanup on the result.
    // 1. Collapse multiple consecutive spaces into a single space.
    int builderLength = builder.length();
    for (int i = 0; i < builderLength; i++) {
      if (builder.charAt(i) == ' ') {
        int j = i + 1;
        while (j < builder.length() && builder.charAt(j) == ' ') {
          j++;
        }
        int spacesToDelete = j - (i + 1);
        if (spacesToDelete > 0) {
          builder.delete(i, i + spacesToDelete);
          builderLength -= spacesToDelete;
        }
      }
    }
    // 2. Remove any spaces from the start of each line.
    if (builderLength > 0 && builder.charAt(0) == ' ') {
      builder.delete(0, 1);
      builderLength--;
    }
    for (int i = 0; i < builderLength - 1; i++) {
      if (builder.charAt(i) == '\n' && builder.charAt(i + 1) == ' ') {
        builder.delete(i + 1, i + 2);
        builderLength--;
      }
    }
    // 3. Remove any spaces from the end of each line.
    if (builderLength > 0 && builder.charAt(builderLength - 1) == ' ') {
      builder.delete(builderLength - 1, builderLength);
      builderLength--;
    }
    for (int i = 0; i < builderLength - 1; i++) {
      if (builder.charAt(i) == ' ' && builder.charAt(i + 1) == '\n') {
        builder.delete(i, i + 1);
        builderLength--;
      }
    }
    // 4. Trim a trailing newline, if there is one.
    if (builderLength > 0 && builder.charAt(builderLength - 1) == '\n') {
      builder.delete(builderLength - 1, builderLength);
      /*builderLength--;*/
    }
    return builder;
  }

}
