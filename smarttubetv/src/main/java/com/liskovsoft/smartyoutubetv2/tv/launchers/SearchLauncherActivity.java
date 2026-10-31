package com.liskovsoft.smartyoutubetv2.tv.launchers;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

public class SearchLauncherActivity extends MotherActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SearchPresenter.instance(this).startSearch(null);

        finish();
    }
}
