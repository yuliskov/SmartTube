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
package com.google.android.exoplayer2.trackselection;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;

/** Constraint parameters for track selection. */
public class TrackSelectionParameters implements Parcelable {

  /**
   * A builder for {@link TrackSelectionParameters}. See the {@link TrackSelectionParameters}
   * documentation for explanations of the parameters that can be configured using this builder.
   */
  public static class Builder {

    // Audio
    @Nullable /* package */ String preferredAudioLanguage;
    // Text
    @Nullable /* package */ String preferredTextLanguage;
    @C.RoleFlags /* package */ int preferredTextRoleFlags;
    /* package */ boolean selectUndeterminedTextLanguage;
    @C.SelectionFlags /* package */ int disabledTextTrackSelectionFlags;

    /** Creates a builder with default initial values. */
    public Builder() {
      this(DEFAULT);
    }

    /**
     * @param initialValues The {@link TrackSelectionParameters} from which the initial values of
     *     the builder are obtained.
     */
    /* package */ Builder(TrackSelectionParameters initialValues) {
      // Audio
      preferredAudioLanguage = initialValues.preferredAudioLanguage;
      // Text
      preferredTextLanguage = initialValues.preferredTextLanguage;
      preferredTextRoleFlags = initialValues.preferredTextRoleFlags;
      selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
      disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
    }

    /**
     * Sets the preferred language for audio and forced text tracks.
     *
     * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track, or the first track if there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      this.preferredAudioLanguage = preferredAudioLanguage;
      return this;
    }

    // Text

    /**
     * Sets the preferred language for text tracks.
     *
     * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track if there is one, or no track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      this.preferredTextLanguage = preferredTextLanguage;
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for text tracks.
     *
     * @param preferredTextRoleFlags Preferred text role flags.
     * @return This builder.
     */
    public Builder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      this.preferredTextRoleFlags = preferredTextRoleFlags;
      return this;
    }

    /**
     * Sets whether a text track with undetermined language should be selected if no track with
     * {@link #setPreferredTextLanguage(String)} is available, or if the preferred language is
     * unset.
     *
     * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
     *     be selected if no preferred language track is available.
     * @return This builder.
     */
    public Builder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    /**
     * Sets a bitmask of selection flags that are disabled for text track selections.
     *
     * @param disabledTextTrackSelectionFlags A bitmask of {@link C.SelectionFlags} that are
     *     disabled for text track selections.
     * @return This builder.
     */
    public Builder setDisabledTextTrackSelectionFlags(
        @C.SelectionFlags int disabledTextTrackSelectionFlags) {
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      return this;
    }

    /** Builds a {@link TrackSelectionParameters} instance with the selected values. */
    public TrackSelectionParameters build() {
      return new TrackSelectionParameters(
          // Audio
          preferredAudioLanguage,
          // Text
          preferredTextLanguage,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags);
    }
  }

  /** An instance with default values. */
  public static final TrackSelectionParameters DEFAULT = new TrackSelectionParameters();

  /**
   * The preferred language for audio and forced text tracks, as an ISO 639-2/T tag. {@code null}
   * selects the default track, or the first track if there's no default. The default value is
   * {@code null}.
   */
  @Nullable public final String preferredAudioLanguage;
  // Text
  /**
   * The preferred language for text tracks as an ISO 639-2/T tag. {@code null} selects the default
   * track if there is one, or no track otherwise. The default value is {@code null}.
   */
  @Nullable public final String preferredTextLanguage;
  /**
   * The preferred {@link C.RoleFlags} for text tracks. {@code 0} selects the default track if there
   * is one, or no track otherwise. The default value is {@code 0}.
   */
  @C.RoleFlags public final int preferredTextRoleFlags;
  /**
   * Whether a text track with undetermined language should be selected if no track with {@link
   * #preferredTextLanguage} is available, or if {@link #preferredTextLanguage} is unset. The
   * default value is {@code false}.
   */
  public final boolean selectUndeterminedTextLanguage;
  /**
   * Bitmask of selection flags that are disabled for text track selections. See {@link
   * C.SelectionFlags}. The default value is {@code 0} (i.e. no flags).
   */
  @C.SelectionFlags public final int disabledTextTrackSelectionFlags;

  /* package */ TrackSelectionParameters() {
    this(
        /* preferredAudioLanguage= */ null,
        // Text
        /* preferredTextLanguage= */ null,
        /* preferredTextRoleFlags= */ 0,
        /* selectUndeterminedTextLanguage= */ false,
        /* disabledTextTrackSelectionFlags= */ 0);
  }

  /* package */ TrackSelectionParameters(
      @Nullable String preferredAudioLanguage,
      @Nullable String preferredTextLanguage,
      @C.RoleFlags int preferredTextRoleFlags,
      boolean selectUndeterminedTextLanguage,
      @C.SelectionFlags int disabledTextTrackSelectionFlags) {
    // Audio
    this.preferredAudioLanguage = Util.normalizeLanguageCode(preferredAudioLanguage);
    // Text
    this.preferredTextLanguage = Util.normalizeLanguageCode(preferredTextLanguage);
    this.preferredTextRoleFlags = preferredTextRoleFlags;
    this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
    this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
  }

  /* package */ TrackSelectionParameters(Parcel in) {
    // Audio
    this.preferredAudioLanguage = in.readString();
    // Text
    this.preferredTextLanguage = in.readString();
    this.preferredTextRoleFlags = in.readInt();
    this.selectUndeterminedTextLanguage = Util.readBoolean(in);
    this.disabledTextTrackSelectionFlags = in.readInt();
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionParameters other = (TrackSelectionParameters) obj;
    return TextUtils.equals(preferredAudioLanguage, other.preferredAudioLanguage)
        // Text
        && TextUtils.equals(preferredTextLanguage, other.preferredTextLanguage)
        && preferredTextRoleFlags == other.preferredTextRoleFlags
        && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
        && disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags;
  }

  @Override
  public int hashCode() {
    int result = 1;
    // Audio
    result = 31 * result + (preferredAudioLanguage == null ? 0 : preferredAudioLanguage.hashCode());
    // Text
    result = 31 * result + (preferredTextLanguage == null ? 0 : preferredTextLanguage.hashCode());
    result = 31 * result + preferredTextRoleFlags;
    result = 31 * result + (selectUndeterminedTextLanguage ? 1 : 0);
    result = 31 * result + disabledTextTrackSelectionFlags;
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    // Audio
    dest.writeString(preferredAudioLanguage);
    // Text
    dest.writeString(preferredTextLanguage);
    dest.writeInt(preferredTextRoleFlags);
    Util.writeBoolean(dest, selectUndeterminedTextLanguage);
    dest.writeInt(disabledTextTrackSelectionFlags);
  }

  public static final Creator<TrackSelectionParameters> CREATOR =
      new Creator<TrackSelectionParameters>() {

        @Override
        public TrackSelectionParameters createFromParcel(Parcel in) {
          return new TrackSelectionParameters(in);
        }

        @Override
        public TrackSelectionParameters[] newArray(int size) {
          return new TrackSelectionParameters[size];
        }
      };
}
