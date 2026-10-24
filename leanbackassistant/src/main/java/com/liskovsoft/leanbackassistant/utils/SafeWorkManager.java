package com.liskovsoft.leanbackassistant.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.os.UserManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * Safe replacement for {@link WorkManager} with the same <code>getInstance(context)</code> API.<br/>
 * WorkManager is optional for the app (backups, keep-alive).<br/>
 * On some devices (e.g. Android 9 TV boxes) it throws:<br/>
 * <code>IllegalStateException: Cannot initialize WorkManager in direct boot mode</code><br/>
 * Never let it kill the process: all calls become no-op when WorkManager is unavailable.<br/>
 * Any other failure (e.g. misconfiguration) is rethrown.<br/>
 * Only the methods used by the app are wrapped.
 */
public final class SafeWorkManager {
    private static final String TAG = SafeWorkManager.class.getSimpleName();
    @Nullable
    private final WorkManager mDelegate;

    private SafeWorkManager(@Nullable WorkManager delegate) {
        mDelegate = delegate;
    }

    @NonNull
    public static SafeWorkManager getInstance(@Nullable Context context) {
        if (context == null) {
            return new SafeWorkManager(null);
        }

        try {
            return new SafeWorkManager(WorkManager.getInstance(context.getApplicationContext()));
        } catch (IllegalStateException e) {
            if (!isDirectBootError(e)) {
                // E.g. 'WorkManager is not initialized properly' (Application isn't a Configuration.Provider).
                // That's a bug in the app, not a device quirk. Don't hide it.
                throw e;
            }

            // The diagnostics help to find out why some devices end up here (real direct boot or not)
            Log.e(TAG, "WorkManager is unavailable on this device: %s (deviceProtectedStorage=%s, userUnlocked=%s)",
                    e.getMessage(), ContextCompat.isDeviceProtectedStorage(context.getApplicationContext()),
                    UserManagerCompat.isUserUnlocked(context.getApplicationContext()));
            return new SafeWorkManager(null);
        }
    }

    private static boolean isDirectBootError(IllegalStateException e) {
        String message = e.getMessage();

        return message != null && message.contains("direct boot");
    }

    public void enqueueUniquePeriodicWork(@NonNull String uniqueWorkName, @NonNull ExistingPeriodicWorkPolicy policy, @NonNull PeriodicWorkRequest request) {
        if (mDelegate != null) {
            mDelegate.enqueueUniquePeriodicWork(uniqueWorkName, policy, request);
        }
    }

    public void cancelUniqueWork(@NonNull String uniqueWorkName) {
        if (mDelegate != null) {
            mDelegate.cancelUniqueWork(uniqueWorkName);
        }
    }
}
