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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Receives track level data extracted by an {@link Extractor}.
 */
public interface TrackOutput {

  /**
   * Holds data required to decrypt a sample.
   */
  final class CryptoData {

    /**
     * The encryption mode used for the sample.
     */
    @C.CryptoMode public final int cryptoMode;

    /**
     * The encryption key associated with the sample. Its contents must not be modified.
     */
    public final byte[] encryptionKey;

    /**
     * The number of encrypted blocks in the encryption pattern, 0 if pattern encryption does not
     * apply.
     */
    public final int encryptedBlocks;

    /**
     * The number of clear blocks in the encryption pattern, 0 if pattern encryption does not
     * apply.
     */
    public final int clearBlocks;

    /**
     * @param cryptoMode See {@link #cryptoMode}.
     * @param encryptionKey See {@link #encryptionKey}.
     * @param encryptedBlocks See {@link #encryptedBlocks}.
     * @param clearBlocks See {@link #clearBlocks}.
     */
    public CryptoData(@C.CryptoMode int cryptoMode, byte[] encryptionKey, int encryptedBlocks,
        int clearBlocks) {
      this.cryptoMode = cryptoMode;
      this.encryptionKey = encryptionKey;
      this.encryptedBlocks = encryptedBlocks;
      this.clearBlocks = clearBlocks;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      CryptoData other = (CryptoData) obj;
      return cryptoMode == other.cryptoMode && encryptedBlocks == other.encryptedBlocks
          && clearBlocks == other.clearBlocks && Arrays.equals(encryptionKey, other.encryptionKey);
    }

    @Override
    public int hashCode() {
      int result = cryptoMode;
      result = 31 * result + Arrays.hashCode(encryptionKey);
      result = 31 * result + encryptedBlocks;
      result = 31 * result + clearBlocks;
      return result;
    }

  }

  /**
   * Called when the {@link Format} of the track has been extracted from the stream.
   *
   * @param format The extracted {@link Format}.
   */
  void format(Format format);

  /**
   * Called to write sample data to the output.
   *
   * @param input An {@link ExtractorInput} from which to read the sample data.
   * @param length The maximum length to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The number of bytes appended.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Called to write sample data to the output.
   *
   * @param data A {@link ParsableByteArray} from which to read the sample data.
   * @param length The number of bytes to read, starting from {@code data.getPosition()}.
   */
  void sampleData(ParsableByteArray data, int length);

  /**
   * Called when metadata associated with a sample has been extracted from the stream.
   *
   * <p>The corresponding sample data will have already been passed to the output via calls to
   * {@link #sampleData(ExtractorInput, int, boolean)} or {@link #sampleData(ParsableByteArray,
   * int)}.
   *
   * @param timeUs The media timestamp associated with the sample, in microseconds.
   * @param flags Flags associated with the sample. See {@code C.BUFFER_FLAG_*}.
   * @param size The size of the sample data, in bytes.
   * @param offset The number of bytes that have been passed to {@link #sampleData(ExtractorInput,
   *     int, boolean)} or {@link #sampleData(ParsableByteArray, int)} since the last byte belonging
   *     to the sample whose metadata is being passed.
   * @param encryptionData The encryption data required to decrypt the sample. May be null.
   */
  void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData encryptionData);
}
