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
package com.google.android.exoplayer2.drm;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit test for {@link ClearKeyUtil}. */
@RunWith(AndroidJUnit4.class)
public final class ClearKeyUtilTest {

  private static final byte[] SINGLE_KEY_RESPONSE =
      Util.getUtf8Bytes(
          "{"
              + "\"keys\":["
              + "{"
              + "\"k\":\"abc_def-\","
              + "\"kid\":\"ab_cde-f\","
              + "\"kty\":\"o_c-t\","
              + "\"ignored\":\"ignored\""
              + "}"
              + "],"
              + "\"ignored\":\"ignored\""
              + "}");
  private static final byte[] MULTI_KEY_RESPONSE =
      Util.getUtf8Bytes(
          "{"
              + "\"keys\":["
              + "{"
              + "\"k\":\"abc_def-\","
              + "\"kid\":\"ab_cde-f\","
              + "\"kty\":\"oct\","
              + "\"ignored\":\"ignored\""
              + "},{"
              + "\"k\":\"ghi_jkl-\","
              + "\"kid\":\"gh_ijk-l\","
              + "\"kty\":\"oct\""
              + "}"
              + "],"
              + "\"ignored\":\"ignored\""
              + "}");
  private static final byte[] KEY_REQUEST =
      Util.getUtf8Bytes(
          "{"
              + "\"kids\":["
              + "\"abc+def/\","
              + "\"ab+cde/f\""
              + "],"
              + "\"type\":\"temporary\""
              + "}");

  @Config(sdk = 26)
  @Test
  public void testAdjustSingleKeyResponseDataV26() {
    // Everything but the keys should be removed. Within each key only the k, kid and kty parameters
    // should remain. Any "-" and "_" characters in the k and kid values should be replaced with "+"
    // and "/".
    byte[] expected =
        Util.getUtf8Bytes(
            "{"
                + "\"keys\":["
                + "{"
                + "\"k\":\"abc/def+\",\"kid\":\"ab/cde+f\",\"kty\":\"o_c-t\""
                + "}"
                + "]"
                + "}");
    assertThat(ClearKeyUtil.adjustResponseData(SINGLE_KEY_RESPONSE)).isEqualTo(expected);
  }

  @Config(sdk = 26)
  @Test
  public void testAdjustMultiKeyResponseDataV26() {
    // Everything but the keys should be removed. Within each key only the k, kid and kty parameters
    // should remain. Any "-" and "_" characters in the k and kid values should be replaced with "+"
    // and "/".
    byte[] expected =
        Util.getUtf8Bytes(
            "{"
                + "\"keys\":["
                + "{"
                + "\"k\":\"abc/def+\",\"kid\":\"ab/cde+f\",\"kty\":\"oct\""
                + "},{"
                + "\"k\":\"ghi/jkl+\",\"kid\":\"gh/ijk+l\",\"kty\":\"oct\""
                + "}"
                + "]"
                + "}");
    assertThat(ClearKeyUtil.adjustResponseData(MULTI_KEY_RESPONSE)).isEqualTo(expected);
  }

  @Config(sdk = 27)
  @Test
  public void testAdjustResponseDataV27() {
    // Response should be unchanged.
    assertThat(ClearKeyUtil.adjustResponseData(SINGLE_KEY_RESPONSE)).isEqualTo(SINGLE_KEY_RESPONSE);
  }

  @Config(sdk = 26)
  @Test
  public void testAdjustRequestDataV26() {
    // We expect "+" and "/" to be replaced with "-" and "_" respectively, for "kids".
    byte[] expected =
        Util.getUtf8Bytes(
            "{"
                + "\"kids\":["
                + "\"abc-def_\","
                + "\"ab-cde_f\""
                + "],"
                + "\"type\":\"temporary\""
                + "}");
    assertThat(ClearKeyUtil.adjustRequestData(KEY_REQUEST)).isEqualTo(expected);
  }

  @Config(sdk = 27)
  @Test
  public void testAdjustRequestDataV27() {
    // Request should be unchanged.
    assertThat(ClearKeyUtil.adjustRequestData(KEY_REQUEST)).isEqualTo(KEY_REQUEST);
  }

}
