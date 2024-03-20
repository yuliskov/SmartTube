package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.BootDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.StreamReminderService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeHubService;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class SplashPresenter extends BasePresenter<SplashView> {
    private static final String CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.RunOnInstallReceiver";
    private static final String TAG = SplashPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SplashPresenter sInstance;
    private static boolean sRunOnce;
    private boolean mRunPerInstance;
    private final List<IntentProcessor> mIntentChain = new ArrayList<>();
    private Disposable mRefreshCachePeriodicAction;
    private String mBridgePackageName;

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

    public static void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        if (getView() == null) {
            return;
        }

        applyRunPerInstanceTasks();
        applyRunOnceTasks();

        //runRefreshCachePeriodicTask();
        showAccountSelectionIfNeeded();

        checkMasterPassword(() -> applyNewIntent(getView().getNewIntent()));

        checkAccountPassword();
        showUpdateNotification();
    }

    private void applyRunPerInstanceTasks() {
        if (!mRunPerInstance) {
            mRunPerInstance = true;
            //clearCache();
            enableHistoryIfNeeded();
            updateChannels();
            initIntentChain();
            // Fake service to prevent the app destroying?
            //runRemoteControlFakeTask();
        }
    }

    private void applyRunOnceTasks() {
        if (!sRunOnce) {
            sRunOnce = true;
            RxHelper.setupGlobalErrorHandler();
            initVideoStateService();
            initStreamReminderService();
        }
    }

    private void showAccountSelectionIfNeeded() {
        AccountSelectionPresenter.instance(getContext()).show();
    }

    private void checkAccountPassword() {
        AccountsData data = AccountsData.instance(getContext());
        // Block even if the password was accepted before
        if (data.getAccountPassword() != null) {
            data.setPasswordAccepted(false);
            PlaybackPresenter.instance(getContext()).forceFinish();
            BrowsePresenter.instance(getContext()).updateSections();
        }
    }

    private void showUpdateNotification() {
        BootDialogPresenter updatePresenter = BootDialogPresenter.instance(getContext());
        updatePresenter.start();
        //updatePresenter.unhold();
    }

    private void runRemoteControlFakeTask() {
        // Fake service to prevent the app from destroying
        if (getContext() != null) {
            //Utils.startRemoteControlService(getContext());
            Utils.startRemoteControlWorkRequest(getContext());
        }
    }

    private void initVideoStateService() {
        if (getContext() != null) {
            VideoStateService.instance(getContext());
        }
    }

    private void initStreamReminderService() {
        if (getContext() != null) {
            StreamReminderService.instance(getContext()).start();
        }
    }

    private void clearCache() {
        if (getContext() != null) {
            int versionCode = AppInfoHelpers.getAppVersionCode(getContext());
            if (GeneralData.instance(getContext()).getVersionCode() != versionCode) {
                GeneralData.instance(getContext()).setVersionCode(versionCode);

                //FileHelpers.deleteCache(getContext());
                ViewManager.instance(getContext()).clearCaches();
            }
        }
    }

    private void runRefreshCachePeriodicTask() {
        if (RxHelper.isAnyActionRunning(mRefreshCachePeriodicAction)) {
            return;
        }

        mRefreshCachePeriodicAction = RxHelper.startInterval(YouTubeHubService.instance()::refreshCacheIfNeeded, 30 * 60);
    }

    private void enableHistoryIfNeeded() {
        // Account history might be turned off (common issue).
        GeneralData generalData = GeneralData.instance(getContext());
        if (generalData.getHistoryState() != GeneralData.HISTORY_AUTO) {
            MediaServiceManager.instance().enableHistory(generalData.isHistoryEnabled());
        }
    }

    private void checkTouchSupport() {
        if (Helpers.isTouchSupported(getContext())) {
            MessageHelpers.showLongMessage(getContext(), "The app is designed for tv boxes. Phones aren't supported.");
            ViewManager.instance(getContext()).forceFinishTheApp();
        }
    }

    public void updateChannels() {
        // Can't use class directly! ATV module is disabled for some flavors.
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

    public String getBridgePackageName() {
        return mBridgePackageName;
    }

    private void initIntentChain() {
        mIntentChain.add(intent -> {
            String searchText = IntentExtractor.extractSearchText(intent);

            if (searchText != null || IntentExtractor.isStartVoiceCommand(intent)) {
                SearchPresenter searchPresenter = SearchPresenter.instance(getContext());
                if (IntentExtractor.isInstantPlayCommand(intent)) {
                    searchPresenter.startPlay(searchText);
                } else {
                    searchPresenter.startSearch(searchText);
                }
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
                long timeMs = IntentExtractor.extractVideoTimeMs(intent);
                PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getContext());
                playbackPresenter.openVideo(videoId, IntentExtractor.hasFinishOnEndedFlag(intent), timeMs);

                enablePlayerOnlyModeIfNeeded();

                return true;
            }

            return false;
        });

        // NOTE: doesn't work very well. E.g. there's problems with focus or conflicts with 'boot to' section option.
        mIntentChain.add(intent -> {
            if (!GeneralData.instance(getContext()).isSelectChannelSectionEnabled()) {
                return false;
            }

            int sectionId = -1;

            // ATV channel icon clicked
            if (IntentExtractor.isSubscriptionsUrl(intent)) {
                sectionId = MediaGroup.TYPE_SUBSCRIPTIONS;
            } else if (IntentExtractor.isHistoryUrl(intent)) {
                sectionId = MediaGroup.TYPE_HISTORY;
            } else if (IntentExtractor.isRecommendedUrl(intent)) {
                sectionId = MediaGroup.TYPE_HOME;
            }

            if (sectionId != -1) {
                ViewManager.instance(getContext()).startDefaultView(); // Nvidia Shield fix
                BrowsePresenter.instance(getContext()).selectSection(sectionId);

                return true;
            }

            return false;
        });

        // Should come last
        mIntentChain.add(intent -> {
            ViewManager viewManager = ViewManager.instance(getContext());
            viewManager.startDefaultView();

            // For debug purpose when using ATV bridge.
            if (IntentExtractor.hasData(intent) && !IntentExtractor.isChannelUrl(intent) && !IntentExtractor.isRootUrl(intent)) {
                MessageHelpers.showLongMessage(getContext(), String.format("Can't process intent: %s", Helpers.toString(intent)));
            }

            return true;
        });
    }

    public void applyNewIntent(Intent intent) {
        if (intent != null) {
            mBridgePackageName = intent.getStringExtra("bridge_package_name");
        }

        for (IntentProcessor processor : mIntentChain) {
            if (processor.process(intent)) {
                break;
            }
        }
    }

    private void checkMasterPassword(Runnable onSuccess) {
        String password = GeneralData.instance(getContext()).getMasterPassword();

        // No passwd or the app already started
        if (password == null || ViewManager.instance(getContext()).getTopView() != null) {
            onSuccess.run();
            getView().finishView(); // critical part, fix black screen on app exit
        } else {
            SimpleEditDialog.show(
                    getContext(),
                    "", newValue -> {
                        if (password.equals(newValue)) {
                            onSuccess.run();
                            return true;
                        }

                        return false;
                    },
                    getContext().getString(R.string.enter_master_password),
                    true,
                    () -> getView().finishView() // critical part, fix black screen on app exit
            );
        }
    }

    private void enablePlayerOnlyModeIfNeeded() {
        ViewManager viewManager = ViewManager.instance(getContext());

        viewManager.enablePlayerOnlyMode(GeneralData.instance(getContext()).isPlayerOnlyModeEnabled());
    }
}
