package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Class<?> lastActivity = MainApplication.getLastActivity();

        if (lastActivity == null) {
            lastActivity = BrowseActivity.class;
        }
        
        Intent intent = new Intent(this, lastActivity);
        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }
}
