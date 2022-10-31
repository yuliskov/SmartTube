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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Default {@link DownloaderFactory}, supporting creation of progressive, DASH, HLS and
 * SmoothStreaming downloaders. Note that for the latter three, the corresponding library module
 * must be built into the application.
 */
public class DefaultDownloaderFactory implements DownloaderFactory {

  @Nullable private static final Constructor<? extends Downloader> DASH_DOWNLOADER_CONSTRUCTOR;
  @Nullable private static final Constructor<? extends Downloader> HLS_DOWNLOADER_CONSTRUCTOR;
  @Nullable private static final Constructor<? extends Downloader> SS_DOWNLOADER_CONSTRUCTOR;

  static {
    Constructor<? extends Downloader> dashDownloaderConstructor = null;
    try {
      // LINT.IfChange
      dashDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName("com.google.android.exoplayer2.source.dash.offline.DashDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the DASH module.
    }
    DASH_DOWNLOADER_CONSTRUCTOR = dashDownloaderConstructor;
    Constructor<? extends Downloader> hlsDownloaderConstructor = null;
    try {
      // LINT.IfChange
      hlsDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName("com.google.android.exoplayer2.source.hls.offline.HlsDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the HLS module.
    }
    HLS_DOWNLOADER_CONSTRUCTOR = hlsDownloaderConstructor;
    Constructor<? extends Downloader> ssDownloaderConstructor = null;
    try {
      // LINT.IfChange
      ssDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName(
                  "com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the SmoothStreaming module.
    }
    SS_DOWNLOADER_CONSTRUCTOR = ssDownloaderConstructor;
  }

  private final DownloaderConstructorHelper downloaderConstructorHelper;

  /** @param downloaderConstructorHelper A helper for instantiating downloaders. */
  public DefaultDownloaderFactory(DownloaderConstructorHelper downloaderConstructorHelper) {
    this.downloaderConstructorHelper = downloaderConstructorHelper;
  }

  @Override
  public Downloader createDownloader(DownloadRequest request) {
    switch (request.type) {
      case DownloadRequest.TYPE_PROGRESSIVE:
        return new ProgressiveDownloader(
            request.uri, request.customCacheKey, downloaderConstructorHelper);
      case DownloadRequest.TYPE_DASH:
        return createDownloader(request, DASH_DOWNLOADER_CONSTRUCTOR);
      case DownloadRequest.TYPE_HLS:
        return createDownloader(request, HLS_DOWNLOADER_CONSTRUCTOR);
      case DownloadRequest.TYPE_SS:
        return createDownloader(request, SS_DOWNLOADER_CONSTRUCTOR);
      default:
        throw new IllegalArgumentException("Unsupported type: " + request.type);
    }
  }

  private Downloader createDownloader(
      DownloadRequest request, @Nullable Constructor<? extends Downloader> constructor) {
    if (constructor == null) {
      throw new IllegalStateException("Module missing for: " + request.type);
    }
    try {
      return constructor.newInstance(request.uri, request.streamKeys, downloaderConstructorHelper);
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate downloader for: " + request.type, e);
    }
  }

  // LINT.IfChange
  private static Constructor<? extends Downloader> getDownloaderConstructor(Class<?> clazz) {
    try {
      return clazz
          .asSubclass(Downloader.class)
          .getConstructor(Uri.class, List.class, DownloaderConstructorHelper.class);
    } catch (NoSuchMethodException e) {
      // The downloader is present, but the expected constructor is missing.
      throw new RuntimeException("Downloader constructor missing", e);
    }
  }
  // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
}
