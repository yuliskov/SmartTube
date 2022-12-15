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
package com.google.android.exoplayer2.source.hls.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.UriUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * A downloader for HLS streams.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * DefaultHttpDataSourceFactory factory = new DefaultHttpDataSourceFactory("ExoPlayer", null);
 * DownloaderConstructorHelper constructorHelper =
 *     new DownloaderConstructorHelper(cache, factory);
 * // Create a downloader for the first variant in a master playlist.
 * HlsDownloader hlsDownloader =
 *     new HlsDownloader(
 *         playlistUri,
 *         Collections.singletonList(new StreamKey(HlsMasterPlaylist.GROUP_INDEX_VARIANT, 0)),
 *         constructorHelper);
 * // Perform the download.
 * hlsDownloader.download(progressListener);
 * // Access downloaded data using CacheDataSource
 * CacheDataSource cacheDataSource =
 *     new CacheDataSource(cache, factory.createDataSource(), CacheDataSource.FLAG_BLOCK_ON_CACHE);
 * }</pre>
 */
public final class HlsDownloader extends SegmentDownloader<HlsPlaylist> {

  /**
   * @param playlistUri The {@link Uri} of the playlist to be downloaded.
   * @param streamKeys Keys defining which renditions in the playlist should be selected for
   *     download. If empty, all renditions are downloaded.
   * @param constructorHelper A {@link DownloaderConstructorHelper} instance.
   */
  public HlsDownloader(
      Uri playlistUri, List<StreamKey> streamKeys, DownloaderConstructorHelper constructorHelper) {
    super(playlistUri, streamKeys, constructorHelper);
  }

  @Override
  protected HlsPlaylist getManifest(DataSource dataSource, DataSpec dataSpec) throws IOException {
    return loadManifest(dataSource, dataSpec);
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, HlsPlaylist playlist, boolean allowIncompleteList) throws IOException {
    ArrayList<DataSpec> mediaPlaylistDataSpecs = new ArrayList<>();
    if (playlist instanceof HlsMasterPlaylist) {
      HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
      addMediaPlaylistDataSpecs(masterPlaylist.mediaPlaylistUrls, mediaPlaylistDataSpecs);
    } else {
      mediaPlaylistDataSpecs.add(
          SegmentDownloader.getCompressibleDataSpec(Uri.parse(playlist.baseUri)));
    }

    ArrayList<Segment> segments = new ArrayList<>();
    HashSet<Uri> seenEncryptionKeyUris = new HashSet<>();
    for (DataSpec mediaPlaylistDataSpec : mediaPlaylistDataSpecs) {
      segments.add(new Segment(/* startTimeUs= */ 0, mediaPlaylistDataSpec));
      HlsMediaPlaylist mediaPlaylist;
      try {
        mediaPlaylist = (HlsMediaPlaylist) loadManifest(dataSource, mediaPlaylistDataSpec);
      } catch (IOException e) {
        if (!allowIncompleteList) {
          throw e;
        }
        // Generating an incomplete segment list is allowed. Advance to the next media playlist.
        continue;
      }
      HlsMediaPlaylist.Segment lastInitSegment = null;
      List<HlsMediaPlaylist.Segment> hlsSegments = mediaPlaylist.segments;
      for (int i = 0; i < hlsSegments.size(); i++) {
        HlsMediaPlaylist.Segment segment = hlsSegments.get(i);
        HlsMediaPlaylist.Segment initSegment = segment.initializationSegment;
        if (initSegment != null && initSegment != lastInitSegment) {
          lastInitSegment = initSegment;
          addSegment(mediaPlaylist, initSegment, seenEncryptionKeyUris, segments);
        }
        addSegment(mediaPlaylist, segment, seenEncryptionKeyUris, segments);
      }
    }
    return segments;
  }

  private void addMediaPlaylistDataSpecs(List<Uri> mediaPlaylistUrls, List<DataSpec> out) {
    for (int i = 0; i < mediaPlaylistUrls.size(); i++) {
      out.add(SegmentDownloader.getCompressibleDataSpec(mediaPlaylistUrls.get(i)));
    }
  }

  private static HlsPlaylist loadManifest(DataSource dataSource, DataSpec dataSpec)
      throws IOException {
    return ParsingLoadable.load(
        dataSource, new HlsPlaylistParser(), dataSpec, C.DATA_TYPE_MANIFEST);
  }

  private void addSegment(
      HlsMediaPlaylist mediaPlaylist,
      HlsMediaPlaylist.Segment segment,
      HashSet<Uri> seenEncryptionKeyUris,
      ArrayList<Segment> out) {
    String baseUri = mediaPlaylist.baseUri;
    long startTimeUs = mediaPlaylist.startTimeUs + segment.relativeStartTimeUs;
    if (segment.fullSegmentEncryptionKeyUri != null) {
      Uri keyUri = UriUtil.resolveToUri(baseUri, segment.fullSegmentEncryptionKeyUri);
      if (seenEncryptionKeyUris.add(keyUri)) {
        out.add(new Segment(startTimeUs, SegmentDownloader.getCompressibleDataSpec(keyUri)));
      }
    }
    Uri segmentUri = UriUtil.resolveToUri(baseUri, segment.url);
    DataSpec dataSpec =
        new DataSpec(segmentUri, segment.byterangeOffset, segment.byterangeLength, /* key= */ null);
    out.add(new Segment(startTimeUs, dataSpec));
  }
}
