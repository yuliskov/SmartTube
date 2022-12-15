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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link VorbisBitArray}. */
@RunWith(AndroidJUnit4.class)
public final class VorbisBitArrayTest {

  @Test
  public void testReadBit() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x5c, 0x50));
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isFalse();
  }

  @Test
  public void testSkipBits() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    bitArray.skipBits(10);
    assertThat(bitArray.getPosition()).isEqualTo(10);
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isFalse();
    bitArray.skipBits(1);
    assertThat(bitArray.getPosition()).isEqualTo(14);
    assertThat(bitArray.readBit()).isFalse();
    assertThat(bitArray.readBit()).isFalse();
  }

  @Test
  public void testGetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    assertThat(bitArray.getPosition()).isEqualTo(0);
    bitArray.readBit();
    assertThat(bitArray.getPosition()).isEqualTo(1);
    bitArray.readBit();
    bitArray.readBit();
    bitArray.skipBits(4);
    assertThat(bitArray.getPosition()).isEqualTo(7);
  }

  @Test
  public void testSetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    assertThat(bitArray.getPosition()).isEqualTo(0);
    bitArray.setPosition(4);
    assertThat(bitArray.getPosition()).isEqualTo(4);
    bitArray.setPosition(15);
    assertThat(bitArray.readBit()).isFalse();
  }

  @Test
  public void testReadInt32() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F, 0xF0, 0x0F));
    assertThat(bitArray.readBits(32)).isEqualTo(0x0FF00FF0);
    bitArray = new VorbisBitArray(TestUtil.createByteArray(0x0F, 0xF0, 0x0F, 0xF0));
    assertThat(bitArray.readBits(32)).isEqualTo(0xF00FF00F);
  }

  @Test
  public void testReadBits() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x03, 0x22));
    assertThat(bitArray.readBits(2)).isEqualTo(3);
    bitArray.skipBits(6);
    assertThat(bitArray.readBits(2)).isEqualTo(2);
    bitArray.skipBits(2);
    assertThat(bitArray.readBits(2)).isEqualTo(2);
    bitArray.reset();
    assertThat(bitArray.readBits(16)).isEqualTo(0x2203);
  }

  @Test
  public void testRead4BitsBeyondBoundary() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x2e, 0x10));
    assertThat(bitArray.readBits(7)).isEqualTo(0x2e);
    assertThat(bitArray.getPosition()).isEqualTo(7);
    assertThat(bitArray.readBits(4)).isEqualTo(0x0);
  }

  @Test
  public void testReadBitsBeyondByteBoundaries() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xFF, 0x0F, 0xFF, 0x0F));
    assertThat(bitArray.readBits(32)).isEqualTo(0x0FFF0FFF);

    bitArray.reset();
    bitArray.skipBits(4);
    assertThat(bitArray.readBits(16)).isEqualTo(0xF0FF);

    bitArray.reset();
    bitArray.skipBits(6);
    assertThat(bitArray.readBits(12)).isEqualTo(0xc3F);

    bitArray.reset();
    bitArray.skipBits(6);
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.readBit()).isTrue();
    assertThat(bitArray.bitsLeft()).isEqualTo(24);

    bitArray.reset();
    bitArray.skipBits(10);
    assertThat(bitArray.readBits(5)).isEqualTo(3);
    assertThat(bitArray.getPosition()).isEqualTo(15);
  }

  @Test
  public void testReadBitsIllegalLengths() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x03, 0x22, 0x30));

    // reading zero bits gets 0 without advancing position
    // (like a zero-bit read is defined to yield zer0)
    assertThat(bitArray.readBits(0)).isEqualTo(0);
    assertThat(bitArray.getPosition()).isEqualTo(0);
    bitArray.readBit();
    assertThat(bitArray.getPosition()).isEqualTo(1);
  }

}
