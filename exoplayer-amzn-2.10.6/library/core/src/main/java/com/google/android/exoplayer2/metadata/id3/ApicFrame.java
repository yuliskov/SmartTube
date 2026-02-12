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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * APIC (Attached Picture) ID3 frame.
 */
public final class ApicFrame extends Id3Frame {

  public static final String ID = "APIC";

  public final String mimeType;
  public final @Nullable String description;
  public final int pictureType;
  public final byte[] pictureData;

  public ApicFrame(
      String mimeType, @Nullable String description, int pictureType, byte[] pictureData) {
    super(ID);
    this.mimeType = mimeType;
    this.description = description;
    this.pictureType = pictureType;
    this.pictureData = pictureData;
  }

  /* package */ ApicFrame(Parcel in) {
    super(ID);
    mimeType = castNonNull(in.readString());
    description = castNonNull(in.readString());
    pictureType = in.readInt();
    pictureData = castNonNull(in.createByteArray());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ApicFrame other = (ApicFrame) obj;
    return pictureType == other.pictureType && Util.areEqual(mimeType, other.mimeType)
        && Util.areEqual(description, other.description)
        && Arrays.equals(pictureData, other.pictureData);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + pictureType;
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(pictureData);
    return result;
  }

  @Override
  public String toString() {
    return id + ": mimeType=" + mimeType + ", description=" + description;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(mimeType);
    dest.writeString(description);
    dest.writeInt(pictureType);
    dest.writeByteArray(pictureData);
  }

  public static final Parcelable.Creator<ApicFrame> CREATOR = new Parcelable.Creator<ApicFrame>() {

    @Override
    public ApicFrame createFromParcel(Parcel in) {
      return new ApicFrame(in);
    }

    @Override
    public ApicFrame[] newArray(int size) {
      return new ApicFrame[size];
    }

  };

}
