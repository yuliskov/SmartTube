/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.icy;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link IcyInfo}. */
@RunWith(AndroidJUnit4.class)
public final class IcyInfoTest {

  @Test
  public void parcelEquals() {
    IcyInfo streamInfo = new IcyInfo("StreamName='name';StreamUrl='url'", "name", "url");
    // Write to parcel.
    Parcel parcel = Parcel.obtain();
    streamInfo.writeToParcel(parcel, 0);
    // Create from parcel.
    parcel.setDataPosition(0);
    IcyInfo fromParcelStreamInfo = IcyInfo.CREATOR.createFromParcel(parcel);
    // Assert equals.
    assertThat(fromParcelStreamInfo).isEqualTo(streamInfo);
  }
}
