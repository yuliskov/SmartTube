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
package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Represents an HLS master playlist. */
public final class HlsMasterPlaylist extends HlsPlaylist {

  /** Represents an empty master playlist, from which no attributes can be inherited. */
  public static final HlsMasterPlaylist EMPTY =
      new HlsMasterPlaylist(
          /* baseUri= */ "",
          /* tags= */ Collections.emptyList(),
          /* variants= */ Collections.emptyList(),
          /* videos= */ Collections.emptyList(),
          /* audios= */ Collections.emptyList(),
          /* subtitles= */ Collections.emptyList(),
          /* closedCaptions= */ Collections.emptyList(),
          /* muxedAudioFormat= */ null,
          /* muxedCaptionFormats= */ Collections.emptyList(),
          /* hasIndependentSegments= */ false,
          /* variableDefinitions= */ Collections.emptyMap(),
          /* sessionKeyDrmInitData= */ Collections.emptyList());

  // These constants must not be changed because they are persisted in offline stream keys.
  public static final int GROUP_INDEX_VARIANT = 0;
  public static final int GROUP_INDEX_AUDIO = 1;
  public static final int GROUP_INDEX_SUBTITLE = 2;

  /** A variant (i.e. an #EXT-X-STREAM-INF tag) in a master playlist. */
  public static final class Variant {

    /** The variant's url. */
    public final Uri url;

    /** Format information associated with this variant. */
    public final Format format;

    /** The video rendition group referenced by this variant, or {@code null}. */
    @Nullable public final String videoGroupId;

    /** The audio rendition group referenced by this variant, or {@code null}. */
    @Nullable public final String audioGroupId;

    /** The subtitle rendition group referenced by this variant, or {@code null}. */
    @Nullable public final String subtitleGroupId;

    /** The caption rendition group referenced by this variant, or {@code null}. */
    @Nullable public final String captionGroupId;

    /**
     * @param url See {@link #url}.
     * @param format See {@link #format}.
     * @param videoGroupId See {@link #videoGroupId}.
     * @param audioGroupId See {@link #audioGroupId}.
     * @param subtitleGroupId See {@link #subtitleGroupId}.
     * @param captionGroupId See {@link #captionGroupId}.
     */
    public Variant(
        Uri url,
        Format format,
        @Nullable String videoGroupId,
        @Nullable String audioGroupId,
        @Nullable String subtitleGroupId,
        @Nullable String captionGroupId) {
      this.url = url;
      this.format = format;
      this.videoGroupId = videoGroupId;
      this.audioGroupId = audioGroupId;
      this.subtitleGroupId = subtitleGroupId;
      this.captionGroupId = captionGroupId;
    }

    /**
     * Creates a variant for a given media playlist url.
     *
     * @param url The media playlist url.
     * @return The variant instance.
     */
    public static Variant createMediaPlaylistVariantUrl(Uri url) {
      Format format =
          Format.createContainerFormat(
              "0",
              /* label= */ null,
              MimeTypes.APPLICATION_M3U8,
              /* sampleMimeType= */ null,
              /* codecs= */ null,
              /* bitrate= */ Format.NO_VALUE,
              /* selectionFlags= */ 0,
              /* roleFlags= */ 0,
              /* language= */ null);
      return new Variant(
          url,
          format,
          /* videoGroupId= */ null,
          /* audioGroupId= */ null,
          /* subtitleGroupId= */ null,
          /* captionGroupId= */ null);
    }

    /** Returns a copy of this instance with the given {@link Format}. */
    public Variant copyWithFormat(Format format) {
      return new Variant(url, format, videoGroupId, audioGroupId, subtitleGroupId, captionGroupId);
    }
  }

  /** A rendition (i.e. an #EXT-X-MEDIA tag) in a master playlist. */
  public static final class Rendition {

    /** The rendition's url, or null if the tag does not have a URI attribute. */
    @Nullable public final Uri url;

