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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MimeTypes}. */
@RunWith(AndroidJUnit4.class)
public final class MimeTypesTest {

  @Test
  public void testGetMediaMimeType_fromValidCodecs_returnsCorrectMimeType() {
    assertThat(MimeTypes.getMediaMimeType("avc1")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.42E01E")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.42E01F")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.4D401F")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.4D4028")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.640028")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc1.640029")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("avc3")).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMediaMimeType("hev1")).isEqualTo(MimeTypes.VIDEO_H265);
    assertThat(MimeTypes.getMediaMimeType("hvc1")).isEqualTo(MimeTypes.VIDEO_H265);
    assertThat(MimeTypes.getMediaMimeType("vp08")).isEqualTo(MimeTypes.VIDEO_VP8);
    assertThat(MimeTypes.getMediaMimeType("vp8")).isEqualTo(MimeTypes.VIDEO_VP8);
    assertThat(MimeTypes.getMediaMimeType("vp09")).isEqualTo(MimeTypes.VIDEO_VP9);
    assertThat(MimeTypes.getMediaMimeType("vp9")).isEqualTo(MimeTypes.VIDEO_VP9);

    assertThat(MimeTypes.getMediaMimeType("ac-3")).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(MimeTypes.getMediaMimeType("dac3")).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(MimeTypes.getMediaMimeType("dec3")).isEqualTo(MimeTypes.AUDIO_E_AC3);
    assertThat(MimeTypes.getMediaMimeType("ec-3")).isEqualTo(MimeTypes.AUDIO_E_AC3);
    assertThat(MimeTypes.getMediaMimeType("ec+3")).isEqualTo(MimeTypes.AUDIO_E_AC3_JOC);
    assertThat(MimeTypes.getMediaMimeType("dtsc")).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMediaMimeType("dtse")).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMediaMimeType("dtsh")).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMediaMimeType("dtsl")).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMediaMimeType("opus")).isEqualTo(MimeTypes.AUDIO_OPUS);
    assertThat(MimeTypes.getMediaMimeType("vorbis")).isEqualTo(MimeTypes.AUDIO_VORBIS);
    assertThat(MimeTypes.getMediaMimeType("mp4a")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.40.02")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.40.05")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.40.2")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.40.5")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.40.29")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.66")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.67")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.68")).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMediaMimeType("mp4a.69")).isEqualTo(MimeTypes.AUDIO_MPEG);
    assertThat(MimeTypes.getMediaMimeType("mp4a.6B")).isEqualTo(MimeTypes.AUDIO_MPEG);
    assertThat(MimeTypes.getMediaMimeType("mp4a.a5")).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(MimeTypes.getMediaMimeType("mp4a.A5")).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(MimeTypes.getMediaMimeType("mp4a.a6")).isEqualTo(MimeTypes.AUDIO_E_AC3);
    assertThat(MimeTypes.getMediaMimeType("mp4a.A6")).isEqualTo(MimeTypes.AUDIO_E_AC3);
    assertThat(MimeTypes.getMediaMimeType("mp4a.A9")).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMediaMimeType("mp4a.AC")).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMediaMimeType("mp4a.AA")).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMediaMimeType("mp4a.AB")).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMediaMimeType("mp4a.AD")).isEqualTo(MimeTypes.AUDIO_OPUS);
  }

  @Test
  public void testGetMimeTypeFromMp4ObjectType_forValidObjectType_returnsCorrectMimeType() {
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x60)).isEqualTo(MimeTypes.VIDEO_MPEG2);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x61)).isEqualTo(MimeTypes.VIDEO_MPEG2);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x20)).isEqualTo(MimeTypes.VIDEO_MP4V);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x21)).isEqualTo(MimeTypes.VIDEO_H264);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x23)).isEqualTo(MimeTypes.VIDEO_H265);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x6B)).isEqualTo(MimeTypes.AUDIO_MPEG);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x40)).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x66)).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x67)).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x68)).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xA5)).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xA6)).isEqualTo(MimeTypes.AUDIO_E_AC3);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xA9)).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xAC)).isEqualTo(MimeTypes.AUDIO_DTS);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xAA)).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xAB)).isEqualTo(MimeTypes.AUDIO_DTS_HD);
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0xAD)).isEqualTo(MimeTypes.AUDIO_OPUS);
  }

  @Test
  public void testGetMimeTypeFromMp4ObjectType_forInvalidObjectType_returnsNull() {
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0)).isNull();
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x600)).isNull();
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(0x01)).isNull();
    assertThat(MimeTypes.getMimeTypeFromMp4ObjectType(-1)).isNull();
  }
}
