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
package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A {@link SampleStream} consisting of serialized {@link EventMessage}s read from an
 * {@link EventStream}.
 */
/* package */ final class EventSampleStream implements SampleStream {

  private final Format upstreamFormat;
  private final EventMessageEncoder eventMessageEncoder;

  private long[] eventTimesUs;
  private boolean eventStreamAppendable;
  private EventStream eventStream;

  private boolean isFormatSentDownstream;
  private int currentIndex;
  private long pendingSeekPositionUs;

  public EventSampleStream(
      EventStream eventStream, Format upstreamFormat, boolean eventStreamAppendable) {
    this.upstreamFormat = upstreamFormat;
    this.eventStream = eventStream;
    eventMessageEncoder = new EventMessageEncoder();
    pendingSeekPositionUs = C.TIME_UNSET;
    eventTimesUs = eventStream.presentationTimesUs;
    updateEventStream(eventStream, eventStreamAppendable);
  }

  public String eventStreamId() {
    return eventStream.id();
  }

  public void updateEventStream(EventStream eventStream, boolean eventStreamAppendable) {
    long lastReadPositionUs = currentIndex == 0 ? C.TIME_UNSET : eventTimesUs[currentIndex - 1];

    this.eventStreamAppendable = eventStreamAppendable;
    this.eventStream = eventStream;
    this.eventTimesUs = eventStream.presentationTimesUs;
    if (pendingSeekPositionUs != C.TIME_UNSET) {
      seekToUs(pendingSeekPositionUs);
    } else if (lastReadPositionUs != C.TIME_UNSET) {
      currentIndex =
          Util.binarySearchCeil(
              eventTimesUs, lastReadPositionUs, /* inclusive= */ false, /* stayInBounds= */ false);
    }
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    currentIndex =
        Util.binarySearchCeil(
            eventTimesUs, positionUs, /* inclusive= */ true, /* stayInBounds= */ false);
    boolean isPendingSeek = eventStreamAppendable && currentIndex == eventTimesUs.length;
    pendingSeekPositionUs = isPendingSeek ? positionUs : C.TIME_UNSET;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void maybeThrowError() throws IOException {
    // Do nothing.
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (formatRequired || !isFormatSentDownstream) {
      formatHolder.format = upstreamFormat;
      isFormatSentDownstream = true;
      return C.RESULT_FORMAT_READ;
    }
    if (currentIndex == eventTimesUs.length) {
      if (!eventStreamAppendable) {
        buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      } else {
        return C.RESULT_NOTHING_READ;
      }
    }
    int sampleIndex = currentIndex++;
    byte[] serializedEvent = eventMessageEncoder.encode(eventStream.events[sampleIndex]);
    if (serializedEvent != null) {
      buffer.ensureSpaceForWrite(serializedEvent.length);
      buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
      buffer.data.put(serializedEvent);
      buffer.timeUs = eventTimesUs[sampleIndex];
      return C.RESULT_BUFFER_READ;
    } else {
      return C.RESULT_NOTHING_READ;
    }
  }

  @Override
  public int skipData(long positionUs) {
    int newIndex =
        Math.max(currentIndex, Util.binarySearchCeil(eventTimesUs, positionUs, true, false));
    int skipped = newIndex - currentIndex;
    currentIndex = newIndex;
    return skipped;
  }

}