    /** Format information associated with this rendition. */
    public final Format format;

    /** The group to which this rendition belongs. */
    public final String groupId;

    /** The name of the rendition. */
    public final String name;

    /**
     * @param url See {@link #url}.
     * @param format See {@link #format}.
     * @param groupId See {@link #groupId}.
     * @param name See {@link #name}.
     */
    public Rendition(@Nullable Uri url, Format format, String groupId, String name) {
      this.url = url;
      this.format = format;
      this.groupId = groupId;
      this.name = name;
    }

  }

  /** All of the media playlist URLs referenced by the playlist. */
  public final List<Uri> mediaPlaylistUrls;
  /** The variants declared by the playlist. */
  public final List<Variant> variants;
  /** The video renditions declared by the playlist. */
  public final List<Rendition> videos;
  /** The audio renditions declared by the playlist. */
  public final List<Rendition> audios;
  /** The subtitle renditions declared by the playlist. */
  public final List<Rendition> subtitles;
  /** The closed caption renditions declared by the playlist. */
  public final List<Rendition> closedCaptions;

  /**
   * The format of the audio muxed in the variants. May be null if the playlist does not declare any
   * muxed audio.
   */
  public final Format muxedAudioFormat;
  /**
   * The format of the closed captions declared by the playlist. May be empty if the playlist
   * explicitly declares no captions are available, or null if the playlist does not declare any
   * captions information.
   */
  public final List<Format> muxedCaptionFormats;
  /** Contains variable definitions, as defined by the #EXT-X-DEFINE tag. */
  public final Map<String, String> variableDefinitions;
  /** DRM initialization data derived from #EXT-X-SESSION-KEY tags. */
  public final List<DrmInitData> sessionKeyDrmInitData;

  /**
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param variants See {@link #variants}.
   * @param videos See {@link #videos}.
   * @param audios See {@link #audios}.
   * @param subtitles See {@link #subtitles}.
   * @param closedCaptions See {@link #closedCaptions}.
   * @param muxedAudioFormat See {@link #muxedAudioFormat}.
   * @param muxedCaptionFormats See {@link #muxedCaptionFormats}.
   * @param hasIndependentSegments See {@link #hasIndependentSegments}.
   * @param variableDefinitions See {@link #variableDefinitions}.
   * @param sessionKeyDrmInitData See {@link #sessionKeyDrmInitData}.
   */
  public HlsMasterPlaylist(
      String baseUri,
      List<String> tags,
      List<Variant> variants,
      List<Rendition> videos,
      List<Rendition> audios,
      List<Rendition> subtitles,
      List<Rendition> closedCaptions,
      Format muxedAudioFormat,
      List<Format> muxedCaptionFormats,
      boolean hasIndependentSegments,
      Map<String, String> variableDefinitions,
      List<DrmInitData> sessionKeyDrmInitData) {
    super(baseUri, tags, hasIndependentSegments);
    this.mediaPlaylistUrls =
        Collections.unmodifiableList(
            getMediaPlaylistUrls(variants, videos, audios, subtitles, closedCaptions));
    this.variants = Collections.unmodifiableList(variants);
    this.videos = Collections.unmodifiableList(videos);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.closedCaptions = Collections.unmodifiableList(closedCaptions);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats = muxedCaptionFormats != null
        ? Collections.unmodifiableList(muxedCaptionFormats) : null;
    this.variableDefinitions = Collections.unmodifiableMap(variableDefinitions);
    this.sessionKeyDrmInitData = Collections.unmodifiableList(sessionKeyDrmInitData);
  }

