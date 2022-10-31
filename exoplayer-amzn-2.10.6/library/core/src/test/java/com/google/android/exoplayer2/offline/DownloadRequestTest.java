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
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.offline.DownloadRequest.TYPE_DASH;
import static com.google.android.exoplayer2.offline.DownloadRequest.TYPE_HLS;
import static com.google.android.exoplayer2.offline.DownloadRequest.TYPE_PROGRESSIVE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DownloadRequest}. */
@RunWith(AndroidJUnit4.class)
public class DownloadRequestTest {

  private Uri uri1;
  private Uri uri2;

  @Before
  public void setUp() {
    uri1 = Uri.parse("http://test/1.uri");
    uri2 = Uri.parse("http://test/2.uri");
  }

  @Test
  public void testMergeRequests_withDifferentIds_fails() {
    DownloadRequest request1 =
        new DownloadRequest(
            "id1",
            TYPE_DASH,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadRequest request2 =
        new DownloadRequest(
            "id2",
            TYPE_DASH,
            uri2,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    try {
      request1.copyWithMergedRequest(request2);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testMergeRequests_withDifferentTypes_fails() {
    DownloadRequest request1 =
        new DownloadRequest(
            "id1",
            TYPE_DASH,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    DownloadRequest request2 =
        new DownloadRequest(
            "id1",
            TYPE_HLS,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            /* customCacheKey= */ null,
            /* data= */ null);
    try {
      request1.copyWithMergedRequest(request2);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testMergeRequest_withSameRequest() {
    DownloadRequest request1 = createRequest(uri1, new StreamKey(0, 0, 0));

    DownloadRequest mergedRequest = request1.copyWithMergedRequest(request1);
    assertEqual(request1, mergedRequest);
  }

  @Test
  public void testMergeRequests_withEmptyStreamKeys() {
    DownloadRequest request1 = createRequest(uri1, new StreamKey(0, 0, 0));
    DownloadRequest request2 = createRequest(uri1);

    // If either of the requests have empty streamKeys, the merge should have empty streamKeys.
    DownloadRequest mergedRequest = request1.copyWithMergedRequest(request2);
    assertThat(mergedRequest.streamKeys).isEmpty();

    mergedRequest = request2.copyWithMergedRequest(request1);
    assertThat(mergedRequest.streamKeys).isEmpty();
  }

  @Test
  public void testMergeRequests_withOverlappingStreamKeys() {
    StreamKey streamKey1 = new StreamKey(0, 1, 2);
    StreamKey streamKey2 = new StreamKey(3, 4, 5);
    StreamKey streamKey3 = new StreamKey(6, 7, 8);
    DownloadRequest request1 = createRequest(uri1, streamKey1, streamKey2);
    DownloadRequest request2 = createRequest(uri1, streamKey2, streamKey3);

    // Merged streamKeys should be in their original order without duplicates.
    DownloadRequest mergedRequest = request1.copyWithMergedRequest(request2);
    assertThat(mergedRequest.streamKeys).containsExactly(streamKey1, streamKey2, streamKey3);

    mergedRequest = request2.copyWithMergedRequest(request1);
    assertThat(mergedRequest.streamKeys).containsExactly(streamKey2, streamKey3, streamKey1);
  }

  @Test
  public void testMergeRequests_withDifferentFields() {
    byte[] data1 = new byte[] {0, 1, 2};
    byte[] data2 = new byte[] {3, 4, 5};
    DownloadRequest request1 =
        new DownloadRequest(
            "id1",
            TYPE_PROGRESSIVE,
            uri1,
            /* streamKeys= */ Collections.emptyList(),
            "key1",
            /* data= */ data1);
    DownloadRequest request2 =
        new DownloadRequest(
            "id1",
            TYPE_PROGRESSIVE,
            uri2,
            /* streamKeys= */ Collections.emptyList(),
            "key2",
            /* data= */ data2);

    // uri, customCacheKey and data should be from the request being merged.
    DownloadRequest mergedRequest = request1.copyWithMergedRequest(request2);
    assertThat(mergedRequest.uri).isEqualTo(uri2);
    assertThat(mergedRequest.customCacheKey).isEqualTo("key2");
    assertThat(mergedRequest.data).isEqualTo(data2);

    mergedRequest = request2.copyWithMergedRequest(request1);
    assertThat(mergedRequest.uri).isEqualTo(uri1);
    assertThat(mergedRequest.customCacheKey).isEqualTo("key1");
    assertThat(mergedRequest.data).isEqualTo(data1);
  }

  @Test
  public void testParcelable() {
    ArrayList<StreamKey> streamKeys = new ArrayList<>();
    streamKeys.add(new StreamKey(1, 2, 3));
    streamKeys.add(new StreamKey(4, 5, 6));
    DownloadRequest requestToParcel =
        new DownloadRequest(
            "id",
            "type",
            Uri.parse("https://abc.def/ghi"),
            streamKeys,
            "key",
            new byte[] {1, 2, 3, 4, 5});
    Parcel parcel = Parcel.obtain();
    requestToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    DownloadRequest requestFromParcel = DownloadRequest.CREATOR.createFromParcel(parcel);
    assertThat(requestFromParcel).isEqualTo(requestToParcel);

    parcel.recycle();
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void testEquals() {
    DownloadRequest request1 = createRequest(uri1);
    assertThat(request1.equals(request1)).isTrue();

    DownloadRequest request2 = createRequest(uri1);
    DownloadRequest request3 = createRequest(uri1);
    assertEqual(request2, request3);

    DownloadRequest request4 = createRequest(uri1);
    DownloadRequest request5 = createRequest(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(request4, request5);

    DownloadRequest request6 = createRequest(uri1, new StreamKey(0, 1, 1));
    DownloadRequest request7 = createRequest(uri1, new StreamKey(0, 0, 0));
    assertNotEqual(request6, request7);

    DownloadRequest request8 = createRequest(uri1);
    DownloadRequest request9 = createRequest(uri2);
    assertNotEqual(request8, request9);

    DownloadRequest request10 = createRequest(uri1, new StreamKey(0, 0, 0), new StreamKey(0, 1, 1));
    DownloadRequest request11 = createRequest(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertEqual(request10, request11);

    DownloadRequest request12 = createRequest(uri1, new StreamKey(0, 0, 0));
    DownloadRequest request13 = createRequest(uri1, new StreamKey(0, 1, 1), new StreamKey(0, 0, 0));
    assertNotEqual(request12, request13);

    DownloadRequest request14 = createRequest(uri1);
    DownloadRequest request15 = createRequest(uri1);
    assertEqual(request14, request15);
  }

  private static void assertNotEqual(DownloadRequest request1, DownloadRequest request2) {
    assertThat(request1).isNotEqualTo(request2);
    assertThat(request2).isNotEqualTo(request1);
  }

  private static void assertEqual(DownloadRequest request1, DownloadRequest request2) {
    assertThat(request1).isEqualTo(request2);
    assertThat(request2).isEqualTo(request1);
  }

  private static DownloadRequest createRequest(Uri uri, StreamKey... keys) {
    return new DownloadRequest(
        uri.toString(), TYPE_DASH, uri, toList(keys), /* customCacheKey= */ null, /* data= */ null);
  }

  private static List<StreamKey> toList(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return keysList;
  }
}
