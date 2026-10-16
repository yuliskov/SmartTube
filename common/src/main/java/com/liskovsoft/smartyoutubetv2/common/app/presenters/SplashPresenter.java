package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
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
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GDriveBackupWorker;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalDriveBackupWorker;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.StreamReminderService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private boolean mResolvingLiveUrl;

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

        Utils.cancelFinishTheApp(getContext());

        runOnceTasks();
        runPerInstanceTasks();
        runPerViewTasks();
    }

    private void runOnceTasks() {
        if (!sRunOnce) {
            sRunOnce = true;
            RxHelper.setupGlobalErrorHandler();
            initGlobalPrefs();
            initProxy();
            initVideoStateService();
            initStreamReminderService();
        }
    }

    private void runPerInstanceTasks() {
        if (!mRunPerInstance) {
            mRunPerInstance = true;
            Utils.postDelayed(mRunBackgroundTasks, APP_INIT_DELAY_MS);
            initIntentChain();
        }
    }

    private void runPerViewTasks() {
        Utils.postDelayed(mCheckForUpdates, APP_INIT_DELAY_MS);
        Utils.updateRemoteControlService(getContext());

        checkMasterPassword(() -> applyNewIntent(getView().getNewIntent()));

        showAccountSelectionIfNeeded(); // should be placed after Intent chain
        checkAccountPassword();
    }

    private void runBackgroundTasks() {
        YouTubeServiceManager.instance().refreshCacheIfNeeded(); // warm up player engine
        enableHistoryIfNeeded();
        Utils.updateChannels(getContext());
        GDriveBackupWorker.schedule(getContext());
        LocalDriveBackupWorker.schedule(getContext());
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
    }

    private void initVideoStateService() {
        if (getContext() != null) {
            VideoStateService.instance(getContext());
        }
    }

    private void initStreamReminderService() {
        if (getContext() != null) {
            StreamReminderService.instance(getContext()).startStop();
        }
    }

    /**
     * Need to be the first line and executed on earliest stage once.<br/>
     * Do init media service language and context.<br/>
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

    public String getBridgePackageName() {
        return mBridgePackageName;
    }


    //https://stackoverflow.com/questions/32454238/how-to-check-if-youtube-channel-is-streaming-live/70026382#70026382
    private String resolveLiveVideoId(String url) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/131.0.0.0 Mobile Safari/537.36"
            );

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                return null;
            }

            // First check whether YouTube redirected directly to a watch URL.
            String videoId = extractYouTubeVideoId(connection.getURL().toString());

            if (videoId != null) {
                return videoId;
            }

            // Otherwise inspect the HTML for a canonical watch URL.
            StringBuilder html = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            "UTF-8" //StandardCharsets.UTF_8 can be used instead  -  explicitly not used since it requires API 19, and min at the moment is 17
                    ))) {
                char[] buffer = new char[8192];
                int count;

                while ((count = reader.read(buffer)) != -1) {
                    // Avoid reading an unexpectedly large page indefinitely.
                    if (html.length() + count > 5_000_000) {
                        return null;
                    }

                    html.append(buffer, 0, count);
                }
            }

            Pattern linkPattern = Pattern.compile(
                    "<link\\b[^>]*>",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher linkMatcher = linkPattern.matcher(html);

            while (linkMatcher.find()) {
                String tag = linkMatcher.group();

                String rel = getHtmlAttribute(tag, "rel");
                String href = getHtmlAttribute(tag, "href");

                if (rel == null || href == null ||
                        !rel.toLowerCase(Locale.ROOT)
                                .matches(".*\\bcanonical\\b.*")) {
                    continue;
                }

                href = href.replace("&amp;", "&");

                videoId = extractYouTubeVideoId(href);

                if (videoId != null) {
                    return videoId;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve YouTube live URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private String extractYouTubeVideoId(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();

            if (host == null ||
                    !(host.equalsIgnoreCase("youtube.com") ||
                            host.equalsIgnoreCase("www.youtube.com") ||
                            host.equalsIgnoreCase("m.youtube.com"))) {
                return null;
            }

            if (!"/watch".equals(uri.getPath())) {
                return null;
            }

            String videoId = uri.getQueryParameter("v");

            if (videoId != null &&
                    videoId.matches("[A-Za-z0-9_-]{11}")) {
                return videoId;
            }
        } catch (Exception ignored) {
            // Invalid URL.
        }

        return null;
    }

    private String getHtmlAttribute(String tag, String attribute) {
        Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(attribute) +
                        "\\s*=\\s*(['\"])(.*?)\\1",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(tag);

        return matcher.find() ? matcher.group(2) : null;
    }


    private void initIntentChain() {
        mIntentChain.add(intent -> {
            String accountName = IntentExtractor.extractAccountName(intent);

            if (accountName != null) {
                List<Account> accounts = getSignInService().getAccounts();
                for (Account account : accounts) {
                    if (Helpers.equals(account.getName(), accountName)) {
                        AccountSelectionPresenter.instance(getContext()).selectAccount(account);
                        break;
                    }
                }
            }

            return false;
        });

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
            if (intent == null || intent.getData() == null) {
                return false;
            }


            Uri uri = intent.getData();
            String host = uri.getHost();
            String path = uri.getPath();

            boolean isYouTube =
                    host != null &&
                            (host.equalsIgnoreCase("youtube.com") ||
                                    host.equalsIgnoreCase("www.youtube.com") ||
                                    host.equalsIgnoreCase("m.youtube.com"));

            boolean isLiveUrl =
                    path != null &&
                            path.matches("^/(?:@[^/]+|channel/[^/]+)/live/?$");

            if (!isYouTube || !isLiveUrl) {
                return false;
            }

            mResolvingLiveUrl = true;

            new Thread(() -> {
                String videoId = resolveLiveVideoId(uri.toString());

                new Handler(Looper.getMainLooper()).post(() -> {
                    mResolvingLiveUrl = false;


                    if (videoId == null) {
                        Log.d(TAG, "No live video found. Falling back to channel page: " + uri);

                        // Remove /live from the channel URL.
                        String channelUrl = uri.toString()
                                .replaceFirst("/live/?$", "");

                        // Create a new intent pointing to the channel page.
                        Intent channelIntent = new Intent(intent);
                        channelIntent.setData(Uri.parse(channelUrl));

                        // Let the existing channel processor handle the URL.
                        applyNewIntent(channelIntent);
                        return;
                    }


                    PlaybackPresenter.instance(getContext()).openVideo(
                            videoId,
                            IntentExtractor.hasFinishOnEndedFlag(intent),
                            IntentExtractor.extractVideoTimeMs(intent),
                            IntentExtractor.isIncognitoIntent(intent)
                    );

                    enablePlayerOnlyModeIfNeeded(intent);
                    getView().finishView();
                });
            }).start();

            return true;
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
                boolean finishOnEnded = IntentExtractor.hasFinishOnEndedFlag(intent);
                boolean incognito = IntentExtractor.isIncognitoIntent(intent);
                playbackPresenter.openVideo(videoId, finishOnEnded, timeMs, incognito);

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
                getViewManager().startDefaultView(); // Nvidia Shield fix
                BrowsePresenter.instance(getContext()).selectSection(sectionId);

                return true;
            }

            return false;
        });

        // Should come last
        mIntentChain.add(intent -> {
            ViewManager viewManager = getViewManager();
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
            String oldBridgeName = mBridgePackageName;
            mBridgePackageName = intent.getStringExtra("bridge_package_name");
            if (!Helpers.equals(oldBridgeName, mBridgePackageName)) {
                updateBadgeIcon();
            }
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
        if (password == null || getViewManager().getTopView() != null) {
            onSuccess.run();
            if (!mResolvingLiveUrl) {
                getView().finishView();
            }
        } else {
            SimpleEditDialog.showPassword(
                    getContext(),
                    getContext().getString(R.string.enter_master_password),
                    null,
                    newValue -> {
                        if (Utils.passwordMatch(password, newValue)) {
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
        ViewManager viewManager = getViewManager();

        boolean isRestartIntent = IntentExtractor.isRestartIntent(intent);
        boolean isATVIntent = IntentExtractor.isATVIntent(intent);
        boolean isExternalIntent = !isRestartIntent && !isATVIntent && !viewManager.isTopViewVisible();

        viewManager.enablePlayerOnlyMode((isATVIntent && GeneralData.instance(getContext()).isReturnToLauncherEnabled()) || isExternalIntent);
    }

    private void updateBadgeIcon() {
        BrowseView browseView = BrowsePresenter.instance(getContext()).getView();

        if (browseView != null) {
            browseView.updateBadge();
        }
    }
}
