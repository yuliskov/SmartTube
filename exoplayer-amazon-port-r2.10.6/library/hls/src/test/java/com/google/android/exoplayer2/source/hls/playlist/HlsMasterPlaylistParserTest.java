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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.hls.HlsTrackMetadataEntry;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link HlsMasterPlaylistParserTest}. */
@RunWith(AndroidJUnit4.class)
public class HlsMasterPlaylistParserTest {

  private static final String PLAYLIST_URI = "https://example.com/test.m3u8";

  private static final String PLAYLIST_SIMPLE =
      " #EXTM3U \n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2 , avc1.66.30 \"\n"
          + "http://example.com/spaces_in_codecs.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2560000,FRAME-RATE=25,RESOLUTION=384x160\n"
          + "http://example.com/mid.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=7680000,FRAME-RATE=29.997\n"
          + "http://example.com/hi.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=65000,CODECS=\"mp4a.40.5\"\n"
          + "http://example.com/audio-only.m3u8";

  private static final String PLAYLIST_WITH_AVG_BANDWIDTH =
      " #EXTM3U \n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,AVERAGE-BANDWIDTH=1270000,"
          + "CODECS=\"mp4a.40.2 , avc1.66.30 \"\n"
          + "http://example.com/spaces_in_codecs.m3u8\n";

  private static final String PLAYLIST_WITH_INVALID_HEADER =
      "#EXTMU3\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n";

  private static final String PLAYLIST_WITH_CC =
      " #EXTM3U \n"
          + "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID=\"cc1\","
          + "LANGUAGE=\"es\",NAME=\"Eng\",INSTREAM-ID=\"SERVICE4\"\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n";

  private static final String PLAYLIST_WITH_CHANNELS_ATTRIBUTE =
      " #EXTM3U \n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",CHANNELS=\"6\",NAME=\"Eng6\","
          + "URI=\"something.m3u8\"\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",CHANNELS=\"2/6\",NAME=\"Eng26\","
          + "URI=\"something2.m3u8\"\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Eng\","
          + "URI=\"something3.m3u8\"\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",AUDIO=\"audio\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n";

  private static final String PLAYLIST_WITHOUT_CC =
      " #EXTM3U \n"
          + "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID=\"cc1\","
          + "LANGUAGE=\"es\",NAME=\"Eng\",INSTREAM-ID=\"SERVICE4\"\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128,"
          + "CLOSED-CAPTIONS=NONE\n"
          + "http://example.com/low.m3u8\n";

  private static final String PLAYLIST_WITH_SUBTITLES =
      " #EXTM3U \n"
          + "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"sub1\","
          + "LANGUAGE=\"es\",NAME=\"Eng\"\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n";

  private static final String PLAYLIST_WITH_AUDIO_MEDIA_TAG =
      "#EXTM3U\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2227464,CODECS=\"avc1.640020,mp4a.40.2\",AUDIO=\"aud1\"\n"
          + "uri1.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=8178040,CODECS=\"avc1.64002a,mp4a.40.2\",AUDIO=\"aud1\"\n"
          + "uri2.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2448841,CODECS=\"avc1.640020,ac-3\",AUDIO=\"aud2\"\n"
          + "uri1.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=8399417,CODECS=\"avc1.64002a,ac-3\",AUDIO=\"aud2\"\n"
          + "uri2.m3u8\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud1\",LANGUAGE=\"en\",NAME=\"English\","
          + "AUTOSELECT=YES,DEFAULT=YES,CHANNELS=\"2\",URI=\"a1/prog_index.m3u8\"\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud2\",LANGUAGE=\"en\",NAME=\"English\","
          + "AUTOSELECT=YES,DEFAULT=YES,CHANNELS=\"6\",URI=\"a2/prog_index.m3u8\"\n";

  private static final String PLAYLIST_WITH_INDEPENDENT_SEGMENTS =
      " #EXTM3U\n"
          + "\n"
          + "#EXT-X-INDEPENDENT-SEGMENTS\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,"
          + "CODECS=\"mp4a.40.2,avc1.66.30\",RESOLUTION=304x128\n"
          + "http://example.com/low.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=1280000,CODECS=\"mp4a.40.2 , avc1.66.30 \"\n"
          + "http://example.com/spaces_in_codecs.m3u8\n";

