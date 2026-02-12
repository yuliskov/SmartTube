/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.flac;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;

/** A vorbis comment. */
public final class VorbisComment implements Metadata.Entry {

  /** The key. */
  public final String key;

  /** The value. */
  public final String value;

  /**
   * @param key The key.
   * @param value The value.
   */
  public VorbisComment(String key, String value) {
    this.key = key;
    this.value = value;
  }

  /* package */ VorbisComment(Parcel in) {
    this.key = castNonNull(in.readString());
    this.value = castNonNull(in.readString());
  }

  @Override
  public String toString() {
    return "VC: " + key + "=" + value;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    VorbisComment other = (VorbisComment) obj;
    return key.equals(other.key) && value.equals(other.value);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + key.hashCode();
    result = 31 * result + value.hashCode();
    return result;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(key);
    dest.writeString(value);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<VorbisComment> CREATOR =
      new Parcelable.Creator<VorbisComment>() {

        @Override
        public VorbisComment createFromParcel(Parcel in) {
          return new VorbisComment(in);
        }

        @Override
        public VorbisComment[] newArray(int size) {
          return new VorbisComment[size];
        }
      };
}
