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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;

/**
 * Url link ID3 frame.
 */
public final class UrlLinkFrame extends Id3Frame {

  public final @Nullable String description;
  public final String url;

  public UrlLinkFrame(String id, @Nullable String description, String url) {
    super(id);
    this.description = description;
    this.url = url;
  }

  /* package */ UrlLinkFrame(Parcel in) {
    super(castNonNull(in.readString()));
    description = in.readString();
    url = castNonNull(in.readString());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    UrlLinkFrame other = (UrlLinkFrame) obj;
    return id.equals(other.id) && Util.areEqual(description, other.description)
        && Util.areEqual(url, other.url);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return id + ": url=" + url;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(description);
    dest.writeString(url);
  }

  public static final Parcelable.Creator<UrlLinkFrame> CREATOR =
      new Parcelable.Creator<UrlLinkFrame>() {

        @Override
        public UrlLinkFrame createFromParcel(Parcel in) {
          return new UrlLinkFrame(in);
        }

        @Override
        public UrlLinkFrame[] newArray(int size) {
          return new UrlLinkFrame[size];
        }

      };

}
