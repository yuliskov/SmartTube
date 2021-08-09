package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlService;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlWorker;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final String TASK_ID = RemoteControlWorker.class.getSimpleName();
    private static final String TAG = Utils.class.getSimpleName();
    private static final String QR_CODE_URL_TEMPLATE = "https://api.qrserver.com/v1/create-qr-code/?data=%s";

    /**
     * Limit the maximum size of a Map by removing oldest entries when limit reached
     */
    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries*10/7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Trim playlist if one exceeds max size
     */
    public static <T> List<T> createLRUList(final int maxEntries) {
        return new LinkedList<T>() {
            @Override
            public boolean add(T t) {
                if (size() > maxEntries) {
                    removeFirst();
                }

                return super.add(t);
            }
        };
    }

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId) {
        Uri videoUrl = convertToFullVideoUrl(videoId);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareEmbedVideoDialog(Context context, String videoId) {
        Uri videoUrl = convertToEmbedVideoUrl(videoId);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareChannelDialog(Context context, String channelId) {
        Uri channelUrl = convertToFullChannelUrl(channelId);
        showMultiChooser(context, channelUrl);
    }

    @TargetApi(17)
    private static void showMultiChooser(Context context, Uri url) {
        Intent primaryIntent = new Intent(Intent.ACTION_VIEW);
        Intent secondaryIntent = new Intent(Intent.ACTION_SEND);
        primaryIntent.setData(url);
        secondaryIntent.putExtra(Intent.EXTRA_TEXT, url.toString());
        secondaryIntent.setType("text/plain");
        Intent chooserIntent = Intent.createChooser(primaryIntent, context.getResources().getText(R.string.share_link));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { secondaryIntent });
        chooserIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.startActivity(chooserIntent);
    }

    private static Uri convertToFullVideoUrl(String videoId) {
        String url = String.format("https://www.youtube.com/watch?v=%s", videoId);
        return Uri.parse(url);
    }

    private static Uri convertToEmbedVideoUrl(String videoId) {
        String url = String.format("https://www.youtube.com/embed/%s", videoId);
        return Uri.parse(url);
    }

    private static Uri convertToFullChannelUrl(String channelId) {
        String url = String.format("https://www.youtube.com/channel/%s", channelId);
        return Uri.parse(url);
    }

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    }

    public static boolean isPlayerInForeground(Context context) {
        if (context == null) {
            return false;
        }

        return isAppInForeground() && ViewManager.instance(context).getTopView() == PlaybackView.class;
    }

    public static void moveAppToForeground(Context context) {
        if (!Utils.isAppInForeground()) {
            ViewManager.instance(context).startView(SplashView.class);
        }
    }

    public static void movePlayerToForeground(Context context) {
        turnScreenOn(context);

        if (!Utils.isPlayerInForeground(context)) {
            ViewManager.instance(context).startView(PlaybackView.class);
        }
    }

    public static boolean checkActivity(Activity activity) {
        return activity != null && !activity.isDestroyed() && !activity.isFinishing();
    }

    public static void startRemoteControlService(Context context) {
        // Fake service to prevent the app from destroying
        Intent serviceIntent = new Intent(context, RemoteControlService.class);

        //context.startService(serviceIntent);

        // https://medium.com/@debuggingisfun/android-auto-stop-background-service-336e8b3ff03c
        // https://medium.com/@debuggingisfun/android-o-work-around-background-service-limitation-e697b2192bc3
        context.getApplicationContext().bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                 // NOP
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                 // NOP
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void startRemoteControlWorkRequest(Context context) {
        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        RemoteControlWorker.class, 30, TimeUnit.MINUTES
                ).build();

        WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                        TASK_ID,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                );
    }

    /**
     * Volume: 0 - 100
     */
    public static void setGlobalVolume(Context context, int volume) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) Math.ceil(streamMaxVolume / 100f * volume), 0);
            }
        }
    }

    /**
     * Volume: 0 - 100
     */
    public static int getGlobalVolume(Context context) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

                return (int) Math.ceil(streamVolume / (streamMaxVolume / 100f));
            }
        }

        return 100;
    }

    /**
     * <a href="https://stackoverflow.com/questions/2891337/turning-on-screen-programmatically">More info</a>
     */
    @SuppressWarnings("deprecation")
    private static void turnScreenOn(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setTurnScreenOn(true);
            } else {
                Window window = activity.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
        }
    }

    /**
     * Need to be the first line and executed on earliest stage once.<br/>
     * Inits media service language and context.<br/>
     * NOTE: this command should run before using any of the media service api.
     */
    public static void initGlobalData(Context context) {
        Log.d(TAG, "initGlobalData called...");

        // Auth token storage init
        GlobalPreferences.instance(context);
        // 1) Remove downloaded apks
        // 2) Setup language
        ViewManager.instance(context).clearCaches();
    }

    public static String toQrCodeLink(String data) {
        return String.format(QR_CODE_URL_TEMPLATE, data);
    }

    public static void openLink(Context context, String url) {
        try {
            openLinkInTabs(context, url);
        } catch (Exception e) {
            // Permission Denial on Android 9 (SecurityException)
            // Chrome Tabs not found (ActivityNotFoundException)
            Helpers.openLink(context, url); // revert to simple in-browser page
        }
    }

    /**
     * <a href="https://developer.chrome.com/docs/android/custom-tabs/integration-guide/">Chrome custom tabs</a>
     */
    private static void openLinkInTabs(Context context, String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(context, Uri.parse(url));
    }

    public static void postDelayed(Handler handler, Runnable callback, int delayMs) {
        handler.removeCallbacks(callback);
        handler.postDelayed(callback, delayMs);
    }

    public static void removeCallbacks(Handler handler, Runnable... callbacks) {
        if (callbacks == null) {
            return;
        }

        for (Runnable callback : callbacks) {
             handler.removeCallbacks(callback);
        }
    }
}
