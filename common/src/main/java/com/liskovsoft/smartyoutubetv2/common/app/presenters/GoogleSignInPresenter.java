package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.googleapi.service.GoogleSignInService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import io.reactivex.disposables.Disposable;

public class GoogleSignInPresenter extends BasePresenter<SignInView> {
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
        RxHelper.disposeActions(mSignInAction);
        updateUserCode();
    }

    public void onActionClicked() {
        if (getView() != null) {
            getView().close();
        }
    }

    private void updateUserCode() {
        mSignInAction = mSignInService.signInObserve()
                .subscribe(
                        code -> getView().showCode(code.getSignInCode(), code.getSignInUrl()),
                        error -> Log.e(TAG, "Sign in error: %s", error.getMessage()),
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

    public void start() {
        start(null);
    }

    public void start(Runnable onSuccess) {
        mCallback = onSuccess;
        RxHelper.disposeActions(mSignInAction);
        ViewManager.instance(getContext()).startView(SignInView.class);
    }
}
