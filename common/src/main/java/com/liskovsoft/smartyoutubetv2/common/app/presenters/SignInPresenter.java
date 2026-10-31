package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;

public class SignInPresenter extends BasePresenter<SignInView> {
    private static final String TAG = SignInPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SignInPresenter sInstance;
    private SignInPresenter mPresenter;
    private boolean mIsWaiting;

    protected SignInPresenter(Context context) {
        super(context);
    }

    public static SignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SignInPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    @Override
    public void onViewInitialized() {
        if (this.getClass() != SignInPresenter.class) {
            doWait(false);
            return;
        }

        if (YTSignInPresenter.instance(getContext()).isWaiting()) {
            mPresenter = YTSignInPresenter.instance(getContext());
        } else if (GoogleSignInPresenter.instance(getContext()).isWaiting()) {
            mPresenter = GoogleSignInPresenter.instance(getContext());
        }

        if (mPresenter == null) {
            mPresenter = YTSignInPresenter.instance(getContext());
            //throw new IllegalStateException("At least one nested sign in presenter should be initialized.");
        }

        mPresenter.setView(getView());
        mPresenter.onViewInitialized();
    }

    public void onActionClicked() {
        if (mPresenter != null) {
            mPresenter.onActionClicked();
        }
    }

    private void doWait(boolean doWait) {
        mIsWaiting = doWait;
    }

    protected final boolean isWaiting() {
        return mIsWaiting;
    }

    public void start() {
        getViewManager().startView(SignInView.class);
        doWait(true);
    }
}
