package com.liskovsoft.smartyoutubetv2.tv.ui.modern.signin;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

public class ModernSignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new ModernSignInFragment())
                    .commitNow();
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
