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
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import android.net.Uri;
import com.google.android.exoplayer2.util.Util;

/** SmoothStreaming related utility methods. */
public final class SsUtil {

  /** Returns a fixed SmoothStreaming client manifest {@link Uri}. */
  public static Uri fixManifestUri(Uri manifestUri) {
    String lastPathSegment = manifestUri.getLastPathSegment();
    if (lastPathSegment != null
        && Util.toLowerInvariant(lastPathSegment).matches("manifest(\\(.+\\))?")) {
      return manifestUri;
    }
    return Uri.withAppendedPath(manifestUri, "Manifest");
  }

  private SsUtil() {}
}
