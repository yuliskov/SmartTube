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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * Chapter table of contents ID3 frame.
 */
public final class ChapterTocFrame extends Id3Frame {

  public static final String ID = "CTOC";

  public final String elementId;
  public final boolean isRoot;
  public final boolean isOrdered;
  public final String[] children;
  private final Id3Frame[] subFrames;

  public ChapterTocFrame(String elementId, boolean isRoot, boolean isOrdered, String[] children,
      Id3Frame[] subFrames) {
    super(ID);
    this.elementId = elementId;
    this.isRoot = isRoot;
    this.isOrdered = isOrdered;
    this.children = children;
    this.subFrames = subFrames;
  }

  /* package */
  ChapterTocFrame(Parcel in) {
    super(ID);
    this.elementId = castNonNull(in.readString());
    this.isRoot = in.readByte() != 0;
    this.isOrdered = in.readByte() != 0;
    this.children = castNonNull(in.createStringArray());
    int subFrameCount = in.readInt();
    subFrames = new Id3Frame[subFrameCount];
    for (int i = 0; i < subFrameCount; i++) {
      subFrames[i] = in.readParcelable(Id3Frame.class.getClassLoader());
    }
  }

  /**
   * Returns the number of sub-frames.
   */
  public int getSubFrameCount() {
    return subFrames.length;
  }

  /**
   * Returns the sub-frame at {@code index}.
   */
  public Id3Frame getSubFrame(int index) {
    return subFrames[index];
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ChapterTocFrame other = (ChapterTocFrame) obj;
    return isRoot == other.isRoot
        && isOrdered == other.isOrdered
        && Util.areEqual(elementId, other.elementId)
        && Arrays.equals(children, other.children)
        && Arrays.equals(subFrames, other.subFrames);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (isRoot ? 1 : 0);
    result = 31 * result + (isOrdered ? 1 : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(elementId);
    dest.writeByte((byte) (isRoot ? 1 : 0));
    dest.writeByte((byte) (isOrdered ? 1 : 0));
    dest.writeStringArray(children);
    dest.writeInt(subFrames.length);
    for (Id3Frame subFrame : subFrames) {
      dest.writeParcelable(subFrame, 0);
    }
  }

  public static final Creator<ChapterTocFrame> CREATOR = new Creator<ChapterTocFrame>() {

    @Override
    public ChapterTocFrame createFromParcel(Parcel in) {
      return new ChapterTocFrame(in);
    }

    @Override
    public ChapterTocFrame[] newArray(int size) {
      return new ChapterTocFrame[size];
    }

  };

}
