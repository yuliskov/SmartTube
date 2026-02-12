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
package com.google.android.exoplayer2.drm;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Initialization data for one or more DRM schemes.
 */
public final class DrmInitData implements Comparator<SchemeData>, Parcelable {

  /**
   * Merges {@link DrmInitData} obtained from a media manifest and a media stream.
   *
   * <p>The result is generated as follows.
   *
   * <ol>
   *   <li>Include all {@link SchemeData}s from {@code manifestData} where {@link
   *       SchemeData#hasData()} is true.
   *   <li>Include all {@link SchemeData}s in {@code mediaData} where {@link SchemeData#hasData()}
   *       is true and for which we did not include an entry from the manifest targeting the same
   *       UUID.
   *   <li>If available, the scheme type from the manifest is used. If not, the scheme type from the
   *       media is used.
   * </ol>
   *
   * @param manifestData DRM session acquisition data obtained from the manifest.
   * @param mediaData DRM session acquisition data obtained from the media.
   * @return A {@link DrmInitData} obtained from merging a media manifest and a media stream.
   */
  public static @Nullable DrmInitData createSessionCreationData(
      @Nullable DrmInitData manifestData, @Nullable DrmInitData mediaData) {
    ArrayList<SchemeData> result = new ArrayList<>();
    String schemeType = null;
    if (manifestData != null) {
      schemeType = manifestData.schemeType;
      for (SchemeData data : manifestData.schemeDatas) {
        if (data.hasData()) {
          result.add(data);
        }
      }
    }

    if (mediaData != null) {
      if (schemeType == null) {
        schemeType = mediaData.schemeType;
      }
      int manifestDatasCount = result.size();
      for (SchemeData data : mediaData.schemeDatas) {
        if (data.hasData() && !containsSchemeDataWithUuid(result, manifestDatasCount, data.uuid)) {
          result.add(data);
        }
      }
    }

    return result.isEmpty() ? null : new DrmInitData(schemeType, result);
  }

  private final SchemeData[] schemeDatas;

  // Lazily initialized hashcode.
  private int hashCode;

  /** The protection scheme type, or null if not applicable or unknown. */
  public final @Nullable String schemeType;

  /**
   * Number of {@link SchemeData}s.
   */
  public final int schemeDataCount;

  /**
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(List<SchemeData> schemeDatas) {
    this(null, false, schemeDatas.toArray(new SchemeData[0]));
  }

  /**
   * @param schemeType See {@link #schemeType}.
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(@Nullable String schemeType, List<SchemeData> schemeDatas) {
    this(schemeType, false, schemeDatas.toArray(new SchemeData[0]));
  }

  /**
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(SchemeData... schemeDatas) {
    this(null, schemeDatas);
  }

  /**
   * @param schemeType See {@link #schemeType}.
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(@Nullable String schemeType, SchemeData... schemeDatas) {
    this(schemeType, true, schemeDatas);
  }

  private DrmInitData(@Nullable String schemeType, boolean cloneSchemeDatas,
      SchemeData... schemeDatas) {
    this.schemeType = schemeType;
    if (cloneSchemeDatas) {
      schemeDatas = schemeDatas.clone();
    }
    this.schemeDatas = schemeDatas;
    schemeDataCount = schemeDatas.length;
    // Sorting ensures that universal scheme data (i.e. data that applies to all schemes) is matched
    // last. It's also required by the equals and hashcode implementations.
    Arrays.sort(this.schemeDatas, this);
  }

  /* package */
  DrmInitData(Parcel in) {
    schemeType = in.readString();
    schemeDatas = Util.castNonNull(in.createTypedArray(SchemeData.CREATOR));
    schemeDataCount = schemeDatas.length;
  }

