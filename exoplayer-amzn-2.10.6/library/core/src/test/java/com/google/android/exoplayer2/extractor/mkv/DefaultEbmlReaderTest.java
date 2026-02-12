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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link DefaultEbmlReader}. */
@RunWith(AndroidJUnit4.class)
public class DefaultEbmlReaderTest {

  @Test
  public void testMasterElement() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x1A, 0x45, 0xDF, 0xA3, 0x84, 0x42, 0x85, 0x81, 0x01);
    TestProcessor expected = new TestProcessor();
    expected.startMasterElement(TestProcessor.ID_EBML, 5, 4);
    expected.integerElement(TestProcessor.ID_DOC_TYPE_READ_VERSION, 1);
    expected.endMasterElement(TestProcessor.ID_EBML);
    assertEvents(input, expected.events);
  }

  @Test
  public void testMasterElementEmpty() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x18, 0x53, 0x80, 0x67, 0x80);
    TestProcessor expected = new TestProcessor();
    expected.startMasterElement(TestProcessor.ID_SEGMENT, 5, 0);
    expected.endMasterElement(TestProcessor.ID_SEGMENT);
    assertEvents(input, expected.events);
  }

  @Test
  public void testUnsignedIntegerElement() throws IOException, InterruptedException {
    // 0xFE is chosen because for signed integers it should be interpreted as -2
    ExtractorInput input = createTestInput(0x42, 0xF7, 0x81, 0xFE);
    TestProcessor expected = new TestProcessor();
    expected.integerElement(TestProcessor.ID_EBML_READ_VERSION, 254);
    assertEvents(input, expected.events);
  }

  @Test
  public void testUnsignedIntegerElementLarge() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x42, 0xF7, 0x88, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    TestProcessor expected = new TestProcessor();
    expected.integerElement(TestProcessor.ID_EBML_READ_VERSION, Long.MAX_VALUE);
    assertEvents(input, expected.events);
  }

  @Test
  public void testUnsignedIntegerElementTooLargeBecomesNegative()
      throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x42, 0xF7, 0x88, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    TestProcessor expected = new TestProcessor();
    expected.integerElement(TestProcessor.ID_EBML_READ_VERSION, -1);
    assertEvents(input, expected.events);
  }

  @Test
  public void testStringElement() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x42, 0x82, 0x86, 0x41, 0x62, 0x63, 0x31, 0x32, 0x33);
    TestProcessor expected = new TestProcessor();
    expected.stringElement(TestProcessor.ID_DOC_TYPE, "Abc123");
    assertEvents(input, expected.events);
  }

  @Test
  public void testStringElementWithZeroPadding() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x42, 0x82, 0x86, 0x41, 0x62, 0x63, 0x00, 0x00, 0x00);
    TestProcessor expected = new TestProcessor();
    expected.stringElement(TestProcessor.ID_DOC_TYPE, "Abc");
    assertEvents(input, expected.events);
  }

  @Test
  public void testStringElementEmpty() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x42, 0x82, 0x80);
    TestProcessor expected = new TestProcessor();
    expected.stringElement(TestProcessor.ID_DOC_TYPE, "");
    assertEvents(input, expected.events);
  }

  @Test
  public void testFloatElementFourBytes() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x44, 0x89, 0x84, 0x3F, 0x80, 0x00, 0x00);
    TestProcessor expected = new TestProcessor();
    expected.floatElement(TestProcessor.ID_DURATION, 1.0);
    assertEvents(input, expected.events);
  }

  @Test
  public void testFloatElementEightBytes() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x44, 0x89, 0x88, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    TestProcessor expected = new TestProcessor();
    expected.floatElement(TestProcessor.ID_DURATION, -2.0);
    assertEvents(input, expected.events);
  }

  @Test
  public void testBinaryElement() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0xA3, 0x88, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
    TestProcessor expected = new TestProcessor();
    expected.binaryElement(
        TestProcessor.ID_SIMPLE_BLOCK,
        8,
        createTestInput(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08));
    assertEvents(input, expected.events);
  }

  private static void assertEvents(ExtractorInput input, List<String> expectedEvents)
      throws IOException, InterruptedException {
    DefaultEbmlReader reader = new DefaultEbmlReader();
    TestProcessor output = new TestProcessor();
    reader.init(output);

    // We expect the number of successful reads to equal the number of expected events.
    for (int i = 0; i < expectedEvents.size(); i++) {
      assertThat(reader.read(input)).isTrue();
    }
    // The next read should be unsuccessful.
    assertThat(reader.read(input)).isFalse();
    // Check that we really did get to the end of input.
    assertThat(input.readFully(new byte[1], 0, 1, true)).isFalse();

    assertThat(output.events).containsExactlyElementsIn(expectedEvents).inOrder();
  }

  /**
   * Helper to build an {@link ExtractorInput} from byte data.
   *
   * @param data Zero or more integers with values between {@code 0x00} and {@code 0xFF}.
   * @return An {@link ExtractorInput} from which the data can be read.
   */
  private static ExtractorInput createTestInput(int... data) {
    return new FakeExtractorInput.Builder()
        .setData(TestUtil.createByteArray(data))
        .setSimulateUnknownLength(true)
        .build();
  }

  /** An {@link EbmlProcessor} that records each event callback. */
  private static final class TestProcessor implements EbmlProcessor {

    // Element IDs
    private static final int ID_EBML = 0x1A45DFA3;
    private static final int ID_EBML_READ_VERSION = 0x42F7;
    private static final int ID_DOC_TYPE = 0x4282;
    private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;

    private static final int ID_SEGMENT = 0x18538067;
    private static final int ID_DURATION = 0x4489;
    private static final int ID_SIMPLE_BLOCK = 0xA3;

    private final List<String> events = new ArrayList<>();

    @Override
    @EbmlProcessor.ElementType
    public int getElementType(int id) {
      switch (id) {
        case ID_EBML:
        case ID_SEGMENT:
          return EbmlProcessor.ELEMENT_TYPE_MASTER;
        case ID_EBML_READ_VERSION:
        case ID_DOC_TYPE_READ_VERSION:
          return EbmlProcessor.ELEMENT_TYPE_UNSIGNED_INT;
        case ID_DOC_TYPE:
          return EbmlProcessor.ELEMENT_TYPE_STRING;
        case ID_SIMPLE_BLOCK:
          return EbmlProcessor.ELEMENT_TYPE_BINARY;
        case ID_DURATION:
          return EbmlProcessor.ELEMENT_TYPE_FLOAT;
        default:
          return EbmlProcessor.ELEMENT_TYPE_UNKNOWN;
      }
    }

    @Override
    public boolean isLevel1Element(int id) {
      return false;
    }

    @Override
    public void startMasterElement(int id, long contentPosition, long contentSize) {
      events.add(formatEvent(id, "start contentPosition=" + contentPosition
          + " contentSize=" + contentSize));
    }

    @Override
    public void endMasterElement(int id) {
      events.add(formatEvent(id, "end"));
    }

    @Override
    public void integerElement(int id, long value) {
      events.add(formatEvent(id, "integer=" + value));
    }

    @Override
    public void floatElement(int id, double value) {
      events.add(formatEvent(id, "float=" + value));
    }

    @Override
    public void stringElement(int id, String value) {
      events.add(formatEvent(id, "string=" + value));
    }

    @Override
    public void binaryElement(int id, int contentSize, ExtractorInput input)
        throws IOException, InterruptedException {
      byte[] bytes = new byte[contentSize];
      input.readFully(bytes, 0, contentSize);
      events.add(formatEvent(id, "bytes=" + Arrays.toString(bytes)));
    }

    private static String formatEvent(int id, String event) {
      return "[" + Integer.toHexString(id) + "] " + event;
    }

  }

}
