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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ActionFile}. */
@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class ActionFileTest {

  private File tempFile;
  private DownloadRequest expectedAction1;
  private DownloadRequest expectedAction2;

  @Before
  public void setUp() throws Exception {
    tempFile = Util.createTempFile(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    expectedAction1 =
        buildExpectedRequest(Uri.parse("http://test1.uri"), TestUtil.buildTestData(16));
    expectedAction2 =
        buildExpectedRequest(Uri.parse("http://test2.uri"), TestUtil.buildTestData(32));
  }

  @After
  public void tearDown() throws Exception {
    tempFile.delete();
  }

  @Test
  public void testLoadNoDataThrowsIOException() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_no_data.exi");
    try {
      actionFile.load();
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadIncompleteHeaderThrowsIOException() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_incomplete_header.exi");
    try {
      actionFile.load();
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  @Test
  public void testLoadZeroActions() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_zero_actions.exi");
    DownloadRequest[] actions = actionFile.load();
    assertThat(actions).isNotNull();
    assertThat(actions).hasLength(0);
  }

  @Test
  public void testLoadOneAction() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_one_action.exi");
    DownloadRequest[] actions = actionFile.load();
    assertThat(actions).hasLength(1);
    assertThat(actions[0]).isEqualTo(expectedAction1);
  }

  @Test
  public void testLoadTwoActions() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_two_actions.exi");
    DownloadRequest[] actions = actionFile.load();
    assertThat(actions).hasLength(2);
    assertThat(actions[0]).isEqualTo(expectedAction1);
    assertThat(actions[1]).isEqualTo(expectedAction2);
  }

  @Test
  public void testLoadUnsupportedVersion() throws Exception {
    ActionFile actionFile = getActionFile("offline/action_file_unsupported_version.exi");
    try {
      actionFile.load();
      Assert.fail();
    } catch (IOException e) {
      // Expected exception.
    }
  }

  private ActionFile getActionFile(String fileName) throws IOException {
    // Copy the test data from the asset to where the ActionFile expects it to be.
    byte[] actionFileBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), fileName);
    try (FileOutputStream output = new FileOutputStream(tempFile)) {
      output.write(actionFileBytes);
    }
    // Load the action file.
    return new ActionFile(tempFile);
  }

  private static DownloadRequest buildExpectedRequest(Uri uri, byte[] data) {
    return new DownloadRequest(
        /* id= */ uri.toString(),
        DownloadRequest.TYPE_PROGRESSIVE,
        uri,
        /* streamKeys= */ Collections.emptyList(),
        /* customCacheKey= */ null,
        data);
  }
}