  /**
   * Retrieves data for a given DRM scheme, specified by its UUID.
   *
   * @deprecated Use {@link #get(int)} and {@link SchemeData#matches(UUID)} instead.
   * @param uuid The DRM scheme's UUID.
   * @return The initialization data for the scheme, or null if the scheme is not supported.
   */
  @Deprecated
  public @Nullable SchemeData get(UUID uuid) {
    for (SchemeData schemeData : schemeDatas) {
      if (schemeData.matches(uuid)) {
        return schemeData;
      }
    }
    return null;
  }

  /**
   * Retrieves the {@link SchemeData} at a given index.
   *
   * @param index The index of the scheme to return. Must not exceed {@link #schemeDataCount}.
   * @return The {@link SchemeData} at the specified index.
   */
  public SchemeData get(int index) {
    return schemeDatas[index];
  }

  /**
   * Returns a copy with the specified protection scheme type.
   *
   * @param schemeType A protection scheme type. May be null.
   * @return A copy with the specified protection scheme type.
   */
  public DrmInitData copyWithSchemeType(@Nullable String schemeType) {
    if (Util.areEqual(this.schemeType, schemeType)) {
      return this;
    }
    return new DrmInitData(schemeType, false, schemeDatas);
  }

  /**
   * Returns an instance containing the {@link #schemeDatas} from both this and {@code other}. The
   * {@link #schemeType} of the instances being merged must either match, or at least one scheme
   * type must be {@code null}.
   *
   * @param drmInitData The instance to merge.
   * @return The merged result.
   */
  public DrmInitData merge(DrmInitData drmInitData) {
    Assertions.checkState(
        schemeType == null
            || drmInitData.schemeType == null
            || TextUtils.equals(schemeType, drmInitData.schemeType));
    String mergedSchemeType = schemeType != null ? this.schemeType : drmInitData.schemeType;
    SchemeData[] mergedSchemeDatas =
        Util.nullSafeArrayConcatenation(schemeDatas, drmInitData.schemeDatas);
    return new DrmInitData(mergedSchemeType, mergedSchemeDatas);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = (schemeType == null ? 0 : schemeType.hashCode());
      result = 31 * result + Arrays.hashCode(schemeDatas);
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
    DrmInitData other = (DrmInitData) obj;
    return Util.areEqual(schemeType, other.schemeType)
        && Arrays.equals(schemeDatas, other.schemeDatas);
  }

  @Override
  public int compare(SchemeData first, SchemeData second) {
    return C.UUID_NIL.equals(first.uuid) ? (C.UUID_NIL.equals(second.uuid) ? 0 : 1)
        : first.uuid.compareTo(second.uuid);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(schemeType);
    dest.writeTypedArray(schemeDatas, 0);
  }

  public static final Parcelable.Creator<DrmInitData> CREATOR =
      new Parcelable.Creator<DrmInitData>() {

    @Override
    public DrmInitData createFromParcel(Parcel in) {
      return new DrmInitData(in);
    }

    @Override
    public DrmInitData[] newArray(int size) {
      return new DrmInitData[size];
    }

  };

  // Internal methods.

