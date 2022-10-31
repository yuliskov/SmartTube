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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.SampleMetadataQueue.SampleExtrasHolder;
import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

/** A queue of media samples. */
public class SampleQueue implements TrackOutput {

  /**
   * A listener for changes to the upstream format.
   */
  public interface UpstreamFormatChangedListener {

    /**
     * Called on the loading thread when an upstream format change occurs.
     *
     * @param format The new upstream format.
     */
    void onUpstreamFormatChanged(Format format);

  }

  public static final int ADVANCE_FAILED = -1;

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private final Allocator allocator;
  private final int allocationLength;
  private final SampleMetadataQueue metadataQueue;
  private final SampleExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;

  // References into the linked list of allocations.
  private AllocationNode firstAllocationNode;
  private AllocationNode readAllocationNode;
  private AllocationNode writeAllocationNode;

  // Accessed only by the consuming thread.
  private Format downstreamFormat;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private boolean pendingFormatAdjustment;
  private Format lastUnadjustedFormat;
  private long sampleOffsetUs;
  private long totalBytesWritten;
  private boolean pendingSplice;
  private UpstreamFormatChangedListener upstreamFormatChangeListener;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public SampleQueue(Allocator allocator) {
    this.allocator = allocator;
    allocationLength = allocator.getIndividualAllocationLength();
    metadataQueue = new SampleMetadataQueue();
    extrasHolder = new SampleExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    firstAllocationNode = new AllocationNode(0, allocationLength);
    readAllocationNode = firstAllocationNode;
    writeAllocationNode = firstAllocationNode;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Resets the output without clearing the upstream format. Equivalent to {@code reset(false)}.
   */
  public void reset() {
    reset(false);
  }

  /**
   * Resets the output.
   *
   * @param resetUpstreamFormat Whether the upstream format should be cleared. If set to false,
   *     samples queued after the reset (and before a subsequent call to {@link #format(Format)})
   *     are assumed to have the current upstream format. If set to true, {@link #format(Format)}
   *     must be called after the reset before any more samples can be queued.
   */
  public void reset(boolean resetUpstreamFormat) {
    metadataQueue.reset(resetUpstreamFormat);
    clearAllocationNodes(firstAllocationNode);
    firstAllocationNode = new AllocationNode(0, allocationLength);
    readAllocationNode = firstAllocationNode;
    writeAllocationNode = firstAllocationNode;
    totalBytesWritten = 0;
    allocator.trim();
  }

  /**
   * Sets a source identifier for subsequent samples.
   *
   * @param sourceId The source identifier.
   */
  public void sourceId(int sourceId) {
    metadataQueue.sourceId(sourceId);
  }

  /**
   * Indicates samples that are subsequently queued should be spliced into those already queued.
   */
  public void splice() {
    pendingSplice = true;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return metadataQueue.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded. Must be in the
   *     range [{@link #getReadIndex()}, {@link #getWriteIndex()}].
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    totalBytesWritten = metadataQueue.discardUpstreamSamples(discardFromIndex);
    if (totalBytesWritten == 0 || totalBytesWritten == firstAllocationNode.startPosition) {
      clearAllocationNodes(firstAllocationNode);
      firstAllocationNode = new AllocationNode(totalBytesWritten, allocationLength);
      readAllocationNode = firstAllocationNode;
      writeAllocationNode = firstAllocationNode;
    } else {
      // Find the last node containing at least 1 byte of data that we need to keep.
      AllocationNode lastNodeToKeep = firstAllocationNode;
      while (totalBytesWritten > lastNodeToKeep.endPosition) {
        lastNodeToKeep = lastNodeToKeep.next;
      }
      // Discard all subsequent nodes.
      AllocationNode firstNodeToDiscard = lastNodeToKeep.next;
      clearAllocationNodes(firstNodeToDiscard);
      // Reset the successor of the last node to be an uninitialized node.
      lastNodeToKeep.next = new AllocationNode(lastNodeToKeep.endPosition, allocationLength);
      // Update writeAllocationNode and readAllocationNode as necessary.
      writeAllocationNode = totalBytesWritten == lastNodeToKeep.endPosition ? lastNodeToKeep.next
          : lastNodeToKeep;
      if (readAllocationNode == firstNodeToDiscard) {
        readAllocationNode = lastNodeToKeep.next;
      }
    }
  }

  // Called by the consuming thread.

  /**
   * Returns whether a sample is available to be read.
   */
  public boolean hasNextSample() {
    return metadataQueue.hasNextSample();
  }

  /**
   * Returns the absolute index of the first sample.
   */
  public int getFirstIndex() {
    return metadataQueue.getFirstIndex();
  }

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return metadataQueue.getReadIndex();
  }

  /**
   * Peeks the source id of the next sample to be read, or the current upstream source id if the
   * queue is empty or if the read position is at the end of the queue.
   *
   * @return The source id.
   */
  public int peekSourceId() {
    return metadataQueue.peekSourceId();
  }

  /**
   * Returns the upstream {@link Format} in which samples are being queued.
   */
  public Format getUpstreamFormat() {
    return metadataQueue.getUpstreamFormat();
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public long getLargestQueuedTimestampUs() {
    return metadataQueue.getLargestQueuedTimestampUs();
  }

  /**
   * Returns whether the last sample of the stream has knowingly been queued. A return value of
   * {@code false} means that the last sample had not been queued or that it's unknown whether the
   * last sample has been queued.
   */
  public boolean isLastSampleQueued() {
    return metadataQueue.isLastSampleQueued();
  }

  /** Returns the timestamp of the first sample, or {@link Long#MIN_VALUE} if the queue is empty. */
  public long getFirstTimestampUs() {
    return metadataQueue.getFirstTimestampUs();
  }

  /**
   * Rewinds the read position to the first sample in the queue.
   */
  public void rewind() {
    metadataQueue.rewind();
    readAllocationNode = firstAllocationNode;
  }

  /**
   * Discards up to but not including the sample immediately before or at the specified time.
   *
   * @param timeUs The time to discard to.
   * @param toKeyframe If true then discards samples up to the keyframe before or at the specified
   *     time, rather than any sample before or at that time.
   * @param stopAtReadPosition If true then samples are only discarded if they're before the
   *     read position. If false then samples at and beyond the read position may be discarded, in
   *     which case the read position is advanced to the first remaining sample.
   */
  public void discardTo(long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
    discardDownstreamTo(metadataQueue.discardTo(timeUs, toKeyframe, stopAtReadPosition));
  }

  /**
   * Discards up to but not including the read position.
   */
  public void discardToRead() {
    discardDownstreamTo(metadataQueue.discardToRead());
  }

  /**
   * Discards to the end of the queue. The read position is also advanced.
   */
  public void discardToEnd() {
    discardDownstreamTo(metadataQueue.discardToEnd());
  }

  /**
   * Advances the read position to the end of the queue.
   *
   * @return The number of samples that were skipped.
   */
  public int advanceToEnd() {
    return metadataQueue.advanceToEnd();
  }

  /**
   * Attempts to advance the read position to the sample before or at the specified time.
   *
   * @param timeUs The time to advance to.
   * @param toKeyframe If true then attempts to advance to the keyframe before or at the specified
   *     time, rather than to any sample before or at that time.
   * @param allowTimeBeyondBuffer Whether the operation can succeed if {@code timeUs} is beyond the
   *     end of the queue, by advancing the read position to the last sample (or keyframe).
   * @return The number of samples that were skipped if the operation was successful, which may be
   *     equal to 0, or {@link #ADVANCE_FAILED} if the operation was not successful. A successful
   *     advance is one in which the read position was unchanged or advanced, and is now at a sample
   *     meeting the specified criteria.
   */
  public int advanceTo(long timeUs, boolean toKeyframe, boolean allowTimeBeyondBuffer) {
    return metadataQueue.advanceTo(timeUs, toKeyframe, allowTimeBeyondBuffer);
  }

  /**
   * Attempts to set the read position to the specified sample index.
   *
   * @param sampleIndex The sample index.
   * @return Whether the read position was set successfully. False is returned if the specified
   *     index is smaller than the index of the first sample in the queue, or larger than the index
   *     of the next sample that will be written.
   */
  public boolean setReadPosition(int sampleIndex) {
    return metadataQueue.setReadPosition(sampleIndex);
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the {@link
   *     C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer. If a {@link
   *     DecoderInputBuffer#isFlagsOnly() flags-only} buffer is passed, only the buffer flags may be
   *     populated by this method and the read position of the queue will not change.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param decodeOnlyUntilUs If a buffer is read, the {@link C#BUFFER_FLAG_DECODE_ONLY} flag will
   *     be set if the buffer's timestamp is less than this value.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  public int read(
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      boolean formatRequired,
      boolean loadingFinished,
      long decodeOnlyUntilUs) {
    int result = metadataQueue.read(formatHolder, buffer, formatRequired, loadingFinished,
        downstreamFormat, extrasHolder);
    switch (result) {
      case C.RESULT_FORMAT_READ:
        downstreamFormat = formatHolder.format;
        return C.RESULT_FORMAT_READ;
      case C.RESULT_BUFFER_READ:
        if (!buffer.isEndOfStream()) {
          if (buffer.timeUs < decodeOnlyUntilUs) {
            buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
          }
          if (!buffer.isFlagsOnly()) {
            // Read encryption data if the sample is encrypted.
            if (buffer.isEncrypted()) {
              readEncryptionData(buffer, extrasHolder);
            }
            // Write the sample data into the holder.
            buffer.ensureSpaceForWrite(extrasHolder.size);
            readData(extrasHolder.offset, buffer.data, extrasHolder.size);
          }
        }
        return C.RESULT_BUFFER_READ;
      case C.RESULT_NOTHING_READ:
        return C.RESULT_NOTHING_READ;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Reads encryption data for the current sample.
   * <p>
   * The encryption data is written into {@link DecoderInputBuffer#cryptoInfo}, and
   * {@link SampleExtrasHolder#size} is adjusted to subtract the number of bytes that were read. The
   * same value is added to {@link SampleExtrasHolder#offset}.
   *
   * @param buffer The buffer into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   */
  private void readEncryptionData(DecoderInputBuffer buffer, SampleExtrasHolder extrasHolder) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    scratch.reset(1);
    readData(offset, scratch.data, 1);
    offset++;
    byte signalByte = scratch.data[0];
    boolean subsampleEncryption = (signalByte & 0x80) != 0;
    int ivSize = signalByte & 0x7F;

    // Read the initialization vector.
    if (buffer.cryptoInfo.iv == null) {
      buffer.cryptoInfo.iv = new byte[16];
    }
    readData(offset, buffer.cryptoInfo.iv, ivSize);
    offset += ivSize;

    // Read the subsample count, if present.
    int subsampleCount;
    if (subsampleEncryption) {
      scratch.reset(2);
      readData(offset, scratch.data, 2);
      offset += 2;
      subsampleCount = scratch.readUnsignedShort();
    } else {
      subsampleCount = 1;
    }

    // Write the clear and encrypted subsample sizes.
    int[] clearDataSizes = buffer.cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    int[] encryptedDataSizes = buffer.cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      int subsampleDataLength = 6 * subsampleCount;
      scratch.reset(subsampleDataLength);
      readData(offset, scratch.data, subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = extrasHolder.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    CryptoData cryptoData = extrasHolder.cryptoData;
    buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
        cryptoData.encryptionKey, buffer.cryptoInfo.iv, cryptoData.cryptoMode,
        cryptoData.encryptedBlocks, cryptoData.clearBlocks);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    extrasHolder.size -= bytesRead;
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The buffer into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, ByteBuffer target, int length) {
    advanceReadTo(absolutePosition);
    int remaining = length;
    while (remaining > 0) {
      int toCopy = Math.min(remaining, (int) (readAllocationNode.endPosition - absolutePosition));
      Allocation allocation = readAllocationNode.allocation;
      target.put(allocation.data, readAllocationNode.translateOffset(absolutePosition), toCopy);
      remaining -= toCopy;
      absolutePosition += toCopy;
      if (absolutePosition == readAllocationNode.endPosition) {
        readAllocationNode = readAllocationNode.next;
      }
    }
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The array into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, byte[] target, int length) {
    advanceReadTo(absolutePosition);
    int remaining = length;
    while (remaining > 0) {
      int toCopy = Math.min(remaining, (int) (readAllocationNode.endPosition - absolutePosition));
      Allocation allocation = readAllocationNode.allocation;
      System.arraycopy(allocation.data, readAllocationNode.translateOffset(absolutePosition),
          target, length - remaining, toCopy);
      remaining -= toCopy;
      absolutePosition += toCopy;
      if (absolutePosition == readAllocationNode.endPosition) {
        readAllocationNode = readAllocationNode.next;
      }
    }
  }

  /**
   * Advances {@link #readAllocationNode} to the specified absolute position.
   *
   * @param absolutePosition The position to which {@link #readAllocationNode} should be advanced.
   */
  private void advanceReadTo(long absolutePosition) {
    while (absolutePosition >= readAllocationNode.endPosition) {
      readAllocationNode = readAllocationNode.next;
    }
  }

  /**
   * Advances {@link #firstAllocationNode} to the specified absolute position.
   * {@link #readAllocationNode} is also advanced if necessary to avoid it falling behind
   * {@link #firstAllocationNode}. Nodes that have been advanced past are cleared, and their
   * underlying allocations are returned to the allocator.
   *
   * @param absolutePosition The position to which {@link #firstAllocationNode} should be advanced.
   *     May be {@link C#POSITION_UNSET}, in which case calling this method is a no-op.
   */
  private void discardDownstreamTo(long absolutePosition) {
    if (absolutePosition == C.POSITION_UNSET) {
      return;
    }
    while (absolutePosition >= firstAllocationNode.endPosition) {
      allocator.release(firstAllocationNode.allocation);
      firstAllocationNode = firstAllocationNode.clear();
    }
    // If we discarded the node referenced by readAllocationNode then we need to advance it to the
    // first remaining node.
    if (readAllocationNode.startPosition < firstAllocationNode.startPosition) {
      readAllocationNode = firstAllocationNode;
    }
  }

  // Called by the loading thread.

  /**
   * Sets a listener to be notified of changes to the upstream format.
   *
   * @param listener The listener.
   */
  public void setUpstreamFormatChangeListener(UpstreamFormatChangedListener listener) {
    upstreamFormatChangeListener = listener;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * that are subsequently queued.
   *
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      pendingFormatAdjustment = true;
    }
  }

  @Override
  public void format(Format format) {
    Format adjustedFormat = getAdjustedSampleFormat(format, sampleOffsetUs);
    boolean formatChanged = metadataQueue.format(adjustedFormat);
    lastUnadjustedFormat = format;
    pendingFormatAdjustment = false;
    if (upstreamFormatChangeListener != null && formatChanged) {
      upstreamFormatChangeListener.onUpstreamFormatChanged(adjustedFormat);
    }
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    length = preAppend(length);
    int bytesAppended = input.read(writeAllocationNode.allocation.data,
        writeAllocationNode.translateOffset(totalBytesWritten), length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    postAppend(bytesAppended);
    return bytesAppended;
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    while (length > 0) {
      int bytesAppended = preAppend(length);
      buffer.readBytes(writeAllocationNode.allocation.data,
          writeAllocationNode.translateOffset(totalBytesWritten), bytesAppended);
      length -= bytesAppended;
      postAppend(bytesAppended);
    }
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    if (pendingFormatAdjustment) {
      format(lastUnadjustedFormat);
    }
    timeUs += sampleOffsetUs;
    if (pendingSplice) {
      if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0 || !metadataQueue.attemptSplice(timeUs)) {
        return;
      }
      pendingSplice = false;
    }
    long absoluteOffset = totalBytesWritten - size - offset;
    metadataQueue.commitSample(timeUs, flags, absoluteOffset, size, cryptoData);
  }

  // Private methods.

  /**
   * Clears allocation nodes starting from {@code fromNode}.
   *
   * @param fromNode The node from which to clear.
   */
  private void clearAllocationNodes(AllocationNode fromNode) {
    if (!fromNode.wasInitialized) {
      return;
    }
    // Bulk release allocations for performance (it's significantly faster when using
    // DefaultAllocator because the allocator's lock only needs to be acquired and released once)
    // [Internal: See b/29542039].
    int allocationCount = (writeAllocationNode.wasInitialized ? 1 : 0)
        + ((int) (writeAllocationNode.startPosition - fromNode.startPosition) / allocationLength);
    Allocation[] allocationsToRelease = new Allocation[allocationCount];
    AllocationNode currentNode = fromNode;
    for (int i = 0; i < allocationsToRelease.length; i++) {
      allocationsToRelease[i] = currentNode.allocation;
      currentNode = currentNode.clear();
    }
    allocator.release(allocationsToRelease);
  }

  /**
   * Called before writing sample data to {@link #writeAllocationNode}. May cause
   * {@link #writeAllocationNode} to be initialized.
   *
   * @param length The number of bytes that the caller wishes to write.
   * @return The number of bytes that the caller is permitted to write, which may be less than
   *     {@code length}.
   */
  private int preAppend(int length) {
    if (!writeAllocationNode.wasInitialized) {
      writeAllocationNode.initialize(allocator.allocate(),
          new AllocationNode(writeAllocationNode.endPosition, allocationLength));
    }
    return Math.min(length, (int) (writeAllocationNode.endPosition - totalBytesWritten));
  }

  /**
   * Called after writing sample data. May cause {@link #writeAllocationNode} to be advanced.
   *
   * @param length The number of bytes that were written.
   */
  private void postAppend(int length) {
    totalBytesWritten += length;
    if (totalBytesWritten == writeAllocationNode.endPosition) {
      writeAllocationNode = writeAllocationNode.next;
    }
  }

  /**
   * Adjusts a {@link Format} to incorporate a sample offset into {@link Format#subsampleOffsetUs}.
   *
   * @param format The {@link Format} to adjust.
   * @param sampleOffsetUs The offset to apply.
   * @return The adjusted {@link Format}.
   */
  private static Format getAdjustedSampleFormat(Format format, long sampleOffsetUs) {
    if (format == null) {
      return null;
    }
    if (sampleOffsetUs != 0 && format.subsampleOffsetUs != Format.OFFSET_SAMPLE_RELATIVE) {
      format = format.copyWithSubsampleOffsetUs(format.subsampleOffsetUs + sampleOffsetUs);
    }
    return format;
  }

  /**
   * A node in a linked list of {@link Allocation}s held by the output.
   */
  private static final class AllocationNode {

    /**
     * The absolute position of the start of the data (inclusive).
     */
    public final long startPosition;
    /**
     * The absolute position of the end of the data (exclusive).
     */
    public final long endPosition;
    /**
     * Whether the node has been initialized. Remains true after {@link #clear()}.
     */
    public boolean wasInitialized;
    /**
     * The {@link Allocation}, or {@code null} if the node is not initialized.
     */
    @Nullable public Allocation allocation;
    /**
     * The next {@link AllocationNode} in the list, or {@code null} if the node has not been
     * initialized. Remains set after {@link #clear()}.
     */
    @Nullable public AllocationNode next;

    /**
     * @param startPosition See {@link #startPosition}.
     * @param allocationLength The length of the {@link Allocation} with which this node will be
     *     initialized.
     */
    public AllocationNode(long startPosition, int allocationLength) {
      this.startPosition = startPosition;
      this.endPosition = startPosition + allocationLength;
    }

    /**
     * Initializes the node.
     *
     * @param allocation The node's {@link Allocation}.
     * @param next The next {@link AllocationNode}.
     */
    public void initialize(Allocation allocation, AllocationNode next) {
      this.allocation = allocation;
      this.next = next;
      wasInitialized = true;
    }

    /**
     * Gets the offset into the {@link #allocation}'s {@link Allocation#data} that corresponds to
     * the specified absolute position.
     *
     * @param absolutePosition The absolute position.
     * @return The corresponding offset into the allocation's data.
     */
    public int translateOffset(long absolutePosition) {
      return (int) (absolutePosition - startPosition) + allocation.offset;
    }

    /**
     * Clears {@link #allocation} and {@link #next}.
     *
     * @return The cleared next {@link AllocationNode}.
     */
    public AllocationNode clear() {
      allocation = null;
      AllocationNode temp = next;
      next = null;
      return temp;
    }

  }

}
