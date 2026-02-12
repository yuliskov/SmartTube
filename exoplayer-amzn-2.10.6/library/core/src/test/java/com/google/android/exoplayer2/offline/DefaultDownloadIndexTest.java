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

import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultDownloadIndex}. */
@RunWith(AndroidJUnit4.class)
public class DefaultDownloadIndexTest {

  private static final String EMPTY_NAME = "";

  private ExoDatabaseProvider databaseProvider;
  private DefaultDownloadIndex downloadIndex;

  @Before
  public void setUp() {
    databaseProvider = new ExoDatabaseProvider(ApplicationProvider.getApplicationContext());
    downloadIndex = new DefaultDownloadIndex(databaseProvider);
  }

  @After
  public void tearDown() {
    databaseProvider.close();
  }

  @Test
  public void getDownload_nonExistingId_returnsNull() throws DatabaseIOException {
    assertThat(downloadIndex.getDownload("non existing id")).isNull();
  }

  @Test
  public void addAndGetDownload_nonExistingId_returnsTheSameDownload() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();

    downloadIndex.putDownload(download);
    Download readDownload = downloadIndex.getDownload(id);

    assertEqual(readDownload, download);
  }

  @Test
  public void addAndGetDownload_existingId_returnsUpdatedDownload() throws DatabaseIOException {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id);
    downloadIndex.putDownload(downloadBuilder.build());

    Download download =
        downloadBuilder
            .setType("different type")
            .setUri("different uri")
            .setCacheKey("different cacheKey")
            .setState(Download.STATE_FAILED)
            .setPercentDownloaded(50)
            .setBytesDownloaded(200)
            .setContentLength(400)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN)
            .setStopReason(0x12345678)
            .setStartTimeMs(10)
            .setUpdateTimeMs(20)
            .setStreamKeys(
                new StreamKey(/* periodIndex= */ 0, /* groupIndex= */ 1, /* trackIndex= */ 2),
                new StreamKey(/* periodIndex= */ 3, /* groupIndex= */ 4, /* trackIndex= */ 5))
            .setCustomMetadata(new byte[] {0, 1, 2, 3, 7, 8, 9, 10})
            .build();
    downloadIndex.putDownload(download);
    Download readDownload = downloadIndex.getDownload(id);

    assertThat(readDownload).isNotNull();
    assertEqual(readDownload, download);
  }

  @Test
  public void releaseAndRecreateDownloadIndex_returnsTheSameDownload() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();
    downloadIndex.putDownload(download);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);
    Download readDownload = downloadIndex.getDownload(id);
    assertThat(readDownload).isNotNull();
    assertEqual(readDownload, download);
  }

  @Test
  public void removeDownload_nonExistingId_doesNotFail() throws DatabaseIOException {
    downloadIndex.removeDownload("non existing id");
  }

  @Test
  public void removeDownload_existingId_getDownloadReturnsNull() throws DatabaseIOException {
    String id = "id";
    Download download = new DownloadBuilder(id).build();
    downloadIndex.putDownload(download);
    downloadIndex.removeDownload(id);

    Download readDownload = downloadIndex.getDownload(id);
    assertThat(readDownload).isNull();
  }

  @Test
  public void getDownloads_emptyDownloadIndex_returnsEmptyArray() throws DatabaseIOException {
    assertThat(downloadIndex.getDownloads().getCount()).isEqualTo(0);
  }

  @Test
  public void getDownloads_noState_returnsAllDownloadStatusSortedByStartTime()
      throws DatabaseIOException {
    Download download1 = new DownloadBuilder("id1").setStartTimeMs(1).build();
    downloadIndex.putDownload(download1);
    Download download2 = new DownloadBuilder("id2").setStartTimeMs(0).build();
    downloadIndex.putDownload(download2);

    try (DownloadCursor cursor = downloadIndex.getDownloads()) {
      assertThat(cursor.getCount()).isEqualTo(2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download1);
    }
  }

  @Test
  public void getDownloads_withStates_returnsAllDownloadStatusWithTheSameStates()
      throws DatabaseIOException {
    Download download1 =
        new DownloadBuilder("id1").setStartTimeMs(0).setState(Download.STATE_REMOVING).build();
    downloadIndex.putDownload(download1);
    Download download2 =
        new DownloadBuilder("id2").setStartTimeMs(1).setState(Download.STATE_STOPPED).build();
    downloadIndex.putDownload(download2);
    Download download3 =
        new DownloadBuilder("id3").setStartTimeMs(2).setState(Download.STATE_COMPLETED).build();
    downloadIndex.putDownload(download3);

    try (DownloadCursor cursor =
        downloadIndex.getDownloads(Download.STATE_REMOVING, Download.STATE_COMPLETED)) {
      assertThat(cursor.getCount()).isEqualTo(2);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download1);
      cursor.moveToNext();
      assertEqual(cursor.getDownload(), download3);
    }
  }

  @Test
  public void putDownload_setsVersion() throws DatabaseIOException {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(VersionTable.VERSION_UNSET);

    downloadIndex.putDownload(new DownloadBuilder("id1").build());

    assertThat(VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void downloadIndex_versionDowngradeWipesData() throws DatabaseIOException {
    Download download1 = new DownloadBuilder("id1").build();
    downloadIndex.putDownload(download1);
    DownloadCursor cursor = downloadIndex.getDownloads();
    assertThat(cursor.getCount()).isEqualTo(1);
    cursor.close();

    SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
    VersionTable.setVersion(
        writableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME, Integer.MAX_VALUE);

    downloadIndex = new DefaultDownloadIndex(databaseProvider);

    cursor = downloadIndex.getDownloads();
    assertThat(cursor.getCount()).isEqualTo(0);
    cursor.close();
    assertThat(VersionTable.getVersion(writableDatabase, VersionTable.FEATURE_OFFLINE, EMPTY_NAME))
        .isEqualTo(DefaultDownloadIndex.TABLE_VERSION);
  }

  @Test
  public void setStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id).setState(Download.STATE_COMPLETED).setStopReason(0x12345678);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);

    downloadIndex.setStopReason(Download.STOP_REASON_NONE);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(Download.STOP_REASON_NONE).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setStopReason_setReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int stopReason = 0x12345678;

    downloadIndex.setStopReason(stopReason);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(stopReason).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setStopReason_notTerminalState_doesNotSetStopReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id).setState(Download.STATE_DOWNLOADING);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int notMetRequirements = 0x12345678;

    downloadIndex.setStopReason(notMetRequirements);

    Download readDownload = downloadIndex.getDownload(id);
    assertEqual(readDownload, download);
  }

  @Test
  public void setSingleDownloadStopReason_setReasonToNone() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id).setState(Download.STATE_COMPLETED).setStopReason(0x12345678);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);

    downloadIndex.setStopReason(id, Download.STOP_REASON_NONE);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(Download.STOP_REASON_NONE).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setSingleDownloadStopReason_setReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(id)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int stopReason = 0x12345678;

    downloadIndex.setStopReason(id, stopReason);

    Download readDownload = downloadIndex.getDownload(id);
    Download expectedDownload = downloadBuilder.setStopReason(stopReason).build();
    assertEqual(readDownload, expectedDownload);
  }

  @Test
  public void setSingleDownloadStopReason_notTerminalState_doesNotSetStopReason() throws Exception {
    String id = "id";
    DownloadBuilder downloadBuilder = new DownloadBuilder(id).setState(Download.STATE_DOWNLOADING);
    Download download = downloadBuilder.build();
    downloadIndex.putDownload(download);
    int notMetRequirements = 0x12345678;

    downloadIndex.setStopReason(id, notMetRequirements);

    Download readDownload = downloadIndex.getDownload(id);
    assertEqual(readDownload, download);
  }

  private static void assertEqual(Download download, Download that) {
    assertThat(download.request).isEqualTo(that.request);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.startTimeMs).isEqualTo(that.startTimeMs);
    assertThat(download.updateTimeMs).isEqualTo(that.updateTimeMs);
    assertThat(download.contentLength).isEqualTo(that.contentLength);
    assertThat(download.stopReason).isEqualTo(that.stopReason);
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.getPercentDownloaded()).isEqualTo(that.getPercentDownloaded());
    assertThat(download.getBytesDownloaded()).isEqualTo(that.getBytesDownloaded());
  }
}
