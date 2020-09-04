package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.app.views.OnboardingView;

public class OnboardingPresenter implements Presenter<OnboardingView> {
    @SuppressLint("StaticFieldLeak")
    private static OnboardingPresenter sInstance;
    private final Context mContext;
    private final ViewManager mViewManager;
    private OnboardingView mView;

    private OnboardingPresenter(Context context) {
        mContext = context;
        mViewManager = ViewManager.instance(context);
    }

    public static OnboardingPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new OnboardingPresenter(context);
        }

        return sInstance;
    }

    public void onClose() {
        completeOnboarding();
    }

    public void onFinish() {
        completeOnboarding();

        if (mView != null) {
            mView.finishOnboarding();
        }
    }

    private void completeOnboarding() {
        AppPrefs.instance(mContext).setCompletedOnboarding(true);
        sInstance = null;
    }

    @Override
    public void onInitDone() {
        // NOP
    }

    @Override
    public void register(OnboardingView view) {
        mView = view;
    }

    @Override
    public void unregister(OnboardingView view) {
        mView = null;
    }

    public void showOnboarding() {
        if (!AppPrefs.instance(mContext).getCompletedOnboarding()) {
            // This is the first time running the app, let's go to onboarding
            mViewManager.startView(OnboardingView.class);
        }
    }
}
