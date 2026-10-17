package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

public class ModernBrowseActivity extends LeanbackActivity {
    private static final String FRAGMENT_TAG = "modern_browse";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.modern_browse_container);
        setContentView(container);

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.modern_browse_container, new ModernBrowseFragment(), FRAGMENT_TAG)
                    .commitNow();
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (fragment instanceof ModernBrowseFragment && ((ModernBrowseFragment) fragment).onBackPressed()) {
            return;
        }

        super.onBackPressed();
    }
}
