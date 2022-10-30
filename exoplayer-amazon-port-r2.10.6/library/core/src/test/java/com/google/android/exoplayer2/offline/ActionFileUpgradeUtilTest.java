/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.android.exoplayer2.offline.DownloadRequest.TYPE_PROGRESSIVE;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ActionFileUpgradeUtil}. */
@RunWith(AndroidJUnit4.class)
public class ActionFileUpgradeUtilTest {

  private static final long NOW_MS = 1234;

  private File tempFile;
  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    databaseProvider = new ExoDatabaseProvider(ApplicationProvider.getApplicationContext());
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
    tempFile.delete();
  }

  @Test
  public void upgradeAndDelete_createsDownloads() throws IOException {
    // Copy the test asset to a file.
    byte[] actionFileBytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(),
            "offline/action_file_for_download_index_upgrade.exi");
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }

    StreamKey expectedStreamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey expectedStreamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadRequest expectedRequest1 =
        new DownloadRequest(
            "key123",
            /* type= */ "test",
            Uri.parse("https://www.test.com/download1"),
            asList(expectedStreamKey1),
            /* customCacheKey= */ "key123",
            new byte[] {1, 2, 3, 4});
    DownloadRequest expectedRequest2 =
        new DownloadRequest(
            "key234",
            /* type= */ "test",
            Uri.parse("https://www.test.com/download2"),
            asList(expectedStreamKey2),
            /* customCacheKey= */ "key234",
            new byte[] {5, 4, 3, 2, 1});

    ActionFileUpgradeUtil.upgradeAndDelete(
        tempFile,
        /* downloadIdProvider= */ null,
        downloadIndex,
        /* deleteOnFailure= */ true,
        /* addNewDownloadsAsCompleted= */ false);

    assertDownloadIndexContainsRequest(expectedRequest1, Download.STATE_QUEUED);
    assertDownloadIndexContainsRequest(expectedRequest2, Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_nonExistingDownload_createsNewDownload() throws IOException {
    byte[] data = new byte[] {1, 2, 3, 4};
    DownloadRequest request =
        new DownloadRequest(
            "id",
            TYPE_PROGRESSIVE,
            Uri.parse("https://www.test.com/download"),
            asList(
                new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5)),
            /* customCacheKey= */ "key123",
            data);

    ActionFileUpgradeUtil.mergeRequest(
        request, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);

    assertDownloadIndexContainsRequest(request, Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_existingDownload_createsMergedDownload() throws IOException {
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadRequest request1 =
        new DownloadRequest(
            "id",
            TYPE_PROGRESSIVE,
            Uri.parse("https://www.test.com/download1"),
            asList(streamKey1),
            /* customCacheKey= */ "key123",
            new byte[] {1, 2, 3, 4});
    DownloadRequest request2 =
        new DownloadRequest(
            "id",
            TYPE_PROGRESSIVE,
            Uri.parse("https://www.test.com/download2"),
            asList(streamKey2),
            /* customCacheKey= */ "key123",
            new byte[] {5, 4, 3, 2, 1});
    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);
    ActionFileUpgradeUtil.mergeRequest(
        request2, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);

    Download download = downloadIndex.getDownload(request2.id);
    assertThat(download).isNotNull();
    assertThat(download.request.type).isEqualTo(request2.type);
    assertThat(download.request.customCacheKey).isEqualTo(request2.customCacheKey);
    assertThat(download.request.data).isEqualTo(request2.data);
    assertThat(download.request.uri).isEqualTo(request2.uri);
    assertThat(download.request.streamKeys).containsExactly(streamKey1, streamKey2);
    assertThat(download.state).isEqualTo(Download.STATE_QUEUED);
  }

  @Test
  public void mergeRequest_addNewDownloadAsCompleted() throws IOException {
    StreamKey streamKey1 =
        new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5);
    StreamKey streamKey2 =
        new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2);
    DownloadRequest request1 =
        new DownloadRequest(
            "id1",
            TYPE_PROGRESSIVE,
            Uri.parse("https://www.test.com/download1"),
            asList(streamKey1),
            /* customCacheKey= */ "key123",
            new byte[] {1, 2, 3, 4});
    DownloadRequest request2 =
        new DownloadRequest(
            "id2",
            TYPE_PROGRESSIVE,
            Uri.parse("https://www.test.com/download2"),
            asList(streamKey2),
            /* customCacheKey= */ "key123",
            new byte[] {5, 4, 3, 2, 1});
    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ false, NOW_MS);

    // Merging existing download, keeps it queued.
    ActionFileUpgradeUtil.mergeRequest(
        request1, downloadIndex, /* addNewDownloadAsCompleted= */ true, NOW_MS);
    assertThat(downloadIndex.getDownload(request1.id).state).isEqualTo(Download.STATE_QUEUED);

    // New download is merged as completed.
    ActionFileUpgradeUtil.mergeRequest(
        request2, downloadIndex, /* addNewDownloadAsCompleted= */ true, NOW_MS);
    assertThat(downloadIndex.getDownload(request2.id).state).isEqualTo(Download.STATE_COMPLETED);
  }

  private void assertDownloadIndexContainsRequest(DownloadRequest request, int state)
      throws IOException {
    Download download = downloadIndex.getDownload(request.id);
    assertThat(download.request).isEqualTo(request);
    assertThat(download.state).isEqualTo(state);
  }

  @SuppressWarnings("unchecked")
  private static List<StreamKey> asList(StreamKey... streamKeys) {
    return Arrays.asList(streamKeys);
  }
}
