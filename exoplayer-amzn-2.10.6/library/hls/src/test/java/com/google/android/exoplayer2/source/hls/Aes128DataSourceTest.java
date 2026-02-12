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
package com.google.android.exoplayer2.source.hls;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link Aes128DataSource}. */
@RunWith(AndroidJUnit4.class)
public class Aes128DataSourceTest {

  @Test
  public void test_OpenCallsUpstreamOpen_CloseCallsUpstreamClose() throws IOException {
    UpstreamDataSource upstream = new UpstreamDataSource();
    Aes128DataSource testInstance = new TestAes123DataSource(upstream, new byte[16], new byte[16]);
    assertThat(upstream.opened).isFalse();

    Uri uri = Uri.parse("http.abc.com/def");
    testInstance.open(new DataSpec(uri));
    assertThat(upstream.opened).isTrue();

    testInstance.close();
    assertThat(upstream.opened).isFalse();
  }

  @Test
  public void test_OpenCallsUpstreamThrowingOpen_CloseCallsUpstreamClose() throws IOException {
    UpstreamDataSource upstream =
        new UpstreamDataSource() {
          @Override
          public long open(DataSpec dataSpec) throws IOException {
            throw new IOException();
          }
        };
    Aes128DataSource testInstance = new TestAes123DataSource(upstream, new byte[16], new byte[16]);
    assertThat(upstream.opened).isFalse();

    Uri uri = Uri.parse("http.abc.com/def");
    try {
      testInstance.open(new DataSpec(uri));
    } catch (IOException e) {
      // Expected.
    }
    assertThat(upstream.opened).isFalse();
    assertThat(upstream.closedCalled).isFalse();

    // Even though the upstream open call failed, close should still call close on the upstream as
    // per the contract of DataSource.
    testInstance.close();
    assertThat(upstream.closedCalled).isTrue();
  }

  private static class TestAes123DataSource extends Aes128DataSource {

    public TestAes123DataSource(DataSource upstream, byte[] encryptionKey, byte[] encryptionIv) {
      super(upstream, encryptionKey, encryptionIv);
    }

    @Override
    protected Cipher getCipherInstance() throws NoSuchPaddingException, NoSuchAlgorithmException {
      try {
        return super.getCipherInstance();
      } catch (NoSuchAlgorithmException e) {
        // Some host machines may not provide an algorithm for "AES/CBC/PKCS7Padding", however on
        // such machines it's possible to get a functionally identical algorithm by requesting
        // "AES/CBC/PKCS5Padding".
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
      }
    }
  }

  private static class UpstreamDataSource implements DataSource {

    public boolean opened;
    public boolean closedCalled;

    @Override
    public void addTransferListener(TransferListener transferListener) {}

    @Override
    public long open(DataSpec dataSpec) throws IOException {
      opened = true;
      return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) {
      return C.RESULT_END_OF_INPUT;
    }

    @Override
    public Uri getUri() {
      return null;
    }

    @Override
    public void close() {
      opened = false;
      closedCalled = true;
    }
  }
}
