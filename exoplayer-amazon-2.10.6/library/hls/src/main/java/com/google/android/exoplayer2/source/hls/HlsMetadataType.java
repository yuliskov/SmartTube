/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.source.hls;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;

/**
 * The types of metadata that can be extracted from HLS streams.
 *
 * <p>See {@link HlsMediaSource.Factory#setMetadataType(int)}.
 */
@Retention(SOURCE)
@IntDef({HlsMetadataType.ID3, HlsMetadataType.EMSG})
public @interface HlsMetadataType {
  int ID3 = 1;
  int EMSG = 3;
}
