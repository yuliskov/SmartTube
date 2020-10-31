package com.liskovsoft.smartyoutubetv2.tv.ui.channelsub;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class ChannelSubActivity extends LeanbackActivity {
    private static final String TAG = ChannelSubActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_channel_sub);
    }
}
