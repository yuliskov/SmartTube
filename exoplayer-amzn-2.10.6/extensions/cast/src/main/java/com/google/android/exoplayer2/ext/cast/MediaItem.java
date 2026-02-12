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
package com.google.android.exoplayer2.ext.cast;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/** Representation of an item that can be played by a media player. */
public final class MediaItem {

  /** A builder for {@link MediaItem} instances. */
  public static final class Builder {

    @Nullable private UUID uuid;
    private String title;
    private String description;
    private MediaItem.UriBundle media;
    @Nullable private Object attachment;
    private List<MediaItem.DrmScheme> drmSchemes;
    private long startPositionUs;
    private long endPositionUs;
    private String mimeType;

    /** Creates an builder with default field values. */
    public Builder() {
      clearInternal();
    }

    /** See {@link MediaItem#uuid}. */
    public Builder setUuid(UUID uuid) {
      this.uuid = uuid;
      return this;
    }

    /** See {@link MediaItem#title}. */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /** See {@link MediaItem#description}. */
    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    /** Equivalent to {@link #setMedia(UriBundle) setMedia(new UriBundle(Uri.parse(uri)))}. */
    public Builder setMedia(String uri) {
      return setMedia(new UriBundle(Uri.parse(uri)));
    }

    /** See {@link MediaItem#media}. */
    public Builder setMedia(UriBundle media) {
      this.media = media;
      return this;
    }

    /** See {@link MediaItem#attachment}. */
    public Builder setAttachment(Object attachment) {
      this.attachment = attachment;
      return this;
    }

    /** See {@link MediaItem#drmSchemes}. */
    public Builder setDrmSchemes(List<MediaItem.DrmScheme> drmSchemes) {
      this.drmSchemes = Collections.unmodifiableList(new ArrayList<>(drmSchemes));
      return this;
    }

    /** See {@link MediaItem#startPositionUs}. */
    public Builder setStartPositionUs(long startPositionUs) {
      this.startPositionUs = startPositionUs;
      return this;
    }

    /** See {@link MediaItem#endPositionUs}. */
    public Builder setEndPositionUs(long endPositionUs) {
      Assertions.checkArgument(endPositionUs != C.TIME_END_OF_SOURCE);
      this.endPositionUs = endPositionUs;
      return this;
    }

    /** See {@link MediaItem#mimeType}. */
    public Builder setMimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /**
     * Equivalent to {@link #build()}, except it also calls {@link #clear()} after creating the
     * {@link MediaItem}.
     */
    public MediaItem buildAndClear() {
      MediaItem item = build();
      clearInternal();
      return item;
    }

    /** Returns the builder to default values. */
    public Builder clear() {
      clearInternal();
      return this;
    }

    /**
     * Returns a new {@link MediaItem} instance with the current builder values. This method also
     * clears any values passed to {@link #setUuid(UUID)}.
     */
    public MediaItem build() {
      UUID uuid = this.uuid;
      this.uuid = null;
      return new MediaItem(
          uuid != null ? uuid : UUID.randomUUID(),
          title,
          description,
          media,
          attachment,
          drmSchemes,
          startPositionUs,
          endPositionUs,
          mimeType);
    }

    @EnsuresNonNull({"title", "description", "media", "drmSchemes", "mimeType"})
    private void clearInternal(@UnknownInitialization Builder this) {
      uuid = null;
      title = "";
      description = "";
      media = UriBundle.EMPTY;
      attachment = null;
      drmSchemes = Collections.emptyList();
      startPositionUs = C.TIME_UNSET;
      endPositionUs = C.TIME_UNSET;
      mimeType = "";
    }
  }

  /** Bundles a resource's URI with headers to attach to any request to that URI. */
  public static final class UriBundle {

    /** An empty {@link UriBundle}. */
    public static final UriBundle EMPTY = new UriBundle(Uri.EMPTY);

    /** A URI. */
    public final Uri uri;

    /** The headers to attach to any request for the given URI. */
    public final Map<String, String> requestHeaders;

    /**
     * Creates an instance with no request headers.
     *
     * @param uri See {@link #uri}.
     */
    public UriBundle(Uri uri) {
      this(uri, Collections.emptyMap());
    }

