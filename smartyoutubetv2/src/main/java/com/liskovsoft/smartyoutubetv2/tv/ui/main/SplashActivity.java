package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.update.AppUpdateManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;

public class SplashActivity extends Activity {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyNewIntent(getIntent());

        updateChannels();
        checkForUpdates();

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        applyNewIntent(intent);
    }

    private void applyNewIntent(Intent intent) {
        String videoId = IntentExtractor.extractVideoId(intent);

        if (videoId != null) {
            PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(this);
            playbackPresenter.openVideo(videoId);
        } else {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null) {
                SearchPresenter searchPresenter = SearchPresenter.instance(this);
                searchPresenter.openSearch(searchText);
            } else {
                ViewManager viewManager = ViewManager.instance(this);
                viewManager.startDefaultView(this);
            }
        }
    }

    private void updateChannels() {
        Class<?> clazz = null;

        try {
            clazz = Class.forName(CHANNELS_RECEIVER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            // NOP
        }

        if (clazz != null) {
            Log.d(TAG, "Starting channels receiver...");
            Intent intent = new Intent(this, clazz);
            sendBroadcast(intent);
        } else {
            Log.e(TAG, "Channels receiver class not found: " + CHANNELS_RECEIVER_CLASS_NAME);
        }
    }

    private void checkForUpdates() {
        AppUpdateManager updatePresenter = AppUpdateManager.instance(this);
        updatePresenter.start();
        updatePresenter.unhold();
    }
}
