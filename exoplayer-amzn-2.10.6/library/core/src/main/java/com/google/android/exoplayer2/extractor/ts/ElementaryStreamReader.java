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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Extracts individual samples from an elementary media stream, preserving original order.
 */
public interface ElementaryStreamReader {

  /**
   * Notifies the reader that a seek has occurred.
   */
  void seek();

  /**
   * Initializes the reader by providing outputs and ids for the tracks.
   *
   * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
   * @param idGenerator A {@link PesReader.TrackIdGenerator} that generates unique track ids for the
   *     {@link TrackOutput}s.
   */
  void createTracks(ExtractorOutput extractorOutput, PesReader.TrackIdGenerator idGenerator);

  /**
   * Called when a packet starts.
   *
   * @param pesTimeUs The timestamp associated with the packet.
   * @param flags See {@link TsPayloadReader.Flags}.
   */
  void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags);

  /**
   * Consumes (possibly partial) data from the current packet.
   *
   * @param data The data to consume.
   * @throws ParserException If the data could not be parsed.
   */
  void consume(ParsableByteArray data) throws ParserException;

  /**
   * Called when a packet ends.
   */
  void packetFinished();

}
