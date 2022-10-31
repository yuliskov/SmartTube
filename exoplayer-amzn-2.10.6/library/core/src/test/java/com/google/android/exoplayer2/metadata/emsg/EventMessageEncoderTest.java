/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.metadata.emsg;

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;
import static com.google.android.exoplayer2.testutil.TestUtil.joinByteArrays;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link EventMessageEncoder}. */
@RunWith(AndroidJUnit4.class)
public final class EventMessageEncoderTest {

  private static final EventMessage DECODED_MESSAGE =
      new EventMessage("urn:test", "123", 3000, 1000403, new byte[] {0, 1, 2, 3, 4});

  private static final byte[] ENCODED_MESSAGE =
      joinByteArrays(
          createByteArray(117, 114, 110, 58, 116, 101, 115, 116, 0), // scheme_id_uri = "urn:test"
          createByteArray(49, 50, 51, 0), // value = "123"
          createByteArray(0, 0, 11, 184), // event_duration_ms = 3000
          createByteArray(0, 15, 67, 211), // id = 1000403
          createByteArray(0, 1, 2, 3, 4)); // message_data = {0, 1, 2, 3, 4}

  @Test
  public void testEncodeEventStream() throws IOException {
    byte[] foo = new byte[] {1, 2, 3};

    byte[] encodedByteArray = new EventMessageEncoder().encode(DECODED_MESSAGE);
    assertThat(encodedByteArray).isEqualTo(ENCODED_MESSAGE);
  }

  @Test
  public void testEncodeDecodeEventStream() throws IOException {
    byte[] encodedByteArray = new EventMessageEncoder().encode(DECODED_MESSAGE);
    MetadataInputBuffer buffer = new MetadataInputBuffer();
    buffer.data = ByteBuffer.allocate(encodedByteArray.length).put(encodedByteArray);

    EventMessageDecoder decoder = new EventMessageDecoder();
    Metadata metadata = decoder.decode(buffer);
    assertThat(metadata.length()).isEqualTo(1);
    assertThat(metadata.get(0)).isEqualTo(DECODED_MESSAGE);
  }

  @Test
  public void testEncodeEventStreamMultipleTimesWorkingCorrectly() throws IOException {
    EventMessage eventMessage1 =
        new EventMessage("urn:test", "123", 3000, 1000402, new byte[] {4, 3, 2, 1, 0});
    byte[] expectedEmsgBody1 =
        joinByteArrays(
            createByteArray(117, 114, 110, 58, 116, 101, 115, 116, 0), // scheme_id_uri = "urn:test"
            createByteArray(49, 50, 51, 0), // value = "123"
            createByteArray(0, 0, 11, 184), // event_duration_ms = 3000
            createByteArray(0, 15, 67, 210), // id = 1000402
            createByteArray(4, 3, 2, 1, 0)); // message_data = {4, 3, 2, 1, 0}

    EventMessageEncoder eventMessageEncoder = new EventMessageEncoder();
    byte[] encodedByteArray = eventMessageEncoder.encode(DECODED_MESSAGE);
    assertThat(encodedByteArray).isEqualTo(ENCODED_MESSAGE);
    byte[] encodedByteArray1 = eventMessageEncoder.encode(eventMessage1);
    assertThat(encodedByteArray1).isEqualTo(expectedEmsgBody1);
  }

}
