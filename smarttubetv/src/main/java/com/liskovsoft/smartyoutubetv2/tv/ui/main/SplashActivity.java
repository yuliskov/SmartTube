package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;

public class SplashActivity extends Activity implements SplashView {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private Intent mNewIntent;
    private SplashPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
}
