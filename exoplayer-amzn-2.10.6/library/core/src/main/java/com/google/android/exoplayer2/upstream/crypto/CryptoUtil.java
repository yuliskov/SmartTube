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

/**
 * Utility functions for the crypto package.
 */
/* package */ final class CryptoUtil {

  private CryptoUtil() {}

  /**
   * Returns the hash value of the input as a long using the 64 bit FNV-1a hash function. The hash
   * values produced by this function are less likely to collide than those produced by
   * {@link #hashCode()}.
   */
  public static long getFNV64Hash(String input) {
    if (input == null) {
      return 0;
    }

    long hash = 0;
    for (int i = 0; i < input.length(); i++) {
      hash ^= input.charAt(i);
      // This is equivalent to hash *= 0x100000001b3 (the FNV magic prime number).
      hash += (hash << 1) + (hash << 4) + (hash << 5) + (hash << 7) + (hash << 8) + (hash << 40);
    }
    return hash;
  }

}
