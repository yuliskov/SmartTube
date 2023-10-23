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

package androidx.leanback.widget;

import android.graphics.Bitmap;

/**
 * Class to be implemented by app to provide seeking data and thumbnails to UI.
 */
public class PlaybackSeekDataProvider {

    /**
     * Client to receive result for {@link PlaybackSeekDataProvider#getThumbnail(int,
     * ResultCallback)}.
     */
    public static class ResultCallback {

        /**
         * Client of thumbnail bitmap being loaded. PlaybackSeekDataProvider must invoke this method
         * in UI thread such as in {@link android.os.AsyncTask#onPostExecute(Object)}.
         *
         * @param bitmap Result of bitmap.
         * @param index Index of {@link #getSeekPositions()}.
         */
        public void onThumbnailLoaded(Bitmap bitmap, int index) {
        }
    }

    /**
     * Get a list of sorted seek positions. The positions should not change after user starts
     * seeking.
     *
     * @return A list of sorted seek positions.
     */
    public long[] getSeekPositions() {
        return null;
    }

    /**
     * Called to get thumbnail bitmap. This method is called on UI thread. When provider finds
     * cache bitmap, it may invoke {@link ResultCallback#onThumbnailLoaded(Bitmap, int)}
     * immediately. Provider may start background thread and invoke
     * {@link ResultCallback#onThumbnailLoaded(Bitmap, int)} later in UI thread. The method might
     * be called multiple times for the same position, PlaybackSeekDataProvider must guarantee
     * to replace pending {@link ResultCallback} with the new one. When seeking right,
     * getThumbnail() will be called with increasing index; when seeking left, getThumbnail() will
     * be called with decreasing index. The increment of index can be used by subclass to determine
     * prefetch direction.
     *
     * @param index Index of position in {@link #getSeekPositions()}.
     * @param callback The callback to receive the result on UI thread. It may be called within
     *                 getThumbnail() if hit cache directly.
     */
    public void getThumbnail(int index, ResultCallback callback) {
    }

    /**
     * Called when seek stops, Provider should cancel pending requests for the thumbnails.
     */
    public void reset() {
    }
}
