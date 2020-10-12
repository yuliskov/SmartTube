package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.update.AppUpdateManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;

public class SplashPresenter implements Presenter<SplashView> {
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";
    private static final String TAG = SplashPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SplashPresenter sInstance;
    private final Context mContext;
    private SplashView mView;
    private static boolean mRunOnce;

    private SplashPresenter(Context context) {
        mContext = context;
    }

    public static SplashPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SplashPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void register(SplashView view) {
        mView = view;
    }

    @Override
    public void unregister(SplashView view) {
        mView = null;
    }

    @Override
    public void onInitDone() {
        applyRunOnceTasks();

        applyNewIntent(mView.getNewIntent());

        checkForUpdates();
    }

    private void applyRunOnceTasks() {
        if (!mRunOnce) {
            updateChannels();
            getBackupDataOnce();
            mRunOnce = true;
        }
    }

    private void checkForUpdates() {
        AppUpdateManager updatePresenter = AppUpdateManager.instance(mContext);
        updatePresenter.start();
        updatePresenter.unhold();
    }

    public void saveBackupData() {
        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(null);
        AppPrefs prefs = AppPrefs.instance(null);

        if (playbackPresenter != null && prefs != null) {
            prefs.setBackupData(
                    playbackPresenter.getVideo() != null ? playbackPresenter.getVideo().videoId : ""
            );
        }
    }

    private String getBackupDataOnce() {
        AppPrefs prefs = AppPrefs.instance(mContext);
        String mBackupVideoId = prefs.getBackupData();
        prefs.setBackupData(null);
        return mBackupVideoId;
    }

    public void updateChannels() {
        Class<?> clazz = null;

        try {
            clazz = Class.forName(CHANNELS_RECEIVER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            // NOP
        }

        if (clazz != null) {
            Log.d(TAG, "Starting channels receiver...");
            Intent intent = new Intent(mContext, clazz);
            mContext.sendBroadcast(intent);
        } else {
            Log.e(TAG, "Channels receiver class not found: " + CHANNELS_RECEIVER_CLASS_NAME);
        }
    }

    private void applyNewIntent(Intent intent) {
        String videoId = IntentExtractor.extractVideoId(intent);

        if (videoId != null) {
            PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(mContext);
            playbackPresenter.openVideo(videoId);

            ViewManager viewManager = ViewManager.instance(mContext);
            viewManager.setSinglePlayerMode(true);
        } else {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null) {
                SearchPresenter searchPresenter = SearchPresenter.instance(mContext);
                searchPresenter.openSearch(searchText);
            } else {
                String backupData = getBackupDataOnce();
                if (backupData != null) {
                    PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(mContext);
                    playbackPresenter.openVideo(backupData);
                } else {
                    ViewManager viewManager = ViewManager.instance(mContext);
                    viewManager.startDefaultView();
                }
            }
        }
    }
}
