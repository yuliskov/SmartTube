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
package com.google.android.exoplayer2.ext.ffmpeg;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Configures and queries the underlying native library.
 */
public final class FfmpegLibrary {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ffmpeg");
  }

  private static final String TAG = "FfmpegLibrary";

  private static final LibraryLoader LOADER =
      new LibraryLoader("avutil", "avresample", "avcodec", "ffmpeg");

  private FfmpegLibrary() {}

  /**
   * Override the names of the FFmpeg native libraries. If an application wishes to call this
   * method, it must do so before calling any other method defined by this class, and before
   * instantiating a {@link FfmpegAudioRenderer} instance.
   *
   * @param libraries The names of the FFmpeg native libraries.
   */
  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /**
   * Returns whether the underlying library is available, loading it if necessary.
   */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }

  /** Returns the version of the underlying library if available, or null otherwise. */
  public static @Nullable String getVersion() {
    return isAvailable() ? ffmpegGetVersion() : null;
  }

  /**
   * Returns whether the underlying library supports the specified MIME type.
   *
   * @param mimeType The MIME type to check.
   * @param encoding The PCM encoding for raw audio.
   */
  public static boolean supportsFormat(String mimeType, @C.PcmEncoding int encoding) {
    if (!isAvailable()) {
      return false;
    }
    String codecName = getCodecName(mimeType, encoding);
    if (codecName == null) {
      return false;
    }
    if (!ffmpegHasDecoder(codecName)) {
      Log.w(TAG, "No " + codecName + " decoder available. Check the FFmpeg build configuration.");
      return false;
    }
    return true;
  }

  /**
   * Returns the name of the FFmpeg decoder that could be used to decode the format, or {@code null}
   * if it's unsupported.
   */
  /* package */ static @Nullable String getCodecName(String mimeType, @C.PcmEncoding int encoding) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        return "aac";
      case MimeTypes.AUDIO_MPEG:
      case MimeTypes.AUDIO_MPEG_L1:
      case MimeTypes.AUDIO_MPEG_L2:
        return "mp3";
      case MimeTypes.AUDIO_AC3:
        return "ac3";
      case MimeTypes.AUDIO_E_AC3:
      case MimeTypes.AUDIO_E_AC3_JOC:
        return "eac3";
      case MimeTypes.AUDIO_TRUEHD:
        return "truehd";
      case MimeTypes.AUDIO_DTS:
      case MimeTypes.AUDIO_DTS_HD:
        return "dca";
      case MimeTypes.AUDIO_VORBIS:
        return "vorbis";
      case MimeTypes.AUDIO_OPUS:
        return "opus";
      case MimeTypes.AUDIO_AMR_NB:
        return "amrnb";
      case MimeTypes.AUDIO_AMR_WB:
        return "amrwb";
      case MimeTypes.AUDIO_FLAC:
        return "flac";
      case MimeTypes.AUDIO_ALAC:
        return "alac";
      case MimeTypes.AUDIO_RAW:
        if (encoding == C.ENCODING_PCM_MU_LAW) {
          return "pcm_mulaw";
        } else if (encoding == C.ENCODING_PCM_A_LAW) {
          return "pcm_alaw";
        } else {
          return null;
        }
      default:
        return null;
    }
  }

  private static native String ffmpegGetVersion();
  private static native boolean ffmpegHasDecoder(String codecName);

}
