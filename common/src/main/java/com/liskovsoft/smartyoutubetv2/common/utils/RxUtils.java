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

    public static boolean isActionRunning(Disposable action) {
        return action != null && !action.isDisposed();
    }
}
