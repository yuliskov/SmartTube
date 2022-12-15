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
package com.google.android.exoplayer2.text.cea;

import java.util.Collections;
import java.util.List;

/** Initialization data for CEA-708 decoders. */
public final class Cea708InitializationData {

  /**
   * Whether the closed caption service is formatted for displays with 16:9 aspect ratio. If false,
   * the closed caption service is formatted for 4:3 displays.
   */
  public final boolean isWideAspectRatio;

  private Cea708InitializationData(List<byte[]> initializationData) {
    isWideAspectRatio = initializationData.get(0)[0] != 0;
  }

  /**
   * Returns an object representation of CEA-708 initialization data
   *
   * @param initializationData Binary CEA-708 initialization data.
   * @return The object representation.
   */
  public static Cea708InitializationData fromData(List<byte[]> initializationData) {
    return new Cea708InitializationData(initializationData);
  }

  /**
   * Builds binary CEA-708 initialization data.
   *
   * @param isWideAspectRatio Whether the closed caption service is formatted for displays with 16:9
   *     aspect ratio.
   * @return Binary CEA-708 initializaton data.
   */
  public static List<byte[]> buildData(boolean isWideAspectRatio) {
    return Collections.singletonList(new byte[] {(byte) (isWideAspectRatio ? 1 : 0)});
  }
}
