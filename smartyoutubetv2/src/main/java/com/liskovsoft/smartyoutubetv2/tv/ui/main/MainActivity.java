package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.os.Bundle;

import android.view.KeyEvent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.base.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.base.LongClickManager;

/*
 * MainActivity class that loads MainFragment.
 */
public class MainActivity extends LeanbackActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private LongClickManager mLongClickManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mLongClickManager = new LongClickManager();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, event);

        mLongClickManager.registerEvent(event);

        return super.dispatchKeyEvent(event);
    }

    public boolean isLongClick() {
        return mLongClickManager.isLongClick();
    }
}
