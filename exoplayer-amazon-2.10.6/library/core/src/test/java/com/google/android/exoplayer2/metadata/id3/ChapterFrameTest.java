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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link ChapterFrame}. */
@RunWith(AndroidJUnit4.class)
public final class ChapterFrameTest {

  @Test
  public void testParcelable() {
    Id3Frame[] subFrames = new Id3Frame[] {
      new TextInformationFrame("TIT2", null, "title"),
      new UrlLinkFrame("WXXX", "description", "url")
    };
    ChapterFrame chapterFrameToParcel = new ChapterFrame("id", 0, 1, 2, 3, subFrames);

    Parcel parcel = Parcel.obtain();
    chapterFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    ChapterFrame chapterFrameFromParcel = ChapterFrame.CREATOR.createFromParcel(parcel);
    assertThat(chapterFrameFromParcel).isEqualTo(chapterFrameToParcel);

    parcel.recycle();
  }

}
