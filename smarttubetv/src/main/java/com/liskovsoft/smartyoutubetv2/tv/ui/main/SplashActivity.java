package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.sharedutils.locale.LangHelper;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.LangUpdater;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

public class SplashActivity extends MotherActivity implements SplashView {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private Intent mNewIntent;
    private SplashPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting SplashView...");

        mNewIntent = getIntent();

        mPresenter = SplashPresenter.instance(this);
        mPresenter.register(this);
        mPresenter.onInitDone();

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;

        mPresenter.onInitDone();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.unregister(this);
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        LangUpdater updater = new LangUpdater(newBase);
        updater.update();
        String langCode = updater.getUpdatedLocale();
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase, LangHelper.parseLangCode(langCode)));
    }
}
