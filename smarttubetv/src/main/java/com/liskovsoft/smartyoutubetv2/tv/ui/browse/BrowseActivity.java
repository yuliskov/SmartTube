package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.Analytics;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
        Analytics.sendActivityStarted(BrowseActivity.class.getSimpleName());
        // State saving should be explicitly enabled for needed activities
        enableSaveState(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SCROLL_LOCK) {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof ISearchNewKeyListener) {
                    ((ISearchNewKeyListener) fragment).onSearchKeyUp();
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
