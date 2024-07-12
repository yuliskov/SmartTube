package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity implements SignInView {
    private SignInView signInView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.acitivity_sign_in);
        SignInPresenter.instance(this).setView(this);
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SignInPresenter.instance(this).onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SignInPresenter.instance(this).onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        ((TextView)findViewById(R.id.user_code)).setText(userCode);
    }

    @Override
    public void close() {
        finish();
    }
}
