package com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser;

import android.app.Fragment;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class WebBrowserActivity extends LeanbackActivity {
    private static final String TAG = WebBrowserActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_webbrowser);
        } catch (Fragment.InstantiationException e) { // WebBrowserFragment is not a Fragment
            e.printStackTrace();
            finishReally();
        }
    }
}
