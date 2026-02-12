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
package com.google.android.exoplayer2.upstream.cache;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Defines multiple mutations on metadata value which are applied atomically. This class isn't
 * thread safe.
 */
public class ContentMetadataMutations {

  /**
   * Adds a mutation to set the {@link ContentMetadata#KEY_CONTENT_LENGTH} value, or to remove any
   * existing value if {@link C#LENGTH_UNSET} is passed.
   *
   * @param mutations The mutations to modify.
   * @param length The length value, or {@link C#LENGTH_UNSET} to remove any existing entry.
   * @return The mutations instance, for convenience.
   */
  public static ContentMetadataMutations setContentLength(
      ContentMetadataMutations mutations, long length) {
    return mutations.set(ContentMetadata.KEY_CONTENT_LENGTH, length);
  }

  /**
   * Adds a mutation to set the {@link ContentMetadata#KEY_REDIRECTED_URI} value, or to remove any
   * existing entry if {@code null} is passed.
   *
   * @param mutations The mutations to modify.
   * @param uri The {@link Uri} value, or {@code null} to remove any existing entry.
   * @return The mutations instance, for convenience.
   */
  public static ContentMetadataMutations setRedirectedUri(
      ContentMetadataMutations mutations, @Nullable Uri uri) {
    if (uri == null) {
      return mutations.remove(ContentMetadata.KEY_REDIRECTED_URI);
    } else {
      return mutations.set(ContentMetadata.KEY_REDIRECTED_URI, uri.toString());
    }
  }

  private final Map<String, Object> editedValues;
  private final List<String> removedValues;

  /** Constructs a DefaultMetadataMutations. */
  public ContentMetadataMutations() {
    editedValues = new HashMap<>();
    removedValues = new ArrayList<>();
  }

  /**
   * Adds a mutation to set a metadata value. Passing {@code null} as {@code name} or {@code value}
   * isn't allowed.
   *
   * @param name The name of the metadata value.
   * @param value The value to be set.
   * @return This instance, for convenience.
   */
  public ContentMetadataMutations set(String name, String value) {
    return checkAndSet(name, value);
  }

  /**
   * Adds a mutation to set a metadata value. Passing {@code null} as {@code name} isn't allowed.
   *
   * @param name The name of the metadata value.
   * @param value The value to be set.
   * @return This instance, for convenience.
   */
  public ContentMetadataMutations set(String name, long value) {
    return checkAndSet(name, value);
  }

  /**
   * Adds a mutation to set a metadata value. Passing {@code null} as {@code name} or {@code value}
   * isn't allowed.
   *
   * @param name The name of the metadata value.
   * @param value The value to be set.
   * @return This instance, for convenience.
   */
  public ContentMetadataMutations set(String name, byte[] value) {
    return checkAndSet(name, Arrays.copyOf(value, value.length));
  }

  /**
   * Adds a mutation to remove a metadata value.
   *
   * @param name The name of the metadata value.
   * @return This instance, for convenience.
   */
  public ContentMetadataMutations remove(String name) {
    removedValues.add(name);
    editedValues.remove(name);
    return this;
  }

  /** Returns a list of names of metadata values to be removed. */
  public List<String> getRemovedValues() {
    return Collections.unmodifiableList(new ArrayList<>(removedValues));
  }

  /** Returns a map of metadata name, value pairs to be set. Values are copied.  */
  public Map<String, Object> getEditedValues() {
    HashMap<String, Object> hashMap = new HashMap<>(editedValues);
    for (Entry<String, Object> entry : hashMap.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof byte[]) {
        byte[] bytes = (byte[]) value;
        entry.setValue(Arrays.copyOf(bytes, bytes.length));
      }
    }
    return Collections.unmodifiableMap(hashMap);
  }

  private ContentMetadataMutations checkAndSet(String name, Object value) {
    editedValues.put(Assertions.checkNotNull(name), Assertions.checkNotNull(value));
    removedValues.remove(name);
    return this;
  }
}
