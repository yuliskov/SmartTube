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
package com.google.android.exoplayer2.source.dash.manifest;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.UriUtil;

/**
 * Defines a range of data located at a reference uri.
 */
public final class RangedUri {

  /**
   * The (zero based) index of the first byte of the range.
   */
  public final long start;

  /**
   * The length of the range, or {@link C#LENGTH_UNSET} to indicate that the range is unbounded.
   */
  public final long length;

  private final String referenceUri;

  private int hashCode;

  /**
   * Constructs an ranged uri.
   *
   * @param referenceUri The reference uri.
   * @param start The (zero based) index of the first byte of the range.
   * @param length The length of the range, or {@link C#LENGTH_UNSET} to indicate that the range is
   *     unbounded.
   */
  public RangedUri(@Nullable String referenceUri, long start, long length) {
    this.referenceUri = referenceUri == null ? "" : referenceUri;
    this.start = start;
    this.length = length;
  }

  /**
   * Returns the resolved {@link Uri} represented by the instance.
   *
   * @param baseUri The base Uri.
   * @return The {@link Uri} represented by the instance.
   */
  public Uri resolveUri(String baseUri) {
    return UriUtil.resolveToUri(baseUri, referenceUri);
  }

  /**
   * Returns the resolved uri represented by the instance as a string.
   *
   * @param baseUri The base Uri.
   * @return The uri represented by the instance.
   */
  public String resolveUriString(String baseUri) {
    return UriUtil.resolve(baseUri, referenceUri);
  }

  /**
   * Attempts to merge this {@link RangedUri} with another and an optional common base uri.
   *
   * <p>A merge is successful if both instances define the same {@link Uri} after resolution with
   * the base uri, and if one starts the byte after the other ends, forming a contiguous region with
   * no overlap.
   *
   * <p>If {@code other} is null then the merge is considered unsuccessful, and null is returned.
   *
   * @param other The {@link RangedUri} to merge.
   * @param baseUri The optional base Uri.
   * @return The merged {@link RangedUri} if the merge was successful. Null otherwise.
   */
  public @Nullable RangedUri attemptMerge(@Nullable RangedUri other, String baseUri) {
    final String resolvedUri = resolveUriString(baseUri);
    if (other == null || !resolvedUri.equals(other.resolveUriString(baseUri))) {
      return null;
    } else if (length != C.LENGTH_UNSET && start + length == other.start) {
      return new RangedUri(resolvedUri, start,
          other.length == C.LENGTH_UNSET ? C.LENGTH_UNSET : length + other.length);
    } else if (other.length != C.LENGTH_UNSET && other.start + other.length == start) {
      return new RangedUri(resolvedUri, other.start,
          length == C.LENGTH_UNSET ? C.LENGTH_UNSET : other.length + length);
    } else {
      return null;
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (int) start;
      result = 31 * result + (int) length;
      result = 31 * result + referenceUri.hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RangedUri other = (RangedUri) obj;
    return this.start == other.start
        && this.length == other.length
        && referenceUri.equals(other.referenceUri);
  }

  @Override
  public String toString() {
    return "RangedUri("
        + "referenceUri="
        + referenceUri
        + ", start="
        + start
        + ", length="
        + length
        + ")";
  }
}
