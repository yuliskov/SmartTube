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
package com.google.android.exoplayer2.util;

import android.net.Uri;
import android.text.TextUtils;

/**
 * Utility methods for manipulating URIs.
 */
public final class UriUtil {

  /**
   * The length of arrays returned by {@link #getUriIndices(String)}.
   */
  private static final int INDEX_COUNT = 4;
  /**
   * An index into an array returned by {@link #getUriIndices(String)}.
   * <p>
   * The value at this position in the array is the index of the ':' after the scheme. Equals -1 if
   * the URI is a relative reference (no scheme). The hier-part starts at (schemeColon + 1),
   * including when the URI has no scheme.
   */
  private static final int SCHEME_COLON = 0;
  /**
   * An index into an array returned by {@link #getUriIndices(String)}.
   * <p>
   * The value at this position in the array is the index of the path part. Equals (schemeColon + 1)
   * if no authority part, (schemeColon + 3) if the authority part consists of just "//", and
   * (query) if no path part. The characters starting at this index can be "//" only if the
   * authority part is non-empty (in this case the double-slash means the first segment is empty).
   */
  private static final int PATH = 1;
  /**
   * An index into an array returned by {@link #getUriIndices(String)}.
   * <p>
   * The value at this position in the array is the index of the query part, including the '?'
   * before the query. Equals fragment if no query part, and (fragment - 1) if the query part is a
   * single '?' with no data.
   */
  private static final int QUERY = 2;
  /**
   * An index into an array returned by {@link #getUriIndices(String)}.
   * <p>
   * The value at this position in the array is the index of the fragment part, including the '#'
   * before the fragment. Equal to the length of the URI if no fragment part, and (length - 1) if
   * the fragment part is a single '#' with no data.
   */
  private static final int FRAGMENT = 3;

  private UriUtil() {}

  /**
   * Like {@link #resolve(String, String)}, but returns a {@link Uri} instead of a {@link String}.
   *
   * @param baseUri The base URI.
   * @param referenceUri The reference URI to resolve.
   */
  public static Uri resolveToUri(String baseUri, String referenceUri) {
    return Uri.parse(resolve(baseUri, referenceUri));
  }

