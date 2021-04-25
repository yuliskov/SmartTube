package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.mylogger.Log;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class RxUtils {
    private static final String TAG = RxUtils.class.getSimpleName();

    public static void disposeActions(Disposable... actions) {
        if (actions != null) {
            for (Disposable action : actions) {
                boolean updateInProgress = action != null && !action.isDisposed();

                if (updateInProgress) {
                    action.dispose();
                }
            }
        }
    }

    public static <T> Disposable execute(Observable<T> observable) {
        return observable
                .subscribeOn(Schedulers.newThread())
                .subscribe(
                        obj -> {}, // ignore result
                        error -> Log.e(TAG, "Execute error: %s", error.getMessage())
                );
    }

    /**
     * NOTE: Don't use it to check that action in completed inside other action (scrollEnd bug).
     */
    public static boolean isAnyActionRunning(Disposable... actions) {
        if (actions != null) {
            for (Disposable action : actions) {
                if (action != null && !action.isDisposed()) {
                    return true;
                }
            }
        }

        return false;
    }
}
