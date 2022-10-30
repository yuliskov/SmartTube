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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link EventSampleStream}. */
@RunWith(AndroidJUnit4.class)
public final class EventSampleStreamTest {

  private static final String SCHEME_ID = "urn:test";
  private static final String VALUE = "123";
  private static final Format FORMAT = Format.createSampleFormat("urn:test/123",
      MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null);
  private static final byte[] MESSAGE_DATA = new byte[] {1, 2, 3, 4};
  private static final long DURATION_MS = 3000;
  private static final long TIME_SCALE = 1000;

  private FormatHolder formatHolder;
  private MetadataInputBuffer inputBuffer;
  private EventMessageEncoder eventMessageEncoder;

  @Before
  public void setUp() {
    formatHolder = new FormatHolder();
    inputBuffer = new MetadataInputBuffer();
    eventMessageEncoder = new EventMessageEncoder();
  }

  /**
   * Tests that {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} will
   * return format for the first call.
   */
  @Test
  public void testReadDataReturnFormatForFirstRead() {
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[0], new EventMessage[0]);
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, false);

    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_FORMAT_READ);
    assertThat(formatHolder.format).isEqualTo(FORMAT);
  }

  /**
   * Tests that a non-dynamic {@link EventSampleStream} will return a buffer with
   * {@link C#BUFFER_FLAG_END_OF_STREAM} when trying to read sample out-of-bound.
   */
  @Test
  public void testReadDataOutOfBoundReturnEndOfStreamAfterFormatForNonDynamicEventSampleStream() {
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[0], new EventMessage[0]);
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, false);
    // first read - read format
    readData(sampleStream);

    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.isEndOfStream()).isTrue();
  }

  /**
   * Tests that a dynamic {@link EventSampleStream} will return {@link C#RESULT_NOTHING_READ}
   * when trying to read sample out-of-bound.
   */
  @Test
  public void testReadDataOutOfBoundReturnEndOfStreamAfterFormatForDynamicEventSampleStream() {
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[0], new EventMessage[0]);
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, true);
    // first read - read format
    readData(sampleStream);

    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_NOTHING_READ);
  }

  /**
   * Tests that {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} will
   * return sample data after the first call.
   */
  @Test
  public void testReadDataReturnDataAfterFormat() {
    long presentationTimeUs = 1000000;
    EventMessage eventMessage = newEventMessageWithId(1);
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs}, new EventMessage[] {eventMessage});
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, false);
    // first read - read format
    readData(sampleStream);

    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage));
  }

  /**
   * Tests that {@link EventSampleStream#skipData(long)} will skip until the given position, and
   * the next {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from that position.
   */
  @Test
  public void testSkipDataThenReadDataReturnDataFromSkippedPosition() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2},
        new EventMessage[] {eventMessage1, eventMessage2});
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, false);
    // first read - read format
    readData(sampleStream);

    int skipped = sampleStream.skipData(presentationTimeUs2);
    int result = readData(sampleStream);
    assertThat(skipped).isEqualTo(1);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage2));
  }

  /**
   * Tests that {@link EventSampleStream#seekToUs(long)} (long)} will seek to the given position,
   * and the next {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from that position.
   */
  @Test
  public void testSeekToUsThenReadDataReturnDataFromSeekPosition() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventStream eventStream = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2},
        new EventMessage[] {eventMessage1, eventMessage2});
    EventSampleStream sampleStream = new EventSampleStream(eventStream, FORMAT, false);
    // first read - read format
    readData(sampleStream);

    sampleStream.seekToUs(presentationTimeUs2);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage2));
  }

  /**
   * Tests that {@link EventSampleStream#updateEventStream(EventStream, boolean)} will update the
   * underlying event stream, but keep the read timestamp, so the next
   * {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from after the last read sample timestamp.
   */
  @Test
  public void testUpdateEventStreamContinueToReadAfterLastReadSamplePresentationTime() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    long presentationTimeUs3 = 3000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventMessage eventMessage3 = newEventMessageWithId(3);
    EventStream eventStream1 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2},
        new EventMessage[] {eventMessage1, eventMessage2});
    EventStream eventStream2 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2, presentationTimeUs3},
        new EventMessage[] {eventMessage1, eventMessage2, eventMessage3});
    EventSampleStream sampleStream = new EventSampleStream(eventStream1, FORMAT, true);
    // first read - read format
    readData(sampleStream);
    // read first and second sample.
    readData(sampleStream);
    readData(sampleStream);

    sampleStream.updateEventStream(eventStream2, true);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage3));
  }

  /**
   * Tests that {@link EventSampleStream#updateEventStream(EventStream, boolean)} will update the
   * underlying event stream, but keep the timestamp the stream has skipped to, so the next
   * {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from the skipped position.
   */
  @Test
  public void testSkipDataThenUpdateStreamContinueToReadFromSkippedPosition() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    long presentationTimeUs3 = 3000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventMessage eventMessage3 = newEventMessageWithId(3);
    EventStream eventStream1 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2},
        new EventMessage[] {eventMessage1, eventMessage2});
    EventStream eventStream2 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2, presentationTimeUs3},
        new EventMessage[] {eventMessage1, eventMessage2, eventMessage3});
    EventSampleStream sampleStream = new EventSampleStream(eventStream1, FORMAT, true);
    // first read - read format
    readData(sampleStream);
    sampleStream.skipData(presentationTimeUs2 + 1);

    sampleStream.updateEventStream(eventStream2, true);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage3));
  }

  /**
   * Tests that {@link EventSampleStream#skipData(long)} will only skip to the point right after
   * it last event. A following {@link EventSampleStream#updateEventStream(EventStream, boolean)}
   * will update the underlying event stream and keep the timestamp the stream has skipped to, so
   * the next {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from the skipped position.
   */
  @Test
  public void testSkipDataThenUpdateStreamContinueToReadDoNotSkippedMoreThanAvailable() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    long presentationTimeUs3 = 3000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventMessage eventMessage3 = newEventMessageWithId(3);
    EventStream eventStream1 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1},
        new EventMessage[] {eventMessage1});
    EventStream eventStream2 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2, presentationTimeUs3},
        new EventMessage[] {eventMessage1, eventMessage2, eventMessage3});
    EventSampleStream sampleStream = new EventSampleStream(eventStream1, FORMAT, true);
    // first read - read format
    readData(sampleStream);
    // even though the skip call is to 2000001, since eventStream1 only contains sample until
    // 1000000, it will only skip to 1000001.
    sampleStream.skipData(presentationTimeUs2 + 1);

    sampleStream.updateEventStream(eventStream2, true);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage2));
  }

  /**
   * Tests that {@link EventSampleStream#updateEventStream(EventStream, boolean)} will update the
   * underlying event stream, but keep the timestamp the stream has seek to, so the next
   * {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from the seek position.
   */
  @Test
  public void testSeekToUsThenUpdateStreamContinueToReadFromSeekPosition() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    long presentationTimeUs3 = 3000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventMessage eventMessage3 = newEventMessageWithId(3);
    EventStream eventStream1 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2},
        new EventMessage[] {eventMessage1, eventMessage2});
    EventStream eventStream2 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2, presentationTimeUs3},
        new EventMessage[] {eventMessage1, eventMessage2, eventMessage3});
    EventSampleStream sampleStream = new EventSampleStream(eventStream1, FORMAT, true);
    // first read - read format
    readData(sampleStream);
    sampleStream.seekToUs(presentationTimeUs2);

    sampleStream.updateEventStream(eventStream2, true);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage2));
  }

  /**
   * Tests that {@link EventSampleStream#updateEventStream(EventStream, boolean)} will update the
   * underlying event stream, but keep the timestamp the stream has seek to, so the next
   * {@link EventSampleStream#readData(FormatHolder, DecoderInputBuffer, boolean)} call
   * will return sample data from the seek position.
   */
  @Test
  public void testSeekToThenUpdateStreamContinueToReadFromSeekPositionEvenSeekMoreThanAvailable() {
    long presentationTimeUs1 = 1000000;
    long presentationTimeUs2 = 2000000;
    long presentationTimeUs3 = 3000000;
    EventMessage eventMessage1 = newEventMessageWithId(1);
    EventMessage eventMessage2 = newEventMessageWithId(2);
    EventMessage eventMessage3 = newEventMessageWithId(3);
    EventStream eventStream1 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1},
        new EventMessage[] {eventMessage1});
    EventStream eventStream2 = new EventStream(SCHEME_ID, VALUE, TIME_SCALE,
        new long[] {presentationTimeUs1, presentationTimeUs2, presentationTimeUs3},
        new EventMessage[] {eventMessage1, eventMessage2, eventMessage3});
    EventSampleStream sampleStream = new EventSampleStream(eventStream1, FORMAT, true);
    // first read - read format
    readData(sampleStream);
    sampleStream.seekToUs(presentationTimeUs2 + 1);

    sampleStream.updateEventStream(eventStream2, true);
    int result = readData(sampleStream);
    assertThat(result).isEqualTo(C.RESULT_BUFFER_READ);
    assertThat(inputBuffer.data.array())
        .isEqualTo(getEncodedMessage(eventMessage3));
  }

  private int readData(EventSampleStream sampleStream) {
    inputBuffer.clear();
    return sampleStream.readData(formatHolder, inputBuffer, false);
  }

  private EventMessage newEventMessageWithId(int id) {
    return new EventMessage(SCHEME_ID, VALUE, DURATION_MS, id, MESSAGE_DATA);
  }

  private byte[] getEncodedMessage(EventMessage eventMessage) {
    return eventMessageEncoder.encode(eventMessage);
  }

}
