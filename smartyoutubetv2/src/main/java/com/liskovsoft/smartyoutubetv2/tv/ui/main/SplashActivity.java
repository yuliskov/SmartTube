package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ViewManager viewManager = ViewManager.instance(this);
        viewManager.startDefaultView(this);

        finish();
    }

    // TODO: implement Android TV channels
    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}
}
