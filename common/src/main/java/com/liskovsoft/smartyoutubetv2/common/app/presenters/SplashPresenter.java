package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class SplashPresenter extends BasePresenter<SplashView> {
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";
    private static final String TAG = SplashPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SplashPresenter sInstance;
    private static boolean mRunOnce;
    private final List<IntentProcessor> mIntentChain = new ArrayList<>();
    private ProxyManager mProxyManager;

    private interface IntentProcessor {
        boolean process(Intent intent);
    }

    private SplashPresenter(Context context) {
        super(context);
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

        showAccountSelection();

        applyNewIntent(getView().getNewIntent());
    }

    private void applyRunOnceTasks() {
        if (!mRunOnce) {
            //checkTouchSupport(); // Not working?
            // Need to be the first line and executed on earliest stage once.
            // Inits service language and context.
            Utils.initGlobalData(getContext());
            initIntentChain();
            updateChannels();
            getBackupDataOnce();
            runRemoteControlTasks();
            configureProxy();
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

    private void runRemoteControlTasks() {
        // Fake service to prevent the app from destroying
        if (getContext() != null) {
            //Utils.startRemoteControlService(getContext());
            Utils.startRemoteControlWorkRequest(getContext());
        }
    }

    private void configureProxy() {
        if (getContext() != null && GeneralData.instance(getContext()).isProxyEnabled()) {
            mProxyManager = ProxyManager.instance(getContext());
            mProxyManager.enableProxy(true);
        }
    }

    private void checkTouchSupport() {
        if (Helpers.isTouchSupported(getContext())) {
            MessageHelpers.showLongMessage(getContext(), "The app is designed for tv boxes. Phones aren't supported.");
            ViewManager.instance(getContext()).forceFinishTheApp();
        }
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
                try {
                    getContext().sendBroadcast(intent);
                } catch (Exception e) {
                    // NullPointerException on MX9Pro (rk3328  7.1.2)
                }
            }
        } else {
            Log.e(TAG, "Channels receiver class not found: " + CHANNELS_RECEIVER_CLASS_NAME);
        }
    }

    private void initIntentChain() {
        mIntentChain.add(intent -> {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null || IntentExtractor.isStartVoiceCommand(intent)) {
                SearchPresenter searchPresenter = SearchPresenter.instance(getContext());
                searchPresenter.startSearch(searchText);
                return true;
            }

            return false;
        });

        mIntentChain.add(intent -> {
            String channelId = IntentExtractor.extractChannelId(intent);

            if (channelId != null) {
                ChannelPresenter channelPresenter = ChannelPresenter.instance(getContext());
                channelPresenter.openChannel(channelId);
                return true;
            }

            return false;
        });

        mIntentChain.add(intent -> {
            String playlistId = IntentExtractor.extractPlaylistId(intent);

            if (playlistId != null) {
                Video video = new Video();
                video.playlistId = playlistId;
                ChannelUploadsPresenter.instance(getContext()).openChannel(video);
                return true;
            }

            return false;
        });

        // Should come after playlist
        mIntentChain.add(intent -> {
            String videoId = IntentExtractor.extractVideoId(intent);

            if (videoId != null) {
                PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
                playbackPresenter.openVideo(videoId);

                ViewManager viewManager = ViewManager.instance(getContext());

                if (GeneralData.instance(getContext()).isReturnToLauncherEnabled()) {
                    viewManager.setSinglePlayerMode(true);
                }

                return true;
            }

            return false;
        });

        mIntentChain.add(intent -> {
            String backupData = getBackupDataOnce();

            if (backupData != null) {
                PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
                playbackPresenter.openVideo(backupData);
                return true;
            }

            return false;
        });

        // Should come last
        mIntentChain.add(intent -> {
            ViewManager viewManager = ViewManager.instance(getContext());
            viewManager.startDefaultView();

            // For debug purpose when using ATV bridge.
            if (IntentExtractor.hasData(intent) && !IntentExtractor.isChannelUrl(intent)) {
                MessageHelpers.showLongMessage(getContext(), String.format("Can't process intent: %s", Helpers.toString(intent)));
            }

            return true;
        });
    }

    private void applyNewIntent(Intent intent) {
        for (IntentProcessor processor : mIntentChain) {
            if (processor.process(intent)) {
                break;
            }
        }
    }

    //private void applyNewIntent(Intent intent) {
    //    String videoId = IntentExtractor.extractVideoId(intent);
    //
    //    if (videoId != null) {
    //        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
    //        playbackPresenter.openVideo(videoId);
    //
    //        ViewManager viewManager = ViewManager.instance(getContext());
    //
    //        if (GeneralData.instance(getContext()).isReturnToLauncherEnabled()) {
    //            viewManager.setSinglePlayerMode(true);
    //        }
    //    } else {
    //        String searchText = IntentExtractor.extractSearchText(intent);
    //
    //        if (searchText != null || IntentExtractor.isStartVoiceCommand(intent)) {
    //            SearchPresenter searchPresenter = SearchPresenter.instance(getContext());
    //            searchPresenter.startSearch(searchText);
    //        } else {
    //            String channelId = IntentExtractor.extractChannelId(intent);
    //
    //            if (channelId != null) {
    //                ChannelPresenter channelPresenter = ChannelPresenter.instance(getContext());
    //                channelPresenter.openChannel(channelId);
    //            } else {
    //                String backupData = getBackupDataOnce();
    //                if (backupData != null) {
    //                    PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
    //                    playbackPresenter.openVideo(backupData);
    //                } else {
    //                    ViewManager viewManager = ViewManager.instance(getContext());
    //                    viewManager.startDefaultView();
    //
    //                    // For debug purpose when using ATV bridge.
    //                    if (IntentExtractor.hasData(intent) && !IntentExtractor.isChannelUrl(intent)) {
    //                        MessageHelpers.showLongMessage(getContext(), String.format("Can't process intent: %s", Helpers.toString(intent)));
    //                    }
    //                }
    //            }
    //        }
    //    }
    //}
}
