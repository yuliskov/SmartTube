/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.leanback.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.os.Build;

import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class PermissionHelper {

    public static void requestPermissions(android.app.Fragment fragment, String[] permissions,
            int requestCode) {
        if (Build.VERSION.SDK_INT >= 23) {
            fragment.requestPermissions(permissions, requestCode);
        }
    }

    private PermissionHelper() {
    }
}
