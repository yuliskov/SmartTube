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
package com.google.android.exoplayer2.upstream.crypto;

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import javax.crypto.Cipher;

/**
 * A wrapping {@link DataSink} that encrypts the data being consumed.
 */
public final class AesCipherDataSink implements DataSink {

  private final DataSink wrappedDataSink;
  private final byte[] secretKey;
  private final byte[] scratch;

  private AesFlushingCipher cipher;

  /**
   * Create an instance whose {@code write} methods have the side effect of overwriting the input
   * {@code data}. Use this constructor for maximum efficiency in the case that there is no
   * requirement for the input data arrays to remain unchanged.
   *
   * @param secretKey The key data.
   * @param wrappedDataSink The wrapped {@link DataSink}.
   */
  public AesCipherDataSink(byte[] secretKey, DataSink wrappedDataSink) {
    this(secretKey, wrappedDataSink, null);
  }

  /**
   * Create an instance whose {@code write} methods are free of side effects. Use this constructor
   * when the input data arrays are required to remain unchanged.
   *
   * @param secretKey The key data.
   * @param wrappedDataSink The wrapped {@link DataSink}.
   * @param scratch Scratch space. Data is encrypted into this array before being written to the
   *     wrapped {@link DataSink}. It should be of appropriate size for the expected writes. If a
   *     write is larger than the size of this array the write will still succeed, but multiple
   *     cipher calls will be required to complete the operation. If {@code null} then encryption
   *     will overwrite the input {@code data}.
   */
  public AesCipherDataSink(byte[] secretKey, DataSink wrappedDataSink, byte[] scratch) {
    this.wrappedDataSink = wrappedDataSink;
    this.secretKey = secretKey;
    this.scratch = scratch;
  }

  @Override
  public void open(DataSpec dataSpec) throws IOException {
    wrappedDataSink.open(dataSpec);
    long nonce = CryptoUtil.getFNV64Hash(dataSpec.key);
    cipher = new AesFlushingCipher(Cipher.ENCRYPT_MODE, secretKey, nonce,
        dataSpec.absoluteStreamPosition);
  }

  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    if (scratch == null) {
      // In-place mode. Writes over the input data.
      cipher.updateInPlace(data, offset, length);
      wrappedDataSink.write(data, offset, length);
    } else {
      // Use scratch space. The original data remains intact.
      int bytesProcessed = 0;
      while (bytesProcessed < length) {
        int bytesToProcess = Math.min(length - bytesProcessed, scratch.length);
        cipher.update(data, offset + bytesProcessed, bytesToProcess, scratch, 0);
        wrappedDataSink.write(scratch, 0, bytesToProcess);
        bytesProcessed += bytesToProcess;
      }
    }
  }

  @Override
  public void close() throws IOException {
    cipher = null;
    wrappedDataSink.close();
  }
}
