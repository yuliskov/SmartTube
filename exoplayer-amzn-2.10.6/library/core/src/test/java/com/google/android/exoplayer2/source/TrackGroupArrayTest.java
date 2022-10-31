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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TrackGroupArray}. */
@RunWith(AndroidJUnit4.class)
public final class TrackGroupArrayTest {

  @Test
  public void testParcelable() {
    Format format1 = Format.createSampleFormat("1", MimeTypes.VIDEO_H264, 0);
    Format format2 = Format.createSampleFormat("2", MimeTypes.AUDIO_AAC, 0);
    Format format3 = Format.createSampleFormat("3", MimeTypes.VIDEO_H264, 0);

    TrackGroup trackGroup1 = new TrackGroup(format1, format2);
    TrackGroup trackGroup2 = new TrackGroup(format3);

    TrackGroupArray trackGroupArrayToParcel = new TrackGroupArray(trackGroup1, trackGroup2);

    Parcel parcel = Parcel.obtain();
    trackGroupArrayToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    TrackGroupArray trackGroupArrayFromParcel = TrackGroupArray.CREATOR.createFromParcel(parcel);
    assertThat(trackGroupArrayFromParcel).isEqualTo(trackGroupArrayToParcel);

    parcel.recycle();
  }
}
