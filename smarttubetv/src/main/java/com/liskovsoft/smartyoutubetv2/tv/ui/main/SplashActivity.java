package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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
        super.onCreate(savedInstanceState);

        final String prefixPrefName = mNewIntent.getStringExtra("prefix_pref_name");
        if (!Objects.equals(SharedPreferencesBase.getPrefixPrefName(getApplicationContext()), prefixPrefName)
                && prefixPrefName != null) {
            Log.d(TAG, "onCreate: apply preferences and restart app");
            SharedPreferencesBase.setSharedPrefPrefixName(getApplicationContext(), prefixPrefName);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            startActivity(Intent.makeRestartActivityTask(getPackageManager()
                            .getLaunchIntentForPackage(getPackageName())
                            .getComponent())
                    .putExtra("prefix_pref_name", prefixPrefName));
            Runtime.getRuntime().exit(0);
        }
        mPresenter = SplashPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        //finish();
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
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    @Override
    public void finishView() {
        finish();
    }

    //@Override
    //protected void attachBaseContext(Context newBase) {
    //    LangUpdater updater = new LangUpdater(newBase);
    //    updater.update();
    //    String langCode = updater.getUpdatedLocale();
    //    super.attachBaseContext(LocaleContextWrapper.wrap(newBase, LangHelper.parseLangCode(langCode)));
    //}
}
