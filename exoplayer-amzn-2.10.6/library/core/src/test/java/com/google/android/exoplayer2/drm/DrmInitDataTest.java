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
package com.google.android.exoplayer2.drm;

import static com.google.android.exoplayer2.C.PLAYREADY_UUID;
import static com.google.android.exoplayer2.C.UUID_NIL;
import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;
import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DrmInitData}. */
@RunWith(AndroidJUnit4.class)
public class DrmInitDataTest {

  private static final SchemeData DATA_1 = new SchemeData(WIDEVINE_UUID, VIDEO_MP4,
      TestUtil.buildTestData(128, 1 /* data seed */));
  private static final SchemeData DATA_2 = new SchemeData(PLAYREADY_UUID, VIDEO_MP4,
      TestUtil.buildTestData(128, 2 /* data seed */));
  private static final SchemeData DATA_1B = new SchemeData(WIDEVINE_UUID, VIDEO_MP4,
      TestUtil.buildTestData(128, 1 /* data seed */));
  private static final SchemeData DATA_2B = new SchemeData(PLAYREADY_UUID, VIDEO_MP4,
      TestUtil.buildTestData(128, 2 /* data seed */));
  private static final SchemeData DATA_UNIVERSAL = new SchemeData(C.UUID_NIL, VIDEO_MP4,
      TestUtil.buildTestData(128, 3 /* data seed */));

  @Test
  public void testParcelable() {
    DrmInitData drmInitDataToParcel = new DrmInitData(DATA_1, DATA_2);

    Parcel parcel = Parcel.obtain();
    drmInitDataToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    DrmInitData drmInitDataFromParcel = DrmInitData.CREATOR.createFromParcel(parcel);
    assertThat(drmInitDataFromParcel).isEqualTo(drmInitDataToParcel);

    parcel.recycle();
  }

  @Test
  public void testEquals() {
    DrmInitData drmInitData = new DrmInitData(DATA_1, DATA_2);

    // Basic non-referential equality test.
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_2);
    assertThat(testInitData).isEqualTo(drmInitData);
    assertThat(testInitData.hashCode()).isEqualTo(drmInitData.hashCode());

    // Basic non-referential equality test with non-referential scheme data.
    testInitData = new DrmInitData(DATA_1B, DATA_2B);
    assertThat(testInitData).isEqualTo(drmInitData);
    assertThat(testInitData.hashCode()).isEqualTo(drmInitData.hashCode());

    // Passing the scheme data in reverse order shouldn't affect equality.
    testInitData = new DrmInitData(DATA_2, DATA_1);
    assertThat(testInitData).isEqualTo(drmInitData);
    assertThat(testInitData.hashCode()).isEqualTo(drmInitData.hashCode());

    // Ditto.
    testInitData = new DrmInitData(DATA_2B, DATA_1B);
    assertThat(testInitData).isEqualTo(drmInitData);
    assertThat(testInitData.hashCode()).isEqualTo(drmInitData.hashCode());

    // Different number of tuples should affect equality.
    testInitData = new DrmInitData(DATA_1);
    assertThat(drmInitData).isNotEqualTo(testInitData);

    // Different data in one of the tuples should affect equality.
    testInitData = new DrmInitData(DATA_1, DATA_UNIVERSAL);
    assertThat(testInitData).isNotEqualTo(drmInitData);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testGetByUuid() {
    // Basic matching.
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_2);
    assertThat(testInitData.get(WIDEVINE_UUID)).isEqualTo(DATA_1);
    assertThat(testInitData.get(PLAYREADY_UUID)).isEqualTo(DATA_2);
    assertThat(testInitData.get(UUID_NIL)).isNull();

    // Basic matching including universal data.
    testInitData = new DrmInitData(DATA_1, DATA_2, DATA_UNIVERSAL);
    assertThat(testInitData.get(WIDEVINE_UUID)).isEqualTo(DATA_1);
    assertThat(testInitData.get(PLAYREADY_UUID)).isEqualTo(DATA_2);
    assertThat(testInitData.get(UUID_NIL)).isEqualTo(DATA_UNIVERSAL);

    // Passing the scheme data in reverse order shouldn't affect equality.
    testInitData = new DrmInitData(DATA_UNIVERSAL, DATA_2, DATA_1);
    assertThat(testInitData.get(WIDEVINE_UUID)).isEqualTo(DATA_1);
    assertThat(testInitData.get(PLAYREADY_UUID)).isEqualTo(DATA_2);
    assertThat(testInitData.get(UUID_NIL)).isEqualTo(DATA_UNIVERSAL);

    // Universal data should be returned in the absence of a specific match.
    testInitData = new DrmInitData(DATA_1, DATA_UNIVERSAL);
    assertThat(testInitData.get(WIDEVINE_UUID)).isEqualTo(DATA_1);
    assertThat(testInitData.get(PLAYREADY_UUID)).isEqualTo(DATA_UNIVERSAL);
    assertThat(testInitData.get(UUID_NIL)).isEqualTo(DATA_UNIVERSAL);
  }

  @Test
  public void testGetByIndex() {
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_2);
    assertThat(getAllSchemeData(testInitData)).containsAllOf(DATA_1, DATA_2);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testSchemeDatasWithSameUuid() {
    DrmInitData testInitData = new DrmInitData(DATA_1, DATA_1B);
    assertThat(testInitData.schemeDataCount).isEqualTo(2);
    // Deprecated get method should return first entry.
    assertThat(testInitData.get(WIDEVINE_UUID)).isEqualTo(DATA_1);
    // Test retrieval of first and second entry.
    assertThat(testInitData.get(0)).isEqualTo(DATA_1);
    assertThat(testInitData.get(1)).isEqualTo(DATA_1B);
  }

  @Test
  public void testSchemeDataMatches() {
    assertThat(DATA_1.matches(WIDEVINE_UUID)).isTrue();
    assertThat(DATA_1.matches(PLAYREADY_UUID)).isFalse();
    assertThat(DATA_2.matches(UUID_NIL)).isFalse();

    assertThat(DATA_2.matches(WIDEVINE_UUID)).isFalse();
    assertThat(DATA_2.matches(PLAYREADY_UUID)).isTrue();
    assertThat(DATA_2.matches(UUID_NIL)).isFalse();

    assertThat(DATA_UNIVERSAL.matches(WIDEVINE_UUID)).isTrue();
    assertThat(DATA_UNIVERSAL.matches(PLAYREADY_UUID)).isTrue();
    assertThat(DATA_UNIVERSAL.matches(UUID_NIL)).isTrue();
  }

  private List<SchemeData> getAllSchemeData(DrmInitData drmInitData) {
    ArrayList<SchemeData> schemeDatas = new ArrayList<>();
    for (int i = 0; i < drmInitData.schemeDataCount; i++) {
      schemeDatas.add(drmInitData.get(i));
    }
    return schemeDatas;
  }

}
