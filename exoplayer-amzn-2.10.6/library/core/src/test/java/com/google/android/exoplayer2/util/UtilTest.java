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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Util.binarySearchCeil;
import static com.google.android.exoplayer2.util.Util.binarySearchFloor;
import static com.google.android.exoplayer2.util.Util.escapeFileName;
import static com.google.android.exoplayer2.util.Util.getCodecsOfType;
import static com.google.android.exoplayer2.util.Util.parseXsDateTime;
import static com.google.android.exoplayer2.util.Util.parseXsDuration;
import static com.google.android.exoplayer2.util.Util.unescapeFileName;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link Util}. */
@RunWith(AndroidJUnit4.class)
public class UtilTest {

  @Test
  public void testAddWithOverflowDefault() {
    long res = Util.addWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(15);

    res = Util.addWithOverflowDefault(Long.MAX_VALUE - 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE + 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);

    res = Util.addWithOverflowDefault(Long.MAX_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void testSubtrackWithOverflowDefault() {
    long res = Util.subtractWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(-5);

    res = Util.subtractWithOverflowDefault(Long.MIN_VALUE + 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE - 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);

    res = Util.subtractWithOverflowDefault(Long.MIN_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void testInferContentType() {
    assertThat(Util.inferContentType("http://a.b/c.ism")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.ism/Manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest(filter=x)")).isEqualTo(C.TYPE_SS);

    assertThat(Util.inferContentType("http://a.b/c.ism/prefix-manifest")).isEqualTo(C.TYPE_OTHER);
    assertThat(Util.inferContentType("http://a.b/c.ism/manifest-suffix")).isEqualTo(C.TYPE_OTHER);
  }

  @Test
  public void testArrayBinarySearchFloor() {
    long[] values = new long[0];
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);

    values = new long[] {1, 3, 5};
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, true, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 0, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 1, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 1, true, false)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 4, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 4, true, false)).isEqualTo(1);

    assertThat(binarySearchFloor(values, 5, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 5, true, false)).isEqualTo(2);

    assertThat(binarySearchFloor(values, 6, false, false)).isEqualTo(2);
    assertThat(binarySearchFloor(values, 6, true, false)).isEqualTo(2);
  }

  @Test
  public void testListBinarySearchFloor() {
    List<Integer> values = new ArrayList<>();
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);

    values.add(1);
    values.add(3);
    values.add(5);
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, true, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 0, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 1, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 1, true, false)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 4, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 4, true, false)).isEqualTo(1);

    assertThat(binarySearchFloor(values, 5, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 5, true, false)).isEqualTo(2);

    assertThat(binarySearchFloor(values, 6, false, false)).isEqualTo(2);
    assertThat(binarySearchFloor(values, 6, true, false)).isEqualTo(2);
  }

  @Test
  public void testArrayBinarySearchCeil() {
    long[] values = new long[0];
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, false, true)).isEqualTo(-1);

    values = new long[] {1, 3, 5};
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 1, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 1, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 2, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 2, true, false)).isEqualTo(1);

    assertThat(binarySearchCeil(values, 5, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 5, true, false)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, true, true)).isEqualTo(2);

    assertThat(binarySearchCeil(values, 6, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, true, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 6, true, true)).isEqualTo(2);
  }

  @Test
  public void testListBinarySearchCeil() {
    List<Integer> values = new ArrayList<>();
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, false, true)).isEqualTo(-1);

    values.add(1);
    values.add(3);
    values.add(5);
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 1, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 1, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 2, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 2, true, false)).isEqualTo(1);

    assertThat(binarySearchCeil(values, 5, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 5, true, false)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, true, true)).isEqualTo(2);

    assertThat(binarySearchCeil(values, 6, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, true, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 6, true, true)).isEqualTo(2);
  }

  @Test
  public void testParseXsDuration() {
    assertThat(parseXsDuration("PT150.279S")).isEqualTo(150279L);
    assertThat(parseXsDuration("PT1.500S")).isEqualTo(1500L);
  }

  @Test
  public void testParseXsDateTime() throws Exception {
    assertThat(parseXsDateTime("2014-06-19T23:07:42")).isEqualTo(1403219262000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00,000Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-08:00")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-800")).isEqualTo(1411161535000L);
  }

  @Test
  public void testGetCodecsOfType() {
    assertThat(getCodecsOfType(null, C.TRACK_TYPE_VIDEO)).isNull();
    assertThat(getCodecsOfType("avc1.64001e,vp9.63.1", C.TRACK_TYPE_AUDIO)).isNull();
    assertThat(getCodecsOfType(" vp9.63.1, ec-3 ", C.TRACK_TYPE_AUDIO)).isEqualTo("ec-3");
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
    assertThat(getCodecsOfType("invalidCodec1, invalidCodec2 ", C.TRACK_TYPE_AUDIO)).isNull();
  }

  @Test
  public void testUnescapeInvalidFileName() {
    assertThat(Util.unescapeFileName("%a")).isNull();
    assertThat(Util.unescapeFileName("%xyz")).isNull();
  }

  @Test
  public void testEscapeUnescapeFileName() {
    assertEscapeUnescapeFileName("just+a regular+fileName", "just+a regular+fileName");
    assertEscapeUnescapeFileName("key:value", "key%3avalue");
    assertEscapeUnescapeFileName("<>:\"/\\|?*%", "%3c%3e%3a%22%2f%5c%7c%3f%2a%25");

    Random random = new Random(0);
    for (int i = 0; i < 1000; i++) {
      String string = TestUtil.buildTestString(1000, random);
      assertEscapeUnescapeFileName(string);
    }
  }

  @Test
  public void testInflate() {
    byte[] testData = TestUtil.buildTestData(/*arbitrary test data size*/ 256 * 1024);
    byte[] compressedData = new byte[testData.length * 2];
    Deflater compresser = new Deflater(9);
    compresser.setInput(testData);
    compresser.finish();
    int compressedDataLength = compresser.deflate(compressedData);
    compresser.end();

    ParsableByteArray input = new ParsableByteArray(compressedData, compressedDataLength);
    ParsableByteArray output = new ParsableByteArray();
    assertThat(Util.inflate(input, output, /* inflater= */ null)).isTrue();
    assertThat(output.limit()).isEqualTo(testData.length);
    assertThat(Arrays.copyOf(output.data, output.limit())).isEqualTo(testData);
  }

  @Test
  @Config(sdk = 21)
  public void testNormalizeLanguageCodeV21() {
    assertThat(Util.normalizeLanguageCode(null)).isNull();
    assertThat(Util.normalizeLanguageCode("")).isEmpty();
    assertThat(Util.normalizeLanguageCode("es")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("spa")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("es-AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("SpA-ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es_AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("spa_ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es-AR-dialect")).isEqualTo("es-ar-dialect");
    assertThat(Util.normalizeLanguageCode("ES-419")).isEqualTo("es-419");
    assertThat(Util.normalizeLanguageCode("zh-hans-tw")).isEqualTo("zh-hans-tw");
    assertThat(Util.normalizeLanguageCode("zh-tw-hans")).isEqualTo("zh-tw");
    assertThat(Util.normalizeLanguageCode("zho-hans-tw")).isEqualTo("zh-hans-tw");
    assertThat(Util.normalizeLanguageCode("und")).isEqualTo("und");
    assertThat(Util.normalizeLanguageCode("DoesNotExist")).isEqualTo("doesnotexist");
  }

  @Test
  @Config(sdk = 16)
  public void testNormalizeLanguageCode() {
    assertThat(Util.normalizeLanguageCode(null)).isNull();
    assertThat(Util.normalizeLanguageCode("")).isEmpty();
    assertThat(Util.normalizeLanguageCode("es")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("spa")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("es-AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("SpA-ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es_AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("spa_ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es-AR-dialect")).isEqualTo("es-ar-dialect");
    assertThat(Util.normalizeLanguageCode("ES-419")).isEqualTo("es-419");
    assertThat(Util.normalizeLanguageCode("zh-hans-tw")).isEqualTo("zh-hans-tw");
    // Doesn't work on API < 21 because we can't use Locale syntax verification.
    // assertThat(Util.normalizeLanguageCode("zh-tw-hans")).isEqualTo("zh-tw");
    assertThat(Util.normalizeLanguageCode("zho-hans-tw")).isEqualTo("zh-hans-tw");
    assertThat(Util.normalizeLanguageCode("und")).isEqualTo("und");
    assertThat(Util.normalizeLanguageCode("DoesNotExist")).isEqualTo("doesnotexist");
  }

  @Test
  public void testNormalizeIso6392BibliographicalAndTextualCodes() {
    // See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
    assertThat(Util.normalizeLanguageCode("alb")).isEqualTo(Util.normalizeLanguageCode("sqi"));
    assertThat(Util.normalizeLanguageCode("arm")).isEqualTo(Util.normalizeLanguageCode("hye"));
    assertThat(Util.normalizeLanguageCode("baq")).isEqualTo(Util.normalizeLanguageCode("eus"));
    assertThat(Util.normalizeLanguageCode("bur")).isEqualTo(Util.normalizeLanguageCode("mya"));
    assertThat(Util.normalizeLanguageCode("chi")).isEqualTo(Util.normalizeLanguageCode("zho"));
    assertThat(Util.normalizeLanguageCode("cze")).isEqualTo(Util.normalizeLanguageCode("ces"));
    assertThat(Util.normalizeLanguageCode("dut")).isEqualTo(Util.normalizeLanguageCode("nld"));
    assertThat(Util.normalizeLanguageCode("fre")).isEqualTo(Util.normalizeLanguageCode("fra"));
    assertThat(Util.normalizeLanguageCode("geo")).isEqualTo(Util.normalizeLanguageCode("kat"));
    assertThat(Util.normalizeLanguageCode("ger")).isEqualTo(Util.normalizeLanguageCode("deu"));
    assertThat(Util.normalizeLanguageCode("gre")).isEqualTo(Util.normalizeLanguageCode("ell"));
    assertThat(Util.normalizeLanguageCode("ice")).isEqualTo(Util.normalizeLanguageCode("isl"));
    assertThat(Util.normalizeLanguageCode("mac")).isEqualTo(Util.normalizeLanguageCode("mkd"));
    assertThat(Util.normalizeLanguageCode("mao")).isEqualTo(Util.normalizeLanguageCode("mri"));
    assertThat(Util.normalizeLanguageCode("may")).isEqualTo(Util.normalizeLanguageCode("msa"));
    assertThat(Util.normalizeLanguageCode("per")).isEqualTo(Util.normalizeLanguageCode("fas"));
    assertThat(Util.normalizeLanguageCode("rum")).isEqualTo(Util.normalizeLanguageCode("ron"));
    assertThat(Util.normalizeLanguageCode("slo")).isEqualTo(Util.normalizeLanguageCode("slk"));
    assertThat(Util.normalizeLanguageCode("tib")).isEqualTo(Util.normalizeLanguageCode("bod"));
    assertThat(Util.normalizeLanguageCode("wel")).isEqualTo(Util.normalizeLanguageCode("cym"));
  }

  private static void assertEscapeUnescapeFileName(String fileName, String escapedFileName) {
    assertThat(escapeFileName(fileName)).isEqualTo(escapedFileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static void assertEscapeUnescapeFileName(String fileName) {
    String escapedFileName = Util.escapeFileName(fileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }
}
