/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.RequiresApi;

@RequiresApi(21)
class ShadowHelperApi21 {

    static class ShadowImpl {
        View mShadowContainer;
        float mNormalZ;
        float mFocusedZ;
    }

    static final ViewOutlineProvider sOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
            outline.setAlpha(1.0f);
        }
    };

    /* add shadows and return a implementation detail object */
    public static Object addDynamicShadow(
            View shadowContainer, float unfocusedZ, float focusedZ, int roundedCornerRadius) {
        if (roundedCornerRadius > 0) {
            RoundedRectHelperApi21.setClipToRoundedOutline(shadowContainer, true,
                    roundedCornerRadius);
        } else {
            shadowContainer.setOutlineProvider(sOutlineProvider);
        }
        ShadowImpl impl = new ShadowImpl();
        impl.mShadowContainer = shadowContainer;
        impl.mNormalZ = unfocusedZ;
        impl.mFocusedZ = focusedZ;
        shadowContainer.setZ(impl.mNormalZ);
        return impl;
    }

    /* set shadow focus level 0 for unfocused 1 for fully focused */
    public static void setShadowFocusLevel(Object object, float level) {
        ShadowImpl impl = (ShadowImpl) object;
        impl.mShadowContainer.setZ(impl.mNormalZ + level * (impl.mFocusedZ - impl.mNormalZ));
    }

    private ShadowHelperApi21() {
    }
}
