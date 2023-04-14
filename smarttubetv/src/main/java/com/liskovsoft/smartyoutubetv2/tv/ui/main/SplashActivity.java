package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

import java.util.Objects;

public class SplashActivity extends MotherActivity implements SplashView {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private Intent mNewIntent;
    private SplashPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mNewIntent = getIntent();
        final String prefixPrefName = mNewIntent.getStringExtra("prefix_pref_name");
        if (!Objects.equals(SharedPreferencesBase.getPrefixPrefName(getApplicationContext()), prefixPrefName)) {
            SharedPreferencesBase.setSharedPrefPrefixName(getApplicationContext(), prefixPrefName);
            startActivity(Intent.makeRestartActivityTask(getPackageManager()
                    .getLaunchIntentForPackage(getPackageName())
                    .getComponent())
                    .putExtra("prefix_pref_name", prefixPrefName));
            Runtime.getRuntime().exit(0);
        }

        super.onCreate(savedInstanceState);

        mPresenter = SplashPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;

        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    //@Override
    //protected void attachBaseContext(Context newBase) {
    //    LangUpdater updater = new LangUpdater(newBase);
    //    updater.update();
    //    String langCode = updater.getUpdatedLocale();
    //    super.attachBaseContext(LocaleContextWrapper.wrap(newBase, LangHelper.parseLangCode(langCode)));
    //}
}
