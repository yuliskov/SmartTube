package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.GlobalConstants;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.BootDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupWorker;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.StreamReminderService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

public class SplashPresenter extends BasePresenter<SplashView> {
    private static final String TAG = SplashPresenter.class.getSimpleName();
    private static final long APP_INIT_DELAY_MS = 10_000;
    @SuppressLint("StaticFieldLeak")
    private static SplashPresenter sInstance;
    private static boolean sRunOnce;
    private boolean mRunPerInstance;
    private final List<IntentProcessor> mIntentChain = new ArrayList<>();
    private String mBridgePackageName;
    private final Runnable mRunBackgroundTasks = this::runBackgroundTasks;
    private final Runnable mCheckForUpdates = this::checkForUpdates;

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
        if (sInstance != null) {
            Utils.removeCallbacks(sInstance.mRunBackgroundTasks);
        }
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        if (getView() == null) {
            return;
        }

        applyRunOnceTasks();
        applyRunPerInstanceTasks();
        Utils.postDelayed(mCheckForUpdates, APP_INIT_DELAY_MS);
        Utils.updateRemoteControlService(getContext());

        //runRefreshCachePeriodicTask();

        checkMasterPassword(() -> applyNewIntent(getView().getNewIntent()));

        showAccountSelectionIfNeeded(); // should be placed after Intent chain
        checkAccountPassword();
    }

    private void applyRunOnceTasks() {
        if (!sRunOnce) {
            sRunOnce = true;
            RxHelper.setupGlobalErrorHandler();
            initGlobalPrefs();
            initProxy();
            initVideoStateService();
            initStreamReminderService();
            //Utils.initVolume(getContext());
        }
    }

    private void applyRunPerInstanceTasks() {
        if (!mRunPerInstance) {
            mRunPerInstance = true;
            Utils.postDelayed(mRunBackgroundTasks, APP_INIT_DELAY_MS);
            initIntentChain();
            // Fake service to prevent the app destroying?
            //runRemoteControlFakeTask();
        }
    }

    private void runBackgroundTasks() {
        YouTubeServiceManager.instance().refreshCacheIfNeeded(); // warm up player engine
        if (PlayerTweaksData.instance(getContext()).isPersistentAntiBotFixEnabled()) {
            YouTubeServiceManager.instance().applyAntiBotFix();
        }
        enableHistoryIfNeeded();
        Utils.updateChannels(getContext());
        GDriveBackupWorker.schedule(getContext());
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

    private void checkForUpdates() {
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

    /**
     * Need to be the first line and executed on earliest stage once.<br/>
     * Inits media service language and context.<br/>
     * NOTE: this command should run before using any of the media service api.
     */
    private void initGlobalPrefs() {
        Log.d(TAG, "initGlobalData called...");

        if (getContext() != null) {
            // 1) Auth token storage init
            // 2) Media service language setup (I assume that context has proper language)
            GlobalPreferences.instance(getContext());
        }
    }

    private void initProxy() {
        if (getContext() != null) {
            // Apply proxy config after global prefs but before starting networking.
            if (GeneralData.instance(getContext()).isProxyEnabled()) {
                new ProxyManager(getContext()).configureSystemProxy();
            }
        }
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
            Utils.forceFinishTheApp();
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
            String channelId = null;

            try {
                channelId = IntentExtractor.extractChannelId(intent);
            } catch (IllegalArgumentException e) {
                MessageHelpers.showLongMessage(getContext(), e.getMessage());
            }

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

                enablePlayerOnlyModeIfNeeded(intent);

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
            if (IntentExtractor.hasData(intent) && !IntentExtractor.isATVChannelUrl(intent) && !IntentExtractor.isRootUrl(intent)) {
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
            SimpleEditDialog.showPassword(
                    getContext(),
                    getContext().getString(R.string.enter_master_password),
                    null,
                    newValue -> {
                        if (password.equals(newValue)) {
                            onSuccess.run();
                            return true;
                        }

                        return false;
                    },
                    () -> getView().finishView() // critical part, fix black screen on app exit
            );
        }
    }

    private void enablePlayerOnlyModeIfNeeded(Intent intent) {
        ViewManager viewManager = ViewManager.instance(getContext());

        boolean isInternalIntent = intent.getBooleanExtra(GlobalConstants.INTERNAL_INTENT, false);

        viewManager.enablePlayerOnlyMode(!isInternalIntent && GeneralData.instance(getContext()).isReturnToLauncherEnabled());
    }
}
