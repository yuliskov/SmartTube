/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mkv;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines EBML element IDs/types and processes events. */
public interface EbmlProcessor {

  /**
   * EBML element types. One of {@link #ELEMENT_TYPE_UNKNOWN}, {@link #ELEMENT_TYPE_MASTER}, {@link
   * #ELEMENT_TYPE_UNSIGNED_INT}, {@link #ELEMENT_TYPE_STRING}, {@link #ELEMENT_TYPE_BINARY} or
   * {@link #ELEMENT_TYPE_FLOAT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ELEMENT_TYPE_UNKNOWN,
    ELEMENT_TYPE_MASTER,
    ELEMENT_TYPE_UNSIGNED_INT,
    ELEMENT_TYPE_STRING,
    ELEMENT_TYPE_BINARY,
    ELEMENT_TYPE_FLOAT
  })
  @interface ElementType {}
  /** Type for unknown elements. */
  int ELEMENT_TYPE_UNKNOWN = 0;
  /** Type for elements that contain child elements. */
  int ELEMENT_TYPE_MASTER = 1;
  /** Type for integer value elements of up to 8 bytes. */
  int ELEMENT_TYPE_UNSIGNED_INT = 2;
  /** Type for string elements. */
  int ELEMENT_TYPE_STRING = 3;
  /** Type for binary elements. */
  int ELEMENT_TYPE_BINARY = 4;
  /** Type for IEEE floating point value elements of either 4 or 8 bytes. */
  int ELEMENT_TYPE_FLOAT = 5;

  /**
   * Maps an element ID to a corresponding type.
   *
   * <p>If {@link #ELEMENT_TYPE_UNKNOWN} is returned then the element is skipped. Note that all
   * children of a skipped element are also skipped.
   *
   * @param id The element ID to map.
   * @return One of {@link #ELEMENT_TYPE_UNKNOWN}, {@link #ELEMENT_TYPE_MASTER}, {@link
   *     #ELEMENT_TYPE_UNSIGNED_INT}, {@link #ELEMENT_TYPE_STRING}, {@link #ELEMENT_TYPE_BINARY} and
   *     {@link #ELEMENT_TYPE_FLOAT}.
   */
  @ElementType
  int getElementType(int id);

  /**
   * Checks if the given id is that of a level 1 element.
   *
   * @param id The element ID.
   * @return Whether the given id is that of a level 1 element.
   */
  boolean isLevel1Element(int id);

  /**
   * Called when the start of a master element is encountered.
   * <p>
   * Following events should be considered as taking place within this element until a matching call
   * to {@link #endMasterElement(int)} is made.
   * <p>
   * Note that it is possible for another master element of the same element ID to be nested within
   * itself.
   *
   * @param id The element ID.
   * @param contentPosition The position of the start of the element's content in the stream.
   * @param contentSize The size of the element's content in bytes.
   * @throws ParserException If a parsing error occurs.
   */
  void startMasterElement(int id, long contentPosition, long contentSize) throws ParserException;

  /**
   * Called when the end of a master element is encountered.
   *
   * @param id The element ID.
   * @throws ParserException If a parsing error occurs.
   */
  void endMasterElement(int id) throws ParserException;

  /**
   * Called when an integer element is encountered.
   *
   * @param id The element ID.
   * @param value The integer value that the element contains.
   * @throws ParserException If a parsing error occurs.
   */
  void integerElement(int id, long value) throws ParserException;

  /**
   * Called when a float element is encountered.
   *
   * @param id The element ID.
   * @param value The float value that the element contains
   * @throws ParserException If a parsing error occurs.
   */
  void floatElement(int id, double value) throws ParserException;

  /**
   * Called when a string element is encountered.
   *
   * @param id The element ID.
   * @param value The string value that the element contains.
   * @throws ParserException If a parsing error occurs.
   */
  void stringElement(int id, String value) throws ParserException;

  /**
   * Called when a binary element is encountered.
   * <p>
   * The element header (containing the element ID and content size) will already have been read.
   * Implementations are required to consume the whole remainder of the element, which is
   * {@code contentSize} bytes in length, before returning. Implementations are permitted to fail
   * (by throwing an exception) having partially consumed the data, however if they do this, they
   * must consume the remainder of the content when called again.
   *
   * @param id The element ID.
   * @param contentsSize The element's content size.
   * @param input The {@link ExtractorInput} from which data should be read.
   * @throws ParserException If a parsing error occurs.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void binaryElement(int id, int contentsSize, ExtractorInput input)
      throws IOException, InterruptedException;

}
