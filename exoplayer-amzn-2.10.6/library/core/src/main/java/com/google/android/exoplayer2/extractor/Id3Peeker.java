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

package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

/**
 * Peeks data from the beginning of an {@link ExtractorInput} to determine if there is any ID3 tag.
 */
public final class Id3Peeker {

  private final ParsableByteArray scratch;

  public Id3Peeker() {
    scratch = new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH);
  }

  /**
   * Peeks ID3 data from the input and parses the first ID3 tag.
   *
   * @param input The {@link ExtractorInput} from which data should be peeked.
   * @param id3FramePredicate Determines which ID3 frames are decoded. May be null to decode all
   *     frames.
   * @return The first ID3 tag decoded into a {@link Metadata} object. May be null if ID3 tag is not
   *     present in the input.
   * @throws IOException If an error occurred peeking from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  @Nullable
  public Metadata peekId3Data(
      ExtractorInput input, @Nullable Id3Decoder.FramePredicate id3FramePredicate)
      throws IOException, InterruptedException {
    int peekedId3Bytes = 0;
    Metadata metadata = null;
    while (true) {
      try {
        input.peekFully(scratch.data, 0, Id3Decoder.ID3_HEADER_LENGTH);
      } catch (EOFException e) {
        // If input has less than ID3_HEADER_LENGTH, ignore the rest.
        break;
      }
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != Id3Decoder.ID3_TAG) {
        // Not an ID3 tag.
        break;
      }
      scratch.skipBytes(3); // Skip major version, minor version and flags.
      int framesLength = scratch.readSynchSafeInt();
      int tagLength = Id3Decoder.ID3_HEADER_LENGTH + framesLength;

      if (metadata == null) {
        byte[] id3Data = new byte[tagLength];
        System.arraycopy(scratch.data, 0, id3Data, 0, Id3Decoder.ID3_HEADER_LENGTH);
        input.peekFully(id3Data, Id3Decoder.ID3_HEADER_LENGTH, framesLength);

        metadata = new Id3Decoder(id3FramePredicate).decode(id3Data, tagLength);
      } else {
        input.advancePeekPosition(framesLength);
      }

      peekedId3Bytes += tagLength;
    }

    input.resetPeekPosition();
    input.advancePeekPosition(peekedId3Bytes);
    return metadata;
  }
}
