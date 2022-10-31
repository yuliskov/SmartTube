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

import android.os.Parcel;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/** MPEG location lookup table frame. */
public final class MlltFrame extends Id3Frame {

  public static final String ID = "MLLT";

  public final int mpegFramesBetweenReference;
  public final int bytesBetweenReference;
  public final int millisecondsBetweenReference;
  public final int[] bytesDeviations;
  public final int[] millisecondsDeviations;

  public MlltFrame(
      int mpegFramesBetweenReference,
      int bytesBetweenReference,
      int millisecondsBetweenReference,
      int[] bytesDeviations,
      int[] millisecondsDeviations) {
    super(ID);
    this.mpegFramesBetweenReference = mpegFramesBetweenReference;
    this.bytesBetweenReference = bytesBetweenReference;
    this.millisecondsBetweenReference = millisecondsBetweenReference;
    this.bytesDeviations = bytesDeviations;
    this.millisecondsDeviations = millisecondsDeviations;
  }

  /* package */
  MlltFrame(Parcel in) {
    super(ID);
    this.mpegFramesBetweenReference = in.readInt();
    this.bytesBetweenReference = in.readInt();
    this.millisecondsBetweenReference = in.readInt();
    this.bytesDeviations = Util.castNonNull(in.createIntArray());
    this.millisecondsDeviations = Util.castNonNull(in.createIntArray());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MlltFrame other = (MlltFrame) obj;
    return mpegFramesBetweenReference == other.mpegFramesBetweenReference
        && bytesBetweenReference == other.bytesBetweenReference
        && millisecondsBetweenReference == other.millisecondsBetweenReference
        && Arrays.equals(bytesDeviations, other.bytesDeviations)
        && Arrays.equals(millisecondsDeviations, other.millisecondsDeviations);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + mpegFramesBetweenReference;
    result = 31 * result + bytesBetweenReference;
    result = 31 * result + millisecondsBetweenReference;
    result = 31 * result + Arrays.hashCode(bytesDeviations);
    result = 31 * result + Arrays.hashCode(millisecondsDeviations);
    return result;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(mpegFramesBetweenReference);
    dest.writeInt(bytesBetweenReference);
    dest.writeInt(millisecondsBetweenReference);
    dest.writeIntArray(bytesDeviations);
    dest.writeIntArray(millisecondsDeviations);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<MlltFrame> CREATOR =
      new Creator<MlltFrame>() {

        @Override
        public MlltFrame createFromParcel(Parcel in) {
          return new MlltFrame(in);
        }

        @Override
        public MlltFrame[] newArray(int size) {
          return new MlltFrame[size];
        }
      };
}
