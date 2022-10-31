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
import java.util.Arrays;

/** A picture parsed from a FLAC file. */
public final class PictureFrame implements Metadata.Entry {

  /** The type of the picture. */
  public final int pictureType;
  /** The mime type of the picture. */
  public final String mimeType;
  /** A description of the picture. */
  public final String description;
  /** The width of the picture in pixels. */
  public final int width;
  /** The height of the picture in pixels. */
  public final int height;
  /** The color depth of the picture in bits-per-pixel. */
  public final int depth;
  /** For indexed-color pictures (e.g. GIF), the number of colors used. 0 otherwise. */
  public final int colors;
  /** The encoded picture data. */
  public final byte[] pictureData;

  public PictureFrame(
      int pictureType,
      String mimeType,
      String description,
      int width,
      int height,
      int depth,
      int colors,
      byte[] pictureData) {
    this.pictureType = pictureType;
    this.mimeType = mimeType;
    this.description = description;
    this.width = width;
    this.height = height;
    this.depth = depth;
    this.colors = colors;
    this.pictureData = pictureData;
  }

  /* package */ PictureFrame(Parcel in) {
    this.pictureType = in.readInt();
    this.mimeType = castNonNull(in.readString());
    this.description = castNonNull(in.readString());
    this.width = in.readInt();
    this.height = in.readInt();
    this.depth = in.readInt();
    this.colors = in.readInt();
    this.pictureData = castNonNull(in.createByteArray());
  }

  @Override
  public String toString() {
    return "Picture: mimeType=" + mimeType + ", description=" + description;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PictureFrame other = (PictureFrame) obj;
    return (pictureType == other.pictureType)
        && mimeType.equals(other.mimeType)
        && description.equals(other.description)
        && (width == other.width)
        && (height == other.height)
        && (depth == other.depth)
        && (colors == other.colors)
        && Arrays.equals(pictureData, other.pictureData);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + pictureType;
    result = 31 * result + mimeType.hashCode();
    result = 31 * result + description.hashCode();
    result = 31 * result + width;
    result = 31 * result + height;
    result = 31 * result + depth;
    result = 31 * result + colors;
    result = 31 * result + Arrays.hashCode(pictureData);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(pictureType);
    dest.writeString(mimeType);
    dest.writeString(description);
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeInt(depth);
    dest.writeInt(colors);
    dest.writeByteArray(pictureData);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<PictureFrame> CREATOR =
      new Parcelable.Creator<PictureFrame>() {

        @Override
        public PictureFrame createFromParcel(Parcel in) {
          return new PictureFrame(in);
        }

        @Override
        public PictureFrame[] newArray(int size) {
          return new PictureFrame[size];
        }
      };
}
