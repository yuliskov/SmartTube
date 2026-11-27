package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import android.os.Bundle;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

public class ModernSearchActivity extends LeanbackActivity {
    private static final String FRAGMENT_TAG = "modern_search";
    private ModernSearchFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.modern_browse_container);
        setContentView(container);

        mFragment = (ModernSearchFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (mFragment == null) {
            mFragment = new ModernSearchFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.modern_browse_container, mFragment, FRAGMENT_TAG)
                    .commitNow();
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    protected boolean isSearchScreen() {
        return true;
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mFragment.onFinish();
    }
}
