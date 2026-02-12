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
package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link BaseDataSource}. */
@RunWith(AndroidJUnit4.class)
public class BaseDataSourceTest {

  @Test
  public void dataTransfer_withLocalSource_isReported() throws IOException {
    TestSource testSource = new TestSource(/* isNetwork= */ false);
    TestTransferListener transferListener = new TestTransferListener();
    testSource.addTransferListener(transferListener);

    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    testSource.open(dataSpec);
    testSource.read(/* buffer= */ null, /* offset= */ 0, /* readLength= */ 100);
    testSource.close();

    assertThat(transferListener.lastTransferInitializingSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferStartSource).isSameAs(testSource);
    assertThat(transferListener.lastBytesTransferredSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferEndSource).isSameAs(testSource);

    assertThat(transferListener.lastTransferInitializingDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastTransferStartDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastBytesTransferredDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastTransferEndDataSpec).isEqualTo(dataSpec);

    assertThat(transferListener.lastTransferInitializingIsNetwork).isEqualTo(false);
    assertThat(transferListener.lastTransferStartIsNetwork).isEqualTo(false);
    assertThat(transferListener.lastBytesTransferredIsNetwork).isEqualTo(false);
    assertThat(transferListener.lastTransferEndIsNetwork).isEqualTo(false);

    assertThat(transferListener.lastBytesTransferred).isEqualTo(100);
  }

  @Test
  public void dataTransfer_withRemoteSource_isReported() throws IOException {
    TestSource testSource = new TestSource(/* isNetwork= */ true);
    TestTransferListener transferListener = new TestTransferListener();
    testSource.addTransferListener(transferListener);

    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    testSource.open(dataSpec);
    testSource.read(/* buffer= */ null, /* offset= */ 0, /* readLength= */ 100);
    testSource.close();

    assertThat(transferListener.lastTransferInitializingSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferStartSource).isSameAs(testSource);
    assertThat(transferListener.lastBytesTransferredSource).isSameAs(testSource);
    assertThat(transferListener.lastTransferEndSource).isSameAs(testSource);

    assertThat(transferListener.lastTransferInitializingDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastTransferStartDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastBytesTransferredDataSpec).isEqualTo(dataSpec);
    assertThat(transferListener.lastTransferEndDataSpec).isEqualTo(dataSpec);

    assertThat(transferListener.lastTransferInitializingIsNetwork).isEqualTo(true);
    assertThat(transferListener.lastTransferStartIsNetwork).isEqualTo(true);
    assertThat(transferListener.lastBytesTransferredIsNetwork).isEqualTo(true);
    assertThat(transferListener.lastTransferEndIsNetwork).isEqualTo(true);

    assertThat(transferListener.lastBytesTransferred).isEqualTo(100);
  }

  private static final class TestSource extends BaseDataSource {

    public TestSource(boolean isNetwork) {
      super(isNetwork);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
      transferInitializing(dataSpec);
      transferStarted(dataSpec);
      return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
      bytesTransferred(readLength);
      return readLength;
    }

    @Override
    public @Nullable Uri getUri() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
      transferEnded();
    }
  }

  private static final class TestTransferListener implements TransferListener {

    public Object lastTransferInitializingSource;
    public DataSpec lastTransferInitializingDataSpec;
    public boolean lastTransferInitializingIsNetwork;

    public Object lastTransferStartSource;
    public DataSpec lastTransferStartDataSpec;
    public boolean lastTransferStartIsNetwork;

    public Object lastBytesTransferredSource;
    public DataSpec lastBytesTransferredDataSpec;
    public boolean lastBytesTransferredIsNetwork;
    public int lastBytesTransferred;

    public Object lastTransferEndSource;
    public DataSpec lastTransferEndDataSpec;
    public boolean lastTransferEndIsNetwork;

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
      lastTransferInitializingSource = source;
      lastTransferInitializingDataSpec = dataSpec;
      lastTransferInitializingIsNetwork = isNetwork;
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
      lastTransferStartSource = source;
      lastTransferStartDataSpec = dataSpec;
      lastTransferStartIsNetwork = isNetwork;
    }

    @Override
    public void onBytesTransferred(
        DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
      lastBytesTransferredSource = source;
      lastBytesTransferredDataSpec = dataSpec;
      lastBytesTransferredIsNetwork = isNetwork;
      lastBytesTransferred = bytesTransferred;
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
      lastTransferEndSource = source;
      lastTransferEndDataSpec = dataSpec;
      lastTransferEndIsNetwork = isNetwork;
    }
  }
}
