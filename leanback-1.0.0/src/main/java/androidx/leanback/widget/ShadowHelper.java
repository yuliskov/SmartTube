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
package androidx.leanback.widget;

import android.os.Build;
import android.view.View;

final class ShadowHelper {
    private ShadowHelper() {
    }

    static boolean supportsDynamicShadow() {
        return Build.VERSION.SDK_INT >= 21;
    }

    static Object addDynamicShadow(
            View shadowContainer, float unfocusedZ, float focusedZ, int roundedCornerRadius) {
        if (Build.VERSION.SDK_INT >= 21) {
            return ShadowHelperApi21.addDynamicShadow(
                    shadowContainer, unfocusedZ, focusedZ, roundedCornerRadius);
        }
        return null;
    }

    static void setShadowFocusLevel(Object impl, float level) {
        if (Build.VERSION.SDK_INT >= 21) {
            ShadowHelperApi21.setShadowFocusLevel(impl, level);
        }
    }
}
