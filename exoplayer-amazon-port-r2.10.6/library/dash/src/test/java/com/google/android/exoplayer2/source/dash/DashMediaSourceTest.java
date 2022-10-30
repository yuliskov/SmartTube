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
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DashMediaSource}. */
@RunWith(AndroidJUnit4.class)
public final class DashMediaSourceTest {

  @Test
  public void testIso8601ParserParse() throws IOException {
    DashMediaSource.Iso8601Parser parser = new DashMediaSource.Iso8601Parser();
    // UTC.
    assertParseStringToLong(1512381697000L, parser, "2017-12-04T10:01:37Z");
    assertParseStringToLong(1512381697000L, parser, "2017-12-04T10:01:37+00:00");
    assertParseStringToLong(1512381697000L, parser, "2017-12-04T10:01:37+0000");
    assertParseStringToLong(1512381697000L, parser, "2017-12-04T10:01:37+00");
    // Positive timezone offsets.
    assertParseStringToLong(1512381697000L - 4980000L, parser, "2017-12-04T10:01:37+01:23");
    assertParseStringToLong(1512381697000L - 4980000L, parser, "2017-12-04T10:01:37+0123");
    assertParseStringToLong(1512381697000L - 3600000L, parser, "2017-12-04T10:01:37+01");
    // Negative timezone offsets with minus character.
    assertParseStringToLong(1512381697000L + 4980000L, parser, "2017-12-04T10:01:37-01:23");
    assertParseStringToLong(1512381697000L + 4980000L, parser, "2017-12-04T10:01:37-0123");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37-01:00");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37-0100");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37-01");
    // Negative timezone offsets with hyphen character.
    assertParseStringToLong(1512381697000L + 4980000L, parser, "2017-12-04T10:01:37−01:23");
    assertParseStringToLong(1512381697000L + 4980000L, parser, "2017-12-04T10:01:37−0123");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37−01:00");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37−0100");
    assertParseStringToLong(1512381697000L + 3600000L, parser, "2017-12-04T10:01:37−01");
  }

  @Test
  public void testIso8601ParserParseMissingTimezone() throws IOException {
    DashMediaSource.Iso8601Parser parser = new DashMediaSource.Iso8601Parser();
    try {
      assertParseStringToLong(0, parser, "2017-12-04T10:01:37");
      fail();
    } catch (ParserException e) {
      // Expected.
    }
  }

  private static void assertParseStringToLong(
      long expected, ParsingLoadable.Parser<Long> parser, String data) throws IOException {
    long actual = parser.parse(null, new ByteArrayInputStream(Util.getUtf8Bytes(data)));
    assertThat(actual).isEqualTo(expected);
  }
}