  private static boolean containsSchemeDataWithUuid(
      ArrayList<SchemeData> datas, int limit, UUID uuid) {
    for (int i = 0; i < limit; i++) {
      if (datas.get(i).uuid.equals(uuid)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Scheme initialization data.
   */
  public static final class SchemeData implements Parcelable {

    // Lazily initialized hashcode.
    private int hashCode;

    /**
     * The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is universal (i.e.
     * applies to all schemes).
     */
    private final UUID uuid;
    /** The URL of the server to which license requests should be made. May be null if unknown. */
    public final @Nullable String licenseServerUrl;
    /** The mimeType of {@link #data}. */
    public final String mimeType;
    /** The initialization data. May be null for scheme support checks only. */
    public final @Nullable byte[] data;
    /**
     * Whether secure decryption is required.
     */
    public final boolean requiresSecureDecryption;

    /**
     * @param uuid The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *     universal (i.e. applies to all schemes).
     * @param mimeType See {@link #mimeType}.
     * @param data See {@link #data}.
     */
    public SchemeData(UUID uuid, String mimeType, @Nullable byte[] data) {
      this(uuid, mimeType, data, false);
    }

    /**
     * @param uuid The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *     universal (i.e. applies to all schemes).
     * @param mimeType See {@link #mimeType}.
     * @param data See {@link #data}.
     * @param requiresSecureDecryption See {@link #requiresSecureDecryption}.
     */
    public SchemeData(
        UUID uuid, String mimeType, @Nullable byte[] data, boolean requiresSecureDecryption) {
      this(uuid, /* licenseServerUrl= */ null, mimeType, data, requiresSecureDecryption);
    }

    /**
     * @param uuid The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *     universal (i.e. applies to all schemes).
     * @param licenseServerUrl See {@link #licenseServerUrl}.
     * @param mimeType See {@link #mimeType}.
     * @param data See {@link #data}.
     * @param requiresSecureDecryption See {@link #requiresSecureDecryption}.
     */
    public SchemeData(
        UUID uuid,
        @Nullable String licenseServerUrl,
        String mimeType,
        @Nullable byte[] data,
        boolean requiresSecureDecryption) {
      this.uuid = Assertions.checkNotNull(uuid);
      this.licenseServerUrl = licenseServerUrl;
      this.mimeType = Assertions.checkNotNull(mimeType);
      this.data = data;
      this.requiresSecureDecryption = requiresSecureDecryption;
    }

    /* package */ SchemeData(Parcel in) {
      uuid = new UUID(in.readLong(), in.readLong());
      licenseServerUrl = in.readString();
      mimeType = Util.castNonNull(in.readString());
      data = in.createByteArray();
      requiresSecureDecryption = in.readByte() != 0;
    }

    /**
     * Returns whether this initialization data applies to the specified scheme.
     *
     * @param schemeUuid The scheme {@link UUID}.
     * @return Whether this initialization data applies to the specified scheme.
     */
    public boolean matches(UUID schemeUuid) {
      return C.UUID_NIL.equals(uuid) || schemeUuid.equals(uuid);
    }

    /**
     * Returns whether this {@link SchemeData} can be used to replace {@code other}.
     *
     * @param other A {@link SchemeData}.
     * @return Whether this {@link SchemeData} can be used to replace {@code other}.
     */
    public boolean canReplace(SchemeData other) {
      return hasData() && !other.hasData() && matches(other.uuid);
    }

    /**
     * Returns whether {@link #data} is non-null.
     */
    public boolean hasData() {
      return data != null;
    }

    /**
     * Returns a copy of this instance with the specified data.
     *
     * @param data The data to include in the copy.
     * @return The new instance.
     */
    public SchemeData copyWithData(@Nullable byte[] data) {
      return new SchemeData(uuid, licenseServerUrl, mimeType, data, requiresSecureDecryption);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof SchemeData)) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      SchemeData other = (SchemeData) obj;
      return Util.areEqual(licenseServerUrl, other.licenseServerUrl)
          && Util.areEqual(mimeType, other.mimeType)
          && Util.areEqual(uuid, other.uuid)
          && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        int result = uuid.hashCode();
        result = 31 * result + (licenseServerUrl == null ? 0 : licenseServerUrl.hashCode());
        result = 31 * result + mimeType.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        hashCode = result;
      }
      return hashCode;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(uuid.getMostSignificantBits());
      dest.writeLong(uuid.getLeastSignificantBits());
      dest.writeString(licenseServerUrl);
      dest.writeString(mimeType);
      dest.writeByteArray(data);
      dest.writeByte((byte) (requiresSecureDecryption ? 1 : 0));
    }

    @SuppressWarnings("hiding")
    public static final Parcelable.Creator<SchemeData> CREATOR =
        new Parcelable.Creator<SchemeData>() {

      @Override
      public SchemeData createFromParcel(Parcel in) {
        return new SchemeData(in);
      }

      @Override
      public SchemeData[] newArray(int size) {
        return new SchemeData[size];
      }

    };

  }

}
