package com.liskovsoft.smartyoutubetv2.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.views.OnboardingView;

public class OnboardingPresenter extends Presenter<OnboardingView> {
    @SuppressLint("StaticFieldLeak")
    private static OnboardingPresenter sInstance;
    private final Context mContext;

    private OnboardingPresenter(Context context) {
        mContext = context;
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

        for (OnboardingView view : mViews) {
            view.finishOnboarding();
        }
    }

    private void completeOnboarding() {
        AppPrefs.instance(mContext).setCompletedOnboarding(true);
        sInstance = null;
    }
}
