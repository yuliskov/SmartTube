package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SignInPresenter extends BasePresenter<SignInView> {
    private static final String TAG = SignInPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SignInPresenter sInstance;
    private final MediaService mMediaService;
    private final BrowsePresenter mBrowsePresenter;
    private final SplashPresenter mSplashPresenter;
    private Disposable mSignInAction;

    private SignInPresenter(Context context) {
        super(context);
        mMediaService = YouTubeMediaService.instance();
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
        RxUtils.disposeActions(mSignInAction);
        sInstance = null;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    @Override
    public void onViewInitialized() {
        RxUtils.disposeActions(mSignInAction);
        updateUserCode();
    }

    public void onActionClicked() {
        getView().close();
    }

    private void updateUserCode() {
        mSignInAction = mMediaService.getSignInManager().signInObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        userCode -> getView().showCode(userCode),
                        error -> Log.e(TAG, "Sign in error: %s", error.getMessage()),
                        () -> {
                            // Success
                            mBrowsePresenter.refresh();
                            if (getView() != null) {
                                getView().close();
                            }
                            mSplashPresenter.updateChannels();
                        }
                 );
    }

    public void start() {
        RxUtils.disposeActions(mSignInAction);
        ViewManager.instance(getContext()).startView(SignInView.class);
    }
}
