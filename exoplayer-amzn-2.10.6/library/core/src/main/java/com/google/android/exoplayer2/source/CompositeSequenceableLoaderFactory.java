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
package com.google.android.exoplayer2.source;

/**
 * A factory to create composite {@link SequenceableLoader}s.
 */
public interface CompositeSequenceableLoaderFactory {

  /**
   * Creates a composite {@link SequenceableLoader}.
   *
   * @param loaders The sub-loaders that make up the {@link SequenceableLoader} to be built.
   * @return A composite {@link SequenceableLoader} that comprises the given loaders.
   */
  SequenceableLoader createCompositeSequenceableLoader(SequenceableLoader... loaders);

}