  private static final String PLAYLIST_WITH_VARIABLE_SUBSTITUTION =
      " #EXTM3U \n"
          + "\n"
          + "#EXT-X-DEFINE:NAME=\"codecs\",VALUE=\"mp4a.40.5\"\n"
          + "#EXT-X-DEFINE:NAME=\"tricky\",VALUE=\"This/{$nested}/reference/shouldnt/work\"\n"
          + "#EXT-X-DEFINE:NAME=\"nested\",VALUE=\"This should not be inserted\"\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=65000,CODECS=\"{$codecs}\"\n"
          + "http://example.com/{$tricky}\n";

  private static final String PLAYLIST_WITH_MATCHING_STREAM_INF_URLS =
      "#EXTM3U\n"
          + "#EXT-X-VERSION:6\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2227464,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud1\",SUBTITLES=\"sub1\"\n"
          + "v5/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=6453202,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud1\",SUBTITLES=\"sub1\"\n"
          + "v8/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=5054232,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud1\",SUBTITLES=\"sub1\"\n"
          + "v7/prog_index.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2448841,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud2\",SUBTITLES=\"sub1\"\n"
          + "v5/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=8399417,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud2\",SUBTITLES=\"sub1\"\n"
          + "v9/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=5275609,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud2\",SUBTITLES=\"sub1\"\n"
          + "v7/prog_index.m3u8\n"
          + "\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=2256841,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud3\",SUBTITLES=\"sub1\"\n"
          + "v5/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=8207417,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud3\",SUBTITLES=\"sub1\"\n"
          + "v9/prog_index.m3u8\n"
          + "#EXT-X-STREAM-INF:BANDWIDTH=6482579,"
          + "CLOSED-CAPTIONS=\"cc1\",AUDIO=\"aud3\",SUBTITLES=\"sub1\"\n"
          + "v8/prog_index.m3u8\n"
          + "\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud1\",NAME=\"English\",URI=\"a1/index.m3u8\"\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud2\",NAME=\"English\",URI=\"a2/index.m3u8\"\n"
          + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud3\",NAME=\"English\",URI=\"a3/index.m3u8\"\n"
          + "\n"
          + "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,"
          + "GROUP-ID=\"cc1\",NAME=\"English\",INSTREAM-ID=\"CC1\"\n"
          + "\n"
          + "#EXT-X-MEDIA:TYPE=SUBTITLES,"
          + "GROUP-ID=\"sub1\",NAME=\"English\",URI=\"s1/en/prog_index.m3u8\"\n";

  @Test
  public void testParseMasterPlaylist() throws IOException {
    HlsMasterPlaylist masterPlaylist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_SIMPLE);

    List<HlsMasterPlaylist.Variant> variants = masterPlaylist.variants;
    assertThat(variants).hasSize(5);
    assertThat(masterPlaylist.muxedCaptionFormats).isNull();

    assertThat(variants.get(0).format.bitrate).isEqualTo(1280000);
    assertThat(variants.get(0).format.codecs).isEqualTo("mp4a.40.2,avc1.66.30");
    assertThat(variants.get(0).format.width).isEqualTo(304);
    assertThat(variants.get(0).format.height).isEqualTo(128);
    assertThat(variants.get(0).url).isEqualTo(Uri.parse("http://example.com/low.m3u8"));

    assertThat(variants.get(1).format.bitrate).isEqualTo(1280000);
    assertThat(variants.get(1).format.codecs).isEqualTo("mp4a.40.2 , avc1.66.30 ");
    assertThat(variants.get(1).url)
        .isEqualTo(Uri.parse("http://example.com/spaces_in_codecs.m3u8"));

    assertThat(variants.get(2).format.bitrate).isEqualTo(2560000);
    assertThat(variants.get(2).format.codecs).isNull();
    assertThat(variants.get(2).format.width).isEqualTo(384);
    assertThat(variants.get(2).format.height).isEqualTo(160);
    assertThat(variants.get(2).format.frameRate).isEqualTo(25.0f);
    assertThat(variants.get(2).url).isEqualTo(Uri.parse("http://example.com/mid.m3u8"));

    assertThat(variants.get(3).format.bitrate).isEqualTo(7680000);
    assertThat(variants.get(3).format.codecs).isNull();
    assertThat(variants.get(3).format.width).isEqualTo(Format.NO_VALUE);
    assertThat(variants.get(3).format.height).isEqualTo(Format.NO_VALUE);
    assertThat(variants.get(3).format.frameRate).isEqualTo(29.997f);
    assertThat(variants.get(3).url).isEqualTo(Uri.parse("http://example.com/hi.m3u8"));

