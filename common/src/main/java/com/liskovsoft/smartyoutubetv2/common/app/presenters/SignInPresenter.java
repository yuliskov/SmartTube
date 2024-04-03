package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import io.reactivex.disposables.Disposable;

public class SignInPresenter extends BasePresenter<SignInView> {
    private static final String TAG = SignInPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SignInPresenter sInstance;
    private final MotherService mService;
    private final BrowsePresenter mBrowsePresenter;
    private final SplashPresenter mSplashPresenter;
    private Disposable mSignInAction;

    private SignInPresenter(Context context) {
        super(context);
        mService = YouTubeMotherService.instance();
        mBrowsePresenter = BrowsePresenter.instance(context);
        mSplashPresenter = SplashPresenter.instance(context);
    }

    public static SignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SignInPresenter(context);
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
        mSignInAction = mService.getSignInService().signInObserve()
                .subscribe(
                        userCode -> getView().showCode(userCode),
                        error -> Log.e(TAG, "Sign in error: %s", error.getMessage()),
                        () -> {
                            // Success
                            if (getView() != null) {
                                getView().close();
                            }

                            AccountSelectionPresenter.instance(getContext()).show(true);

                            //mBrowsePresenter.refresh();
                            //mSplashPresenter.updateChannels();
                            //
                            //// Account history might be turned off (common issue).
                            //GeneralData.instance(getContext()).enableHistory(true);
                            //MediaServiceManager.instance().enableHistory(true);
                        }
                 );
    }

    public void start() {
        RxHelper.disposeActions(mSignInAction);
        ViewManager.instance(getContext()).startView(SignInView.class);
    }
}
