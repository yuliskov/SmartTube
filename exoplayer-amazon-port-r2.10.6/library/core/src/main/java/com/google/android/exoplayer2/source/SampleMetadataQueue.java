/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.TrackOutput.CryptoData;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * A queue of metadata describing the contents of a media buffer.
 */
/* package */ final class SampleMetadataQueue {

  /**
   * A holder for sample metadata not held by {@link DecoderInputBuffer}.
   */
  public static final class SampleExtrasHolder {

    public int size;
    public long offset;
    public CryptoData cryptoData;

  }

  private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

  private int capacity;
  private int[] sourceIds;
  private long[] offsets;
  private int[] sizes;
  private int[] flags;
  private long[] timesUs;
  private CryptoData[] cryptoDatas;
  private Format[] formats;

  private int length;
  private int absoluteFirstIndex;
  private int relativeFirstIndex;
  private int readPosition;

  private long largestDiscardedTimestampUs;
  private long largestQueuedTimestampUs;
  private boolean isLastSampleQueued;
  private boolean upstreamKeyframeRequired;
  private boolean upstreamFormatRequired;
  private Format upstreamFormat;
  private int upstreamSourceId;

  public SampleMetadataQueue() {
    capacity = SAMPLE_CAPACITY_INCREMENT;
    sourceIds = new int[capacity];
    offsets = new long[capacity];
    timesUs = new long[capacity];
    flags = new int[capacity];
    sizes = new int[capacity];
    cryptoDatas = new CryptoData[capacity];
    formats = new Format[capacity];
    largestDiscardedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
    upstreamFormatRequired = true;
    upstreamKeyframeRequired = true;
  }

  /**
   * Clears all sample metadata from the queue.
   *
   * @param resetUpstreamFormat Whether the upstream format should be cleared. If set to false,
   *     samples queued after the reset (and before a subsequent call to {@link #format(Format)})
   *     are assumed to have the current upstream format. If set to true, {@link #format(Format)}
   *     must be called after the reset before any more samples can be queued.
   */
  public void reset(boolean resetUpstreamFormat) {
    length = 0;
    absoluteFirstIndex = 0;
    relativeFirstIndex = 0;
    readPosition = 0;
    upstreamKeyframeRequired = true;
    largestDiscardedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
    isLastSampleQueued = false;
    if (resetUpstreamFormat) {
      upstreamFormat = null;
      upstreamFormatRequired = true;
    }
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return absoluteFirstIndex + length;
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   * @return The reduced total number of bytes written after the samples have been discarded, or 0
   *     if the queue is now empty.
   */
  public long discardUpstreamSamples(int discardFromIndex) {
    int discardCount = getWriteIndex() - discardFromIndex;
    Assertions.checkArgument(0 <= discardCount && discardCount <= (length - readPosition));
    length -= discardCount;
    largestQueuedTimestampUs = Math.max(largestDiscardedTimestampUs, getLargestTimestamp(length));
    isLastSampleQueued = discardCount == 0 && isLastSampleQueued;
    if (length == 0) {
      return 0;
    } else {
      int relativeLastWriteIndex = getRelativeIndex(length - 1);
      return offsets[relativeLastWriteIndex] + sizes[relativeLastWriteIndex];
    }
  }

  public void sourceId(int sourceId) {
    upstreamSourceId = sourceId;
  }

  // Called by the consuming thread.

  /**
   * Returns the current absolute start index.
   */
  public int getFirstIndex() {
    return absoluteFirstIndex;
  }

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return absoluteFirstIndex + readPosition;
  }

  /**
   * Peeks the source id of the next sample to be read, or the current upstream source id if the
   * queue is empty or if the read position is at the end of the queue.
   *
   * @return The source id.
   */
  public int peekSourceId() {
    int relativeReadIndex = getRelativeIndex(readPosition);
    return hasNextSample() ? sourceIds[relativeReadIndex] : upstreamSourceId;
  }

  /**
   * Returns whether a sample is available to be read.
   */
  public synchronized boolean hasNextSample() {
    return readPosition != length;
  }

  /**
   * Returns the upstream {@link Format} in which samples are being queued.
   */
  public synchronized Format getUpstreamFormat() {
    return upstreamFormatRequired ? null : upstreamFormat;
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last call to
   * {@link #reset(boolean)}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public synchronized long getLargestQueuedTimestampUs() {
    return largestQueuedTimestampUs;
  }

  /**
   * Returns whether the last sample of the stream has knowingly been queued. A return value of
   * {@code false} means that the last sample had not been queued or that it's unknown whether the
   * last sample has been queued.
   *
   * <p>Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   */
  public synchronized boolean isLastSampleQueued() {
    return isLastSampleQueued;
  }

  /** Returns the timestamp of the first sample, or {@link Long#MIN_VALUE} if the queue is empty. */
  public synchronized long getFirstTimestampUs() {
    return length == 0 ? Long.MIN_VALUE : timesUs[relativeFirstIndex];
  }

  /**
   * Rewinds the read position to the first sample retained in the queue.
   */
  public synchronized void rewind() {
    readPosition = 0;
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If a sample is read then the buffer is populated with information about
   *     the sample, but not its data. The size and absolute position of the data in the rolling
   *     buffer is stored in {@code extrasHolder}, along with an encryption id if present and the
   *     absolute position of the first byte that may still be required after the current sample has
   *     been read. If a {@link DecoderInputBuffer#isFlagsOnly() flags-only} buffer is passed, only
   *     the buffer flags may be populated by this method and the read position of the queue will
   *     not change. May be null if the caller requires that the format of the stream be read even
   *     if it's not changing.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param downstreamFormat The current downstream {@link Format}. If the format of the next sample
   *     is different to the current downstream format then a format will be read.
   * @param extrasHolder The holder into which extra sample information should be written.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  @SuppressWarnings("ReferenceEquality")
  public synchronized int read(
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      boolean formatRequired,
      boolean loadingFinished,
      Format downstreamFormat,
      SampleExtrasHolder extrasHolder) {
    if (!hasNextSample()) {
      if (loadingFinished || isLastSampleQueued) {
        buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      } else if (upstreamFormat != null
          && (formatRequired || upstreamFormat != downstreamFormat)) {
        formatHolder.format = upstreamFormat;
        return C.RESULT_FORMAT_READ;
      } else {
        return C.RESULT_NOTHING_READ;
      }
    }

    int relativeReadIndex = getRelativeIndex(readPosition);
    if (formatRequired || formats[relativeReadIndex] != downstreamFormat) {
      formatHolder.format = formats[relativeReadIndex];
      return C.RESULT_FORMAT_READ;
    }

    buffer.setFlags(flags[relativeReadIndex]);
    buffer.timeUs = timesUs[relativeReadIndex];
    if (buffer.isFlagsOnly()) {
      return C.RESULT_BUFFER_READ;
    }

    extrasHolder.size = sizes[relativeReadIndex];
    extrasHolder.offset = offsets[relativeReadIndex];
    extrasHolder.cryptoData = cryptoDatas[relativeReadIndex];

    readPosition++;
    return C.RESULT_BUFFER_READ;
  }

  /**
   * Attempts to advance the read position to the sample before or at the specified time.
   *
   * @param timeUs The time to advance to.
   * @param toKeyframe If true then attempts to advance to the keyframe before or at the specified
   *     time, rather than to any sample before or at that time.
   * @param allowTimeBeyondBuffer Whether the operation can succeed if {@code timeUs} is beyond the
   *     end of the queue, by advancing the read position to the last sample (or keyframe) in the
   *     queue.
   * @return The number of samples that were skipped if the operation was successful, which may be
   *     equal to 0, or {@link SampleQueue#ADVANCE_FAILED} if the operation was not successful. A
   *     successful advance is one in which the read position was unchanged or advanced, and is now
   *     at a sample meeting the specified criteria.
   */
  public synchronized int advanceTo(long timeUs, boolean toKeyframe,
      boolean allowTimeBeyondBuffer) {
    int relativeReadIndex = getRelativeIndex(readPosition);
    if (!hasNextSample() || timeUs < timesUs[relativeReadIndex]
        || (timeUs > largestQueuedTimestampUs && !allowTimeBeyondBuffer)) {
      return SampleQueue.ADVANCE_FAILED;
    }
    int offset = findSampleBefore(relativeReadIndex, length - readPosition, timeUs, toKeyframe);
    if (offset == -1) {
      return SampleQueue.ADVANCE_FAILED;
    }
    readPosition += offset;
    return offset;
  }

  /**
   * Advances the read position to the end of the queue.
   *
   * @return The number of samples that were skipped.
   */
  public synchronized int advanceToEnd() {
    int skipCount = length - readPosition;
    readPosition = length;
    return skipCount;
  }

  /**
   * Attempts to set the read position to the specified sample index.
   *
   * @param sampleIndex The sample index.
   * @return Whether the read position was set successfully. False is returned if the specified
   *     index is smaller than the index of the first sample in the queue, or larger than the index
   *     of the next sample that will be written.
   */
  public synchronized boolean setReadPosition(int sampleIndex) {
    if (absoluteFirstIndex <= sampleIndex && sampleIndex <= absoluteFirstIndex + length) {
      readPosition = sampleIndex - absoluteFirstIndex;
      return true;
    }
    return false;
  }

  /**
   * Discards up to but not including the sample immediately before or at the specified time.
   *
   * @param timeUs The time to discard up to.
   * @param toKeyframe If true then discards samples up to the keyframe before or at the specified
   *     time, rather than just any sample before or at that time.
   * @param stopAtReadPosition If true then samples are only discarded if they're before the read
   *     position. If false then samples at and beyond the read position may be discarded, in which
   *     case the read position is advanced to the first remaining sample.
   * @return The corresponding offset up to which data should be discarded, or
   *     {@link C#POSITION_UNSET} if no discarding of data is necessary.
   */
  public synchronized long discardTo(long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
    if (length == 0 || timeUs < timesUs[relativeFirstIndex]) {
      return C.POSITION_UNSET;
    }
    int searchLength = stopAtReadPosition && readPosition != length ? readPosition + 1 : length;
    int discardCount = findSampleBefore(relativeFirstIndex, searchLength, timeUs, toKeyframe);
    if (discardCount == -1) {
      return C.POSITION_UNSET;
    }
    return discardSamples(discardCount);
  }

  /**
   * Discards samples up to but not including the read position.
   *
   * @return The corresponding offset up to which data should be discarded, or
   *     {@link C#POSITION_UNSET} if no discarding of data is necessary.
   */
  public synchronized long discardToRead() {
    if (readPosition == 0) {
      return C.POSITION_UNSET;
    }
    return discardSamples(readPosition);
  }

  /**
   * Discards all samples in the queue. The read position is also advanced.
   *
   * @return The corresponding offset up to which data should be discarded, or
   *     {@link C#POSITION_UNSET} if no discarding of data is necessary.
   */
  public synchronized long discardToEnd() {
    if (length == 0) {
      return C.POSITION_UNSET;
    }
    return discardSamples(length);
  }

  // Called by the loading thread.

  public synchronized boolean format(Format format) {
    if (format == null) {
      upstreamFormatRequired = true;
      return false;
    }
    upstreamFormatRequired = false;
    if (Util.areEqual(format, upstreamFormat)) {
      // Suppress changes between equal formats so we can use referential equality in readData.
      return false;
    } else {
      upstreamFormat = format;
      return true;
    }
  }

  public synchronized void commitSample(long timeUs, @C.BufferFlags int sampleFlags, long offset,
      int size, CryptoData cryptoData) {
    if (upstreamKeyframeRequired) {
      if ((sampleFlags & C.BUFFER_FLAG_KEY_FRAME) == 0) {
        return;
      }
      upstreamKeyframeRequired = false;
    }
    Assertions.checkState(!upstreamFormatRequired);

    isLastSampleQueued = (sampleFlags & C.BUFFER_FLAG_LAST_SAMPLE) != 0;
    largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timeUs);

    int relativeEndIndex = getRelativeIndex(length);
    timesUs[relativeEndIndex] = timeUs;
    offsets[relativeEndIndex] = offset;
    sizes[relativeEndIndex] = size;
    flags[relativeEndIndex] = sampleFlags;
    cryptoDatas[relativeEndIndex] = cryptoData;
    formats[relativeEndIndex] = upstreamFormat;
    sourceIds[relativeEndIndex] = upstreamSourceId;

    length++;
    if (length == capacity) {
      // Increase the capacity.
      int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
      int[] newSourceIds = new int[newCapacity];
      long[] newOffsets = new long[newCapacity];
      long[] newTimesUs = new long[newCapacity];
      int[] newFlags = new int[newCapacity];
      int[] newSizes = new int[newCapacity];
      CryptoData[] newCryptoDatas = new CryptoData[newCapacity];
      Format[] newFormats = new Format[newCapacity];
      int beforeWrap = capacity - relativeFirstIndex;
      System.arraycopy(offsets, relativeFirstIndex, newOffsets, 0, beforeWrap);
      System.arraycopy(timesUs, relativeFirstIndex, newTimesUs, 0, beforeWrap);
      System.arraycopy(flags, relativeFirstIndex, newFlags, 0, beforeWrap);
      System.arraycopy(sizes, relativeFirstIndex, newSizes, 0, beforeWrap);
      System.arraycopy(cryptoDatas, relativeFirstIndex, newCryptoDatas, 0, beforeWrap);
      System.arraycopy(formats, relativeFirstIndex, newFormats, 0, beforeWrap);
      System.arraycopy(sourceIds, relativeFirstIndex, newSourceIds, 0, beforeWrap);
      int afterWrap = relativeFirstIndex;
      System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
      System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
      System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
      System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
      System.arraycopy(cryptoDatas, 0, newCryptoDatas, beforeWrap, afterWrap);
      System.arraycopy(formats, 0, newFormats, beforeWrap, afterWrap);
      System.arraycopy(sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
      offsets = newOffsets;
      timesUs = newTimesUs;
      flags = newFlags;
      sizes = newSizes;
      cryptoDatas = newCryptoDatas;
      formats = newFormats;
      sourceIds = newSourceIds;
      relativeFirstIndex = 0;
      length = capacity;
      capacity = newCapacity;
    }
  }

  /**
   * Attempts to discard samples from the end of the queue to allow samples starting from the
   * specified timestamp to be spliced in. Samples will not be discarded prior to the read position.
   *
   * @param timeUs The timestamp at which the splice occurs.
   * @return Whether the splice was successful.
   */
  public synchronized boolean attemptSplice(long timeUs) {
    if (length == 0) {
      return timeUs > largestDiscardedTimestampUs;
    }
    long largestReadTimestampUs = Math.max(largestDiscardedTimestampUs,
        getLargestTimestamp(readPosition));
    if (largestReadTimestampUs >= timeUs) {
      return false;
    }
    int retainCount = length;
    int relativeSampleIndex = getRelativeIndex(length - 1);
    while (retainCount > readPosition && timesUs[relativeSampleIndex] >= timeUs) {
      retainCount--;
      relativeSampleIndex--;
      if (relativeSampleIndex == -1) {
        relativeSampleIndex = capacity - 1;
      }
    }
    discardUpstreamSamples(absoluteFirstIndex + retainCount);
    return true;
  }

  // Internal methods.

  /**
   * Finds the sample in the specified range that's before or at the specified time. If
   * {@code keyframe} is {@code true} then the sample is additionally required to be a keyframe.
   *
   * @param relativeStartIndex The relative index from which to start searching.
   * @param length The length of the range being searched.
   * @param timeUs The specified time.
   * @param keyframe Whether only keyframes should be considered.
   * @return The offset from {@code relativeFirstIndex} to the found sample, or -1 if no matching
   *     sample was found.
   */
  private int findSampleBefore(int relativeStartIndex, int length, long timeUs, boolean keyframe) {
    // This could be optimized to use a binary search, however in practice callers to this method
    // normally pass times near to the start of the search region. Hence it's unclear whether
    // switching to a binary search would yield any real benefit.
    int sampleCountToTarget = -1;
    int searchIndex = relativeStartIndex;
    for (int i = 0; i < length && timesUs[searchIndex] <= timeUs; i++) {
      if (!keyframe || (flags[searchIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        // We've found a suitable sample.
        sampleCountToTarget = i;
      }
      searchIndex++;
      if (searchIndex == capacity) {
        searchIndex = 0;
      }
    }
    return sampleCountToTarget;
  }

  /**
   * Discards the specified number of samples.
   *
   * @param discardCount The number of samples to discard.
   * @return The corresponding offset up to which data should be discarded.
   */
  private long discardSamples(int discardCount) {
    largestDiscardedTimestampUs = Math.max(largestDiscardedTimestampUs,
        getLargestTimestamp(discardCount));
    length -= discardCount;
    absoluteFirstIndex += discardCount;
    relativeFirstIndex += discardCount;
    if (relativeFirstIndex >= capacity) {
      relativeFirstIndex -= capacity;
    }
    readPosition -= discardCount;
    if (readPosition < 0) {
      readPosition = 0;
    }
    if (length == 0) {
      int relativeLastDiscardIndex = (relativeFirstIndex == 0 ? capacity : relativeFirstIndex) - 1;
      return offsets[relativeLastDiscardIndex] + sizes[relativeLastDiscardIndex];
    } else {
      return offsets[relativeFirstIndex];
    }
  }

  /**
   * Finds the largest timestamp of any sample from the start of the queue up to the specified
   * length, assuming that the timestamps prior to a keyframe are always less than the timestamp of
   * the keyframe itself, and of subsequent frames.
   *
   * @param length The length of the range being searched.
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if {@code length == 0}.
   */
  private long getLargestTimestamp(int length) {
    if (length == 0) {
      return Long.MIN_VALUE;
    }
    long largestTimestampUs = Long.MIN_VALUE;
    int relativeSampleIndex = getRelativeIndex(length - 1);
    for (int i = 0; i < length; i++) {
      largestTimestampUs = Math.max(largestTimestampUs, timesUs[relativeSampleIndex]);
      if ((flags[relativeSampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        break;
      }
      relativeSampleIndex--;
      if (relativeSampleIndex == -1) {
        relativeSampleIndex = capacity - 1;
      }
    }
    return largestTimestampUs;
  }

   /**
    * Returns the relative index for a given offset from the start of the queue.
    *
    * @param offset The offset, which must be in the range [0, length].
    */
  private int getRelativeIndex(int offset) {
    int relativeIndex = relativeFirstIndex + offset;
    return relativeIndex < capacity ? relativeIndex : relativeIndex - capacity;
  }

}
