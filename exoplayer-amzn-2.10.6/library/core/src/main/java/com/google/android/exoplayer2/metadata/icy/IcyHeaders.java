/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.icy;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.List;
import java.util.Map;

/** ICY headers. */
public final class IcyHeaders implements Metadata.Entry {

  public static final String REQUEST_HEADER_ENABLE_METADATA_NAME = "Icy-MetaData";
  public static final String REQUEST_HEADER_ENABLE_METADATA_VALUE = "1";

  private static final String TAG = "IcyHeaders";

  private static final String RESPONSE_HEADER_BITRATE = "icy-br";
  private static final String RESPONSE_HEADER_GENRE = "icy-genre";
  private static final String RESPONSE_HEADER_NAME = "icy-name";
  private static final String RESPONSE_HEADER_URL = "icy-url";
  private static final String RESPONSE_HEADER_PUB = "icy-pub";
  private static final String RESPONSE_HEADER_METADATA_INTERVAL = "icy-metaint";

  /**
   * Parses {@link IcyHeaders} from response headers.
   *
   * @param responseHeaders The response headers.
   * @return The parsed {@link IcyHeaders}, or {@code null} if no ICY headers were present.
   */
  @Nullable
  public static IcyHeaders parse(Map<String, List<String>> responseHeaders) {
    boolean icyHeadersPresent = false;
    int bitrate = Format.NO_VALUE;
    String genre = null;
    String name = null;
    String url = null;
    boolean isPublic = false;
    int metadataInterval = C.LENGTH_UNSET;

    List<String> headers = responseHeaders.get(RESPONSE_HEADER_BITRATE);
    if (headers != null) {
      String bitrateHeader = headers.get(0);
      try {
        bitrate = Integer.parseInt(bitrateHeader) * 1000;
        if (bitrate > 0) {
          icyHeadersPresent = true;
        } else {
          Log.w(TAG, "Invalid bitrate: " + bitrateHeader);
          bitrate = Format.NO_VALUE;
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, "Invalid bitrate header: " + bitrateHeader);
      }
    }
    headers = responseHeaders.get(RESPONSE_HEADER_GENRE);
    if (headers != null) {
      genre = headers.get(0);
      icyHeadersPresent = true;
    }
    headers = responseHeaders.get(RESPONSE_HEADER_NAME);
    if (headers != null) {
      name = headers.get(0);
      icyHeadersPresent = true;
    }
    headers = responseHeaders.get(RESPONSE_HEADER_URL);
    if (headers != null) {
      url = headers.get(0);
      icyHeadersPresent = true;
    }
    headers = responseHeaders.get(RESPONSE_HEADER_PUB);
    if (headers != null) {
      isPublic = headers.get(0).equals("1");
      icyHeadersPresent = true;
    }
    headers = responseHeaders.get(RESPONSE_HEADER_METADATA_INTERVAL);
    if (headers != null) {
      String metadataIntervalHeader = headers.get(0);
      try {
        metadataInterval = Integer.parseInt(metadataIntervalHeader);
        if (metadataInterval > 0) {
          icyHeadersPresent = true;
        } else {
          Log.w(TAG, "Invalid metadata interval: " + metadataIntervalHeader);
          metadataInterval = C.LENGTH_UNSET;
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, "Invalid metadata interval: " + metadataIntervalHeader);
      }
    }
    return icyHeadersPresent
        ? new IcyHeaders(bitrate, genre, name, url, isPublic, metadataInterval)
        : null;
  }

  /**
   * Bitrate in bits per second ({@code (icy-br * 1000)}), or {@link Format#NO_VALUE} if the header
   * was not present.
   */
  public final int bitrate;
  /** The genre ({@code icy-genre}). */
  @Nullable public final String genre;
  /** The stream name ({@code icy-name}). */
  @Nullable public final String name;
  /** The URL of the radio station ({@code icy-url}). */
  @Nullable public final String url;
  /**
   * Whether the radio station is listed ({@code icy-pub}), or {@code false} if the header was not
   * present.
   */
  public final boolean isPublic;

  /**
   * The interval in bytes between metadata chunks ({@code icy-metaint}), or {@link C#LENGTH_UNSET}
   * if the header was not present.
   */
  public final int metadataInterval;

  /**
   * @param bitrate See {@link #bitrate}.
   * @param genre See {@link #genre}.
   * @param name See {@link #name See}.
   * @param url See {@link #url}.
   * @param isPublic See {@link #isPublic}.
   * @param metadataInterval See {@link #metadataInterval}.
   */
  public IcyHeaders(
      int bitrate,
      @Nullable String genre,
      @Nullable String name,
      @Nullable String url,
      boolean isPublic,
      int metadataInterval) {
    Assertions.checkArgument(metadataInterval == C.LENGTH_UNSET || metadataInterval > 0);
    this.bitrate = bitrate;
    this.genre = genre;
    this.name = name;
    this.url = url;
    this.isPublic = isPublic;
    this.metadataInterval = metadataInterval;
  }

  /* package */ IcyHeaders(Parcel in) {
    bitrate = in.readInt();
    genre = in.readString();
    name = in.readString();
    url = in.readString();
    isPublic = Util.readBoolean(in);
    metadataInterval = in.readInt();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    IcyHeaders other = (IcyHeaders) obj;
    return bitrate == other.bitrate
        && Util.areEqual(genre, other.genre)
        && Util.areEqual(name, other.name)
        && Util.areEqual(url, other.url)
        && isPublic == other.isPublic
        && metadataInterval == other.metadataInterval;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + bitrate;
    result = 31 * result + (genre != null ? genre.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    result = 31 * result + (isPublic ? 1 : 0);
    result = 31 * result + metadataInterval;
    return result;
  }

  @Override
  public String toString() {
    return "IcyHeaders: name=\""
        + name
        + "\", genre=\""
        + genre
        + "\", bitrate="
        + bitrate
        + ", metadataInterval="
        + metadataInterval;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(bitrate);
    dest.writeString(genre);
    dest.writeString(name);
    dest.writeString(url);
    Util.writeBoolean(dest, isPublic);
    dest.writeInt(metadataInterval);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<IcyHeaders> CREATOR =
      new Parcelable.Creator<IcyHeaders>() {

        @Override
        public IcyHeaders createFromParcel(Parcel in) {
          return new IcyHeaders(in);
        }

        @Override
        public IcyHeaders[] newArray(int size) {
          return new IcyHeaders[size];
        }
      };
}
