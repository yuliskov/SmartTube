package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.tv.update.AppUpdateManager;

public class SplashActivity extends Activity {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";
    private static boolean mRunOnce;
    private String mBackupVideoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyRunOnceTasks();

        applyNewIntent(getIntent());

        checkForUpdates();

        finish();
    }

    private void applyRunOnceTasks() {
        if (!mRunOnce) {
            updateChannels();
            restoreData();
            mRunOnce = true;
        }
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

            ViewManager viewManager = ViewManager.instance(this);
            viewManager.setSinglePlayerMode(true);
        } else {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null) {
                SearchPresenter searchPresenter = SearchPresenter.instance(this);
                searchPresenter.openSearch(searchText);
            } else {
                if (mBackupVideoId != null) {
                    PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(this);
                    playbackPresenter.openVideo(mBackupVideoId);
                    mBackupVideoId = null;
                } else {
                    ViewManager viewManager = ViewManager.instance(this);
                    viewManager.startDefaultView();
                }
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

    public static void backupData() {
        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(null);
        AppPrefs prefs = AppPrefs.instance(null);

        if (playbackPresenter != null && prefs != null) {
            prefs.setBackupData(
                    playbackPresenter.getVideo() != null ? playbackPresenter.getVideo().videoId : ""
            );
        }
    }

    private void restoreData() {
        AppPrefs prefs = AppPrefs.instance(this);
        mBackupVideoId = prefs.getBackupData();
        prefs.setBackupData(null);
    }
}
