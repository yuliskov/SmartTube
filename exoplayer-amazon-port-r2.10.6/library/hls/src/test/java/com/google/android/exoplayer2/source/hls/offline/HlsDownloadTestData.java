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

import com.google.android.exoplayer2.C;
import java.nio.charset.Charset;

/** Data for HLS downloading tests. */
/* package */ interface HlsDownloadTestData {

  String MASTER_PLAYLIST_URI = "test.m3u8";
  int MASTER_MEDIA_PLAYLIST_1_INDEX = 0;
  int MASTER_MEDIA_PLAYLIST_2_INDEX = 1;
  int MASTER_MEDIA_PLAYLIST_3_INDEX = 2;
  int MASTER_MEDIA_PLAYLIST_0_INDEX = 3;

  String MEDIA_PLAYLIST_0_DIR = "gear0/";
  String MEDIA_PLAYLIST_0_URI = MEDIA_PLAYLIST_0_DIR + "prog_index.m3u8";
  String MEDIA_PLAYLIST_1_DIR = "gear1/";
  String MEDIA_PLAYLIST_1_URI = MEDIA_PLAYLIST_1_DIR + "prog_index.m3u8";
  String MEDIA_PLAYLIST_2_DIR = "gear2/";
  String MEDIA_PLAYLIST_2_URI = MEDIA_PLAYLIST_2_DIR + "prog_index.m3u8";
  String MEDIA_PLAYLIST_3_DIR = "gear3/";
  String MEDIA_PLAYLIST_3_URI = MEDIA_PLAYLIST_3_DIR + "prog_index.m3u8";

  byte[] MASTER_PLAYLIST_DATA =
      ("#EXTM3U\n"
              + "#EXT-X-STREAM-INF:BANDWIDTH=232370,CODECS=\"mp4a.40.2, avc1.4d4015\"\n"
              + MEDIA_PLAYLIST_1_URI
              + "\n"
              + "#EXT-X-STREAM-INF:BANDWIDTH=649879,CODECS=\"mp4a.40.2, avc1.4d401e\"\n"
              + MEDIA_PLAYLIST_2_URI
              + "\n"
              + "#EXT-X-STREAM-INF:BANDWIDTH=991714,CODECS=\"mp4a.40.2, avc1.4d401e\"\n"
              + MEDIA_PLAYLIST_3_URI
              + "\n"
              + "#EXT-X-STREAM-INF:BANDWIDTH=41457,CODECS=\"mp4a.40.2\"\n"
              + MEDIA_PLAYLIST_0_URI)
          .getBytes(Charset.forName(C.UTF8_NAME));

  byte[] MEDIA_PLAYLIST_DATA =
      ("#EXTM3U\n"
              + "#EXT-X-TARGETDURATION:10\n"
              + "#EXT-X-VERSION:3\n"
              + "#EXT-X-MEDIA-SEQUENCE:0\n"
              + "#EXT-X-PLAYLIST-TYPE:VOD\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence0.ts\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence1.ts\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence2.ts\n"
              + "#EXT-X-ENDLIST")
          .getBytes(Charset.forName(C.UTF8_NAME));

  String ENC_MEDIA_PLAYLIST_URI = "enc_index.m3u8";

  byte[] ENC_MEDIA_PLAYLIST_DATA =
      ("#EXTM3U\n"
              + "#EXT-X-TARGETDURATION:10\n"
              + "#EXT-X-VERSION:3\n"
              + "#EXT-X-MEDIA-SEQUENCE:0\n"
              + "#EXT-X-PLAYLIST-TYPE:VOD\n"
              + "#EXT-X-KEY:METHOD=AES-128,URI=\"enc.key\"\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence0.ts\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence1.ts\n"
              + "#EXT-X-KEY:METHOD=AES-128,URI=\"enc2.key\"\n"
              + "#EXTINF:9.97667,\n"
              + "fileSequence2.ts\n"
              + "#EXT-X-ENDLIST")
          .getBytes(Charset.forName(C.UTF8_NAME));
}
