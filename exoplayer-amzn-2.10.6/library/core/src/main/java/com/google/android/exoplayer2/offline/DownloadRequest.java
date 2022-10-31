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
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Defines content to be downloaded. */
public final class DownloadRequest implements Parcelable {

  /** Thrown when the encoded request data belongs to an unsupported request type. */
  public static class UnsupportedRequestException extends IOException {}

  /** Type for progressive downloads. */
  public static final String TYPE_PROGRESSIVE = "progressive";
  /** Type for DASH downloads. */
  public static final String TYPE_DASH = "dash";
  /** Type for HLS downloads. */
  public static final String TYPE_HLS = "hls";
  /** Type for SmoothStreaming downloads. */
  public static final String TYPE_SS = "ss";

  /** The unique content id. */
  public final String id;
  /** The type of the request. */
  public final String type;
  /** The uri being downloaded. */
  public final Uri uri;
  /** Stream keys to be downloaded. If empty, all streams will be downloaded. */
  public final List<StreamKey> streamKeys;
  /**
   * Custom key for cache indexing, or null. Must be null for DASH, HLS and SmoothStreaming
   * downloads.
   */
  @Nullable public final String customCacheKey;
  /** Application defined data associated with the download. May be empty. */
  public final byte[] data;

  /**
   * @param id See {@link #id}.
   * @param type See {@link #type}.
   * @param uri See {@link #uri}.
   * @param streamKeys See {@link #streamKeys}.
   * @param customCacheKey See {@link #customCacheKey}.
   * @param data See {@link #data}.
   */
  public DownloadRequest(
      String id,
      String type,
      Uri uri,
      List<StreamKey> streamKeys,
      @Nullable String customCacheKey,
      @Nullable byte[] data) {
    if (TYPE_DASH.equals(type) || TYPE_HLS.equals(type) || TYPE_SS.equals(type)) {
      Assertions.checkArgument(
          customCacheKey == null, "customCacheKey must be null for type: " + type);
    }
    this.id = id;
    this.type = type;
    this.uri = uri;
    ArrayList<StreamKey> mutableKeys = new ArrayList<>(streamKeys);
    Collections.sort(mutableKeys);
    this.streamKeys = Collections.unmodifiableList(mutableKeys);
    this.customCacheKey = customCacheKey;
    this.data = data != null ? Arrays.copyOf(data, data.length) : Util.EMPTY_BYTE_ARRAY;
  }

  /* package */ DownloadRequest(Parcel in) {
    id = castNonNull(in.readString());
    type = castNonNull(in.readString());
    uri = Uri.parse(castNonNull(in.readString()));
    int streamKeyCount = in.readInt();
    ArrayList<StreamKey> mutableStreamKeys = new ArrayList<>(streamKeyCount);
    for (int i = 0; i < streamKeyCount; i++) {
      mutableStreamKeys.add(in.readParcelable(StreamKey.class.getClassLoader()));
    }
    streamKeys = Collections.unmodifiableList(mutableStreamKeys);
    customCacheKey = in.readString();
    data = new byte[in.readInt()];
    in.readByteArray(data);
  }

  /**
   * Returns a copy with the specified ID.
   *
   * @param id The ID of the copy.
   * @return The copy with the specified ID.
   */
  public DownloadRequest copyWithId(String id) {
    return new DownloadRequest(id, type, uri, streamKeys, customCacheKey, data);
  }

  /**
   * Returns the result of merging {@code newRequest} into this request. The requests must have the
   * same {@link #id} and {@link #type}.
   *
   * <p>If the requests have different {@link #uri}, {@link #customCacheKey} and {@link #data}
   * values, then those from the request being merged are included in the result.
   *
   * @param newRequest The request being merged.
   * @return The merged result.
   * @throws IllegalArgumentException If the requests do not have the same {@link #id} and {@link
   *     #type}.
   */
  public DownloadRequest copyWithMergedRequest(DownloadRequest newRequest) {
    Assertions.checkArgument(id.equals(newRequest.id));
    Assertions.checkArgument(type.equals(newRequest.type));
    List<StreamKey> mergedKeys;
    if (streamKeys.isEmpty() || newRequest.streamKeys.isEmpty()) {
      // If either streamKeys is empty then all streams should be downloaded.
      mergedKeys = Collections.emptyList();
    } else {
      mergedKeys = new ArrayList<>(streamKeys);
      for (int i = 0; i < newRequest.streamKeys.size(); i++) {
        StreamKey newKey = newRequest.streamKeys.get(i);
        if (!mergedKeys.contains(newKey)) {
          mergedKeys.add(newKey);
        }
      }
    }
    return new DownloadRequest(
        id, type, newRequest.uri, mergedKeys, newRequest.customCacheKey, newRequest.data);
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof DownloadRequest)) {
      return false;
    }
    DownloadRequest that = (DownloadRequest) o;
    return id.equals(that.id)
        && type.equals(that.type)
        && uri.equals(that.uri)
        && streamKeys.equals(that.streamKeys)
        && Util.areEqual(customCacheKey, that.customCacheKey)
        && Arrays.equals(data, that.data);
  }

  @Override
  public final int hashCode() {
    int result = type.hashCode();
    result = 31 * result + id.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + uri.hashCode();
    result = 31 * result + streamKeys.hashCode();
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(type);
    dest.writeString(uri.toString());
    dest.writeInt(streamKeys.size());
    for (int i = 0; i < streamKeys.size(); i++) {
      dest.writeParcelable(streamKeys.get(i), /* parcelableFlags= */ 0);
    }
    dest.writeString(customCacheKey);
    dest.writeInt(data.length);
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<DownloadRequest> CREATOR =
      new Parcelable.Creator<DownloadRequest>() {

        @Override
        public DownloadRequest createFromParcel(Parcel in) {
          return new DownloadRequest(in);
        }

        @Override
        public DownloadRequest[] newArray(int size) {
          return new DownloadRequest[size];
        }
      };
}
