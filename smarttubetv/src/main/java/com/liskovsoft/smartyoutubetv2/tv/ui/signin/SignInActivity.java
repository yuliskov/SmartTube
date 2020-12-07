package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity implements SignInView {
    private SignInPresenter mSignInPresenter;
    private TextView mUserCode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.acitivity_sign_in);

        mSignInPresenter = SignInPresenter.instance(this);
        mSignInPresenter.setView(this);
        mUserCode = findViewById(R.id.user_code);

//
//        Fragment fragment =
//                getSupportFragmentManager().;
//        if (fragment instanceof PlaybackFragment) {
//            mPlaybackFragment = (PlaybackFragment) fragment;
//        }
//
////        if (null == savedInstanceState) {
////            GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
////        }
    }

    @Override
    public void finish() {
        super.finish();

        destroyActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void showCode(String userCode) {
        if (mUserCode != null) {
            mUserCode.setText(userCode);
        }
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }
}
