package com.liskovsoft.smartyoutubetv2.tv.ui.signin.old;

import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivityOld extends LeanbackActivity {
    private SignInFragmentOld mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.fragment_signin);
        //mFragment = (SignInFragment) getSupportFragmentManager()
        //        .findFragmentById(R.id.signin_fragment);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(android.R.id.content, new SignInFragmentOld());
        ft.commitAllowingStateLoss();
    }
}
