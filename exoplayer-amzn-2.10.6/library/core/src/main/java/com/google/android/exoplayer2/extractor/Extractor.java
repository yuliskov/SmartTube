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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Extracts media data from a container format.
 */
public interface Extractor {

  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the {@link ExtractorInput} passed
   * to the next {@link #read(ExtractorInput, PositionHolder)} is required to provide data
   * continuing from the position in the stream reached by the returning call.
   */
  int RESULT_CONTINUE = 0;
  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the {@link ExtractorInput} passed
   * to the next {@link #read(ExtractorInput, PositionHolder)} is required to provide data starting
   * from a specified position in the stream.
   */
  int RESULT_SEEK = 1;
  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the end of the
   * {@link ExtractorInput} was reached. Equal to {@link C#RESULT_END_OF_INPUT}.
   */
  int RESULT_END_OF_INPUT = C.RESULT_END_OF_INPUT;

  /**
   * Result values that can be returned by {@link #read(ExtractorInput, PositionHolder)}. One of
   * {@link #RESULT_CONTINUE}, {@link #RESULT_SEEK} or {@link #RESULT_END_OF_INPUT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {RESULT_CONTINUE, RESULT_SEEK, RESULT_END_OF_INPUT})
  @interface ReadResult {}

  /**
   * Returns whether this extractor can extract samples from the {@link ExtractorInput}, which must
   * provide data from the start of the stream.
   * <p>
   * If {@code true} is returned, the {@code input}'s reading position may have been modified.
   * Otherwise, only its peek position may have been modified.
   *
   * @param input The {@link ExtractorInput} from which data should be peeked/read.
   * @return Whether this extractor can read the provided input.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  boolean sniff(ExtractorInput input) throws IOException, InterruptedException;

  /**
   * Initializes the extractor with an {@link ExtractorOutput}. Called at most once.
   *
   * @param output An {@link ExtractorOutput} to receive extracted data.
   */
  void init(ExtractorOutput output);

  /**
   * Extracts data read from a provided {@link ExtractorInput}. Must not be called before {@link
   * #init(ExtractorOutput)}.
   *
   * <p>A single call to this method will block until some progress has been made, but will not
   * block for longer than this. Hence each call will consume only a small amount of input data.
   *
   * <p>In the common case, {@link #RESULT_CONTINUE} is returned to indicate that the {@link
   * ExtractorInput} passed to the next read is required to provide data continuing from the
   * position in the stream reached by the returning call. If the extractor requires data to be
   * provided from a different position, then that position is set in {@code seekPosition} and
   * {@link #RESULT_SEEK} is returned. If the extractor reached the end of the data provided by the
   * {@link ExtractorInput}, then {@link #RESULT_END_OF_INPUT} is returned.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPosition If {@link #RESULT_SEEK} is returned, this holder is updated to hold the
   *     position of the required data.
   * @return One of the {@code RESULT_} values defined in this interface.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  @ReadResult
  int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException;

  /**
   * Notifies the extractor that a seek has occurred.
   * <p>
   * Following a call to this method, the {@link ExtractorInput} passed to the next invocation of
   * {@link #read(ExtractorInput, PositionHolder)} is required to provide data starting from {@code
   * position} in the stream. Valid random access positions are the start of the stream and
   * positions that can be obtained from any {@link SeekMap} passed to the {@link ExtractorOutput}.
   *
   * @param position The byte offset in the stream from which data will be provided.
   * @param timeUs The seek time in microseconds.
   */
  void seek(long position, long timeUs);

  /**
   * Releases all kept resources.
   */
  void release();

}
