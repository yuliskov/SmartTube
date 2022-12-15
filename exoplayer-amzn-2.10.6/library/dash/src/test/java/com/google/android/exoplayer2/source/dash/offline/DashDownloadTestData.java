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
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import java.nio.charset.Charset;

/** Data for DASH downloading tests. */
/* package */ interface DashDownloadTestData {

  String TEST_ID = "test.mpd";
  Uri TEST_MPD_URI = Uri.parse(TEST_ID);

  byte[] TEST_MPD =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" type=\"static\" "
              + "    mediaPresentationDuration=\"PT31S\">\n"
              + "    <Period duration=\"PT16S\" >\n"
              + "        <AdaptationSet>\n"
              + "            <SegmentList>\n"
              + "                <SegmentTimeline>\n"
              + "                    <S d=\"5\" />\n"
              + "                    <S d=\"5\" />\n"
              + "                    <S d=\"5\" />\n"
              + "                </SegmentTimeline>\n"
              + "            </SegmentList>\n"
              + "            <Representation>\n"
              + "                <SegmentList>\n"
              // Bounded range data
              + "                    <Initialization\n"
              + "                        range=\"0-9\" sourceURL=\"audio_init_data\" />\n"
              // Unbounded range data
              + "                    <SegmentURL media=\"audio_segment_1\" />\n"
              + "                    <SegmentURL media=\"audio_segment_2\" />\n"
              + "                    <SegmentURL media=\"audio_segment_3\" />\n"
              + "                </SegmentList>\n"
              + "            </Representation>\n"
              + "        </AdaptationSet>\n"
              + "        <AdaptationSet>\n"
              // This segment list has a 1 second offset to make sure the progressive download order
              + "            <SegmentList>\n"
              + "                <SegmentTimeline>\n"
              + "                    <S t=\"1\" d=\"5\" />\n" // 1s offset
              + "                    <S d=\"5\" />\n"
              + "                    <S d=\"5\" />\n"
              + "                </SegmentTimeline>\n"
              + "            </SegmentList>\n"
              + "            <Representation>\n"
              + "                <SegmentList>\n"
              + "                    <SegmentURL media=\"text_segment_1\" />\n"
              + "                    <SegmentURL media=\"text_segment_2\" />\n"
              + "                    <SegmentURL media=\"text_segment_3\" />\n"
              + "                </SegmentList>\n"
              + "            </Representation>\n"
              + "        </AdaptationSet>\n"
              + "    </Period>\n"
              + "    <Period>\n"
              + "        <SegmentList>\n"
              + "            <SegmentTimeline>\n"
              + "                <S d=\"5\" />\n"
              + "                <S d=\"5\" />\n"
              + "                <S d=\"5\" />\n"
              + "            </SegmentTimeline>\n"
              + "        </SegmentList>\n"
              + "        <AdaptationSet>\n"
              + "            <Representation>\n"
              + "                <SegmentList>\n"
              + "                    <SegmentURL media=\"period_2_segment_1\" />\n"
              + "                    <SegmentURL media=\"period_2_segment_2\" />\n"
              + "                    <SegmentURL media=\"period_2_segment_3\" />\n"
              + "                </SegmentList>\n"
              + "            </Representation>\n"
              + "        </AdaptationSet>\n"
              + "    </Period>\n"
              + "</MPD>")
          .getBytes(Charset.forName(C.UTF8_NAME));

  byte[] TEST_MPD_NO_INDEX =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" type=\"dynamic\">\n"
              + "    <Period start=\"PT6462826.784S\" >\n"
              + "        <AdaptationSet>\n"
              + "            <Representation>\n"
              + "                <SegmentBase indexRange='0-10'/>\n"
              + "            </Representation>\n"
              + "        </AdaptationSet>\n"
              + "    </Period>\n"
              + "</MPD>")
          .getBytes(Charset.forName(C.UTF8_NAME));
}
