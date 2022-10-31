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
package com.google.android.exoplayer2.source.hls;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.Rendition;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.Variant;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts;
import com.google.android.exoplayer2.testutil.MediaPeriodAsserts.FilterableManifestMediaPeriodFactory;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit test for {@link HlsMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public final class HlsMediaPeriodTest {

  @Test
  public void getSteamKeys_isCompatibleWithHlsMasterPlaylistFilter() {
    HlsMasterPlaylist testMasterPlaylist =
        createMasterPlaylist(
            /* variants= */ Arrays.asList(
                createAudioOnlyVariant(/* bitrate= */ 10000),
                createMuxedVideoAudioVariant(/* bitrate= */ 200000),
                createAudioOnlyVariant(/* bitrate= */ 300000),
                createMuxedVideoAudioVariant(/* bitrate= */ 400000),
                createMuxedVideoAudioVariant(/* bitrate= */ 600000)),
            /* audios= */ Arrays.asList(
                createAudioRendition(/* language= */ "spa"),
                createAudioRendition(/* language= */ "ger"),
                createAudioRendition(/* language= */ "tur")),
            /* subtitles= */ Arrays.asList(
                createSubtitleRendition(/* language= */ "spa"),
                createSubtitleRendition(/* language= */ "ger"),
                createSubtitleRendition(/* language= */ "tur")),
            /* muxedAudioFormat= */ createAudioFormat("eng"),
            /* muxedCaptionFormats= */ Arrays.asList(
                createSubtitleFormat("eng"), createSubtitleFormat("gsw")));
    FilterableManifestMediaPeriodFactory<HlsPlaylist> mediaPeriodFactory =
        (playlist, periodIndex) -> {
          HlsDataSourceFactory mockDataSourceFactory = mock(HlsDataSourceFactory.class);
          when(mockDataSourceFactory.createDataSource(anyInt())).thenReturn(mock(DataSource.class));
          HlsPlaylistTracker mockPlaylistTracker = mock(HlsPlaylistTracker.class);
          when(mockPlaylistTracker.getMasterPlaylist()).thenReturn((HlsMasterPlaylist) playlist);
          return new HlsMediaPeriod(
              mock(HlsExtractorFactory.class),
              mockPlaylistTracker,
              mockDataSourceFactory,
              mock(TransferListener.class),
              mock(LoadErrorHandlingPolicy.class),
              new EventDispatcher()
                  .withParameters(
                      /* windowIndex= */ 0,
                      /* mediaPeriodId= */ new MediaPeriodId(/* periodUid= */ new Object()),
                      /* mediaTimeOffsetMs= */ 0),
              mock(Allocator.class),
              mock(CompositeSequenceableLoaderFactory.class),
              /* allowChunklessPreparation =*/ true,
              HlsMetadataType.ID3,
              /* useSessionKeys= */ false);
        };

    MediaPeriodAsserts.assertGetStreamKeysAndManifestFilterIntegration(
        mediaPeriodFactory, testMasterPlaylist);
  }

  private static HlsMasterPlaylist createMasterPlaylist(
      List<Variant> variants,
      List<Rendition> audios,
      List<Rendition> subtitles,
      Format muxedAudioFormat,
      List<Format> muxedCaptionFormats) {
    return new HlsMasterPlaylist(
        "http://baseUri",
        /* tags= */ Collections.emptyList(),
        variants,
        /* videos= */ Collections.emptyList(),
        audios,
        subtitles,
        /* closedCaptions= */ Collections.emptyList(),
        muxedAudioFormat,
        muxedCaptionFormats,
        /* hasIndependentSegments= */ true,
        /* variableDefinitions= */ Collections.emptyMap(),
        /* sessionKeyDrmInitData= */ Collections.emptyList());
  }

  private static Variant createMuxedVideoAudioVariant(int bitrate) {
    return createVariant(
        Format.createVideoContainerFormat(
            /* id= */ null,
            /* label= */ null,
            /* containerMimeType= */ MimeTypes.APPLICATION_M3U8,
            /* sampleMimeType= */ null,
            /* codecs= */ "avc1.100.41,mp4a.40.2",
            /* metadata= */ null,
            bitrate,
            /* width= */ Format.NO_VALUE,
            /* height= */ Format.NO_VALUE,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* selectionFlags= */ 0,
            /* roleFlags= */ 0));
  }

  private static Variant createAudioOnlyVariant(int bitrate) {
    return createVariant(
        Format.createVideoContainerFormat(
            /* id= */ null,
            /* label= */ null,
            /* containerMimeType= */ MimeTypes.APPLICATION_M3U8,
            /* sampleMimeType= */ null,
            /* codecs= */ "mp4a.40.2",
            /* metadata= */ null,
            bitrate,
            /* width= */ Format.NO_VALUE,
            /* height= */ Format.NO_VALUE,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* selectionFlags= */ 0,
            /* roleFlags= */ 0));
  }

  private static Rendition createAudioRendition(String language) {
    return createRendition(createAudioFormat(language), "", "");
  }

  private static Rendition createSubtitleRendition(String language) {
    return createRendition(createSubtitleFormat(language), "", "");
  }

  private static Variant createVariant(Format format) {
    return new Variant(Uri.parse("https://variant"), format, null, null, null, null);
  }

  private static Rendition createRendition(Format format, String groupId, String name) {
    return new Rendition(Uri.parse("https://rendition"), format, groupId, name);
  }

  private static Format createAudioFormat(String language) {
    return Format.createAudioContainerFormat(
        /* id= */ null,
        /* label= */ null,
        /* containerMimeType= */ MimeTypes.APPLICATION_M3U8,
        MimeTypes.getMediaMimeType("mp4a.40.2"),
        /* codecs= */ "mp4a.40.2",
        /* metadata= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* channelCount= */ Format.NO_VALUE,
        /* sampleRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        language);
  }

  private static Format createSubtitleFormat(String language) {
    return Format.createTextContainerFormat(
        /* id= */ null,
        /* label= */ null,
        /* containerMimeType= */ MimeTypes.APPLICATION_M3U8,
        /* sampleMimeType= */ MimeTypes.TEXT_VTT,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        language);
  }
}
