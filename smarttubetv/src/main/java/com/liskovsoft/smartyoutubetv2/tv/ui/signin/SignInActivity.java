package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
        }
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
