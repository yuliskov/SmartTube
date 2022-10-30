/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.text.pgs;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.Inflater;

/** A {@link SimpleSubtitleDecoder} for PGS subtitles. */
public final class PgsDecoder extends SimpleSubtitleDecoder {

  private static final int SECTION_TYPE_PALETTE = 0x14;
  private static final int SECTION_TYPE_BITMAP_PICTURE = 0x15;
  private static final int SECTION_TYPE_IDENTIFIER = 0x16;
  private static final int SECTION_TYPE_END = 0x80;

  private static final byte INFLATE_HEADER = 0x78;

  private final ParsableByteArray buffer;
  private final ParsableByteArray inflatedBuffer;
  private final CueBuilder cueBuilder;

  @Nullable private Inflater inflater;

  public PgsDecoder() {
    super("PgsDecoder");
    buffer = new ParsableByteArray();
    inflatedBuffer = new ParsableByteArray();
    cueBuilder = new CueBuilder();
  }

  @Override
  protected Subtitle decode(byte[] data, int size, boolean reset) throws SubtitleDecoderException {
    buffer.reset(data, size);
    maybeInflateData(buffer);
    cueBuilder.reset();
    ArrayList<Cue> cues = new ArrayList<>();
    while (buffer.bytesLeft() >= 3) {
      Cue cue = readNextSection(buffer, cueBuilder);
      if (cue != null) {
        cues.add(cue);
      }
    }
    return new PgsSubtitle(Collections.unmodifiableList(cues));
  }

  private void maybeInflateData(ParsableByteArray buffer) {
    if (buffer.bytesLeft() > 0 && buffer.peekUnsignedByte() == INFLATE_HEADER) {
      if (inflater == null) {
        inflater = new Inflater();
      }
      if (Util.inflate(buffer, inflatedBuffer, inflater)) {
        buffer.reset(inflatedBuffer.data, inflatedBuffer.limit());
      } // else assume data is not compressed.
    }
  }

  @Nullable
  private static Cue readNextSection(ParsableByteArray buffer, CueBuilder cueBuilder) {
    int limit = buffer.limit();
    int sectionType = buffer.readUnsignedByte();
    int sectionLength = buffer.readUnsignedShort();

    int nextSectionPosition = buffer.getPosition() + sectionLength;
    if (nextSectionPosition > limit) {
      buffer.setPosition(limit);
      return null;
    }

    Cue cue = null;
    switch (sectionType) {
      case SECTION_TYPE_PALETTE:
        cueBuilder.parsePaletteSection(buffer, sectionLength);
        break;
      case SECTION_TYPE_BITMAP_PICTURE:
        cueBuilder.parseBitmapSection(buffer, sectionLength);
        break;
      case SECTION_TYPE_IDENTIFIER:
        cueBuilder.parseIdentifierSection(buffer, sectionLength);
        break;
      case SECTION_TYPE_END:
        cue = cueBuilder.build();
        cueBuilder.reset();
        break;
      default:
        break;
    }

    buffer.setPosition(nextSectionPosition);
    return cue;
  }

  private static final class CueBuilder {

    private final ParsableByteArray bitmapData;
    private final int[] colors;

    private boolean colorsSet;
    private int planeWidth;
    private int planeHeight;
    private int bitmapX;
    private int bitmapY;
    private int bitmapWidth;
    private int bitmapHeight;

    public CueBuilder() {
      bitmapData = new ParsableByteArray();
      colors = new int[256];
    }

    private void parsePaletteSection(ParsableByteArray buffer, int sectionLength) {
      if ((sectionLength % 5) != 2) {
        // Section must be two bytes followed by a whole number of (index, y, cb, cr, a) entries.
        return;
      }
      buffer.skipBytes(2);

      Arrays.fill(colors, 0);
      int entryCount = sectionLength / 5;
      for (int i = 0; i < entryCount; i++) {
        int index = buffer.readUnsignedByte();
        int y = buffer.readUnsignedByte();
        int cr = buffer.readUnsignedByte();
        int cb = buffer.readUnsignedByte();
        int a = buffer.readUnsignedByte();
        int r = (int) (y + (1.40200 * (cr - 128)));
        int g = (int) (y - (0.34414 * (cb - 128)) - (0.71414 * (cr - 128)));
        int b = (int) (y + (1.77200 * (cb - 128)));
        colors[index] =
            (a << 24)
                | (Util.constrainValue(r, 0, 255) << 16)
                | (Util.constrainValue(g, 0, 255) << 8)
                | Util.constrainValue(b, 0, 255);
      }
      colorsSet = true;
    }

