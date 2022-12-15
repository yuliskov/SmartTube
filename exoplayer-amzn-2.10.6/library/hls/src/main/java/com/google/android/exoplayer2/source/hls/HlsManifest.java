/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;

/**
 * Holds a master playlist along with a snapshot of one of its media playlists.
 */
public final class HlsManifest {

  /**
   * The master playlist of an HLS stream.
   */
  public final HlsMasterPlaylist masterPlaylist;
  /**
   * A snapshot of a media playlist referred to by {@link #masterPlaylist}.
   */
  public final HlsMediaPlaylist mediaPlaylist;

  /**
   * @param masterPlaylist The master playlist.
   * @param mediaPlaylist The media playlist.
   */
  HlsManifest(HlsMasterPlaylist masterPlaylist, HlsMediaPlaylist mediaPlaylist) {
    this.masterPlaylist = masterPlaylist;
    this.mediaPlaylist = mediaPlaylist;
  }

}
