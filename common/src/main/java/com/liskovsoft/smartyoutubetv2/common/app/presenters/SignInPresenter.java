package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SignInPresenter implements Presenter<SignInView> {
    private static final String TAG = SignInPresenter.class.getSimpleName();
    private static SignInPresenter sInstance;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private final Context mContext;
    private SignInView mView;
    private String mUserCode;

    public SignInPresenter(Context context) {
        mContext = context;
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
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
    }

    @Override
    public void onInitDone() {
        mView.showCode(mUserCode);
    }

    @SuppressLint("CheckResult")
    public void checkUserIsSigned() {
        SignInManager signInManager = mMediaService.getSignInManager();

        signInManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isSigned -> {
                    if (!isSigned) {
                        signInManager.signInObserve()
                                .subscribeOn(Schedulers.newThread())
                                .subscribe(
                                        userCode -> {
                                            Log.d(TAG, "checkUserIsSigned: User code is: " + userCode);
                                            mUserCode = userCode;
                                            mViewManager.startView(SignInView.class);
                                        },
                                        error -> Log.e(TAG, "checkUserIsSigned code error: " + error));
                    } else {
                        Log.d(TAG, "checkUserIsSigned: User already signed");
                    }
                }, error -> Log.e(TAG, "checkUserIsSigned error: " + error));
    }
}
