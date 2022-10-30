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
package com.google.android.exoplayer2.metadata;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A collection of metadata entries.
 */
public final class Metadata implements Parcelable {

  /** A metadata entry. */
  public interface Entry extends Parcelable {

    /**
     * Returns the {@link Format} that can be used to decode the wrapped metadata in {@link
     * #getWrappedMetadataBytes()}, or null if this Entry doesn't contain wrapped metadata.
     */
    @Nullable
    default Format getWrappedMetadataFormat() {
      return null;
    }

    /**
     * Returns the bytes of the wrapped metadata in this Entry, or null if it doesn't contain
     * wrapped metadata.
     */
    @Nullable
    default byte[] getWrappedMetadataBytes() {
      return null;
    }
  }

  private final Entry[] entries;

  /**
   * @param entries The metadata entries.
   */
  public Metadata(Entry... entries) {
    this.entries = entries == null ? new Entry[0] : entries;
  }

  /**
   * @param entries The metadata entries.
   */
  public Metadata(List<? extends Entry> entries) {
    if (entries != null) {
      this.entries = new Entry[entries.size()];
      entries.toArray(this.entries);
    } else {
      this.entries = new Entry[0];
    }
  }

  /* package */ Metadata(Parcel in) {
    entries = new Metadata.Entry[in.readInt()];
    for (int i = 0; i < entries.length; i++) {
      entries[i] = in.readParcelable(Entry.class.getClassLoader());
    }
  }

  /**
   * Returns the number of metadata entries.
   */
  public int length() {
    return entries.length;
  }

  /**
   * Returns the entry at the specified index.
   *
   * @param index The index of the entry.
   * @return The entry at the specified index.
   */
  public Metadata.Entry get(int index) {
    return entries[index];
  }

  /**
   * Returns a copy of this metadata with the entries of the specified metadata appended. Returns
   * this instance if {@code other} is null.
   *
   * @param other The metadata that holds the entries to append. If null, this methods returns this
   *     instance.
   * @return The metadata instance with the appended entries.
   */
  public Metadata copyWithAppendedEntriesFrom(@Nullable Metadata other) {
    if (other == null) {
      return this;
    }
    return copyWithAppendedEntries(other.entries);
  }

  /**
   * Returns a copy of this metadata with the specified entries appended.
   *
   * @param entriesToAppend The entries to append.
   * @return The metadata instance with the appended entries.
   */
  public Metadata copyWithAppendedEntries(Entry... entriesToAppend) {
    @NullableType Entry[] merged = Arrays.copyOf(entries, entries.length + entriesToAppend.length);
    System.arraycopy(entriesToAppend, 0, merged, entries.length, entriesToAppend.length);
    return new Metadata(Util.castNonNullTypeArray(merged));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Metadata other = (Metadata) obj;
    return Arrays.equals(entries, other.entries);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(entries);
  }

  @Override
  public String toString() {
    return "entries=" + Arrays.toString(entries);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(entries.length);
    for (Entry entry : entries) {
      dest.writeParcelable(entry, 0);
    }
  }

  public static final Parcelable.Creator<Metadata> CREATOR =
      new Parcelable.Creator<Metadata>() {
        @Override
        public Metadata createFromParcel(Parcel in) {
          return new Metadata(in);
        }

        @Override
        public Metadata[] newArray(int size) {
          return new Metadata[size];
        }
      };
}
