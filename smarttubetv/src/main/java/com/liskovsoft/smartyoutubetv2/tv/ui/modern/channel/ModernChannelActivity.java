package com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

public class ModernChannelActivity extends LeanbackActivity {
    private static final String FRAGMENT_TAG = "modern_channel";
    private ModernChannelFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.modern_browse_container);
        setContentView(container);

        mFragment = (ModernChannelFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (mFragment == null) {
            mFragment = new ModernChannelFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.modern_browse_container, mFragment, FRAGMENT_TAG)
                    .commitNow();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && mFragment != null && mFragment.onUpKey()) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mFragment.onFinish();
    }
}
