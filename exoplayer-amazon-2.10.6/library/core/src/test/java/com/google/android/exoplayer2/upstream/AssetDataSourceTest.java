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

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AssetDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class AssetDataSourceTest {

  private static final String DATA_PATH = "binary/1024_incrementing_bytes.mp3";

  @Test
  public void testReadFileUri() throws Exception {
    AssetDataSource dataSource = new AssetDataSource(ApplicationProvider.getApplicationContext());
    DataSpec dataSpec = new DataSpec(Uri.parse("file:///android_asset/" + DATA_PATH));
    TestUtil.assertDataSourceContent(
        dataSource,
        dataSpec,
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), DATA_PATH),
        true);
  }

  @Test
  public void testReadAssetUri() throws Exception {
    AssetDataSource dataSource = new AssetDataSource(ApplicationProvider.getApplicationContext());
    DataSpec dataSpec = new DataSpec(Uri.parse("asset:///" + DATA_PATH));
    TestUtil.assertDataSourceContent(
        dataSource,
        dataSpec,
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), DATA_PATH),
        true);
  }
}
