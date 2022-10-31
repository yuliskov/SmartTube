/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link MlltFrame}. */
@RunWith(AndroidJUnit4.class)
public final class MlltFrameTest {

  @Test
  public void testParcelable() {
    MlltFrame mlltFrameToParcel =
        new MlltFrame(
            /* mpegFramesBetweenReference= */ 1,
            /* bytesBetweenReference= */ 1,
            /* millisecondsBetweenReference= */ 1,
            /* bytesDeviations= */ new int[] {1, 2},
            /* millisecondsDeviations= */ new int[] {1, 2});

    Parcel parcel = Parcel.obtain();
    mlltFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    MlltFrame mlltFrameFromParcel = MlltFrame.CREATOR.createFromParcel(parcel);
    assertThat(mlltFrameFromParcel).isEqualTo(mlltFrameToParcel);

    parcel.recycle();
  }

}
