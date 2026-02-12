package com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class ChannelUploadsActivity extends LeanbackActivity {
    private static final String TAG = ChannelUploadsActivity.class.getSimpleName();
    private ChannelUploadsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_channel_uploads);
        mFragment = (ChannelUploadsFragment) getSupportFragmentManager().findFragmentById(R.id.channel_uploads_fragment);
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mFragment.onFinish();
    }
}
