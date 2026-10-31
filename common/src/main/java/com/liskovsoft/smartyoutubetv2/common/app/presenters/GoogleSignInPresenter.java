package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;

import io.reactivex.disposables.Disposable;

public class GoogleSignInPresenter extends SignInPresenter {
    private static final String TAG = GoogleSignInPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static GoogleSignInPresenter sInstance;
    private final GoogleSignInService mSignInService;
    private Disposable mSignInAction;
    private Runnable mCallback;

    private GoogleSignInPresenter(Context context) {
        super(context);
        mSignInService = GoogleSignInService.instance();
    }

    public static GoogleSignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new GoogleSignInPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        RxHelper.disposeActions(mSignInAction);
        sInstance = null;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        RxHelper.disposeActions(mSignInAction);
        updateUserCode();
    }

    @Override
    public void onActionClicked() {
        if (getView() != null) {
            getView().close();
        }
    }

    private void updateUserCode() {
        mSignInAction = mSignInService.signInObserve()
                .subscribe(
                        code -> getView().showCode(code.getSignInCode(), code.getSignInUrl()),
                        error -> {
                            Log.e(TAG, "Sign in error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showCode(error.getMessage(), "");
                            }
                        },
                        () -> {
                            // Success
                            if (getView() != null) {
                                getView().close();
                            }

                            if (mCallback != null) {
                                mCallback.run();
                            }
                        }
                 );
    }

    @Override
    public void start() {
        super.start();
        RxHelper.disposeActions(mSignInAction);
    }

    public void start(Runnable onSuccess) {
        super.start();
        mCallback = onSuccess;
        RxHelper.disposeActions(mSignInAction);
    }
}
