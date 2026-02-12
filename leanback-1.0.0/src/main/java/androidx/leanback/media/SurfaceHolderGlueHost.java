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

package androidx.leanback.media;

import android.view.SurfaceHolder;

/**
 * Optional interface to be implemented by any subclass of {@link PlaybackGlueHost} that contains
 * a {@link android.view.SurfaceView}. This will allow subclass of {@link PlaybackGlue} to setup
 * the surface holder callback during {@link PlaybackGlue#setHost(PlaybackGlueHost)}.
 *
 * @see PlaybackGlue#setHost(PlaybackGlueHost)
 */
public interface SurfaceHolderGlueHost {
    /**
     * Sets the {@link SurfaceHolder.Callback} on the the host.
     */
    void setSurfaceHolderCallback(SurfaceHolder.Callback callback);
}