  /**
   * Performs relative resolution of a {@code referenceUri} with respect to a {@code baseUri}.
   * <p>
   * The resolution is performed as specified by RFC-3986.
   *
   * @param baseUri The base URI.
   * @param referenceUri The reference URI to resolve.
   */
  public static String resolve(String baseUri, String referenceUri) {
    StringBuilder uri = new StringBuilder();

    // Map null onto empty string, to make the following logic simpler.
    baseUri = baseUri == null ? "" : baseUri;
    referenceUri = referenceUri == null ? "" : referenceUri;

    int[] refIndices = getUriIndices(referenceUri);
    if (refIndices[SCHEME_COLON] != -1) {
      // The reference is absolute. The target Uri is the reference.
      uri.append(referenceUri);
      removeDotSegments(uri, refIndices[PATH], refIndices[QUERY]);
      return uri.toString();
    }

    int[] baseIndices = getUriIndices(baseUri);
    if (refIndices[FRAGMENT] == 0) {
      // The reference is empty or contains just the fragment part, then the target Uri is the
      // concatenation of the base Uri without its fragment, and the reference.
      return uri.append(baseUri, 0, baseIndices[FRAGMENT]).append(referenceUri).toString();
    }

    if (refIndices[QUERY] == 0) {
      // The reference starts with the query part. The target is the base up to (but excluding) the
      // query, plus the reference.
      return uri.append(baseUri, 0, baseIndices[QUERY]).append(referenceUri).toString();
    }

    if (refIndices[PATH] != 0) {
      // The reference has authority. The target is the base scheme plus the reference.
      int baseLimit = baseIndices[SCHEME_COLON] + 1;
      uri.append(baseUri, 0, baseLimit).append(referenceUri);
      return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY]);
    }

    if (referenceUri.charAt(refIndices[PATH]) == '/') {
      // The reference path is rooted. The target is the base scheme and authority (if any), plus
      // the reference.
      uri.append(baseUri, 0, baseIndices[PATH]).append(referenceUri);
      return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY]);
    }

    // The target Uri is the concatenation of the base Uri up to (but excluding) the last segment,
    // and the reference. This can be split into 2 cases:
    if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH]
        && baseIndices[PATH] == baseIndices[QUERY]) {
      // Case 1: The base hier-part is just the authority, with an empty path. An additional '/' is
      // needed after the authority, before appending the reference.
      uri.append(baseUri, 0, baseIndices[PATH]).append('/').append(referenceUri);
      return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1);
    } else {
      // Case 2: Otherwise, find the last '/' in the base hier-part and append the reference after
      // it. If base hier-part has no '/', it could only mean that it is completely empty or
      // contains only one segment, in which case the whole hier-part is excluded and the reference
      // is appended right after the base scheme colon without an added '/'.
      int lastSlashIndex = baseUri.lastIndexOf('/', baseIndices[QUERY] - 1);
      int baseLimit = lastSlashIndex == -1 ? baseIndices[PATH] : lastSlashIndex + 1;
      uri.append(baseUri, 0, baseLimit).append(referenceUri);
      return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY]);
    }
  }

  /**
   * Removes query parameter from an Uri, if present.
   *
   * @param uri The uri.
   * @param queryParameterName The name of the query parameter.
   * @return The uri without the query parameter.
   */
  public static Uri removeQueryParameter(Uri uri, String queryParameterName) {
    Uri.Builder builder = uri.buildUpon();
    builder.clearQuery();
    for (String key : uri.getQueryParameterNames()) {
      if (!key.equals(queryParameterName)) {
        for (String value : uri.getQueryParameters(key)) {
          builder.appendQueryParameter(key, value);
        }
      }
    }
    return builder.build();
  }

  /**
   * Removes dot segments from the path of a URI.
   *
   * @param uri A {@link StringBuilder} containing the URI.
   * @param offset The index of the start of the path in {@code uri}.
   * @param limit The limit (exclusive) of the path in {@code uri}.
   */
  private static String removeDotSegments(StringBuilder uri, int offset, int limit) {
    if (offset >= limit) {
      // Nothing to do.
      return uri.toString();
    }
    if (uri.charAt(offset) == '/') {
      // If the path starts with a /, always retain it.
      offset++;
    }
    // The first character of the current path segment.
    int segmentStart = offset;
    int i = offset;
    while (i <= limit) {
      int nextSegmentStart;
      if (i == limit) {
        nextSegmentStart = i;
      } else if (uri.charAt(i) == '/') {
        nextSegmentStart = i + 1;
      } else {
        i++;
        continue;
      }
      // We've encountered the end of a segment or the end of the path. If the final segment was
      // "." or "..", remove the appropriate segments of the path.
      if (i == segmentStart + 1 && uri.charAt(segmentStart) == '.') {
        // Given "abc/def/./ghi", remove "./" to get "abc/def/ghi".
        uri.delete(segmentStart, nextSegmentStart);
        limit -= nextSegmentStart - segmentStart;
        i = segmentStart;
      } else if (i == segmentStart + 2 && uri.charAt(segmentStart) == '.'
          && uri.charAt(segmentStart + 1) == '.') {
        // Given "abc/def/../ghi", remove "def/../" to get "abc/ghi".
        int prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1;
        int removeFrom = prevSegmentStart > offset ? prevSegmentStart : offset;
        uri.delete(removeFrom, nextSegmentStart);
        limit -= nextSegmentStart - removeFrom;
        segmentStart = prevSegmentStart;
        i = prevSegmentStart;
      } else {
        i++;
        segmentStart = i;
      }
    }
    return uri.toString();
  }

  /**
   * Calculates indices of the constituent components of a URI.
   *
   * @param uriString The URI as a string.
   * @return The corresponding indices.
   */
  private static int[] getUriIndices(String uriString) {
    int[] indices = new int[INDEX_COUNT];
    if (TextUtils.isEmpty(uriString)) {
      indices[SCHEME_COLON] = -1;
      return indices;
    }

    // Determine outer structure from right to left.
    // Uri = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
    int length = uriString.length();
    int fragmentIndex = uriString.indexOf('#');
    if (fragmentIndex == -1) {
      fragmentIndex = length;
    }
    int queryIndex = uriString.indexOf('?');
    if (queryIndex == -1 || queryIndex > fragmentIndex) {
      // '#' before '?': '?' is within the fragment.
      queryIndex = fragmentIndex;
    }
    // Slashes are allowed only in hier-part so any colon after the first slash is part of the
    // hier-part, not the scheme colon separator.
    int schemeIndexLimit = uriString.indexOf('/');
    if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
      schemeIndexLimit = queryIndex;
    }
    int schemeIndex = uriString.indexOf(':');
    if (schemeIndex > schemeIndexLimit) {
      // '/' before ':'
      schemeIndex = -1;
    }

    // Determine hier-part structure: hier-part = "//" authority path / path
    // This block can also cope with schemeIndex == -1.
    boolean hasAuthority = schemeIndex + 2 < queryIndex
        && uriString.charAt(schemeIndex + 1) == '/'
        && uriString.charAt(schemeIndex + 2) == '/';
    int pathIndex;
    if (hasAuthority) {
      pathIndex = uriString.indexOf('/', schemeIndex + 3); // find first '/' after "://"
      if (pathIndex == -1 || pathIndex > queryIndex) {
        pathIndex = queryIndex;
      }
    } else {
      pathIndex = schemeIndex + 1;
    }

    indices[SCHEME_COLON] = schemeIndex;
    indices[PATH] = pathIndex;
    indices[QUERY] = queryIndex;
    indices[FRAGMENT] = fragmentIndex;
    return indices;
  }

}
