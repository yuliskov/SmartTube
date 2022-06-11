package com.liskovsoft.smartyoutubetv2.tv.ui.webview;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class WebViewActivity extends LeanbackActivity {
    private static final String TAG = WebViewActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_webview);
    }
}
