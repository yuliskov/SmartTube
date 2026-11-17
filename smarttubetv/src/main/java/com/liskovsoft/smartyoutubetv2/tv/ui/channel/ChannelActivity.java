package com.liskovsoft.smartyoutubetv2.tv.ui.channel;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class ChannelActivity extends LeanbackActivity {
    private static final String TAG = ChannelActivity.class.getSimpleName();
    private ChannelFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_channel);
        mFragment = (ChannelFragment) getSupportFragmentManager().findFragmentById(R.id.channel_fragment);
    }

    @Override
    public void finish() {
        // Return to the previous channel when a channel was opened from another one (e.g. linked channels)
        if (isBackPressed() && ChannelPresenter.instance(this).goBack()) {
            resetBackState();
            return;
        }

        super.finish();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mFragment.onFinish();
    }
}
