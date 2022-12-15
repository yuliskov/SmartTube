/*
 * Copyright (C) 2014 The Android Open Source Project
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;

/**
 * A descriptor, as defined by ISO 23009-1, 2nd edition, 5.8.2.
 */
public final class Descriptor {

  /**
   * The scheme URI.
   */
  @NonNull public final String schemeIdUri;
  /**
   * The value, or null.
   */
  @Nullable public final String value;
  /**
   * The identifier, or null.
   */
  @Nullable public final String id;

  /**
   * @param schemeIdUri The scheme URI.
   * @param value The value, or null.
   * @param id The identifier, or null.
   */
  public Descriptor(@NonNull String schemeIdUri, @Nullable String value, @Nullable String id) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
    this.id = id;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Descriptor other = (Descriptor) obj;
    return Util.areEqual(schemeIdUri, other.schemeIdUri) && Util.areEqual(value, other.value)
        && Util.areEqual(id, other.id);
  }

  @Override
  public int hashCode() {
    int result = (schemeIdUri != null ? schemeIdUri.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (id != null ? id.hashCode() : 0);
    return result;
  }

}
