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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link MediaItem}. */
@RunWith(AndroidJUnit4.class)
public class MediaItemTest {

  @Test
  public void buildMediaItem_resetsUuid() {
    MediaItem.Builder builder = new MediaItem.Builder();
    UUID uuid = new UUID(1, 1);
    MediaItem item1 = builder.setUuid(uuid).build();
    MediaItem item2 = builder.build();
    MediaItem item3 = builder.build();
    assertThat(item1.uuid).isEqualTo(uuid);
    assertThat(item2.uuid).isNotEqualTo(uuid);
    assertThat(item3.uuid).isNotEqualTo(item2.uuid);
    assertThat(item3.uuid).isNotEqualTo(uuid);
  }

  @Test
  public void buildMediaItem_doesNotChangeState() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item1 =
        builder
            .setUuid(new UUID(0, 1))
            .setMedia("http://example.com")
            .setTitle("title")
            .setMimeType(MimeTypes.AUDIO_MP4)
            .setStartPositionUs(3)
            .setEndPositionUs(4)
            .build();
    MediaItem item2 = builder.setUuid(new UUID(0, 1)).build();
    assertThat(item1).isEqualTo(item2);
  }

  @Test
  public void buildMediaItem_assertDefaultValues() {
    assertDefaultValues(new MediaItem.Builder().build());
  }

  @Test
  public void buildAndClear_assertDefaultValues() {
    MediaItem.Builder builder = new MediaItem.Builder();
    builder
        .setMedia("http://example.com")
        .setTitle("title")
        .setMimeType(MimeTypes.AUDIO_MP4)
        .setStartPositionUs(3)
        .setEndPositionUs(4)
        .buildAndClear();
    assertDefaultValues(builder.build());
  }

  @Test
  public void equals_withEqualDrmSchemes_returnsTrue() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem mediaItem1 =
        builder
            .setUuid(new UUID(0, 1))
            .setMedia("www.google.com")
            .setDrmSchemes(createDummyDrmSchemes(1))
            .buildAndClear();
    MediaItem mediaItem2 =
        builder
            .setUuid(new UUID(0, 1))
            .setMedia("www.google.com")
            .setDrmSchemes(createDummyDrmSchemes(1))
            .buildAndClear();
    assertThat(mediaItem1).isEqualTo(mediaItem2);
  }

  @Test
  public void equals_withDifferentDrmRequestHeaders_returnsFalse() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem mediaItem1 =
        builder
            .setUuid(new UUID(0, 1))
            .setMedia("www.google.com")
            .setDrmSchemes(createDummyDrmSchemes(1))
            .buildAndClear();
    MediaItem mediaItem2 =
        builder
            .setUuid(new UUID(0, 1))
            .setMedia("www.google.com")
            .setDrmSchemes(createDummyDrmSchemes(2))
            .buildAndClear();
    assertThat(mediaItem1).isNotEqualTo(mediaItem2);
  }

  private static void assertDefaultValues(MediaItem item) {
    assertThat(item.title).isEmpty();
    assertThat(item.description).isEmpty();
    assertThat(item.media.uri).isEqualTo(Uri.EMPTY);
    assertThat(item.attachment).isNull();
    assertThat(item.drmSchemes).isEmpty();
    assertThat(item.startPositionUs).isEqualTo(C.TIME_UNSET);
    assertThat(item.endPositionUs).isEqualTo(C.TIME_UNSET);
    assertThat(item.mimeType).isEmpty();
  }

  private static List<MediaItem.DrmScheme> createDummyDrmSchemes(int seed) {
    HashMap<String, String> requestHeaders1 = new HashMap<>();
    requestHeaders1.put("key1", "value1");
    requestHeaders1.put("key2", "value1");
    MediaItem.UriBundle uriBundle1 =
        new MediaItem.UriBundle(Uri.parse("www.uri1.com"), requestHeaders1);
    MediaItem.DrmScheme drmScheme1 = new MediaItem.DrmScheme(C.WIDEVINE_UUID, uriBundle1);
    HashMap<String, String> requestHeaders2 = new HashMap<>();
    requestHeaders2.put("key3", "value3");
    requestHeaders2.put("key4", "valueWithSeed" + seed);
    MediaItem.UriBundle uriBundle2 =
        new MediaItem.UriBundle(Uri.parse("www.uri2.com"), requestHeaders2);
    MediaItem.DrmScheme drmScheme2 = new MediaItem.DrmScheme(C.PLAYREADY_UUID, uriBundle2);
    return Arrays.asList(drmScheme1, drmScheme2);
  }
}
