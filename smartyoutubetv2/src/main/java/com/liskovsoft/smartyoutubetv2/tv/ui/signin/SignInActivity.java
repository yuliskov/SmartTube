package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchFragment;

public class SignInActivity extends LeanbackActivity {
    private SearchFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_signin);
        mFragment = (SearchFragment) getSupportFragmentManager()
                .findFragmentById(R.id.signin_fragment);
    }
}
