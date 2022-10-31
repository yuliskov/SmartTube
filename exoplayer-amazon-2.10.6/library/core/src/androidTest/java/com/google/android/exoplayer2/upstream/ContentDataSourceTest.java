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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ContentDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class ContentDataSourceTest {

  private static final String AUTHORITY = "com.google.android.exoplayer2.core.test";
  private static final String DATA_PATH = "binary/1024_incrementing_bytes.mp3";

  @Test
  public void testRead() throws Exception {
    assertData(0, C.LENGTH_UNSET, false);
  }

  @Test
  public void testReadPipeMode() throws Exception {
    assertData(0, C.LENGTH_UNSET, true);
  }

  @Test
  public void testReadFixedLength() throws Exception {
    assertData(0, 100, false);
  }

  @Test
  public void testReadFromOffsetToEndOfInput() throws Exception {
    assertData(1, C.LENGTH_UNSET, false);
  }

  @Test
  public void testReadFromOffsetToEndOfInputPipeMode() throws Exception {
    assertData(1, C.LENGTH_UNSET, true);
  }

  @Test
  public void testReadFromOffsetFixedLength() throws Exception {
    assertData(1, 100, false);
  }

  @Test
  public void testReadInvalidUri() throws Exception {
    ContentDataSource dataSource =
        new ContentDataSource(InstrumentationRegistry.getTargetContext());
    Uri contentUri = TestContentProvider.buildUri("does/not.exist", false);
    DataSpec dataSpec = new DataSpec(contentUri);
    try {
      dataSource.open(dataSpec);
      fail();
    } catch (ContentDataSource.ContentDataSourceException e) {
      // Expected.
      assertThat(e).hasCauseThat().isInstanceOf(FileNotFoundException.class);
    } finally {
      dataSource.close();
    }
  }

  private static void assertData(int offset, int length, boolean pipeMode) throws IOException {
    Uri contentUri = TestContentProvider.buildUri(DATA_PATH, pipeMode);
    ContentDataSource dataSource =
        new ContentDataSource(InstrumentationRegistry.getTargetContext());
    try {
      DataSpec dataSpec = new DataSpec(contentUri, offset, length, null);
      byte[] completeData =
          TestUtil.getByteArray(InstrumentationRegistry.getTargetContext(), DATA_PATH);
      byte[] expectedData = Arrays.copyOfRange(completeData, offset,
          length == C.LENGTH_UNSET ? completeData.length : offset + length);
      TestUtil.assertDataSourceContent(dataSource, dataSpec, expectedData, !pipeMode);
    } finally {
      dataSource.close();
    }
  }

  /**
   * A {@link ContentProvider} for the test.
   */
  public static final class TestContentProvider extends ContentProvider
      implements ContentProvider.PipeDataWriter<Object> {

    private static final String PARAM_PIPE_MODE = "pipe-mode";

    public static Uri buildUri(String filePath, boolean pipeMode) {
      Uri.Builder builder = new Uri.Builder()
          .scheme(ContentResolver.SCHEME_CONTENT)
          .authority(AUTHORITY)
          .path(filePath);
      if (pipeMode) {
        builder.appendQueryParameter(TestContentProvider.PARAM_PIPE_MODE, "1");
      }
      return builder.build();
    }

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
        String[] selectionArgs, String sortOrder) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
        throws FileNotFoundException {
      if (uri.getPath() == null) {
        return null;
      }
      try {
        String fileName = getFileName(uri);
        boolean pipeMode = uri.getQueryParameter(PARAM_PIPE_MODE) != null;
        if (pipeMode) {
          ParcelFileDescriptor fileDescriptor = openPipeHelper(uri, null, null, null, this);
          return new AssetFileDescriptor(fileDescriptor, 0, C.LENGTH_UNSET);
        } else {
          return getContext().getAssets().openFd(fileName);
        }
      } catch (IOException e) {
        FileNotFoundException exception = new FileNotFoundException(e.getMessage());
        exception.initCause(e);
        throw exception;
      }
    }

    @Override
    public String getType(@NonNull Uri uri) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
        String[] selectionArgs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri,
        @NonNull String mimeType, @Nullable Bundle opts, @Nullable Object args) {
      try {
        byte[] data = TestUtil.getByteArray(getContext(), getFileName(uri));
        FileOutputStream outputStream = new FileOutputStream(output.getFileDescriptor());
        outputStream.write(data);
        outputStream.close();
      } catch (IOException e) {
        throw new RuntimeException("Error writing to pipe", e);
      }
    }

    private static String getFileName(Uri uri) {
      return uri.getPath().replaceFirst("/", "");
    }

  }

}
