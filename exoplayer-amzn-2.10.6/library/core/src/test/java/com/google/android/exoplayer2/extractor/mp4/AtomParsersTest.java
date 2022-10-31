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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link AtomParsers}. */
@RunWith(AndroidJUnit4.class)
public final class AtomParsersTest {

  private static final String ATOM_HEADER = "000000000000000000000000";
  private static final String SAMPLE_COUNT = "00000004";
  private static final byte[] FOUR_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000004"
      + SAMPLE_COUNT + "1234");
  private static final byte[] EIGHT_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000008"
      + SAMPLE_COUNT + "01020304");
  private static final byte[] SIXTEEN_BIT_STZ2 = Util.getBytesFromHexString(ATOM_HEADER + "00000010"
      + SAMPLE_COUNT + "0001000200030004");

  @Test
  public void testParseCommonEncryptionSinfFromParentIgnoresUnknownSchemeType() {
    byte[] cencSinf = new byte[] {
        0, 0, 0, 24, 115, 105, 110, 102, // size (4), 'sinf' (4)
        0, 0, 0, 16, 115, 99, 104, 109, // size (4), 'schm' (4)
        0, 0, 0, 0, 88, 88, 88, 88}; // version (1), flags (3), 'xxxx' (4)
    assertThat(AtomParsers.parseCommonEncryptionSinfFromParent(
        new ParsableByteArray(cencSinf), 0, cencSinf.length)).isNull();
  }

  @Test
  public void testStz2Parsing4BitFieldSize() {
    verifyStz2Parsing(new Atom.LeafAtom(Atom.TYPE_stsz, new ParsableByteArray(FOUR_BIT_STZ2)));
  }

  @Test
  public void testStz2Parsing8BitFieldSize() {
    verifyStz2Parsing(new Atom.LeafAtom(Atom.TYPE_stsz, new ParsableByteArray(EIGHT_BIT_STZ2)));
  }

  @Test
  public void testStz2Parsing16BitFieldSize() {
    verifyStz2Parsing(new Atom.LeafAtom(Atom.TYPE_stsz, new ParsableByteArray(SIXTEEN_BIT_STZ2)));
  }

  private static void verifyStz2Parsing(Atom.LeafAtom stz2Atom) {
    AtomParsers.Stz2SampleSizeBox box = new AtomParsers.Stz2SampleSizeBox(stz2Atom);
    assertThat(box.getSampleCount()).isEqualTo(4);
    assertThat(box.isFixedSampleSize()).isFalse();
    for (int i = 0; i < box.getSampleCount(); i++) {
      assertThat(box.readNextSampleSize()).isEqualTo(i + 1);
    }
  }

}
