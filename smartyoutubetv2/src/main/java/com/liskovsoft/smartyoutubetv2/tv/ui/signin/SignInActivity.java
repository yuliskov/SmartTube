package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    private SignInFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_signin);
        //mFragment = (SignInFragment) getSupportFragmentManager()
        //        .findFragmentById(R.id.signin_fragment);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.signin_root, new SignInFragment());
        ft.commit();
    }
}
