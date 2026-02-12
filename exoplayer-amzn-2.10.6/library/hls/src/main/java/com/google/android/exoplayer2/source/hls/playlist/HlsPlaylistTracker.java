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
package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import java.io.IOException;

/**
 * Tracks playlists associated to an HLS stream and provides snapshots.
 *
 * <p>The playlist tracker is responsible for exposing the seeking window, which is defined by the
 * segments that one of the playlists exposes. This playlist is called primary and needs to be
 * periodically refreshed in the case of live streams. Note that the primary playlist is one of the
 * media playlists while the master playlist is an optional kind of playlist defined by the HLS
 * specification (RFC 8216).
 *
 * <p>Playlist loads might encounter errors. The tracker may choose to blacklist them to ensure a
 * primary playlist is always available.
 */
public interface HlsPlaylistTracker {

  /** Factory for {@link HlsPlaylistTracker} instances. */
  interface Factory {

    /**
     * Creates a new tracker instance.
     *
     * @param dataSourceFactory The {@link HlsDataSourceFactory} to use for playlist loading.
     * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy} for playlist load errors.
     * @param playlistParserFactory The {@link HlsPlaylistParserFactory} for playlist parsing.
     */
    HlsPlaylistTracker createTracker(
        HlsDataSourceFactory dataSourceFactory,
        LoadErrorHandlingPolicy loadErrorHandlingPolicy,
        HlsPlaylistParserFactory playlistParserFactory);
  }

  /** Listener for primary playlist changes. */
  interface PrimaryPlaylistListener {

    /**
     * Called when the primary playlist changes.
     *
     * @param mediaPlaylist The primary playlist new snapshot.
     */
    void onPrimaryPlaylistRefreshed(HlsMediaPlaylist mediaPlaylist);
  }

  /** Called on playlist loading events. */
  interface PlaylistEventListener {

    /**
     * Called a playlist changes.
     */
    void onPlaylistChanged();

    /**
     * Called if an error is encountered while loading a playlist.
     *
     * @param url The loaded url that caused the error.
     * @param blacklistDurationMs The duration for which the playlist should be blacklisted. Or
     *     {@link C#TIME_UNSET} if the playlist should not be blacklisted.
     * @return True if blacklisting did not encounter errors. False otherwise.
     */
    boolean onPlaylistError(Uri url, long blacklistDurationMs);
  }

  /** Thrown when a playlist is considered to be stuck due to a server side error. */
  final class PlaylistStuckException extends IOException {

    /** The url of the stuck playlist. */
    public final Uri url;

    /**
     * Creates an instance.
     *
     * @param url See {@link #url}.
     */
    public PlaylistStuckException(Uri url) {
      this.url = url;
    }
  }

  /** Thrown when the media sequence of a new snapshot indicates the server has reset. */
  final class PlaylistResetException extends IOException {

    /** The url of the reset playlist. */
    public final Uri url;

    /**
     * Creates an instance.
     *
     * @param url See {@link #url}.
     */
    public PlaylistResetException(Uri url) {
      this.url = url;
    }
  }

  /**
   * Starts the playlist tracker.
   *
   * <p>Must be called from the playback thread. A tracker may be restarted after a {@link #stop()}
   * call.
   *
   * @param initialPlaylistUri Uri of the HLS stream. Can point to a media playlist or a master
   *     playlist.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param listener A callback for the primary playlist change events.
   */
  void start(
      Uri initialPlaylistUri, EventDispatcher eventDispatcher, PrimaryPlaylistListener listener);

  /**
   * Stops the playlist tracker and releases any acquired resources.
   *
   * <p>Must be called once per {@link #start} call.
   */
  void stop();

  /**
   * Registers a listener to receive events from the playlist tracker.
   *
   * @param listener The listener.
   */
  void addListener(PlaylistEventListener listener);

  /**
   * Unregisters a listener.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(PlaylistEventListener listener);

  /**
   * Returns the master playlist.
   *
   * <p>If the uri passed to {@link #start} points to a media playlist, an {@link HlsMasterPlaylist}
   * with a single variant for said media playlist is returned.
   *
   * @return The master playlist. Null if the initial playlist has yet to be loaded.
   */
  @Nullable
  HlsMasterPlaylist getMasterPlaylist();

  /**
   * Returns the most recent snapshot available of the playlist referenced by the provided {@link
   * Uri}.
   *
   * @param url The {@link Uri} corresponding to the requested media playlist.
   * @param isForPlayback Whether the caller might use the snapshot to request media segments for
   *     playback. If true, the primary playlist may be updated to the one requested.
   * @return The most recent snapshot of the playlist referenced by the provided {@link Uri}. May be
   *     null if no snapshot has been loaded yet.
   */
  @Nullable
  HlsMediaPlaylist getPlaylistSnapshot(Uri url, boolean isForPlayback);

  /**
   * Returns the start time of the first loaded primary playlist, or {@link C#TIME_UNSET} if no
   * media playlist has been loaded.
   */
  long getInitialStartTimeUs();

  /**
   * Returns whether the snapshot of the playlist referenced by the provided {@link Uri} is valid,
   * meaning all the segments referenced by the playlist are expected to be available. If the
   * playlist is not valid then some of the segments may no longer be available.
   *
   * @param url The {@link Uri}.
   * @return Whether the snapshot of the playlist referenced by the provided {@link Uri} is valid.
   */
  boolean isSnapshotValid(Uri url);

  /**
   * If the tracker is having trouble refreshing the master playlist or the primary playlist, this
   * method throws the underlying error. Otherwise, does nothing.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowPrimaryPlaylistRefreshError() throws IOException;

  /**
   * If the playlist is having trouble refreshing the playlist referenced by the given {@link Uri},
   * this method throws the underlying error.
   *
   * @param url The {@link Uri}.
   * @throws IOException The underyling error.
   */
  void maybeThrowPlaylistRefreshError(Uri url) throws IOException;

  /**
   * Requests a playlist refresh and whitelists it.
   *
   * <p>The playlist tracker may choose the delay the playlist refresh. The request is discarded if
   * a refresh was already pending.
   *
   * @param url The {@link Uri} of the playlist to be refreshed.
   */
  void refreshPlaylist(Uri url);

  /**
   * Returns whether the tracked playlists describe a live stream.
   *
   * @return True if the content is live. False otherwise.
   */
  boolean isLive();
}
