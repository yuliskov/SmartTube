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
package com.google.android.exoplayer2.drm;

import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} that provides a fixed response to key requests. Provisioning is not
 * supported. This implementation is primarily useful for providing locally stored keys to decrypt
 * ClearKey protected content. It is not suitable for use with Widevine or PlayReady protected
 * content.
 */
public final class LocalMediaDrmCallback implements MediaDrmCallback {

  private final byte[] keyResponse;

  /**
   * @param keyResponse The fixed response for all key requests.
   */
  public LocalMediaDrmCallback(byte[] keyResponse) {
    this.keyResponse = Assertions.checkNotNull(keyResponse);
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception {
    return keyResponse;
  }

}
