package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.view.KeyEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

public class ModernPlaybackActivity extends PlaybackActivity {
    @Override
    protected int getContentLayoutId() {
        return R.layout.modern_fragment_playback;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.playback_controls_fragment);

        if (fragment instanceof ModernPlaybackFragment && ((ModernPlaybackFragment) fragment).dispatchShortsKey(event)) {
            return true;
        }

        if (fragment instanceof ModernPlaybackFragment && ((ModernPlaybackFragment) fragment).dispatchNavigationKey(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern_Player);
        ModernColorScheme.apply(this);
    }
}
