/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.graphics;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.SparseArray;

/**
 * Cache of {@link ColorFilter}s for a given color at different alpha levels.
 */
public final class ColorFilterCache {

    private static final SparseArray<ColorFilterCache> sColorToFiltersMap =
            new SparseArray<ColorFilterCache>();

    private final PorterDuffColorFilter[] mFilters = new PorterDuffColorFilter[0x100];

    /**
     * Get a ColorDimmer for a given color.  Only the RGB values are used; the
     * alpha channel is ignored in color. Subsequent calls to this method
     * with the same color value will return the same cache.
     *
     * @param color The color to use for the color filters.
     * @return A cache of ColorFilters at different alpha levels for the color.
     */
    public static ColorFilterCache getColorFilterCache(int color) {
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        color = Color.rgb(r, g, b);
        ColorFilterCache filters = sColorToFiltersMap.get(color);
        if (filters == null) {
            filters = new ColorFilterCache(r, g, b);
            sColorToFiltersMap.put(color, filters);
        }
        return filters;
    }

    private ColorFilterCache(int r, int g, int b) {
        // Pre cache all 256 filter levels
        for (int i = 0x00; i <= 0xFF; i++) {
            int color = Color.argb(i, r, g, b);
            mFilters[i] = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
    }

    /**
     * Returns a ColorFilter for a given alpha level between 0 and 1.0.
     *
     * @param level The alpha level the filter should apply.
     * @return A ColorFilter at the alpha level for the color represented by the
     *         cache.
     */
    public ColorFilter getFilterForLevel(float level) {
        if (level >= 0 && level <= 1.0) {
            int filterIndex = (int) (0xFF * level);
            return mFilters[filterIndex];
        } else {
            return null;
        }
    }
}
