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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.MediaClock;

/** Fake abstract {@link Renderer} which is also a {@link MediaClock}. */
public abstract class FakeMediaClockRenderer extends FakeRenderer implements MediaClock {

  public FakeMediaClockRenderer(Format... expectedFormats) {
    super(expectedFormats);
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
  }
}
