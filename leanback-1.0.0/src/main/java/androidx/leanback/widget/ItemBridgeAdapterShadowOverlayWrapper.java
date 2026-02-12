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

import android.content.Context;
import android.view.View;

/**
 * A wrapper class working with {@link ItemBridgeAdapter} to wrap item view in a
 * {@link ShadowOverlayContainer}.  The ShadowOverlayContainer is created from conditions
 * of {@link ShadowOverlayHelper}.
 */
public class ItemBridgeAdapterShadowOverlayWrapper extends ItemBridgeAdapter.Wrapper {

    private final ShadowOverlayHelper mHelper;

    public ItemBridgeAdapterShadowOverlayWrapper(ShadowOverlayHelper helper) {
        mHelper = helper;
    }

    @Override
    public View createWrapper(View root) {
        Context context = root.getContext();
        ShadowOverlayContainer wrapper = mHelper.createShadowOverlayContainer(context);
        return wrapper;
    }
    @Override
    public void wrap(View wrapper, View wrapped) {
        ((ShadowOverlayContainer) wrapper).wrap(wrapped);
    }

}
