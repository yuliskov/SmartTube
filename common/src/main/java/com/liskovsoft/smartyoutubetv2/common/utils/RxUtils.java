package com.liskovsoft.smartyoutubetv2.common.utils;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class RxUtils {
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

    public static <T> Disposable subscribe(Observable<T> observable) {
        return observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }
}
