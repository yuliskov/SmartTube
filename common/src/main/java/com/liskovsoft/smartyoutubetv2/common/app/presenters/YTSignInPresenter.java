package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;

import io.reactivex.disposables.Disposable;

public class YTSignInPresenter extends SignInPresenter {
    private static final String TAG = YTSignInPresenter.class.getSimpleName();
    //private static final String SIGN_IN_URL_SHORT = "https://yt.be/activate"; // doesn't support query params, no search history
    //private static final String SIGN_IN_URL_FULL = "https://youtube.com/tv/activate"; // support query params, no search history
    private static final String SIGN_IN_URL = "https://youtube.com/activate"; // supports search history
    @SuppressLint("StaticFieldLeak")
    private static YTSignInPresenter sInstance;
    private final MotherService mService;
    private Disposable mSignInAction;

    private YTSignInPresenter(Context context) {
        super(context);
        mService = YouTubeMotherService.instance();
    }

    public static YTSignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new YTSignInPresenter(context);
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
        mSignInAction = mService.getSignInService().signInObserve()
                .subscribe(
                        userCode -> getView().showCode(userCode, SIGN_IN_URL),
                        error -> Log.e(TAG, "Sign in error: %s", error.getMessage()),
                        () -> {
                            // Success
                            if (getView() != null) {
                                getView().close();
                            }

                            AccountSelectionPresenter.instance(getContext()).show(true);
                        }
                 );
    }

    public void start() {
        super.start();
        RxHelper.disposeActions(mSignInAction);
    }
}
