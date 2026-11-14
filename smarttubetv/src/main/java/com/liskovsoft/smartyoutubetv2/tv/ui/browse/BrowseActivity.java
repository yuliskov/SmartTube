package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import android.view.KeyEvent;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Fix: Back on Settings should leave Settings and return to Browse
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            BrowseFragment fragment = (BrowseFragment) getSupportFragmentManager().findFragmentById(R.id.main_frame);
            if (fragment != null) {
                BrowsePresenter presenter = fragment.getBrowsePresenter();
                if (presenter != null && presenter.getCurrentSection() != null
                        && presenter.getCurrentSection().getType() == com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection.TYPE_SETTINGS_GRID) {
                    // Navigate to first (Home) section
                    fragment.selectSection(0, true);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