    /**
     * Creates an instance with the given URI and request headers.
     *
     * @param uri See {@link #uri}.
     * @param requestHeaders See {@link #requestHeaders}.
     */
    public UriBundle(Uri uri, Map<String, String> requestHeaders) {
      this.uri = uri;
      this.requestHeaders = Collections.unmodifiableMap(new HashMap<>(requestHeaders));
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      UriBundle uriBundle = (UriBundle) other;
      return uri.equals(uriBundle.uri) && requestHeaders.equals(uriBundle.requestHeaders);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + requestHeaders.hashCode();
      return result;
    }
  }

  /**
   * Represents a DRM protection scheme, and optionally provides information about how to acquire
   * the license for the media.
   */
  public static final class DrmScheme {

    /** The UUID of the protection scheme. */
    public final UUID uuid;

    /**
     * Optional {@link UriBundle} for the license server. If no license server is provided, the
     * server must be provided by the media.
     */
    @Nullable public final UriBundle licenseServer;

    /**
     * Creates an instance.
     *
     * @param uuid See {@link #uuid}.
     * @param licenseServer See {@link #licenseServer}.
     */
    public DrmScheme(UUID uuid, @Nullable UriBundle licenseServer) {
      this.uuid = uuid;
      this.licenseServer = licenseServer;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      DrmScheme drmScheme = (DrmScheme) other;
      return uuid.equals(drmScheme.uuid) && Util.areEqual(licenseServer, drmScheme.licenseServer);
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + (licenseServer != null ? licenseServer.hashCode() : 0);
      return result;
    }
  }

  /**
   * A UUID that identifies this item, potentially across different devices. The default value is
   * obtained by calling {@link UUID#randomUUID()}.
   */
  public final UUID uuid;

  /** The title of the item. The default value is an empty string. */
  public final String title;

  /** A description for the item. The default value is an empty string. */
  public final String description;

  /**
   * A {@link UriBundle} to fetch the media content. The default value is {@link UriBundle#EMPTY}.
   */
  public final UriBundle media;

  /**
   * An optional opaque object to attach to the media item. Handling of this attachment is
   * implementation specific. The default value is null.
   */
  @Nullable public final Object attachment;

  /**
   * Immutable list of {@link DrmScheme} instances sorted in decreasing order of preference. The
   * default value is an empty list.
   */
  public final List<DrmScheme> drmSchemes;

  /**
   * The position in microseconds at which playback of this media item should start. {@link
   * C#TIME_UNSET} if playback should start at the default position. The default value is {@link
   * C#TIME_UNSET}.
   */
  public final long startPositionUs;

  /**
   * The position in microseconds at which playback of this media item should end. {@link
   * C#TIME_UNSET} if playback should end at the end of the media. The default value is {@link
   * C#TIME_UNSET}.
   */
  public final long endPositionUs;

  /**
   * The mime type of this media item. The default value is an empty string.
   *
   * <p>The usage of this mime type is optional and player implementation specific.
   */
  public final String mimeType;

  // TODO: Add support for sideloaded tracks, artwork, icon, and subtitle.

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    MediaItem mediaItem = (MediaItem) other;
    return startPositionUs == mediaItem.startPositionUs
        && endPositionUs == mediaItem.endPositionUs
        && uuid.equals(mediaItem.uuid)
        && title.equals(mediaItem.title)
        && description.equals(mediaItem.description)
        && media.equals(mediaItem.media)
        && Util.areEqual(attachment, mediaItem.attachment)
        && drmSchemes.equals(mediaItem.drmSchemes)
        && mimeType.equals(mediaItem.mimeType);
  }

  @Override
  public int hashCode() {
    int result = uuid.hashCode();
    result = 31 * result + title.hashCode();
    result = 31 * result + description.hashCode();
    result = 31 * result + media.hashCode();
    result = 31 * result + (attachment != null ? attachment.hashCode() : 0);
    result = 31 * result + drmSchemes.hashCode();
    result = 31 * result + (int) (startPositionUs ^ (startPositionUs >>> 32));
    result = 31 * result + (int) (endPositionUs ^ (endPositionUs >>> 32));
    result = 31 * result + mimeType.hashCode();
    return result;
  }

  private MediaItem(
      UUID uuid,
      String title,
      String description,
      UriBundle media,
      @Nullable Object attachment,
      List<DrmScheme> drmSchemes,
      long startPositionUs,
      long endPositionUs,
      String mimeType) {
    this.uuid = uuid;
    this.title = title;
    this.description = description;
    this.media = media;
    this.attachment = attachment;
    this.drmSchemes = drmSchemes;
    this.startPositionUs = startPositionUs;
    this.endPositionUs = endPositionUs;
    this.mimeType = mimeType;
  }
}
