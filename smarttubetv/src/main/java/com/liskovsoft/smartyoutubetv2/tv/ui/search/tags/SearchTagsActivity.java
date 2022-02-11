package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SearchTagsActivity extends LeanbackActivity {
    private SearchTagsFragment mFragment;
    private boolean mDownPressed;
    private boolean mIsKeyboardShowing;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_search_tags);
        mFragment = (SearchTagsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.search_tags_fragment);

        // check visible state keyboard
        // only for KITKAT
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
                && mFragment != null
                && mFragment.getView() != null) {
            mFragment.getView()
                    .getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            () -> {
                                final Rect r = new Rect();
                                mFragment.getView().getWindowVisibleDisplayFrame(r);
                                final int screenHeight = mFragment.getView().getRootView().getHeight();
                                final int keypadHeight = screenHeight - r.bottom;
                                mIsKeyboardShowing = keypadHeight > screenHeight * 0.15;
                            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If there are no results found, press the left key to reselect the microphone
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !mFragment.hasResults()) {
            mFragment.focusOnSearchField();
        } else if ((keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_SCROLL_LOCK)&& event.getRepeatCount() == 1) {
            mFragment.pressKeySearch();
        } else if (keyCode == KeyEvent.KEYCODE_BACK && mIsKeyboardShowing && mFragment.getView() != null) {
            // hide keyboard first if visible
            final InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mFragment.getView(), InputMethodManager.SHOW_IMPLICIT);
            imm.hideSoftInputFromWindow(mFragment.getView().getWindowToken(), 0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mFragment.onFinish();
    }
}
