package com.liskovsoft.smartyoutubetv2.common.utils;

import io.reactivex.disposables.Disposable;

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
}
