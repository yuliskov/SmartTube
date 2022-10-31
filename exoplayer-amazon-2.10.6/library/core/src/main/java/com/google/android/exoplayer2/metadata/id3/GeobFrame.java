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
 * GEOB (General Encapsulated Object) ID3 frame.
 */
public final class GeobFrame extends Id3Frame {

  public static final String ID = "GEOB";

  public final String mimeType;
  public final String filename;
  public final String description;
  public final byte[] data;

  public GeobFrame(String mimeType, String filename, String description, byte[] data) {
    super(ID);
    this.mimeType = mimeType;
    this.filename = filename;
    this.description = description;
    this.data = data;
  }

  /* package */ GeobFrame(Parcel in) {
    super(ID);
    mimeType = castNonNull(in.readString());
    filename = castNonNull(in.readString());
    description = castNonNull(in.readString());
    data = castNonNull(in.createByteArray());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    GeobFrame other = (GeobFrame) obj;
    return Util.areEqual(mimeType, other.mimeType) && Util.areEqual(filename, other.filename)
        && Util.areEqual(description, other.description) && Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + (filename != null ? filename.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public String toString() {
    return id
        + ": mimeType="
        + mimeType
        + ", filename="
        + filename
        + ", description="
        + description;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(mimeType);
    dest.writeString(filename);
    dest.writeString(description);
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<GeobFrame> CREATOR = new Parcelable.Creator<GeobFrame>() {

    @Override
    public GeobFrame createFromParcel(Parcel in) {
      return new GeobFrame(in);
    }

    @Override
    public GeobFrame[] newArray(int size) {
      return new GeobFrame[size];
    }

  };

}
