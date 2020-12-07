package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.OnboardingView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

public class OnboardingPresenter extends BasePresenter<OnboardingView> {
    @SuppressLint("StaticFieldLeak")
    private static OnboardingPresenter sInstance;
    private final ViewManager mViewManager;

    private OnboardingPresenter(Context context) {
        super(context);
        mViewManager = ViewManager.instance(context);
    }

    public static OnboardingPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new OnboardingPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void onClose() {
        completeOnboarding();
    }

    public void onFinish() {
        completeOnboarding();

        if (getView() != null) {
            getView().finishOnboarding();
        }
    }

    private void completeOnboarding() {
        AppPrefs.instance(getContext()).setCompletedOnboarding(true);
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        // NOP
    }

    public void showOnboarding() {
        if (!AppPrefs.instance(getContext()).getCompletedOnboarding()) {
            // This is the first time running the app, let's go to onboarding
            mViewManager.startView(OnboardingView.class);
        }
    }
}