  @Override
  public HlsMasterPlaylist copy(List<StreamKey> streamKeys) {
    return new HlsMasterPlaylist(
        baseUri,
        tags,
        copyStreams(variants, GROUP_INDEX_VARIANT, streamKeys),
        // TODO: Allow stream keys to specify video renditions to be retained.
        /* videos= */ Collections.emptyList(),
        copyStreams(audios, GROUP_INDEX_AUDIO, streamKeys),
        copyStreams(subtitles, GROUP_INDEX_SUBTITLE, streamKeys),
        // TODO: Update to retain all closed captions.
        /* closedCaptions= */ Collections.emptyList(),
        muxedAudioFormat,
        muxedCaptionFormats,
        hasIndependentSegments,
        variableDefinitions,
        sessionKeyDrmInitData);
  }

  /**
   * Creates a playlist with a single variant.
   *
   * @param variantUrl The url of the single variant.
   * @return A master playlist with a single variant for the provided url.
   */
  public static HlsMasterPlaylist createSingleVariantMasterPlaylist(String variantUrl) {
    List<Variant> variant =
        Collections.singletonList(Variant.createMediaPlaylistVariantUrl(Uri.parse(variantUrl)));
    return new HlsMasterPlaylist(
        /* baseUri= */ null,
        /* tags= */ Collections.emptyList(),
        variant,
        /* videos= */ Collections.emptyList(),
        /* audios= */ Collections.emptyList(),
        /* subtitles= */ Collections.emptyList(),
        /* closedCaptions= */ Collections.emptyList(),
        /* muxedAudioFormat= */ null,
        /* muxedCaptionFormats= */ null,
        /* hasIndependentSegments= */ false,
        /* variableDefinitions= */ Collections.emptyMap(),
        /* sessionKeyDrmInitData= */ Collections.emptyList());
  }

  private static List<Uri> getMediaPlaylistUrls(
      List<Variant> variants,
      List<Rendition> videos,
      List<Rendition> audios,
      List<Rendition> subtitles,
      List<Rendition> closedCaptions) {
    ArrayList<Uri> mediaPlaylistUrls = new ArrayList<>();
    for (int i = 0; i < variants.size(); i++) {
      Uri uri = variants.get(i).url;
      if (!mediaPlaylistUrls.contains(uri)) {
        mediaPlaylistUrls.add(uri);
      }
    }
    addMediaPlaylistUrls(videos, mediaPlaylistUrls);
    addMediaPlaylistUrls(audios, mediaPlaylistUrls);
    addMediaPlaylistUrls(subtitles, mediaPlaylistUrls);
    addMediaPlaylistUrls(closedCaptions, mediaPlaylistUrls);
    return mediaPlaylistUrls;
  }

  private static void addMediaPlaylistUrls(List<Rendition> renditions, List<Uri> out) {
    for (int i = 0; i < renditions.size(); i++) {
      Uri uri = renditions.get(i).url;
      if (uri != null && !out.contains(uri)) {
        out.add(uri);
      }
    }
  }

  private static <T> List<T> copyStreams(
      List<T> streams, int groupIndex, List<StreamKey> streamKeys) {
    List<T> copiedStreams = new ArrayList<>(streamKeys.size());
    // TODO:
    // 1. When variants with the same URL are not de-duplicated, duplicates must not increment
    //    trackIndex so as to avoid breaking stream keys that have been persisted for offline. All
    //    duplicates should be copied if the first variant is copied, or discarded otherwise.
    // 2. When renditions with null URLs are permitted, they must not increment trackIndex so as to
    //    avoid breaking stream keys that have been persisted for offline. All renitions with null
    //    URLs should be copied. They may become unreachable if all variants that reference them are
    //    removed, but this is OK.
    // 3. Renditions with URLs matching copied variants should always themselves be copied, even if
    //    the corresponding stream key is omitted. Else we're throwing away information for no gain.
    for (int i = 0; i < streams.size(); i++) {
      T stream = streams.get(i);
      for (int j = 0; j < streamKeys.size(); j++) {
        StreamKey streamKey = streamKeys.get(j);
        if (streamKey.groupIndex == groupIndex && streamKey.trackIndex == i) {
          copiedStreams.add(stream);
          break;
        }
      }
    }
    return copiedStreams;
  }

}
