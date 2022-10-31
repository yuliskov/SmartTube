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
package com.google.android.exoplayer2.extractor.mp4;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;

/**
 * Encapsulates information parsed from a track encryption (tenc) box or sample group description 
 * (sgpd) box in an MP4 stream.
 */
public final class TrackEncryptionBox {

  private static final String TAG = "TrackEncryptionBox";

  /**
   * Indicates the encryption state of the samples in the sample group.
   */
  public final boolean isEncrypted;

  /**
   * The protection scheme type, as defined by the 'schm' box, or null if unknown.
   */
  @Nullable public final String schemeType;

  /**
   * A {@link TrackOutput.CryptoData} instance containing the encryption information from this
   * {@link TrackEncryptionBox}.
   */
  public final TrackOutput.CryptoData cryptoData;

  /** The initialization vector size in bytes for the samples in the corresponding sample group. */
  public final int perSampleIvSize;

  /**
   * If {@link #perSampleIvSize} is 0, holds the default initialization vector as defined in the
   * track encryption box or sample group description box. Null otherwise.
   */
  @Nullable public final byte[] defaultInitializationVector;

  /**
   * @param isEncrypted See {@link #isEncrypted}.
   * @param schemeType See {@link #schemeType}.
   * @param perSampleIvSize See {@link #perSampleIvSize}.
   * @param keyId See {@link TrackOutput.CryptoData#encryptionKey}.
   * @param defaultEncryptedBlocks See {@link TrackOutput.CryptoData#encryptedBlocks}.
   * @param defaultClearBlocks See {@link TrackOutput.CryptoData#clearBlocks}.
   * @param defaultInitializationVector See {@link #defaultInitializationVector}.
   */
  public TrackEncryptionBox(
      boolean isEncrypted,
      @Nullable String schemeType,
      int perSampleIvSize,
      byte[] keyId,
      int defaultEncryptedBlocks,
      int defaultClearBlocks,
      @Nullable byte[] defaultInitializationVector) {
    Assertions.checkArgument(perSampleIvSize == 0 ^ defaultInitializationVector == null);
    this.isEncrypted = isEncrypted;
    this.schemeType = schemeType;
    this.perSampleIvSize = perSampleIvSize;
    this.defaultInitializationVector = defaultInitializationVector;
    cryptoData = new TrackOutput.CryptoData(schemeToCryptoMode(schemeType), keyId,
        defaultEncryptedBlocks, defaultClearBlocks);
  }

  @C.CryptoMode
  private static int schemeToCryptoMode(@Nullable String schemeType) {
    if (schemeType == null) {
      // If unknown, assume cenc.
      return C.CRYPTO_MODE_AES_CTR;
    }
    switch (schemeType) {
      case C.CENC_TYPE_cenc:
      case C.CENC_TYPE_cens:
        return C.CRYPTO_MODE_AES_CTR;
      case C.CENC_TYPE_cbc1:
      case C.CENC_TYPE_cbcs:
        return C.CRYPTO_MODE_AES_CBC;
      default:
        Log.w(TAG, "Unsupported protection scheme type '" + schemeType + "'. Assuming AES-CTR "
            + "crypto mode.");
        return C.CRYPTO_MODE_AES_CTR;
    }
  }

}
