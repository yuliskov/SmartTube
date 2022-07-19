package com.liskovsoft.smartyoutubetv2.tv.launchers;

import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

public class SearchLauncherActivity extends MotherActivity implements SplashView {
    private static final String TAG = SearchLauncherActivity.class.getSimpleName();
    private Intent mNewIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNewIntent = getIntent();

        SearchPresenter.instance(this).startSearch(null);

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }
}
