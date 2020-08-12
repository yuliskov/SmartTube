package com.liskovsoft.smartyoutubetv2.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.prefs.AppPrefs;

public class OnboardingPresenter {
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

    public void onBackPressed() {
        completeOnboarding();
    }

    public void onFinish() {
        completeOnboarding();
    }

    private void completeOnboarding() {
        AppPrefs.instance(mContext).setCompletedOnboarding(true);
        sInstance = null;
    }
}
