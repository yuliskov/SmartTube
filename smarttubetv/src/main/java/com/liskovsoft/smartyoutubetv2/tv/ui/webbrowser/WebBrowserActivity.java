package com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class WebBrowserActivity extends LeanbackActivity {
    private static final String TAG = WebBrowserActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // WebBrowserFragment is not a Fragment: ClassCastException or Fragment.InstantiationException
        setContentView(R.layout.fragment_webbrowser);
    }
}
