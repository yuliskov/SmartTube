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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DecryptionResource;

/**
 * Holds a {@link Format}.
 */
public final class FormatHolder {

  /**
   * Whether the object expected to populate {@link #format} is also expected to populate {@link
   * #decryptionResource}.
   */
  // TODO: Remove once all Renderers and MediaSources have migrated to the new DRM model [Internal
  // ref: b/129764794].
  public boolean decryptionResourceIsProvided;

  /** An accompanying context for decrypting samples in the format. */
  @Nullable public DecryptionResource<?> decryptionResource;

  /** The held {@link Format}. */
  @Nullable public Format format;
}
