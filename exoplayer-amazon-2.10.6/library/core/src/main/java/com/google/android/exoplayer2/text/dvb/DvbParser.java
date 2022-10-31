/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.dvb;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.SparseArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses {@link Cue}s from a DVB subtitle bitstream.
 */
/* package */ final class DvbParser {

  private static final String TAG = "DvbParser";

  // Segment types, as defined by ETSI EN 300 743 Table 2
  private static final int SEGMENT_TYPE_PAGE_COMPOSITION = 0x10;
  private static final int SEGMENT_TYPE_REGION_COMPOSITION = 0x11;
  private static final int SEGMENT_TYPE_CLUT_DEFINITION = 0x12;
  private static final int SEGMENT_TYPE_OBJECT_DATA = 0x13;
  private static final int SEGMENT_TYPE_DISPLAY_DEFINITION = 0x14;

  // Page states, as defined by ETSI EN 300 743 Table 3
  private static final int PAGE_STATE_NORMAL = 0; // Update. Only changed elements.
  // private static final int PAGE_STATE_ACQUISITION = 1; // Refresh. All elements.
  // private static final int PAGE_STATE_CHANGE = 2; // New. All elements.

  // Region depths, as defined by ETSI EN 300 743 Table 5
  // private static final int REGION_DEPTH_2_BIT = 1;
  private static final int REGION_DEPTH_4_BIT = 2;
  private static final int REGION_DEPTH_8_BIT = 3;

  // Object codings, as defined by ETSI EN 300 743 Table 8
  private static final int OBJECT_CODING_PIXELS = 0;
  private static final int OBJECT_CODING_STRING = 1;

  // Pixel-data types, as defined by ETSI EN 300 743 Table 9
  private static final int DATA_TYPE_2BP_CODE_STRING = 0x10;
  private static final int DATA_TYPE_4BP_CODE_STRING = 0x11;
  private static final int DATA_TYPE_8BP_CODE_STRING = 0x12;
  private static final int DATA_TYPE_24_TABLE_DATA = 0x20;
  private static final int DATA_TYPE_28_TABLE_DATA = 0x21;
  private static final int DATA_TYPE_48_TABLE_DATA = 0x22;
  private static final int DATA_TYPE_END_LINE = 0xF0;

  // Clut mapping tables, as defined by ETSI EN 300 743 10.4, 10.5, 10.6
  private static final byte[] defaultMap2To4 = {
      (byte) 0x00, (byte) 0x07, (byte) 0x08, (byte) 0x0F};
  private static final byte[] defaultMap2To8 = {
      (byte) 0x00, (byte) 0x77, (byte) 0x88, (byte) 0xFF};
  private static final byte[] defaultMap4To8 = {
      (byte) 0x00, (byte) 0x11, (byte) 0x22, (byte) 0x33,
      (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77,
      (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
      (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};

  private final Paint defaultPaint;
  private final Paint fillRegionPaint;
  private final Canvas canvas;
  private final DisplayDefinition defaultDisplayDefinition;
  private final ClutDefinition defaultClutDefinition;
  private final SubtitleService subtitleService;

  private Bitmap bitmap;

  /**
   * Construct an instance for the given subtitle and ancillary page ids.
   *
   * @param subtitlePageId The id of the subtitle page carrying the subtitle to be parsed.
   * @param ancillaryPageId The id of the ancillary page containing additional data.
   */
  public DvbParser(int subtitlePageId, int ancillaryPageId) {
    defaultPaint = new Paint();
    defaultPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    defaultPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    defaultPaint.setPathEffect(null);
    fillRegionPaint = new Paint();
    fillRegionPaint.setStyle(Paint.Style.FILL);
    fillRegionPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
    fillRegionPaint.setPathEffect(null);
    canvas = new Canvas();
    defaultDisplayDefinition = new DisplayDefinition(719, 575, 0, 719, 0, 575);
    defaultClutDefinition = new ClutDefinition(0, generateDefault2BitClutEntries(),
        generateDefault4BitClutEntries(), generateDefault8BitClutEntries());
    subtitleService = new SubtitleService(subtitlePageId, ancillaryPageId);
  }

  /**
   * Resets the parser.
   */
  public void reset() {
    subtitleService.reset();
  }

  /**
   * Decodes a subtitling packet, returning a list of parsed {@link Cue}s.
   *
   * @param data The subtitling packet data to decode.
   * @param limit The limit in {@code data} at which to stop decoding.
   * @return The parsed {@link Cue}s.
   */
  public List<Cue> decode(byte[] data, int limit) {
    // Parse the input data.
    ParsableBitArray dataBitArray = new ParsableBitArray(data, limit);
    while (dataBitArray.bitsLeft() >= 48 // sync_byte (8) + segment header (40)
        && dataBitArray.readBits(8) == 0x0F) {
      parseSubtitlingSegment(dataBitArray, subtitleService);
    }

    if (subtitleService.pageComposition == null) {
      return Collections.emptyList();
    }

    // Update the canvas bitmap if necessary.
    DisplayDefinition displayDefinition = subtitleService.displayDefinition != null
        ? subtitleService.displayDefinition : defaultDisplayDefinition;
    if (bitmap == null || displayDefinition.width + 1 != bitmap.getWidth()
        || displayDefinition.height + 1 != bitmap.getHeight()) {
      bitmap = Bitmap.createBitmap(displayDefinition.width + 1, displayDefinition.height + 1,
          Bitmap.Config.ARGB_8888);
      canvas.setBitmap(bitmap);
    }

    // Build the cues.
    List<Cue> cues = new ArrayList<>();
    SparseArray<PageRegion> pageRegions = subtitleService.pageComposition.regions;
    for (int i = 0; i < pageRegions.size(); i++) {
      // Save clean clipping state.
      canvas.save();
      PageRegion pageRegion = pageRegions.valueAt(i);
      int regionId = pageRegions.keyAt(i);
      RegionComposition regionComposition = subtitleService.regions.get(regionId);

      // Clip drawing to the current region and display definition window.
      int baseHorizontalAddress = pageRegion.horizontalAddress
          + displayDefinition.horizontalPositionMinimum;
      int baseVerticalAddress = pageRegion.verticalAddress
          + displayDefinition.verticalPositionMinimum;
      int clipRight = Math.min(baseHorizontalAddress + regionComposition.width,
          displayDefinition.horizontalPositionMaximum);
      int clipBottom = Math.min(baseVerticalAddress + regionComposition.height,
          displayDefinition.verticalPositionMaximum);
      canvas.clipRect(baseHorizontalAddress, baseVerticalAddress, clipRight, clipBottom);
      ClutDefinition clutDefinition = subtitleService.cluts.get(regionComposition.clutId);
      if (clutDefinition == null) {
        clutDefinition = subtitleService.ancillaryCluts.get(regionComposition.clutId);
        if (clutDefinition == null) {
          clutDefinition = defaultClutDefinition;
        }
      }

      SparseArray<RegionObject> regionObjects = regionComposition.regionObjects;
      for (int j = 0; j < regionObjects.size(); j++) {
        int objectId = regionObjects.keyAt(j);
        RegionObject regionObject = regionObjects.valueAt(j);
        ObjectData objectData = subtitleService.objects.get(objectId);
        if (objectData == null) {
          objectData = subtitleService.ancillaryObjects.get(objectId);
        }
        if (objectData != null) {
          Paint paint = objectData.nonModifyingColorFlag ? null : defaultPaint;
          paintPixelDataSubBlocks(objectData, clutDefinition, regionComposition.depth,
              baseHorizontalAddress + regionObject.horizontalPosition,
              baseVerticalAddress + regionObject.verticalPosition, paint, canvas);
        }
      }

      if (regionComposition.fillFlag) {
        int color;
        if (regionComposition.depth == REGION_DEPTH_8_BIT) {
          color = clutDefinition.clutEntries8Bit[regionComposition.pixelCode8Bit];
        } else if (regionComposition.depth == REGION_DEPTH_4_BIT) {
          color = clutDefinition.clutEntries4Bit[regionComposition.pixelCode4Bit];
        } else {
          color = clutDefinition.clutEntries2Bit[regionComposition.pixelCode2Bit];
        }
        fillRegionPaint.setColor(color);
        canvas.drawRect(baseHorizontalAddress, baseVerticalAddress,
            baseHorizontalAddress + regionComposition.width,
            baseVerticalAddress + regionComposition.height,
            fillRegionPaint);
      }

      Bitmap cueBitmap = Bitmap.createBitmap(bitmap, baseHorizontalAddress, baseVerticalAddress,
          regionComposition.width, regionComposition.height);
      cues.add(new Cue(cueBitmap, (float) baseHorizontalAddress / displayDefinition.width,
          Cue.ANCHOR_TYPE_START, (float) baseVerticalAddress / displayDefinition.height,
          Cue.ANCHOR_TYPE_START, (float) regionComposition.width / displayDefinition.width,
          (float) regionComposition.height / displayDefinition.height));

      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      // Restore clean clipping state.
      canvas.restore();
    }

    return Collections.unmodifiableList(cues);
  }

  // Static parsing.

  /**
   * Parses a subtitling segment, as defined by ETSI EN 300 743 7.2
   * <p>
   * The {@link SubtitleService} is updated with the parsed segment data.
   */
  private static void parseSubtitlingSegment(ParsableBitArray data, SubtitleService service) {
    int segmentType = data.readBits(8);
    int pageId = data.readBits(16);
    int dataFieldLength = data.readBits(16);
    int dataFieldLimit = data.getBytePosition() + dataFieldLength;

    if ((dataFieldLength * 8) > data.bitsLeft()) {
      Log.w(TAG, "Data field length exceeds limit");
      // Skip to the very end.
      data.skipBits(data.bitsLeft());
      return;
    }

    switch (segmentType) {
      case SEGMENT_TYPE_DISPLAY_DEFINITION:
        if (pageId == service.subtitlePageId) {
          service.displayDefinition = parseDisplayDefinition(data);
        }
        break;
      case SEGMENT_TYPE_PAGE_COMPOSITION:
        if (pageId == service.subtitlePageId) {
          PageComposition current = service.pageComposition;
          PageComposition pageComposition = parsePageComposition(data, dataFieldLength);
          if (pageComposition.state != PAGE_STATE_NORMAL) {
            service.pageComposition = pageComposition;
            service.regions.clear();
            service.cluts.clear();
            service.objects.clear();
          } else if (current != null && current.version != pageComposition.version) {
            service.pageComposition = pageComposition;
          }
        }
        break;
      case SEGMENT_TYPE_REGION_COMPOSITION:
        PageComposition pageComposition = service.pageComposition;
        if (pageId == service.subtitlePageId && pageComposition != null) {
          RegionComposition regionComposition = parseRegionComposition(data, dataFieldLength);
          if (pageComposition.state == PAGE_STATE_NORMAL) {
            regionComposition.mergeFrom(service.regions.get(regionComposition.id));
          }
          service.regions.put(regionComposition.id, regionComposition);
        }
        break;
      case SEGMENT_TYPE_CLUT_DEFINITION:
        if (pageId == service.subtitlePageId) {
          ClutDefinition clutDefinition = parseClutDefinition(data, dataFieldLength);
          service.cluts.put(clutDefinition.id, clutDefinition);
        } else if (pageId == service.ancillaryPageId) {
          ClutDefinition clutDefinition = parseClutDefinition(data, dataFieldLength);
          service.ancillaryCluts.put(clutDefinition.id, clutDefinition);
        }
        break;
      case SEGMENT_TYPE_OBJECT_DATA:
        if (pageId == service.subtitlePageId) {
          ObjectData objectData = parseObjectData(data);
          service.objects.put(objectData.id, objectData);
        } else if (pageId == service.ancillaryPageId) {
          ObjectData objectData = parseObjectData(data);
          service.ancillaryObjects.put(objectData.id, objectData);
        }
        break;
      default:
        // Do nothing.
        break;
    }

    // Skip to the next segment.
    data.skipBytes(dataFieldLimit - data.getBytePosition());
  }

  /**
   * Parses a display definition segment, as defined by ETSI EN 300 743 7.2.1.
   */
  private static DisplayDefinition parseDisplayDefinition(ParsableBitArray data) {
    data.skipBits(4); // dds_version_number (4).
    boolean displayWindowFlag = data.readBit();
    data.skipBits(3); // Skip reserved.
    int width = data.readBits(16);
    int height = data.readBits(16);

    int horizontalPositionMinimum;
    int horizontalPositionMaximum;
    int verticalPositionMinimum;
    int verticalPositionMaximum;
    if (displayWindowFlag) {
      horizontalPositionMinimum = data.readBits(16);
      horizontalPositionMaximum = data.readBits(16);
      verticalPositionMinimum = data.readBits(16);
      verticalPositionMaximum = data.readBits(16);
    } else {
      horizontalPositionMinimum = 0;
      horizontalPositionMaximum = width;
      verticalPositionMinimum = 0;
      verticalPositionMaximum = height;
    }

    return new DisplayDefinition(width, height, horizontalPositionMinimum,
        horizontalPositionMaximum, verticalPositionMinimum, verticalPositionMaximum);
  }

  /**
   * Parses a page composition segment, as defined by ETSI EN 300 743 7.2.2.
   */
  private static PageComposition parsePageComposition(ParsableBitArray data, int length) {
    int timeoutSecs = data.readBits(8);
    int version = data.readBits(4);
    int state = data.readBits(2);
    data.skipBits(2);
    int remainingLength = length - 2;

    SparseArray<PageRegion> regions = new SparseArray<>();
    while (remainingLength > 0) {
      int regionId = data.readBits(8);
      data.skipBits(8); // Skip reserved.
      int regionHorizontalAddress = data.readBits(16);
      int regionVerticalAddress = data.readBits(16);
      remainingLength -= 6;
      regions.put(regionId, new PageRegion(regionHorizontalAddress, regionVerticalAddress));
    }

    return new PageComposition(timeoutSecs, version, state, regions);
  }

  /**
   * Parses a region composition segment, as defined by ETSI EN 300 743 7.2.3.
   */
  private static RegionComposition parseRegionComposition(ParsableBitArray data, int length) {
    int id = data.readBits(8);
    data.skipBits(4); // Skip region_version_number
    boolean fillFlag = data.readBit();
    data.skipBits(3); // Skip reserved.
    int width = data.readBits(16);
    int height = data.readBits(16);
    int levelOfCompatibility = data.readBits(3);
    int depth = data.readBits(3);
    data.skipBits(2); // Skip reserved.
    int clutId = data.readBits(8);
    int pixelCode8Bit = data.readBits(8);
    int pixelCode4Bit = data.readBits(4);
    int pixelCode2Bit = data.readBits(2);
    data.skipBits(2); // Skip reserved
    int remainingLength = length - 10;

    SparseArray<RegionObject> regionObjects = new SparseArray<>();
    while (remainingLength > 0) {
      int objectId = data.readBits(16);
      int objectType = data.readBits(2);
      int objectProvider = data.readBits(2);
      int objectHorizontalPosition = data.readBits(12);
      data.skipBits(4); // Skip reserved.
      int objectVerticalPosition = data.readBits(12);
      remainingLength -= 6;

      int foregroundPixelCode = 0;
      int backgroundPixelCode = 0;
      if (objectType == 0x01 || objectType == 0x02) { // Only seems to affect to char subtitles.
        foregroundPixelCode = data.readBits(8);
        backgroundPixelCode = data.readBits(8);
        remainingLength -= 2;
      }

      regionObjects.put(objectId, new RegionObject(objectType, objectProvider,
          objectHorizontalPosition, objectVerticalPosition, foregroundPixelCode,
          backgroundPixelCode));
    }

    return new RegionComposition(id, fillFlag, width, height, levelOfCompatibility, depth, clutId,
        pixelCode8Bit, pixelCode4Bit, pixelCode2Bit, regionObjects);
  }

  /**
   * Parses a CLUT definition segment, as defined by ETSI EN 300 743 7.2.4.
   */
  private static ClutDefinition parseClutDefinition(ParsableBitArray data, int length) {
    int clutId = data.readBits(8);
    data.skipBits(8); // Skip clut_version_number (4), reserved (4)
    int remainingLength = length - 2;

    int[] clutEntries2Bit = generateDefault2BitClutEntries();
    int[] clutEntries4Bit = generateDefault4BitClutEntries();
    int[] clutEntries8Bit = generateDefault8BitClutEntries();

    while (remainingLength > 0) {
      int entryId = data.readBits(8);
      int entryFlags = data.readBits(8);
      remainingLength -= 2;

      int[] clutEntries;
      if ((entryFlags & 0x80) != 0) {
        clutEntries = clutEntries2Bit;
      } else if ((entryFlags & 0x40) != 0) {
        clutEntries = clutEntries4Bit;
      } else {
        clutEntries = clutEntries8Bit;
      }

      int y;
      int cr;
      int cb;
      int t;
      if ((entryFlags & 0x01) != 0) {
        y = data.readBits(8);
        cr = data.readBits(8);
        cb = data.readBits(8);
        t = data.readBits(8);
        remainingLength -= 4;
      } else {
        y = data.readBits(6) << 2;
        cr = data.readBits(4) << 4;
        cb = data.readBits(4) << 4;
        t = data.readBits(2) << 6;
        remainingLength -= 2;
      }

      if (y == 0x00) {
        cr = 0x00;
        cb = 0x00;
        t = 0xFF;
      }

      int a = (byte) (0xFF - (t & 0xFF));
      int r = (int) (y + (1.40200 * (cr - 128)));
      int g = (int) (y - (0.34414 * (cb - 128)) - (0.71414 * (cr - 128)));
      int b = (int) (y + (1.77200 * (cb - 128)));
      clutEntries[entryId] = getColor(a, Util.constrainValue(r, 0, 255),
          Util.constrainValue(g, 0, 255), Util.constrainValue(b, 0, 255));
    }

    return new ClutDefinition(clutId, clutEntries2Bit, clutEntries4Bit, clutEntries8Bit);
  }

  /**
   * Parses an object data segment, as defined by ETSI EN 300 743 7.2.5.
   *
   * @return The parsed object data.
   */
  private static ObjectData parseObjectData(ParsableBitArray data) {
    int objectId = data.readBits(16);
    data.skipBits(4); // Skip object_version_number
    int objectCodingMethod = data.readBits(2);
    boolean nonModifyingColorFlag = data.readBit();
    data.skipBits(1); // Skip reserved.

    byte[] topFieldData = null;
    byte[] bottomFieldData = null;

    if (objectCodingMethod == OBJECT_CODING_STRING) {
      int numberOfCodes = data.readBits(8);
      // TODO: Parse and use character_codes.
      data.skipBits(numberOfCodes * 16); // Skip character_codes.
    } else if (objectCodingMethod == OBJECT_CODING_PIXELS) {
      int topFieldDataLength = data.readBits(16);
      int bottomFieldDataLength = data.readBits(16);
      if (topFieldDataLength > 0) {
        topFieldData = new byte[topFieldDataLength];
        data.readBytes(topFieldData, 0, topFieldDataLength);
      }
      if (bottomFieldDataLength > 0) {
        bottomFieldData = new byte[bottomFieldDataLength];
        data.readBytes(bottomFieldData, 0, bottomFieldDataLength);
      } else {
        bottomFieldData = topFieldData;
      }
    }

    return new ObjectData(objectId, nonModifyingColorFlag, topFieldData, bottomFieldData);
  }

  private static int[] generateDefault2BitClutEntries() {
    int[] entries = new int[4];
    entries[0] = 0x00000000;
    entries[1] = 0xFFFFFFFF;
    entries[2] = 0xFF000000;
    entries[3] = 0xFF7F7F7F;
    return entries;
  }

  private static int[] generateDefault4BitClutEntries() {
    int[] entries = new int[16];
    entries[0] = 0x00000000;
    for (int i = 1; i < entries.length; i++) {
      if (i < 8) {
        entries[i] = getColor(
            0xFF,
            ((i & 0x01) != 0 ? 0xFF : 0x00),
            ((i & 0x02) != 0 ? 0xFF : 0x00),
            ((i & 0x04) != 0 ? 0xFF : 0x00));
      } else {
        entries[i] = getColor(
            0xFF,
            ((i & 0x01) != 0 ? 0x7F : 0x00),
            ((i & 0x02) != 0 ? 0x7F : 0x00),
            ((i & 0x04) != 0 ? 0x7F : 0x00));
      }
    }
    return entries;
  }

  private static int[] generateDefault8BitClutEntries() {
    int[] entries = new int[256];
    entries[0] = 0x00000000;
    for (int i = 0; i < entries.length; i++) {
      if (i < 8) {
        entries[i] = getColor(
            0x3F,
            ((i & 0x01) != 0 ? 0xFF : 0x00),
            ((i & 0x02) != 0 ? 0xFF : 0x00),
            ((i & 0x04) != 0 ? 0xFF : 0x00));
      } else {
        switch (i & 0x88) {
          case 0x00:
            entries[i] = getColor(
                0xFF,
                (((i & 0x01) != 0 ? 0x55 : 0x00) + ((i & 0x10) != 0 ? 0xAA : 0x00)),
                (((i & 0x02) != 0 ? 0x55 : 0x00) + ((i & 0x20) != 0 ? 0xAA : 0x00)),
                (((i & 0x04) != 0 ? 0x55 : 0x00) + ((i & 0x40) != 0 ? 0xAA : 0x00)));
            break;
          case 0x08:
            entries[i] = getColor(
                0x7F,
                (((i & 0x01) != 0 ? 0x55 : 0x00) + ((i & 0x10) != 0 ? 0xAA : 0x00)),
                (((i & 0x02) != 0 ? 0x55 : 0x00) + ((i & 0x20) != 0 ? 0xAA : 0x00)),
                (((i & 0x04) != 0 ? 0x55 : 0x00) + ((i & 0x40) != 0 ? 0xAA : 0x00)));
            break;
          case 0x80:
            entries[i] = getColor(
                0xFF,
                (127 + ((i & 0x01) != 0 ? 0x2B : 0x00) + ((i & 0x10) != 0 ? 0x55 : 0x00)),
                (127 + ((i & 0x02) != 0 ? 0x2B : 0x00) + ((i & 0x20) != 0 ? 0x55 : 0x00)),
                (127 + ((i & 0x04) != 0 ? 0x2B : 0x00) + ((i & 0x40) != 0 ? 0x55 : 0x00)));
            break;
          case 0x88:
            entries[i] = getColor(
                0xFF,
                (((i & 0x01) != 0 ? 0x2B : 0x00) + ((i & 0x10) != 0 ? 0x55 : 0x00)),
                (((i & 0x02) != 0 ? 0x2B : 0x00) + ((i & 0x20) != 0 ? 0x55 : 0x00)),
                (((i & 0x04) != 0 ? 0x2B : 0x00) + ((i & 0x40) != 0 ? 0x55 : 0x00)));
            break;
        }
      }
    }
    return entries;
  }

  private static int getColor(int a, int r, int g, int b) {
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  // Static drawing.

  /**
   * Draws a pixel data sub-block, as defined by ETSI EN 300 743 7.2.5.1, into a canvas.
   */
  private static void paintPixelDataSubBlocks(ObjectData objectData, ClutDefinition clutDefinition,
      int regionDepth, int horizontalAddress, int verticalAddress, Paint paint, Canvas canvas) {
    int[] clutEntries;
    if (regionDepth == REGION_DEPTH_8_BIT) {
      clutEntries = clutDefinition.clutEntries8Bit;
    } else if (regionDepth == REGION_DEPTH_4_BIT) {
      clutEntries = clutDefinition.clutEntries4Bit;
    } else {
      clutEntries = clutDefinition.clutEntries2Bit;
    }
    paintPixelDataSubBlock(objectData.topFieldData, clutEntries, regionDepth, horizontalAddress,
        verticalAddress, paint, canvas);
    paintPixelDataSubBlock(objectData.bottomFieldData, clutEntries, regionDepth, horizontalAddress,
        verticalAddress + 1, paint, canvas);
  }

  /**
   * Draws a pixel data sub-block, as defined by ETSI EN 300 743 7.2.5.1, into a canvas.
   */
  private static void paintPixelDataSubBlock(byte[] pixelData, int[] clutEntries, int regionDepth,
      int horizontalAddress, int verticalAddress, Paint paint, Canvas canvas) {
    ParsableBitArray data = new ParsableBitArray(pixelData);
    int column = horizontalAddress;
    int line = verticalAddress;
    byte[] clutMapTable2To4 = null;
    byte[] clutMapTable2To8 = null;
    byte[] clutMapTable4To8 = null;

    while (data.bitsLeft() != 0) {
      int dataType = data.readBits(8);
      switch (dataType) {
        case DATA_TYPE_2BP_CODE_STRING:
          byte[] clutMapTable2ToX;
          if (regionDepth == REGION_DEPTH_8_BIT) {
            clutMapTable2ToX = clutMapTable2To8 == null ? defaultMap2To8 : clutMapTable2To8;
          } else if (regionDepth == REGION_DEPTH_4_BIT) {
            clutMapTable2ToX = clutMapTable2To4 == null ? defaultMap2To4 : clutMapTable2To4;
          } else {
            clutMapTable2ToX = null;
          }
          column = paint2BitPixelCodeString(data, clutEntries, clutMapTable2ToX, column, line,
              paint, canvas);
          data.byteAlign();
          break;
        case DATA_TYPE_4BP_CODE_STRING:
          byte[] clutMapTable4ToX;
          if (regionDepth == REGION_DEPTH_8_BIT) {
            clutMapTable4ToX = clutMapTable4To8 == null ? defaultMap4To8 : clutMapTable4To8;
          } else {
            clutMapTable4ToX = null;
          }
          column = paint4BitPixelCodeString(data, clutEntries, clutMapTable4ToX, column, line,
              paint, canvas);
          data.byteAlign();
          break;
        case DATA_TYPE_8BP_CODE_STRING:
          column = paint8BitPixelCodeString(data, clutEntries, null, column, line, paint, canvas);
          break;
        case DATA_TYPE_24_TABLE_DATA:
          clutMapTable2To4 = buildClutMapTable(4, 4, data);
          break;
        case DATA_TYPE_28_TABLE_DATA:
          clutMapTable2To8 = buildClutMapTable(4, 8, data);
          break;
        case DATA_TYPE_48_TABLE_DATA:
          clutMapTable2To8 = buildClutMapTable(16, 8, data);
          break;
        case DATA_TYPE_END_LINE:
          column = horizontalAddress;
          line += 2;
          break;
        default:
          // Do nothing.
          break;
      }
    }
  }

  /**
   * Paint a 2-bit/pixel code string, as defined by ETSI EN 300 743 7.2.5.2, to a canvas.
   */
  private static int paint2BitPixelCodeString(ParsableBitArray data, int[] clutEntries,
      byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
    boolean endOfPixelCodeString = false;
    do {
      int runLength = 0;
      int clutIndex = 0;
      int peek = data.readBits(2);
      if (peek != 0x00) {
        runLength = 1;
        clutIndex = peek;
      } else if (data.readBit()) {
        runLength = 3 + data.readBits(3);
        clutIndex = data.readBits(2);
      } else if (data.readBit()) {
        runLength = 1;
      } else {
        switch (data.readBits(2)) {
          case 0x00:
            endOfPixelCodeString = true;
            break;
          case 0x01:
            runLength = 2;
            break;
          case 0x02:
            runLength = 12 + data.readBits(4);
            clutIndex = data.readBits(2);
            break;
          case 0x03:
            runLength = 29 + data.readBits(8);
            clutIndex = data.readBits(2);
            break;
        }
      }

      if (runLength != 0 && paint != null) {
        paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
        canvas.drawRect(column, line, column + runLength, line + 1, paint);
      }

      column += runLength;
    } while (!endOfPixelCodeString);

    return column;
  }

  /**
   * Paint a 4-bit/pixel code string, as defined by ETSI EN 300 743 7.2.5.2, to a canvas.
   */
  private static int paint4BitPixelCodeString(ParsableBitArray data, int[] clutEntries,
      byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
    boolean endOfPixelCodeString = false;
    do {
      int runLength = 0;
      int clutIndex = 0;
      int peek = data.readBits(4);
      if (peek != 0x00) {
        runLength = 1;
        clutIndex = peek;
      } else if (!data.readBit()) {
        peek = data.readBits(3);
        if (peek != 0x00) {
          runLength = 2 + peek;
          clutIndex = 0x00;
        } else {
          endOfPixelCodeString = true;
        }
      } else if (!data.readBit()) {
        runLength = 4 + data.readBits(2);
        clutIndex = data.readBits(4);
      } else {
        switch (data.readBits(2)) {
          case 0x00:
            runLength = 1;
            break;
          case 0x01:
            runLength = 2;
            break;
          case 0x02:
            runLength = 9 + data.readBits(4);
            clutIndex = data.readBits(4);
            break;
          case 0x03:
            runLength = 25 + data.readBits(8);
            clutIndex = data.readBits(4);
            break;
        }
      }

      if (runLength != 0 && paint != null) {
        paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
        canvas.drawRect(column, line, column + runLength, line + 1, paint);
      }

      column += runLength;
    } while (!endOfPixelCodeString);

    return column;
  }

  /**
   * Paint an 8-bit/pixel code string, as defined by ETSI EN 300 743 7.2.5.2, to a canvas.
   */
  private static int paint8BitPixelCodeString(ParsableBitArray data, int[] clutEntries,
      byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
    boolean endOfPixelCodeString = false;
    do {
      int runLength = 0;
      int clutIndex = 0;
      int peek = data.readBits(8);
      if (peek != 0x00) {
        runLength = 1;
        clutIndex = peek;
      } else {
        if (!data.readBit()) {
          peek = data.readBits(7);
          if (peek != 0x00) {
            runLength = peek;
            clutIndex = 0x00;
          } else {
            endOfPixelCodeString = true;
          }
        } else {
          runLength = data.readBits(7);
          clutIndex = data.readBits(8);
        }
      }

      if (runLength != 0 && paint != null) {
        paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
        canvas.drawRect(column, line, column + runLength, line + 1, paint);
      }
      column += runLength;
    } while (!endOfPixelCodeString);

    return column;
  }

  private static byte[] buildClutMapTable(int length, int bitsPerEntry, ParsableBitArray data) {
    byte[] clutMapTable = new byte[length];
    for (int i = 0; i < length; i++) {
      clutMapTable[i] = (byte) data.readBits(bitsPerEntry);
    }
    return clutMapTable;
  }

  // Private inner classes.

  /**
   * The subtitle service definition.
   */
  private static final class SubtitleService {

    public final int subtitlePageId;
    public final int ancillaryPageId;

    public final SparseArray<RegionComposition> regions = new SparseArray<>();
    public final SparseArray<ClutDefinition> cluts = new SparseArray<>();
    public final SparseArray<ObjectData> objects = new SparseArray<>();
    public final SparseArray<ClutDefinition> ancillaryCluts = new SparseArray<>();
    public final SparseArray<ObjectData> ancillaryObjects = new SparseArray<>();

    public DisplayDefinition displayDefinition;
    public PageComposition pageComposition;

    public SubtitleService(int subtitlePageId, int ancillaryPageId) {
      this.subtitlePageId = subtitlePageId;
      this.ancillaryPageId = ancillaryPageId;
    }

    public void reset() {
      regions.clear();
      cluts.clear();
      objects.clear();
      ancillaryCluts.clear();
      ancillaryObjects.clear();
      displayDefinition = null;
      pageComposition = null;
    }

  }

  /**
   * Contains the geometry and active area of the subtitle service.
   * <p>
   * See ETSI EN 300 743 7.2.1
   */
  private static final class DisplayDefinition {

    public final int width;
    public final int height;

    public final int horizontalPositionMinimum;
    public final int horizontalPositionMaximum;
    public final int verticalPositionMinimum;
    public final int verticalPositionMaximum;

    public DisplayDefinition(int width, int height, int horizontalPositionMinimum,
        int horizontalPositionMaximum, int verticalPositionMinimum, int verticalPositionMaximum) {
      this.width = width;
      this.height = height;
      this.horizontalPositionMinimum = horizontalPositionMinimum;
      this.horizontalPositionMaximum = horizontalPositionMaximum;
      this.verticalPositionMinimum = verticalPositionMinimum;
      this.verticalPositionMaximum = verticalPositionMaximum;
    }

  }

  /**
   *  The page is the definition and arrangement of regions in the screen.
   *  <p>
   *  See ETSI EN 300 743 7.2.2
   */
  private static final class PageComposition {

    public final int timeOutSecs; // TODO: Use this or remove it.
    public final int version;
    public final int state;
    public final SparseArray<PageRegion> regions;

    public PageComposition(int timeoutSecs, int version, int state,
        SparseArray<PageRegion> regions) {
      this.timeOutSecs = timeoutSecs;
      this.version = version;
      this.state = state;
      this.regions = regions;
    }

  }

  /**
   * A region within a {@link PageComposition}.
   * <p>
   * See ETSI EN 300 743 7.2.2
   */
  private static final class PageRegion {

    public final int horizontalAddress;
    public final int verticalAddress;

    public PageRegion(int horizontalAddress, int verticalAddress) {
      this.horizontalAddress = horizontalAddress;
      this.verticalAddress = verticalAddress;
    }

  }

  /**
   * An area of the page composed of a list of objects and a CLUT.
   * <p>
   * See ETSI EN 300 743 7.2.3
   */
  private static final class RegionComposition {

    public final int id;
    public final boolean fillFlag;
    public final int width;
    public final int height;
    public final int levelOfCompatibility; // TODO: Use this or remove it.
    public final int depth;
    public final int clutId;
    public final int pixelCode8Bit;
    public final int pixelCode4Bit;
    public final int pixelCode2Bit;
    public final SparseArray<RegionObject> regionObjects;

    public RegionComposition(int id, boolean fillFlag, int width, int height,
        int levelOfCompatibility, int depth, int clutId, int pixelCode8Bit, int pixelCode4Bit,
        int pixelCode2Bit, SparseArray<RegionObject> regionObjects) {
      this.id = id;
      this.fillFlag = fillFlag;
      this.width = width;
      this.height = height;
      this.levelOfCompatibility = levelOfCompatibility;
      this.depth = depth;
      this.clutId = clutId;
      this.pixelCode8Bit = pixelCode8Bit;
      this.pixelCode4Bit = pixelCode4Bit;
      this.pixelCode2Bit = pixelCode2Bit;
      this.regionObjects = regionObjects;
    }

    public void mergeFrom(RegionComposition otherRegionComposition) {
      if (otherRegionComposition == null) {
        return;
      }
      SparseArray<RegionObject> otherRegionObjects = otherRegionComposition.regionObjects;
      for (int i = 0; i < otherRegionObjects.size(); i++) {
        regionObjects.put(otherRegionObjects.keyAt(i), otherRegionObjects.valueAt(i));
      }
    }

  }

  /**
   * An object within a {@link RegionComposition}.
   * <p>
   * See ETSI EN 300 743 7.2.3
   */
  private static final class RegionObject {

    public final int type; // TODO: Use this or remove it.
    public final int provider; // TODO: Use this or remove it.
    public final int horizontalPosition;
    public final int verticalPosition;
    public final int foregroundPixelCode; // TODO: Use this or remove it.
    public final int backgroundPixelCode; // TODO: Use this or remove it.

    public RegionObject(int type, int provider, int horizontalPosition,
        int verticalPosition, int foregroundPixelCode, int backgroundPixelCode) {
      this.type = type;
      this.provider = provider;
      this.horizontalPosition = horizontalPosition;
      this.verticalPosition = verticalPosition;
      this.foregroundPixelCode = foregroundPixelCode;
      this.backgroundPixelCode = backgroundPixelCode;
    }

  }

  /**
   * CLUT family definition containing the color tables for the three bit depths defined
   * <p>
   * See ETSI EN 300 743 7.2.4
   */
  private static final class ClutDefinition {

    public final int id;
    public final int[] clutEntries2Bit;
    public final int[] clutEntries4Bit;
    public final int[] clutEntries8Bit;

    public ClutDefinition(int id, int[] clutEntries2Bit, int[] clutEntries4Bit,
        int[] clutEntries8bit) {
      this.id = id;
      this.clutEntries2Bit = clutEntries2Bit;
      this.clutEntries4Bit = clutEntries4Bit;
      this.clutEntries8Bit = clutEntries8bit;
    }

  }

  /**
   * The textual or graphical representation of an object.
   * <p>
   * See ETSI EN 300 743 7.2.5
   */
  private static final class ObjectData {

    public final int id;
    public final boolean nonModifyingColorFlag;
    public final byte[] topFieldData;
    public final byte[] bottomFieldData;

    public ObjectData(int id, boolean nonModifyingColorFlag, byte[] topFieldData,
        byte[] bottomFieldData) {
      this.id = id;
      this.nonModifyingColorFlag = nonModifyingColorFlag;
      this.topFieldData = topFieldData;
      this.bottomFieldData = bottomFieldData;
    }

  }

}
