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

package androidx.leanback.system;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RestrictTo;
import androidx.leanback.widget.ShadowOverlayContainer;

/**
 * Provides various preferences affecting Leanback runtime behavior.
 * <p>Note this class is not thread safe and its methods should only
 * be invoked from the UI thread
 * </p>
 */
public class Settings {
    static private final String TAG = "Settings";
    static private final boolean DEBUG = false;

    // The intent action that must be provided by a broadcast receiver
    // in a customization package.
    private static final String ACTION_PARTNER_CUSTOMIZATION =
            "android.support.v17.leanback.action.PARTNER_CUSTOMIZATION";

    public static final String PREFER_STATIC_SHADOWS = "PREFER_STATIC_SHADOWS";

    public static final String OUTLINE_CLIPPING_DISABLED = "OUTLINE_CLIPPING_DISABLED";

    static private Settings sInstance;

    private boolean mPreferStaticShadows;
    private boolean mOutlineClippingDisabled;

    /**
     * Returns the singleton Settings instance.
     */
    static public Settings getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Settings(context);
        }
        return sInstance;
    }

    private Settings(Context context) {
        if (DEBUG) Log.v(TAG, "generating preferences");
        Customizations customizations = getCustomizations(context);
        generateSetting(customizations);
    }

    /**
     * Returns true if static shadows are recommended.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public boolean preferStaticShadows() {
        return mPreferStaticShadows;
    }

    /**
     * Returns true if view outline is disabled on low power chipset.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public boolean isOutlineClippingDisabled() {
        return mOutlineClippingDisabled;
    }

    /**
     * Returns the boolean preference for the given key.
     */
    public boolean getBoolean(String key) {
        return getOrSetBoolean(key, false, false);
    }

    /**
     * Sets the boolean preference for the given key.  If an app uses this api to override
     * a default preference, it must do so on every activity create.
     */
    public void setBoolean(String key, boolean value) {
        getOrSetBoolean(key, true, value);
    }

    boolean getOrSetBoolean(String key, boolean set, boolean value) {
        if (key.compareTo(PREFER_STATIC_SHADOWS) == 0) {
            return set ? (mPreferStaticShadows = value) : mPreferStaticShadows;
        } else if (key.compareTo(OUTLINE_CLIPPING_DISABLED) == 0) {
            return set ? (mOutlineClippingDisabled = value) : mOutlineClippingDisabled;
        }
        throw new IllegalArgumentException("Invalid key");
    }

    private void generateSetting(Customizations customizations) {
        if (ShadowOverlayContainer.supportsDynamicShadow()) {
            mPreferStaticShadows = false;
            if (customizations != null) {
                mPreferStaticShadows = customizations.getBoolean(
                        "leanback_prefer_static_shadows", mPreferStaticShadows);
            }
        } else {
            mPreferStaticShadows = true;
        }

        if (Build.VERSION.SDK_INT >= 21) {
            mOutlineClippingDisabled = false;
            if (customizations != null) {
                mOutlineClippingDisabled = customizations.getBoolean(
                        "leanback_outline_clipping_disabled", mOutlineClippingDisabled);
            }
        } else {
            mOutlineClippingDisabled = true;
        }
        if (DEBUG) Log.v(TAG, "generated preference " + PREFER_STATIC_SHADOWS + ": "
                + mPreferStaticShadows + " "
                + OUTLINE_CLIPPING_DISABLED + " : " + mOutlineClippingDisabled);
    }

    static class Customizations {
        Resources mResources;
        String mPackageName;

        public Customizations(Resources resources, String packageName) {
            mResources = resources;
            mPackageName = packageName;
        }

        public boolean getBoolean(String resourceName, boolean defaultValue) {
            int resId = mResources.getIdentifier(resourceName, "bool", mPackageName);
            return resId > 0 ? mResources.getBoolean(resId) : defaultValue;
        }
    };

    private Customizations getCustomizations(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(ACTION_PARTNER_CUSTOMIZATION);
        if (DEBUG) {
            Log.v(TAG, "getting oem customizations by intent: " + ACTION_PARTNER_CUSTOMIZATION);
        }

        Resources resources = null;
        String packageName = null;
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            packageName = info.activityInfo.packageName;
            if (DEBUG) Log.v(TAG, "got package " + packageName);
            if (packageName != null && isSystemApp(info)) try {
                resources = pm.getResourcesForApplication(packageName);
            } catch (PackageManager.NameNotFoundException ex) {
                // Do nothing
            }
            if (resources != null) {
                if (DEBUG) Log.v(TAG, "found customization package: " + packageName);
                break;
            }
        }
        return resources == null ? null : new Customizations(resources, packageName);
    }

    private static boolean isSystemApp(ResolveInfo info) {
        return (info.activityInfo != null
                && (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}
