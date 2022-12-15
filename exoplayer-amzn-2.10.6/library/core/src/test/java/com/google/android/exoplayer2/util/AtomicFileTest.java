/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link AtomicFile}. */
@RunWith(AndroidJUnit4.class)
public final class AtomicFileTest {

  private File tempFolder;
  private File file;
  private AtomicFile atomicFile;

  @Before
  public void setUp() throws Exception {
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    file = new File(tempFolder, "atomicFile");
    atomicFile = new AtomicFile(file);
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testDelete() throws Exception {
    assertThat(file.createNewFile()).isTrue();
    atomicFile.delete();
    assertThat(file.exists()).isFalse();
  }

  @Test
  public void testWriteRead() throws Exception {
    OutputStream output = atomicFile.startWrite();
    output.write(5);
    atomicFile.endWrite(output);
    output.close();

    assertRead();

    output = atomicFile.startWrite();
    output.write(5);
    output.write(6);
    output.close();

    assertRead();

    output = atomicFile.startWrite();
    output.write(6);

    assertRead();
    output.close();

    output = atomicFile.startWrite();

    assertRead();
    output.close();
  }

  private void assertRead() throws IOException {
    InputStream input = atomicFile.openRead();
    assertThat(input.read()).isEqualTo(5);
    assertThat(input.read()).isEqualTo(-1);
    input.close();
  }

}
