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
import java.util.Arrays;

/**
 * Binary ID3 frame.
 */
public final class BinaryFrame extends Id3Frame {

  public final byte[] data;

  public BinaryFrame(String id, byte[] data) {
    super(id);
    this.data = data;
  }

  /* package */ BinaryFrame(Parcel in) {
    super(castNonNull(in.readString()));
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
    BinaryFrame other = (BinaryFrame) obj;
    return id.equals(other.id) && Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<BinaryFrame> CREATOR =
      new Parcelable.Creator<BinaryFrame>() {

        @Override
        public BinaryFrame createFromParcel(Parcel in) {
          return new BinaryFrame(in);
        }

        @Override
        public BinaryFrame[] newArray(int size) {
          return new BinaryFrame[size];
        }

      };

}
