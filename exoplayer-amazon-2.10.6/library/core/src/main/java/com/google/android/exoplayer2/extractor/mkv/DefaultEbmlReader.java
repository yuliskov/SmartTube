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
package com.google.android.exoplayer2.extractor.mkv;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.Assertions;
import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;

/**
 * Default implementation of {@link EbmlReader}.
 */
/* package */ final class DefaultEbmlReader implements EbmlReader {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ELEMENT_STATE_READ_ID, ELEMENT_STATE_READ_CONTENT_SIZE, ELEMENT_STATE_READ_CONTENT})
  private @interface ElementState {}

  private static final int ELEMENT_STATE_READ_ID = 0;
  private static final int ELEMENT_STATE_READ_CONTENT_SIZE = 1;
  private static final int ELEMENT_STATE_READ_CONTENT = 2;

  private static final int MAX_ID_BYTES = 4;
  private static final int MAX_LENGTH_BYTES = 8;

  private static final int MAX_INTEGER_ELEMENT_SIZE_BYTES = 8;
  private static final int VALID_FLOAT32_ELEMENT_SIZE_BYTES = 4;
  private static final int VALID_FLOAT64_ELEMENT_SIZE_BYTES = 8;

  private final byte[] scratch;
  private final ArrayDeque<MasterElement> masterElementsStack;
  private final VarintReader varintReader;

  private EbmlProcessor processor;
  private @ElementState int elementState;
  private int elementId;
  private long elementContentSize;

  public DefaultEbmlReader() {
    scratch = new byte[8];
    masterElementsStack = new ArrayDeque<>();
    varintReader = new VarintReader();
  }

  @Override
  public void init(EbmlProcessor processor) {
    this.processor = processor;
  }

  @Override
  public void reset() {
    elementState = ELEMENT_STATE_READ_ID;
    masterElementsStack.clear();
    varintReader.reset();
  }

  @Override
  public boolean read(ExtractorInput input) throws IOException, InterruptedException {
    Assertions.checkNotNull(processor);
    while (true) {
      if (!masterElementsStack.isEmpty()
          && input.getPosition() >= masterElementsStack.peek().elementEndPosition) {
        processor.endMasterElement(masterElementsStack.pop().elementId);
        return true;
      }

      if (elementState == ELEMENT_STATE_READ_ID) {
        long result = varintReader.readUnsignedVarint(input, true, false, MAX_ID_BYTES);
        if (result == C.RESULT_MAX_LENGTH_EXCEEDED) {
          result = maybeResyncToNextLevel1Element(input);
        }
        if (result == C.RESULT_END_OF_INPUT) {
          return false;
        }
        // Element IDs are at most 4 bytes, so we can cast to integers.
        elementId = (int) result;
        elementState = ELEMENT_STATE_READ_CONTENT_SIZE;
      }

      if (elementState == ELEMENT_STATE_READ_CONTENT_SIZE) {
        elementContentSize = varintReader.readUnsignedVarint(input, false, true, MAX_LENGTH_BYTES);
        elementState = ELEMENT_STATE_READ_CONTENT;
      }

      @EbmlProcessor.ElementType int type = processor.getElementType(elementId);
      switch (type) {
        case EbmlProcessor.ELEMENT_TYPE_MASTER:
          long elementContentPosition = input.getPosition();
          long elementEndPosition = elementContentPosition + elementContentSize;
          masterElementsStack.push(new MasterElement(elementId, elementEndPosition));
          processor.startMasterElement(elementId, elementContentPosition, elementContentSize);
          elementState = ELEMENT_STATE_READ_ID;
          return true;
        case EbmlProcessor.ELEMENT_TYPE_UNSIGNED_INT:
          if (elementContentSize > MAX_INTEGER_ELEMENT_SIZE_BYTES) {
            throw new ParserException("Invalid integer size: " + elementContentSize);
          }
          processor.integerElement(elementId, readInteger(input, (int) elementContentSize));
          elementState = ELEMENT_STATE_READ_ID;
          return true;
        case EbmlProcessor.ELEMENT_TYPE_FLOAT:
          if (elementContentSize != VALID_FLOAT32_ELEMENT_SIZE_BYTES
              && elementContentSize != VALID_FLOAT64_ELEMENT_SIZE_BYTES) {
            throw new ParserException("Invalid float size: " + elementContentSize);
          }
          processor.floatElement(elementId, readFloat(input, (int) elementContentSize));
          elementState = ELEMENT_STATE_READ_ID;
          return true;
        case EbmlProcessor.ELEMENT_TYPE_STRING:
          if (elementContentSize > Integer.MAX_VALUE) {
            throw new ParserException("String element size: " + elementContentSize);
          }
          processor.stringElement(elementId, readString(input, (int) elementContentSize));
          elementState = ELEMENT_STATE_READ_ID;
          return true;
        case EbmlProcessor.ELEMENT_TYPE_BINARY:
          processor.binaryElement(elementId, (int) elementContentSize, input);
          elementState = ELEMENT_STATE_READ_ID;
          return true;
        case EbmlProcessor.ELEMENT_TYPE_UNKNOWN:
          input.skipFully((int) elementContentSize);
          elementState = ELEMENT_STATE_READ_ID;
          break;
        default:
          throw new ParserException("Invalid element type " + type);
      }
    }
  }

  /**
   * Does a byte by byte search to try and find the next level 1 element. This method is called if
   * some invalid data is encountered in the parser.
   *
   * @param input The {@link ExtractorInput} from which data has to be read.
   * @return id of the next level 1 element that has been found.
   * @throws EOFException If the end of input was encountered when searching for the next level 1
   *     element.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private long maybeResyncToNextLevel1Element(ExtractorInput input) throws IOException,
      InterruptedException {
    input.resetPeekPosition();
    while (true) {
      input.peekFully(scratch, 0, MAX_ID_BYTES);
      int varintLength = VarintReader.parseUnsignedVarintLength(scratch[0]);
      if (varintLength != C.LENGTH_UNSET && varintLength <= MAX_ID_BYTES) {
        int potentialId = (int) VarintReader.assembleVarint(scratch, varintLength, false);
        if (processor.isLevel1Element(potentialId)) {
          input.skipFully(varintLength);
          return potentialId;
        }
      }
      input.skipFully(1);
    }
  }

  /**
   * Reads and returns an integer of length {@code byteLength} from the {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @param byteLength The length of the integer being read.
   * @return The read integer value.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private long readInteger(ExtractorInput input, int byteLength)
      throws IOException, InterruptedException {
    input.readFully(scratch, 0, byteLength);
    long value = 0;
    for (int i = 0; i < byteLength; i++) {
      value = (value << 8) | (scratch[i] & 0xFF);
    }
    return value;
  }

  /**
   * Reads and returns a float of length {@code byteLength} from the {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @param byteLength The length of the float being read.
   * @return The read float value.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private double readFloat(ExtractorInput input, int byteLength)
      throws IOException, InterruptedException {
    long integerValue = readInteger(input, byteLength);
    double floatValue;
    if (byteLength == VALID_FLOAT32_ELEMENT_SIZE_BYTES) {
      floatValue = Float.intBitsToFloat((int) integerValue);
    } else {
      floatValue = Double.longBitsToDouble(integerValue);
    }
    return floatValue;
  }

  /**
   * Reads a string of length {@code byteLength} from the {@link ExtractorInput}. Zero padding is
   * removed, so the returned string may be shorter than {@code byteLength}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @param byteLength The length of the string being read, including zero padding.
   * @return The read string value.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private String readString(ExtractorInput input, int byteLength)
      throws IOException, InterruptedException {
    if (byteLength == 0) {
      return "";
    }
    byte[] stringBytes = new byte[byteLength];
    input.readFully(stringBytes, 0, byteLength);
    // Remove zero padding.
    int trimmedLength = byteLength;
    while (trimmedLength > 0 && stringBytes[trimmedLength - 1] == 0) {
      trimmedLength--;
    }
    return new String(stringBytes, 0, trimmedLength);
  }

  /**
   * Used in {@link #masterElementsStack} to track when the current master element ends, so that
   * {@link EbmlProcessor#endMasterElement(int)} can be called.
   */
  private static final class MasterElement {

    private final int elementId;
    private final long elementEndPosition;

    private MasterElement(int elementId, long elementEndPosition) {
      this.elementId = elementId;
      this.elementEndPosition = elementEndPosition;
    }

  }

}
