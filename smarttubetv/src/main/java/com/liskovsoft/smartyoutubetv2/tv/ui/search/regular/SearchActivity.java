package com.liskovsoft.smartyoutubetv2.tv.ui.search.regular;

import android.os.Bundle;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SearchActivity extends LeanbackActivity {
    private SearchFragment mFragment;
    private boolean mDownPressed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_search);
        mFragment = (SearchFragment) getSupportFragmentManager()
                .findFragmentById(R.id.search_fragment);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If there are no results found, press the left key to reselect the microphone
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !mFragment.hasResults()) {
            mFragment.focusOnSearch();
        }
        return super.onKeyDown(keyCode, event);
    }
}
