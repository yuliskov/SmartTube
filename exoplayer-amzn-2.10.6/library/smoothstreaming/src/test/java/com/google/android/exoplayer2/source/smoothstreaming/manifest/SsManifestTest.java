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
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import static com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils.createSsManifest;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.smoothstreaming.SsTestUtils;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SsManifest}. */
@RunWith(AndroidJUnit4.class)
public class SsManifestTest {

  @Test
  public void testCopy() throws Exception {
    Format[][] formats = newFormats(2, 3);
    SsManifest sourceManifest =
        createSsManifest(
            createStreamElement("1", formats[0]), createStreamElement("2", formats[1]));

    List<StreamKey> keys =
        Arrays.asList(new StreamKey(0, 0), new StreamKey(0, 2), new StreamKey(1, 0));
    // Keys don't need to be in any particular order
    Collections.shuffle(keys, new Random(0));

    SsManifest copyManifest = sourceManifest.copy(keys);

    SsManifest expectedManifest =
        createSsManifest(
            createStreamElement("1", formats[0][0], formats[0][2]),
            createStreamElement("2", formats[1][0]));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  @Test
  public void testCopyRemoveStreamElement() throws Exception {
    Format[][] formats = newFormats(2, 3);
    SsManifest sourceManifest =
        createSsManifest(
            createStreamElement("1", formats[0]), createStreamElement("2", formats[1]));

    List<StreamKey> keys = Collections.singletonList(new StreamKey(1, 0));

    SsManifest copyManifest = sourceManifest.copy(keys);

    SsManifest expectedManifest = createSsManifest(createStreamElement("2", formats[1][0]));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  private static void assertManifestEquals(SsManifest expected, SsManifest actual) {
    assertThat(actual.durationUs).isEqualTo(expected.durationUs);
    assertThat(actual.dvrWindowLengthUs).isEqualTo(expected.dvrWindowLengthUs);
    assertThat(actual.isLive).isEqualTo(expected.isLive);
    assertThat(actual.lookAheadCount).isEqualTo(expected.lookAheadCount);
    assertThat(actual.majorVersion).isEqualTo(expected.majorVersion);
    assertThat(actual.minorVersion).isEqualTo(expected.minorVersion);
    assertThat(actual.protectionElement.uuid).isEqualTo(expected.protectionElement.uuid);
    assertThat(actual.protectionElement).isEqualTo(expected.protectionElement);
    for (int i = 0; i < expected.streamElements.length; i++) {
      StreamElement expectedStreamElement = expected.streamElements[i];
      StreamElement actualStreamElement = actual.streamElements[i];
      assertThat(actualStreamElement.chunkCount).isEqualTo(expectedStreamElement.chunkCount);
      assertThat(actualStreamElement.displayHeight).isEqualTo(expectedStreamElement.displayHeight);
      assertThat(actualStreamElement.displayWidth).isEqualTo(expectedStreamElement.displayWidth);
      assertThat(actualStreamElement.language).isEqualTo(expectedStreamElement.language);
      assertThat(actualStreamElement.maxHeight).isEqualTo(expectedStreamElement.maxHeight);
      assertThat(actualStreamElement.maxWidth).isEqualTo(expectedStreamElement.maxWidth);
      assertThat(actualStreamElement.name).isEqualTo(expectedStreamElement.name);
      assertThat(actualStreamElement.subType).isEqualTo(expectedStreamElement.subType);
      assertThat(actualStreamElement.timescale).isEqualTo(expectedStreamElement.timescale);
      assertThat(actualStreamElement.type).isEqualTo(expectedStreamElement.type);
      assertThat(actualStreamElement.formats).isEqualTo(expectedStreamElement.formats);
    }
  }

  private static Format[][] newFormats(int streamElementCount, int trackCounts) {
    Format[][] formats = new Format[streamElementCount][];
    for (int i = 0; i < streamElementCount; i++) {
      formats[i] = new Format[trackCounts];
      for (int j = 0; j < trackCounts; j++) {
        formats[i][j] = newFormat(i + "." + j);
      }
    }
    return formats;
  }

  private static StreamElement createStreamElement(String name, Format... formats) {
    return SsTestUtils.createStreamElement(name, C.TRACK_TYPE_VIDEO, formats);
  }

  private static Format newFormat(String id) {
    return Format.createContainerFormat(
        id,
        /* label= */ null,
        MimeTypes.VIDEO_MP4,
        MimeTypes.VIDEO_H264,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        /* language= */ null);
  }
}
