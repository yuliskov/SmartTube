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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builder for {@link Download}.
 *
 * <p>Defines default values for each field (except {@code id}) to facilitate {@link Download}
 * creation for tests. Tests must avoid depending on the default values but explicitly set tested
 * parameters during test initialization.
 */
/* package */ final class DownloadBuilder {

  private final DownloadProgress progress;

  private String id;
  private String type;
  private Uri uri;
  private List<StreamKey> streamKeys;
  @Nullable private String cacheKey;
  private byte[] customMetadata;

  @Download.State private int state;
  private long startTimeMs;
  private long updateTimeMs;
  private long contentLength;
  private int stopReason;
  private int failureReason;

  /* package */ DownloadBuilder(String id) {
    this(
        id,
        "type",
        Uri.parse("uri"),
        /* streamKeys= */ Collections.emptyList(),
        /* cacheKey= */ null,
        new byte[0]);
  }

  /* package */ DownloadBuilder(DownloadRequest request) {
    this(
        request.id,
        request.type,
        request.uri,
        request.streamKeys,
        request.customCacheKey,
        request.data);
  }

  /* package */ DownloadBuilder(
      String id,
      String type,
      Uri uri,
      List<StreamKey> streamKeys,
      String cacheKey,
      byte[] customMetadata) {
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.streamKeys = streamKeys;
    this.cacheKey = cacheKey;
    this.customMetadata = customMetadata;
    this.state = Download.STATE_QUEUED;
    this.contentLength = C.LENGTH_UNSET;
    this.failureReason = Download.FAILURE_REASON_NONE;
    this.progress = new DownloadProgress();
  }

  public DownloadBuilder setId(String id) {
    this.id = id;
    return this;
  }

  public DownloadBuilder setType(String type) {
    this.type = type;
    return this;
  }

  public DownloadBuilder setUri(String uri) {
    this.uri = Uri.parse(uri);
    return this;
  }

  public DownloadBuilder setUri(Uri uri) {
    this.uri = uri;
    return this;
  }

  public DownloadBuilder setCacheKey(@Nullable String cacheKey) {
    this.cacheKey = cacheKey;
    return this;
  }

  public DownloadBuilder setState(@Download.State int state) {
    this.state = state;
    return this;
  }

  public DownloadBuilder setPercentDownloaded(float percentDownloaded) {
    progress.percentDownloaded = percentDownloaded;
    return this;
  }

  public DownloadBuilder setBytesDownloaded(long bytesDownloaded) {
    progress.bytesDownloaded = bytesDownloaded;
    return this;
  }

  public DownloadBuilder setContentLength(long contentLength) {
    this.contentLength = contentLength;
    return this;
  }

  public DownloadBuilder setFailureReason(int failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public DownloadBuilder setStopReason(int stopReason) {
    this.stopReason = stopReason;
    return this;
  }

  public DownloadBuilder setStartTimeMs(long startTimeMs) {
    this.startTimeMs = startTimeMs;
    return this;
  }

  public DownloadBuilder setUpdateTimeMs(long updateTimeMs) {
    this.updateTimeMs = updateTimeMs;
    return this;
  }

  public DownloadBuilder setStreamKeys(StreamKey... streamKeys) {
    this.streamKeys = Arrays.asList(streamKeys);
    return this;
  }

  public DownloadBuilder setCustomMetadata(byte[] customMetadata) {
    this.customMetadata = customMetadata;
    return this;
  }

  public Download build() {
    DownloadRequest request =
        new DownloadRequest(id, type, uri, streamKeys, cacheKey, customMetadata);
    return new Download(
        request,
        state,
        startTimeMs,
        updateTimeMs,
        contentLength,
        stopReason,
        failureReason,
        progress);
  }
}
