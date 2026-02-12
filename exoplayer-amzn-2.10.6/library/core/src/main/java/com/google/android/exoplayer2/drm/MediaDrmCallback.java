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
package com.google.android.exoplayer2.drm;

import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import java.util.UUID;

/**
 * Performs {@link ExoMediaDrm} key and provisioning requests.
 */
public interface MediaDrmCallback {

  /**
   * Executes a provisioning request.
   *
   * @param uuid The UUID of the content protection scheme.
   * @param request The request.
   * @return The response data.
   * @throws Exception If an error occurred executing the request.
   */
  byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws Exception;

  /**
   * Executes a key request.
   *
   * @param uuid The UUID of the content protection scheme.
   * @param request The request.
   * @return The response data.
   * @throws Exception If an error occurred executing the request.
   */
  byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception;
}