    assertThat(variants.get(4).format.bitrate).isEqualTo(65000);
    assertThat(variants.get(4).format.codecs).isEqualTo("mp4a.40.5");
    assertThat(variants.get(4).format.width).isEqualTo(Format.NO_VALUE);
    assertThat(variants.get(4).format.height).isEqualTo(Format.NO_VALUE);
    assertThat(variants.get(4).format.frameRate).isEqualTo((float) Format.NO_VALUE);
    assertThat(variants.get(4).url).isEqualTo(Uri.parse("http://example.com/audio-only.m3u8"));
  }

  @Test
  public void testMasterPlaylistWithBandwdithAverage() throws IOException {
    HlsMasterPlaylist masterPlaylist =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_AVG_BANDWIDTH);

    List<HlsMasterPlaylist.Variant> variants = masterPlaylist.variants;

    assertThat(variants.get(0).format.bitrate).isEqualTo(1280000);
    assertThat(variants.get(1).format.bitrate).isEqualTo(1270000);
  }

  @Test
  public void testPlaylistWithInvalidHeader() throws IOException {
    try {
      parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_INVALID_HEADER);
      fail("Expected exception not thrown.");
    } catch (ParserException e) {
      // Expected due to invalid header.
    }
  }

  @Test
  public void testPlaylistWithClosedCaption() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_CC);
    assertThat(playlist.muxedCaptionFormats).hasSize(1);
    Format closedCaptionFormat = playlist.muxedCaptionFormats.get(0);
    assertThat(closedCaptionFormat.sampleMimeType).isEqualTo(MimeTypes.APPLICATION_CEA708);
    assertThat(closedCaptionFormat.accessibilityChannel).isEqualTo(4);
    assertThat(closedCaptionFormat.language).isEqualTo("es");
  }

  @Test
  public void testPlaylistWithChannelsAttribute() throws IOException {
    HlsMasterPlaylist playlist =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_CHANNELS_ATTRIBUTE);
    List<HlsMasterPlaylist.Rendition> audios = playlist.audios;
    assertThat(audios).hasSize(3);
    assertThat(audios.get(0).format.channelCount).isEqualTo(6);
    assertThat(audios.get(1).format.channelCount).isEqualTo(2);
    assertThat(audios.get(2).format.channelCount).isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void testPlaylistWithoutClosedCaptions() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITHOUT_CC);
    assertThat(playlist.muxedCaptionFormats).isEmpty();
  }

  @Test
  public void testCodecPropagation() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_AUDIO_MEDIA_TAG);

    Format firstAudioFormat = playlist.audios.get(0).format;
    assertThat(firstAudioFormat.codecs).isEqualTo("mp4a.40.2");
    assertThat(firstAudioFormat.sampleMimeType).isEqualTo(MimeTypes.AUDIO_AAC);

    Format secondAudioFormat = playlist.audios.get(1).format;
    assertThat(secondAudioFormat.codecs).isEqualTo("ac-3");
    assertThat(secondAudioFormat.sampleMimeType).isEqualTo(MimeTypes.AUDIO_AC3);
  }

  @Test
  public void testAudioIdPropagation() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_AUDIO_MEDIA_TAG);

    Format firstAudioFormat = playlist.audios.get(0).format;
    assertThat(firstAudioFormat.id).isEqualTo("aud1:English");

    Format secondAudioFormat = playlist.audios.get(1).format;
    assertThat(secondAudioFormat.id).isEqualTo("aud2:English");
  }

  @Test
  public void testCCIdPropagation() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_CC);

    Format firstTextFormat = playlist.muxedCaptionFormats.get(0);
    assertThat(firstTextFormat.id).isEqualTo("cc1:Eng");
  }

  @Test
  public void testSubtitleIdPropagation() throws IOException {
    HlsMasterPlaylist playlist = parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_SUBTITLES);

    Format firstTextFormat = playlist.subtitles.get(0).format;
    assertThat(firstTextFormat.id).isEqualTo("sub1:Eng");
  }

  @Test
  public void testIndependentSegments() throws IOException {
    HlsMasterPlaylist playlistWithIndependentSegments =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_INDEPENDENT_SEGMENTS);
    assertThat(playlistWithIndependentSegments.hasIndependentSegments).isTrue();

    HlsMasterPlaylist playlistWithoutIndependentSegments =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_SIMPLE);
    assertThat(playlistWithoutIndependentSegments.hasIndependentSegments).isFalse();
  }

  @Test
  public void testVariableSubstitution() throws IOException {
    HlsMasterPlaylist playlistWithSubstitutions =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_VARIABLE_SUBSTITUTION);
    HlsMasterPlaylist.Variant variant = playlistWithSubstitutions.variants.get(0);
    assertThat(variant.format.codecs).isEqualTo("mp4a.40.5");
    assertThat(variant.url)
        .isEqualTo(Uri.parse("http://example.com/This/{$nested}/reference/shouldnt/work"));
  }

  @Test
  public void testHlsMetadata() throws IOException {
    HlsMasterPlaylist playlist =
        parseMasterPlaylist(PLAYLIST_URI, PLAYLIST_WITH_MATCHING_STREAM_INF_URLS);
    assertThat(playlist.variants).hasSize(4);
    assertThat(playlist.variants.get(0).format.metadata)
        .isEqualTo(
            createExtXStreamInfMetadata(
                createVariantInfo(/* bitrate= */ 2227464, /* audioGroupId= */ "aud1"),
                createVariantInfo(/* bitrate= */ 2448841, /* audioGroupId= */ "aud2"),
                createVariantInfo(/* bitrate= */ 2256841, /* audioGroupId= */ "aud3")));
    assertThat(playlist.variants.get(1).format.metadata)
        .isEqualTo(
            createExtXStreamInfMetadata(
                createVariantInfo(/* bitrate= */ 6453202, /* audioGroupId= */ "aud1"),
                createVariantInfo(/* bitrate= */ 6482579, /* audioGroupId= */ "aud3")));
    assertThat(playlist.variants.get(2).format.metadata)
        .isEqualTo(
            createExtXStreamInfMetadata(
                createVariantInfo(/* bitrate= */ 5054232, /* audioGroupId= */ "aud1"),
                createVariantInfo(/* bitrate= */ 5275609, /* audioGroupId= */ "aud2")));
    assertThat(playlist.variants.get(3).format.metadata)
        .isEqualTo(
            createExtXStreamInfMetadata(
                createVariantInfo(/* bitrate= */ 8399417, /* audioGroupId= */ "aud2"),
                createVariantInfo(/* bitrate= */ 8207417, /* audioGroupId= */ "aud3")));

    assertThat(playlist.audios).hasSize(3);
    assertThat(playlist.audios.get(0).format.metadata)
        .isEqualTo(createExtXMediaMetadata(/* groupId= */ "aud1", /* name= */ "English"));
    assertThat(playlist.audios.get(1).format.metadata)
        .isEqualTo(createExtXMediaMetadata(/* groupId= */ "aud2", /* name= */ "English"));
    assertThat(playlist.audios.get(2).format.metadata)
        .isEqualTo(createExtXMediaMetadata(/* groupId= */ "aud3", /* name= */ "English"));
  }

  private static Metadata createExtXStreamInfMetadata(HlsTrackMetadataEntry.VariantInfo... infos) {
    return new Metadata(
        new HlsTrackMetadataEntry(/* groupId= */ null, /* name= */ null, Arrays.asList(infos)));
  }

  private static Metadata createExtXMediaMetadata(String groupId, String name) {
    return new Metadata(new HlsTrackMetadataEntry(groupId, name, Collections.emptyList()));
  }

  private static HlsTrackMetadataEntry.VariantInfo createVariantInfo(
      long bitrate, String audioGroupId) {
    return new HlsTrackMetadataEntry.VariantInfo(
        bitrate,
        /* videoGroupId= */ null,
        audioGroupId,
        /* subtitleGroupId= */ "sub1",
        /* captionGroupId= */ "cc1");
  }

  private static HlsMasterPlaylist parseMasterPlaylist(String uri, String playlistString)
      throws IOException {
    Uri playlistUri = Uri.parse(uri);
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(playlistString.getBytes(Charset.forName(C.UTF8_NAME)));
    return (HlsMasterPlaylist) new HlsPlaylistParser().parse(playlistUri, inputStream);
  }
}
