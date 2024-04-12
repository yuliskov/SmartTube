package com.liskovsoft.smartyoutubetv2.tv.launchers;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class ProperlyFinishTheAppActivity extends MotherActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.startRemoteControl(this);
        Utils.updateChannels(this);

        finish();
    }
}