    private void parseBitmapSection(ParsableByteArray buffer, int sectionLength) {
      if (sectionLength < 4) {
        return;
      }
      buffer.skipBytes(3); // Id (2 bytes), version (1 byte).
      boolean isBaseSection = (0x80 & buffer.readUnsignedByte()) != 0;
      sectionLength -= 4;

      if (isBaseSection) {
        if (sectionLength < 7) {
          return;
        }
        int totalLength = buffer.readUnsignedInt24();
        if (totalLength < 4) {
          return;
        }
        bitmapWidth = buffer.readUnsignedShort();
        bitmapHeight = buffer.readUnsignedShort();
        bitmapData.reset(totalLength - 4);
        sectionLength -= 7;
      }

      int position = bitmapData.getPosition();
      int limit = bitmapData.limit();
      if (position < limit && sectionLength > 0) {
        int bytesToRead = Math.min(sectionLength, limit - position);
        buffer.readBytes(bitmapData.data, position, bytesToRead);
        bitmapData.setPosition(position + bytesToRead);
      }
    }

    private void parseIdentifierSection(ParsableByteArray buffer, int sectionLength) {
      if (sectionLength < 19) {
        return;
      }
      planeWidth = buffer.readUnsignedShort();
      planeHeight = buffer.readUnsignedShort();
      buffer.skipBytes(11);
      bitmapX = buffer.readUnsignedShort();
      bitmapY = buffer.readUnsignedShort();
    }

    @Nullable
    public Cue build() {
      if (planeWidth == 0
          || planeHeight == 0
          || bitmapWidth == 0
          || bitmapHeight == 0
          || bitmapData.limit() == 0
          || bitmapData.getPosition() != bitmapData.limit()
          || !colorsSet) {
        return null;
      }
      // Build the bitmapData.
      bitmapData.setPosition(0);
      int[] argbBitmapData = new int[bitmapWidth * bitmapHeight];
      int argbBitmapDataIndex = 0;
      while (argbBitmapDataIndex < argbBitmapData.length) {
        int colorIndex = bitmapData.readUnsignedByte();
        if (colorIndex != 0) {
          argbBitmapData[argbBitmapDataIndex++] = colors[colorIndex];
        } else {
          int switchBits = bitmapData.readUnsignedByte();
          if (switchBits != 0) {
            int runLength =
                (switchBits & 0x40) == 0
                    ? (switchBits & 0x3F)
                    : (((switchBits & 0x3F) << 8) | bitmapData.readUnsignedByte());
            int color = (switchBits & 0x80) == 0 ? 0 : colors[bitmapData.readUnsignedByte()];
            Arrays.fill(
                argbBitmapData, argbBitmapDataIndex, argbBitmapDataIndex + runLength, color);
            argbBitmapDataIndex += runLength;
          }
        }
      }
      Bitmap bitmap =
          Bitmap.createBitmap(argbBitmapData, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
      // Build the cue.
      return new Cue(
          bitmap,
          (float) bitmapX / planeWidth,
          Cue.ANCHOR_TYPE_START,
          (float) bitmapY / planeHeight,
          Cue.ANCHOR_TYPE_START,
          (float) bitmapWidth / planeWidth,
          (float) bitmapHeight / planeHeight);
    }

    public void reset() {
      planeWidth = 0;
      planeHeight = 0;
      bitmapX = 0;
      bitmapY = 0;
      bitmapWidth = 0;
      bitmapHeight = 0;
      bitmapData.reset(0);
      colorsSet = false;
    }
  }
}
