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
package com.google.android.exoplayer2.extractor.ogg;

import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/** Seeks in an Ogg stream. */
/* package */ final class DefaultOggSeeker implements OggSeeker {

  private static final int MATCH_RANGE = 72000;
  private static final int MATCH_BYTE_RANGE = 100000;
  private static final int DEFAULT_OFFSET = 30000;

  private static final int STATE_SEEK_TO_END = 0;
  private static final int STATE_READ_LAST_PAGE = 1;
  private static final int STATE_SEEK = 2;
  private static final int STATE_SKIP = 3;
  private static final int STATE_IDLE = 4;

  private final OggPageHeader pageHeader = new OggPageHeader();
  private final long payloadStartPosition;
  private final long payloadEndPosition;
  private final StreamReader streamReader;

  private int state;
  private long totalGranules;
  private long positionBeforeSeekToEnd;
  private long targetGranule;

  private long start;
  private long end;
  private long startGranule;
  private long endGranule;

  /**
   * Constructs an OggSeeker.
   *
   * @param streamReader The {@link StreamReader} that owns this seeker.
   * @param payloadStartPosition Start position of the payload (inclusive).
   * @param payloadEndPosition End position of the payload (exclusive).
   * @param firstPayloadPageSize The total size of the first payload page, in bytes.
   * @param firstPayloadPageGranulePosition The granule position of the first payload page.
   * @param firstPayloadPageIsLastPage Whether the first payload page is also the last page.
   */
  public DefaultOggSeeker(
      StreamReader streamReader,
      long payloadStartPosition,
      long payloadEndPosition,
      long firstPayloadPageSize,
      long firstPayloadPageGranulePosition,
      boolean firstPayloadPageIsLastPage) {
    Assertions.checkArgument(
        payloadStartPosition >= 0 && payloadEndPosition > payloadStartPosition);
    this.streamReader = streamReader;
    this.payloadStartPosition = payloadStartPosition;
    this.payloadEndPosition = payloadEndPosition;
    if (firstPayloadPageSize == payloadEndPosition - payloadStartPosition
        || firstPayloadPageIsLastPage) {
      totalGranules = firstPayloadPageGranulePosition;
      state = STATE_IDLE;
    } else {
      state = STATE_SEEK_TO_END;
    }
  }

  @Override
  public long read(ExtractorInput input) throws IOException, InterruptedException {
    switch (state) {
      case STATE_IDLE:
        return -1;
      case STATE_SEEK_TO_END:
        positionBeforeSeekToEnd = input.getPosition();
        state = STATE_READ_LAST_PAGE;
        // Seek to the end just before the last page of stream to get the duration.
        long lastPageSearchPosition = payloadEndPosition - OggPageHeader.MAX_PAGE_SIZE;
        if (lastPageSearchPosition > positionBeforeSeekToEnd) {
          return lastPageSearchPosition;
        }
        // Fall through.
      case STATE_READ_LAST_PAGE:
        totalGranules = readGranuleOfLastPage(input);
        state = STATE_IDLE;
        return positionBeforeSeekToEnd;
      case STATE_SEEK:
        long position = getNextSeekPosition(input);
        if (position != C.POSITION_UNSET) {
          return position;
        }
        state = STATE_SKIP;
        // Fall through.
      case STATE_SKIP:
        skipToPageOfTargetGranule(input);
        state = STATE_IDLE;
        return -(startGranule + 2);
      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  @Override
  public OggSeekMap createSeekMap() {
    return totalGranules != 0 ? new OggSeekMap() : null;
  }

  @Override
  public void startSeek(long targetGranule) {
    this.targetGranule = Util.constrainValue(targetGranule, 0, totalGranules - 1);
    state = STATE_SEEK;
    start = payloadStartPosition;
    end = payloadEndPosition;
    startGranule = 0;
    endGranule = totalGranules;
  }

  /**
   * Performs a single step of a seeking binary search, returning the byte position from which data
   * should be provided for the next step, or {@link C#POSITION_UNSET} if the search has converged.
   * If the search has converged then {@link #skipToPageOfTargetGranule(ExtractorInput)} should be
   * called to skip to the target page.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @return The byte position from which data should be provided for the next step, or {@link
   *     C#POSITION_UNSET} if the search has converged.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If interrupted while reading from the input.
   */
  private long getNextSeekPosition(ExtractorInput input) throws IOException, InterruptedException {
    if (start == end) {
      return C.POSITION_UNSET;
    }

    long currentPosition = input.getPosition();
    if (!skipToNextPage(input, end)) {
      if (start == currentPosition) {
        throw new IOException("No ogg page can be found.");
      }
      return start;
    }

    pageHeader.populate(input, /* quiet= */ false);
    input.resetPeekPosition();

    long granuleDistance = targetGranule - pageHeader.granulePosition;
    int pageSize = pageHeader.headerSize + pageHeader.bodySize;
    if (0 <= granuleDistance && granuleDistance < MATCH_RANGE) {
      return C.POSITION_UNSET;
    }

    if (granuleDistance < 0) {
      end = currentPosition;
      endGranule = pageHeader.granulePosition;
    } else {
      start = input.getPosition() + pageSize;
      startGranule = pageHeader.granulePosition;
    }

    if (end - start < MATCH_BYTE_RANGE) {
      end = start;
      return start;
    }

    long offset = pageSize * (granuleDistance <= 0 ? 2L : 1L);
    long nextPosition =
        input.getPosition()
            - offset
            + (granuleDistance * (end - start) / (endGranule - startGranule));
    return Util.constrainValue(nextPosition, start, end - 1);
  }

  /**
   * Skips forward to the start of the page containing the {@code targetGranule}.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @throws ParserException If populating the page header fails.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If interrupted while reading from the input.
   */
  private void skipToPageOfTargetGranule(ExtractorInput input)
      throws IOException, InterruptedException {
    pageHeader.populate(input, /* quiet= */ false);
    while (pageHeader.granulePosition <= targetGranule) {
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
      start = input.getPosition();
      startGranule = pageHeader.granulePosition;
      pageHeader.populate(input, /* quiet= */ false);
    }
    input.resetPeekPosition();
  }

  /**
   * Skips to the next page.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @throws IOException If peeking/reading from the input fails.
   * @throws InterruptedException If the thread is interrupted.
   * @throws EOFException If the next page can't be found before the end of the input.
   */
  @VisibleForTesting
  void skipToNextPage(ExtractorInput input) throws IOException, InterruptedException {
    if (!skipToNextPage(input, payloadEndPosition)) {
      // Not found until eof.
      throw new EOFException();
    }
  }

  /**
   * Skips to the next page. Searches for the next page header.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @param limit The limit up to which the search should take place.
   * @return Whether the next page was found.
   * @throws IOException If peeking/reading from the input fails.
   * @throws InterruptedException If interrupted while peeking/reading from the input.
   */
  private boolean skipToNextPage(ExtractorInput input, long limit)
      throws IOException, InterruptedException {
    limit = Math.min(limit + 3, payloadEndPosition);
    byte[] buffer = new byte[2048];
    int peekLength = buffer.length;
    while (true) {
      if (input.getPosition() + peekLength > limit) {
        // Make sure to not peek beyond the end of the input.
        peekLength = (int) (limit - input.getPosition());
        if (peekLength < 4) {
          // Not found until end.
          return false;
        }
      }
      input.peekFully(buffer, 0, peekLength, false);
      for (int i = 0; i < peekLength - 3; i++) {
        if (buffer[i] == 'O'
            && buffer[i + 1] == 'g'
            && buffer[i + 2] == 'g'
            && buffer[i + 3] == 'S') {
          // Match! Skip to the start of the pattern.
          input.skipFully(i);
          return true;
        }
      }
      // Overlap by not skipping the entire peekLength.
      input.skipFully(peekLength - 3);
    }
  }

  /**
   * Skips to the last Ogg page in the stream and reads the header's granule field which is the
   * total number of samples per channel.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @return The total number of samples of this input.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If the thread is interrupted.
   */
  @VisibleForTesting
  long readGranuleOfLastPage(ExtractorInput input) throws IOException, InterruptedException {
    skipToNextPage(input);
    pageHeader.reset();
    while ((pageHeader.type & 0x04) != 0x04 && input.getPosition() < payloadEndPosition) {
      pageHeader.populate(input, /* quiet= */ false);
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
    }
    return pageHeader.granulePosition;
  }

  private final class OggSeekMap implements SeekMap {

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      long targetGranule = streamReader.convertTimeToGranule(timeUs);
      long estimatedPosition =
          payloadStartPosition
              + (targetGranule * (payloadEndPosition - payloadStartPosition) / totalGranules)
              - DEFAULT_OFFSET;
      estimatedPosition =
          Util.constrainValue(estimatedPosition, payloadStartPosition, payloadEndPosition - 1);
      return new SeekPoints(new SeekPoint(timeUs, estimatedPosition));
    }

    @Override
    public long getDurationUs() {
      return streamReader.convertGranuleToTime(totalGranules);
    }
  }
}
