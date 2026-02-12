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

/**
 * Interface for an immutable snapshot of keyed metadata.
 */
public interface ContentMetadata {

  /**
   * Prefix for custom metadata keys. Applications can use keys starting with this prefix without
   * any risk of their keys colliding with ones defined by the ExoPlayer library.
   */
  @SuppressWarnings("unused")
  String KEY_CUSTOM_PREFIX = "custom_";
  /** Key for redirected uri (type: String). */
  String KEY_REDIRECTED_URI = "exo_redir";
  /** Key for content length in bytes (type: long). */
  String KEY_CONTENT_LENGTH = "exo_len";

  /**
   * Returns a metadata value.
   *
   * @param key Key of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  @Nullable
  byte[] get(String key, @Nullable byte[] defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param key Key of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  @Nullable
  String get(String key, @Nullable String defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param key Key of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  long get(String key, long defaultValue);

  /** Returns whether the metadata is available. */
  boolean contains(String key);

  /**
   * Returns the value stored under {@link #KEY_CONTENT_LENGTH}, or {@link C#LENGTH_UNSET} if not
   * set.
   */
  static long getContentLength(ContentMetadata contentMetadata) {
    return contentMetadata.get(KEY_CONTENT_LENGTH, C.LENGTH_UNSET);
  }

  /**
   * Returns the value stored under {@link #KEY_REDIRECTED_URI} as a {@link Uri}, or {code null} if
   * not set.
   */
  @Nullable
  static Uri getRedirectedUri(ContentMetadata contentMetadata) {
    String redirectedUri = contentMetadata.get(KEY_REDIRECTED_URI, (String) null);
    return redirectedUri == null ? null : Uri.parse(redirectedUri);
  }
}
