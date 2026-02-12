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

/* package */ final class DefaultSampleValues {

  public final int sampleDescriptionIndex;
  public final int duration;
  public final int size;
  public final int flags;

  public DefaultSampleValues(int sampleDescriptionIndex, int duration, int size, int flags) {
    this.sampleDescriptionIndex = sampleDescriptionIndex;
    this.duration = duration;
    this.size = size;
    this.flags = flags;
  }

}
