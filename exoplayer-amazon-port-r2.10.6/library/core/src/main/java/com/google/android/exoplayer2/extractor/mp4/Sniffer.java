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
package com.google.android.exoplayer2.extractor.mp4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Provides methods that peek data from an {@link ExtractorInput} and return whether the input
 * appears to be in MP4 format.
 */
/* package */ final class Sniffer {

  /** The maximum number of bytes to peek when sniffing. */
  private static final int SEARCH_LENGTH = 4 * 1024;

  private static final int[] COMPATIBLE_BRANDS =
      new int[] {
        Util.getIntegerCodeForString("isom"),
        Util.getIntegerCodeForString("iso2"),
        Util.getIntegerCodeForString("iso3"),
        Util.getIntegerCodeForString("iso4"),
        Util.getIntegerCodeForString("iso5"),
        Util.getIntegerCodeForString("iso6"),
        Util.getIntegerCodeForString("avc1"),
        Util.getIntegerCodeForString("hvc1"),
        Util.getIntegerCodeForString("hev1"),
        Util.getIntegerCodeForString("av01"),
        Util.getIntegerCodeForString("mp41"),
        Util.getIntegerCodeForString("mp42"),
        Util.getIntegerCodeForString("3g2a"),
        Util.getIntegerCodeForString("3g2b"),
        Util.getIntegerCodeForString("3gr6"),
        Util.getIntegerCodeForString("3gs6"),
        Util.getIntegerCodeForString("3ge6"),
        Util.getIntegerCodeForString("3gg6"),
        Util.getIntegerCodeForString("M4V "),
        Util.getIntegerCodeForString("M4A "),
        Util.getIntegerCodeForString("f4v "),
        Util.getIntegerCodeForString("kddi"),
        Util.getIntegerCodeForString("M4VP"),
        Util.getIntegerCodeForString("qt  "), // Apple QuickTime
        Util.getIntegerCodeForString("MSNV"), // Sony PSP
        Util.getIntegerCodeForString("dby1"), // Dolby Vision
      };

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being a fragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return Whether the input appears to be in the fragmented MP4 format.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffFragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, true);
  }

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being an unfragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return Whether the input appears to be in the unfragmented MP4 format.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffUnfragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, false);
  }

  private static boolean sniffInternal(ExtractorInput input, boolean fragmented)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    int bytesToSearch = (int) (inputLength == C.LENGTH_UNSET || inputLength > SEARCH_LENGTH
        ? SEARCH_LENGTH : inputLength);

    ParsableByteArray buffer = new ParsableByteArray(64);
    int bytesSearched = 0;
    boolean foundGoodFileType = false;
    boolean isFragmented = false;
    while (bytesSearched < bytesToSearch) {
      // Read an atom header.
      int headerSize = Atom.HEADER_SIZE;
      buffer.reset(headerSize);
      input.peekFully(buffer.data, 0, headerSize);
      long atomSize = buffer.readUnsignedInt();
      int atomType = buffer.readInt();
      if (atomSize == Atom.DEFINES_LARGE_SIZE) {
        // Read the large atom size.
        headerSize = Atom.LONG_HEADER_SIZE;
        input.peekFully(buffer.data, Atom.HEADER_SIZE, Atom.LONG_HEADER_SIZE - Atom.HEADER_SIZE);
        buffer.setLimit(Atom.LONG_HEADER_SIZE);
        atomSize = buffer.readLong();
      } else if (atomSize == Atom.EXTENDS_TO_END_SIZE) {
        // The atom extends to the end of the file.
        long fileEndPosition = input.getLength();
        if (fileEndPosition != C.LENGTH_UNSET) {
          atomSize = fileEndPosition - input.getPeekPosition() + headerSize;
        }
      }

      if (inputLength != C.LENGTH_UNSET && bytesSearched + atomSize > inputLength) {
        // The file is invalid because the atom extends past the end of the file.
        return false;
      }
      if (atomSize < headerSize) {
        // The file is invalid because the atom size is too small for its header.
        return false;
      }
      bytesSearched += headerSize;

      if (atomType == Atom.TYPE_moov) {
        // We have seen the moov atom. We increase the search size to make sure we don't miss an
        // mvex atom because the moov's size exceeds the search length.
        bytesToSearch += (int) atomSize;
        if (inputLength != C.LENGTH_UNSET && bytesToSearch > inputLength) {
          // Make sure we don't exceed the file size.
          bytesToSearch = (int) inputLength;
        }
        // Check for an mvex atom inside the moov atom to identify whether the file is fragmented.
        continue;
      }

      if (atomType == Atom.TYPE_moof || atomType == Atom.TYPE_mvex) {
        // The movie is fragmented. Stop searching as we must have read any ftyp atom already.
        isFragmented = true;
        break;
      }

      if (bytesSearched + atomSize - headerSize >= bytesToSearch) {
        // Stop searching as peeking this atom would exceed the search limit.
        break;
      }

      int atomDataSize = (int) (atomSize - headerSize);
      bytesSearched += atomDataSize;
      if (atomType == Atom.TYPE_ftyp) {
        // Parse the atom and check the file type/brand is compatible with the extractors.
        if (atomDataSize < 8) {
          return false;
        }
        buffer.reset(atomDataSize);
        input.peekFully(buffer.data, 0, atomDataSize);
        int brandsCount = atomDataSize / 4;
        for (int i = 0; i < brandsCount; i++) {
          if (i == 1) {
            // This index refers to the minorVersion, not a brand, so skip it.
            buffer.skipBytes(4);
          } else if (isCompatibleBrand(buffer.readInt())) {
            foundGoodFileType = true;
            break;
          }
        }
        if (!foundGoodFileType) {
          // The types were not compatible and there is only one ftyp atom, so reject the file.
          return false;
        }
      } else if (atomDataSize != 0) {
        // Skip the atom.
        input.advancePeekPosition(atomDataSize);
      }
    }
    return foundGoodFileType && fragmented == isFragmented;
  }

  /**
   * Returns whether {@code brand} is an ftyp atom brand that is compatible with the MP4 extractors.
   */
  private static boolean isCompatibleBrand(int brand) {
    // Accept all brands starting '3gp'.
    if (brand >>> 8 == Util.getIntegerCodeForString("3gp")) {
      return true;
    }
    for (int compatibleBrand : COMPATIBLE_BRANDS) {
      if (compatibleBrand == brand) {
        return true;
      }
    }
    return false;
  }

  private Sniffer() {
    // Prevent instantiation.
  }

}
