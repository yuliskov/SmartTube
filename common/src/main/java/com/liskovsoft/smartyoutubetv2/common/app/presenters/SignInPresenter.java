package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SignInPresenter implements Presenter<SignInView> {
    private static final String TAG = SignInPresenter.class.getSimpleName();
    private static SignInPresenter sInstance;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private final Context mContext;
    private final BrowsePresenter mBrowsePresenter;
    private SignInView mView;
    private String mUserCode;
    private Disposable mSignInAction;

    public SignInPresenter(Context context) {
        mContext = context;
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        mBrowsePresenter = BrowsePresenter.instance(context);
    }

    public static SignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SignInPresenter(context.getApplicationContext());
        }

        return sInstance;
    }
    public static void unhold() {
        sInstance = null;
    }

    @Override
    public void register(SignInView view) {
        mView = view;
    }

    @Override
    public void unregister(SignInView view) {
        mView = null;
        unhold();
    }

    @Override
    public void onInitDone() {
        if (mSignInAction != null && !mSignInAction.isDisposed()) {
            mSignInAction.dispose();
        }

        updateUserCode();
    }

    private void updateUserCode() {
        mSignInAction = mMediaService.getSignInManager().signInObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        userCode -> mView.showCode(userCode),
                        error -> Log.e(TAG, error),
                        () -> {
                            mBrowsePresenter.refresh();
                            mView.close();
                        });
    }
}
