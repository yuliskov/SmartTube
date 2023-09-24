/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */
package androidx.leanback.widget;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.R;

/**
 * Helper for static (nine patch) shadows.
 */
final class StaticShadowHelper {
    private StaticShadowHelper() {
    }

    static boolean supportsShadow() {
        return Build.VERSION.SDK_INT >= 21;
    }

    static void prepareParent(ViewGroup parent) {
        if (Build.VERSION.SDK_INT >= 21) {
            parent.setLayoutMode(ViewGroup.LAYOUT_MODE_OPTICAL_BOUNDS);
        }
    }

    static Object addStaticShadow(ViewGroup shadowContainer) {
        if (Build.VERSION.SDK_INT >= 21) {
            shadowContainer.setLayoutMode(ViewGroup.LAYOUT_MODE_OPTICAL_BOUNDS);
            LayoutInflater inflater = LayoutInflater.from(shadowContainer.getContext());
            inflater.inflate(R.layout.lb_shadow, shadowContainer, true);
            ShadowImpl impl = new ShadowImpl();
            impl.mNormalShadow = shadowContainer.findViewById(R.id.lb_shadow_normal);
            impl.mFocusShadow = shadowContainer.findViewById(R.id.lb_shadow_focused);
            return impl;
        }
        return null;
    }

    static void setShadowFocusLevel(Object impl, float level) {
        if (Build.VERSION.SDK_INT >= 21) {
            ShadowImpl shadowImpl = (ShadowImpl) impl;
            shadowImpl.mNormalShadow.setAlpha(1 - level);
            shadowImpl.mFocusShadow.setAlpha(level);
        }
    }

    static class ShadowImpl {
        View mNormalShadow;
        View mFocusShadow;
    }
}
