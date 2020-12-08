package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;

public class SplashPresenter extends BasePresenter<SplashView> {
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";
    private static final String TAG = SplashPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SplashPresenter sInstance;
    private static boolean mRunOnce;

    private SplashPresenter(Context context) {
        super(context);
        GlobalPreferences.instance(context); // auth token storage init
    }

    public static SplashPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SplashPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        mRunOnce = false;
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        applyRunOnceTasks();

        applyNewIntent(getView().getNewIntent());
    }

    private void applyRunOnceTasks() {
        if (!mRunOnce) {
            showAccountSelection();
            updateChannels();
            getBackupDataOnce();
            mRunOnce = true;
        }
    }

    private void showAccountSelection() {
        AccountSelectionPresenter.instance(getContext()).show();
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
        AppPrefs prefs = AppPrefs.instance(getContext());
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
            if (getContext() != null) {
                Log.d(TAG, "Starting channels receiver...");
                Intent intent = new Intent(getContext(), clazz);
                getContext().sendBroadcast(intent);
            }
        } else {
            Log.e(TAG, "Channels receiver class not found: " + CHANNELS_RECEIVER_CLASS_NAME);
        }
    }

    private void applyNewIntent(Intent intent) {
        String videoId = IntentExtractor.extractVideoId(intent);

        if (videoId != null) {
            PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
            playbackPresenter.openVideo(videoId);

            ViewManager viewManager = ViewManager.instance(getContext());
            viewManager.setSinglePlayerMode(true);
        } else {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null) {
                SearchPresenter searchPresenter = SearchPresenter.instance(getContext());
                searchPresenter.startSearch(searchText);
            } else {
                String channelId = IntentExtractor.extractChannelId(intent);

                if (channelId != null) {
                    ChannelPresenter channelPresenter = ChannelPresenter.instance(getContext());
                    channelPresenter.openChannel(channelId);
                } else {
                    String backupData = getBackupDataOnce();
                    if (backupData != null) {
                        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
                        playbackPresenter.openVideo(backupData);
                    } else {
                        ViewManager viewManager = ViewManager.instance(getContext());
                        viewManager.startDefaultView();
                    }
                }
            }
        }
    }
}
