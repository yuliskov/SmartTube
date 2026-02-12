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
package com.google.android.exoplayer2.extractor.flv;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Extracts individual samples from FLV tags, preserving original order.
 */
/* package */ abstract class TagPayloadReader {

  /**
   * Thrown when the format is not supported.
   */
  public static final class UnsupportedFormatException extends ParserException {

    public UnsupportedFormatException(String msg) {
      super(msg);
    }

  }

  protected final TrackOutput output;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected TagPayloadReader(TrackOutput output) {
    this.output = output;
  }

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, long)} will not be a continuation of the data that
   * was previously passed. Hence the reader should reset any internal state.
   */
  public abstract void seek();

  /**
   * Consumes payload data.
   *
   * @param data The payload data to consume.
   * @param timeUs The timestamp associated with the payload.
   * @return Whether a sample was output.
   * @throws ParserException If an error occurs parsing the data.
   */
  public final boolean consume(ParsableByteArray data, long timeUs) throws ParserException {
    return parseHeader(data) && parsePayload(data, timeUs);
  }

  /**
   * Parses tag header.
   *
   * @param data Buffer where the tag header is stored.
   * @return Whether the header was parsed successfully.
   * @throws ParserException If an error occurs parsing the header.
   */
  protected abstract boolean parseHeader(ParsableByteArray data) throws ParserException;

  /**
   * Parses tag payload.
   *
   * @param data Buffer where tag payload is stored.
   * @param timeUs Time position of the frame.
   * @return Whether a sample was output.
   * @throws ParserException If an error occurs parsing the payload.
   */
  protected abstract boolean parsePayload(ParsableByteArray data, long timeUs)
      throws ParserException;
}
