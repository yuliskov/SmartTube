package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.liskovsoft.sharedutils.GlobalConstants;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlService;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlWorker;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final int RANDOM_FAIL_REPEAT_TIMES = 10;
    private static final String REMOTE_CONTROL_RECEIVER_CLASS_NAME = "com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlReceiver";
    private static final String UPDATE_CHANNELS_RECEIVER_CLASS_NAME = "com.liskovsoft.leanbackassistant.channels.UpdateChannelsReceiver";
    private static final String BOOTSTRAP_ACTIVITY_CLASS_NAME = "com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity";
    private static final String TASK_ID = RemoteControlWorker.class.getSimpleName();
    private static final String TAG = Utils.class.getSimpleName();
    private static final String QR_CODE_URL_TEMPLATE = "https://api.qrserver.com/v1/create-qr-code/?data=%s";
    private static final int GLOBAL_VOLUME_TYPE = AudioManager.STREAM_MUSIC;
    private static final String GLOBAL_VOLUME_SERVICE = Context.AUDIO_SERVICE;
    public static final Handler sHandler = new Handler(Looper.getMainLooper());
    public static final float[] SPEED_LIST_LONG =
            new float[]{0.25f, 0.5f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3.0f, 3.25f, 3.5f, 3.75f, 4.0f};
    public static final float[] SPEED_LIST_EXTRA_LONG = Helpers.range(0.05f, 4f, 0.05f);
    public static final float[] SPEED_LIST_SHORT =
            new float[] {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f, 3.25f, 3.5f, 3.75f, 4.0f};
    private static Boolean sGlobalVolumeFixed;
    private static int sCurrentVolume = -1;

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId) {
        displayShareVideoDialog(context, videoId, 0);
    }

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId, int posSec) {
        Uri videoUrl = convertToFullVideoUrl(videoId, posSec);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareEmbedVideoDialog(Context context, String videoId) {
        displayShareEmbedVideoDialog(context, videoId, 0);
    }

    @TargetApi(17)
    public static void displayShareEmbedVideoDialog(Context context, String videoId, int posSec) {
        Uri videoUrl = convertToEmbedVideoUrl(videoId, posSec);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareChannelDialog(Context context, String channelId) {
        Uri channelUrl = convertToFullChannelUrl(channelId);
        showMultiChooser(context, channelUrl);
    }

    @TargetApi(17)
    public static void openUrlInternally(Context context, Uri url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(url);
        intent.setPackage(context.getPackageName());
        //intent.setClass(context, ViewManager.instance(context).getActivity(SplashView.class));
        PackageManager packageManager = context.getPackageManager();
        if (intent.resolveActivity(packageManager) != null) {
            SplashPresenter.instance(context).applyNewIntent(intent);
            //context.startActivity(intent);
        } else {
            // Fallback to the chooser dialog
            showMultiChooser(context, url);
        }
    }

    @TargetApi(17)
    public static void showMultiChooser(Context context, Uri url) {
        Intent primaryIntent = new Intent(Intent.ACTION_VIEW);
        Intent secondaryIntent = new Intent(Intent.ACTION_SEND);
        primaryIntent.setData(url);
        secondaryIntent.putExtra(Intent.EXTRA_TEXT, url.toString());
        secondaryIntent.setType("text/plain");
        Intent chooserIntent = Intent.createChooser(primaryIntent, context.getResources().getText(R.string.share_link));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { secondaryIntent });
        chooserIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        try {
            context.startActivity(chooserIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Chooser intent not found", e);
        }
    }

    /**
     * https://youtu.be/nragduYePsQ?t=193<br/>
     * https://www.youtube.com/watch?v=nragduYePsQ&t=193
     */
    public static Uri convertToFullVideoUrl(String videoId, int posSec) {
        String url = String.format("https://youtu.be/%s?t=%s", videoId, posSec);
        return Uri.parse(url);
    }

    /**
     * https://www.youtube.com/embed/nragduYePsQ?start=193
     */
    public static Uri convertToEmbedVideoUrl(String videoId, int posSec) {
        String url = String.format("https://www.youtube.com/embed/%s?start=%s", videoId, posSec);
        return Uri.parse(url);
    }

    public static Uri convertToFullChannelUrl(String channelId) {
        String url = String.format("https://www.youtube.com/channel/%s", channelId);
        return Uri.parse(url);
    }

    public static boolean isAppInForegroundFixed() {
        // Skip situation when the splash presenter still running
        return Helpers.isAppInForeground() && SplashPresenter.instance(null).getView() == null;
    }

    /**
     * NOTE: Below won't help with "Can not perform this action after onSaveInstanceState"
     */
    public static boolean checkActivity(Activity activity) {
        return activity != null && !activity.isDestroyed() && !activity.isFinishing();
    }

    public static void updateRemoteControlService(Context context) {
        if (context == null || VERSION.SDK_INT <= 19) { // Eltex NPE fix
            return;
        }

        if (RemoteControlData.instance(context).isDeviceLinkEnabled()) {
            // Service that prevents the app from destroying
            startService(context, RemoteControlService.class);
        } else {
            stopService(context, RemoteControlService.class);
        }
    }

    private static void bindService(Context context, Intent serviceIntent) {
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
    private static void setGlobalVolume(Context context, int volume) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(GLOBAL_VOLUME_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(GLOBAL_VOLUME_TYPE);
                double newVolume = streamMaxVolume / 100d * volume;
                if (sCurrentVolume < volume) {
                    newVolume = Math.ceil(newVolume);
                } else {
                    newVolume = Math.floor(newVolume);
                }
                try {
                    audioManager.setStreamVolume(GLOBAL_VOLUME_TYPE, (int) newVolume, 0);
                    sCurrentVolume = volume;
                } catch (SecurityException | IllegalArgumentException e) {
                    // Not allowed to change Do Not Disturb state
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Volume: 0 - 100
     */
    private static int getGlobalVolume(Context context) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(GLOBAL_VOLUME_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(GLOBAL_VOLUME_TYPE);
                int streamVolume = audioManager.getStreamVolume(GLOBAL_VOLUME_TYPE);

                int volume = (int) Math.ceil(streamVolume / (streamMaxVolume / 100f));
                return Math.abs(sCurrentVolume - volume) == 1 ? sCurrentVolume : volume; // fix small steps (1). volume should be precise.
            }
        }

        return 100;
    }

    public static void initVolume(Context context) {
        if (sGlobalVolumeFixed != null) {
            return;
        }

        if ("D2150 (Nebula-Cosmos-Max)".equals(Helpers.getDeviceName())) { // A projector
            sGlobalVolumeFixed = true;
            return;
        }

        int volume = getGlobalVolume(context);
        setGlobalVolume(context, volume > 50 ? volume - 10 : volume + 10);
        sGlobalVolumeFixed = volume == getGlobalVolume(context);
        setGlobalVolume(context, volume);
    }

    /**
     * Global volume may not be supported (see FireTV Stick)
     */
    private static boolean isGlobalVolumeFixed(Context context) {
        if (sGlobalVolumeFixed != null) {
            return sGlobalVolumeFixed;
        }

        initVolume(context);
        return sGlobalVolumeFixed;
    }

    /**
     * Volume: 0 - 100
     */
    public static int getVolume(Context context, PlayerManager player) {
        if (context != null) {
            if (isGlobalVolumeFixed(context)) {
                return getPlayerVolume(player);
            } else {
                try {
                    return getGlobalVolume(context);
                } catch (SecurityException e) {
                    // Permission denial: writing to settings requires:android.permission.WRITE_SECURE_SETTINGS
                    return getPlayerVolume(player);
                }
            }
        }

        return 100;
    }

    private static int getPlayerVolume(PlayerManager player) {
        if (player == null) {
            return -1;
        }
        return (int)(player.getVolume() * 100);
    }

    /**
     * Volume: 0 - 100
     */
    public static void setVolume(Context context, PlayerManager player, int volume) {
        Log.d(TAG, "setVolume: %s", volume);

        if (volume < 0) {
            volume = 0;
        }

        if (context != null) {
            if (isGlobalVolumeFixed(context)) {
                setPlayerVolume(context, player, volume);
            } else {
                try {
                    setGlobalVolume(context, volume);
                    showSystemVolumeUI(context);
                } catch (SecurityException e) {
                    // Permission denial: writing to settings requires:android.permission.WRITE_SECURE_SETTINGS
                    setPlayerVolume(context, player, volume);
                }
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private static void setPlayerVolume(Context context, PlayerManager player, int volume) {
        if (player == null) {
            return;
        }
        player.setVolume(volume / 100f);
        MessageHelpers.showMessage(context, context.getString(R.string.volume, getPlayerVolume(player)));
    }

    public static void volumeUp(Context context, PlayerManager player, boolean up) {
        if (player != null) {
            int volume = getVolume(context, player);
            final int delta = 1; // volume step

            if (up) {
                setVolume(context, player, Math.min(volume + delta, 100));
            } else {
                setVolume(context, player, Math.max(volume - delta, 0));
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    public static void volumeUpPlayer(Context context, PlayerManager player, boolean up) {
        if (player != null) {
            int volume = (int) (player.getVolume() * 100);
            int round = 10 - volume % 10;
            if (round != 10) {
                volume += round;
            }
            final int delta = 10; // volume step

            int newVolume;

            if (up) {
                newVolume = Math.min(volume + delta, 300);
            } else {
                newVolume = Math.max(volume - delta, 0);
            }

            player.setVolume(newVolume / 100f);

            PlayerData.instance(context).setPlayerVolume(newVolume / 100f);

            // Check that volume is set.
            // Because global value may not be supported (see FireTV Stick).
            MessageHelpers.showMessage(context, context.getString(R.string.volume, (int) (player.getVolume() * 100)));
        }
    }

    public static void showSystemVolumeUI(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // Show the system volume bar without changing the volume
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, // Target the music stream
                    AudioManager.ADJUST_SAME, // No actual adjustment
                    AudioManager.FLAG_SHOW_UI // This flag displays the volume UI
            );
        }
    }

    public static void registerAudioObserver(Context context, ContentObserver observer) {
        context.getApplicationContext().getContentResolver()
                .registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer);
    }

    public static void unregisterAudioObserver(Context context, ContentObserver observer) {
        context.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
    }

    /**
     * <a href="https://stackoverflow.com/questions/2891337/turning-on-screen-programmatically">More info</a>
     */
    @SuppressWarnings("deprecation")
    public static void turnScreenOn(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true);
                activity.setTurnScreenOn(true);
                KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguardManager != null) {
                    keyguardManager.requestDismissKeyguard(activity, null);
                }
            } else {
                Window window = activity.getWindow();
                window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                );
            }
        }
    }

    public static String toQrCodeLink(String data) {
        return String.format(QR_CODE_URL_TEMPLATE, data);
    }

    public static void openLink(Context context, String url) {
        try {
            WebBrowserPresenter.instance(context).loadUrl(url);
        } catch (Exception e) {
            // WebView not found. Use alt method.
            openLinkExt(context, url);
        }
    }

    public static void openLinkExt(Context context, String url) {
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

    public static void postDelayed(Runnable callback, long delayMs) {
        if (callback == null) {
            return;
        }

        sHandler.removeCallbacks(callback);
        sHandler.postDelayed(callback, delayMs);
    }

    public static void post(Runnable callback) {
        if (callback == null) {
            return;
        }

        sHandler.removeCallbacks(callback);
        sHandler.post(callback);
    }

    public static void removeCallbacks(Runnable... callbacks) {
        if (callbacks == null) {
            return;
        }

        for (Runnable callback : callbacks) {
            if (callback != null) {
                sHandler.removeCallbacks(callback);
            }
        }
    }

    public static CharSequence color(CharSequence string, int color, int start, int end) {
        SpannableString spannable = new SpannableString(string);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(color);
        spannable.setSpan(foregroundColorSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    public static CharSequence color(CharSequence string, int color) {
        SpannableString spannable = new SpannableString(string);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(color);
        spannable.setSpan(foregroundColorSpan, 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    /**
     * NOTE: Android 5.1: Italic cause crashes with Arabic fonts
     */
    public static CharSequence italic(CharSequence string) {
        if (Build.VERSION.SDK_INT <= 22) {
            return string;
        }

        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public static CharSequence bold(CharSequence string) {
        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public static CharSequence icon(Context context, int resId, int lineHeight) {
        SpannableString spannable = new SpannableString(" ");
        Drawable drawable = ContextCompat.getDrawable(context, resId);
        drawable.setBounds(0, 0, lineHeight, lineHeight);
        ImageSpan imageSpan = new ImageSpan(drawable);
        spannable.setSpan(imageSpan, 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    @SuppressWarnings("deprecation")
    public static boolean isServiceRunning(Context context, Class<? extends Service> serviceClass) {
        List<RunningServiceInfo> services = getRunningServices(context);

        if (services == null) {
            return false;
        }

        for (RunningServiceInfo service : services) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static List<RunningServiceInfo> getRunningServices(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        try {
            return manager.getRunningServices(Integer.MAX_VALUE);
        } catch (NullPointerException e) {
            // NullPointerException: Attempt to invoke interface method 'java.lang.Object android.os.Parcelable$Creator.createFromParcel(android.os.Parcel)' on a null object reference
            e.printStackTrace();
        }

        return null;
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    public static Notification createNotification(Context context, int iconResId, String title, Class<? extends Activity> activityCls) {
        return createNotification(context, iconResId, title, null, activityCls);
    }

    @SuppressWarnings("deprecation")
    public static Notification createNotification(Context context, int iconResId, String title, String content, Class<? extends Activity> activityCls) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(iconResId)
                        .setContentTitle(title);

        if (content != null) {
            builder.setContentText(content);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= 23) {
            // IllegalArgumentException fix: Targeting S+ (version 31 and above) requires that one of FLAG_IMMUTABLE...
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent targetIntent = new Intent(context, activityCls);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, targetIntent, flags);
        builder.setContentIntent(contentIntent);

        if (VERSION.SDK_INT >= 26) {
            String channelId = context.getPackageName();
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    title,
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        return builder.build();
    }

    public static void showNotification(Context context, int notificationId, Notification notification) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

    public static void startService(Context context, Class<? extends Service> serviceCls) {
        if (isServiceRunning(context, serviceCls)) {
            return;
        }

        Intent serviceIntent = new Intent(context, serviceCls);

        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        if (VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static void stopService(Context context, Class<? extends Service> serviceCls) {
        if (!isServiceRunning(context, serviceCls)) {
            return;
        }

        Intent serviceIntent = new Intent(context, serviceCls);

        context.stopService(serviceIntent);
    }

    public static void showRepeatInfo(Context context, int modeIndex) {
        switch (modeIndex) {
            case PlayerConstants.PLAYBACK_MODE_ALL:
                MessageHelpers.showMessage(context, R.string.repeat_mode_all);
                break;
            case PlayerConstants.PLAYBACK_MODE_ONE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_one);
                break;
            case PlayerConstants.PLAYBACK_MODE_PAUSE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_pause);
                break;
            case PlayerConstants.PLAYBACK_MODE_LIST:
                MessageHelpers.showMessage(context, R.string.repeat_mode_pause_alt);
                break;
            case PlayerConstants.PLAYBACK_MODE_CLOSE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_none);
                break;
        }
    }

    /**
     * NOTE: Doesn't work in Android 13<br/>
     * java.lang.SecurityException: Injecting input events requires the caller (or the source of the instrumentation, if any) to have the INJECT_EVENTS permission.
     */
    public static void sendKey(int key) {
        if (VERSION.SDK_INT < 33) {
            try {
                Instrumentation instrumentation = new Instrumentation();
                instrumentation.sendKeyDownUpSync(key);
            } catch (SecurityException e) {
                // Injecting to another application requires INJECT_EVENTS permission
                e.printStackTrace();
            }
        }
    }

    /**
     * NOTE: Doesn't work in Android 13<br/>
     * java.lang.SecurityException: Injecting input events requires the caller (or the source of the instrumentation, if any) to have the INJECT_EVENTS permission.
     */
    public static void sendKey(KeyEvent keyEvent) {
        if (VERSION.SDK_INT < 33) {
            try {
                Instrumentation instrumentation = new Instrumentation();
                instrumentation.sendKeySync(keyEvent);
            } catch (SecurityException e) {
                // Injecting to another application requires INJECT_EVENTS permission
                e.printStackTrace();
            }
        }
    }

    public static void sendKey(Activity activity, int keyCode) {
        BaseInputConnection  inputConnection = new BaseInputConnection(activity.getWindow().getDecorView().getRootView(), true);
        KeyEvent kd = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent ku = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        inputConnection.sendKeyEvent(kd);
        inputConnection.sendKeyEvent(ku);
    }

    public static void showNotCompatibleMessage(Context context, int msgResId) {
        MessageHelpers.showMessage(context, String.format("%s '%s'",
                context.getString(R.string.not_compatible_with),
                context.getString(msgResId)));
    }

    public static String getCountryFlagUrl(String countryCode) {
        // Sometimes down
        //return "https://countryflagsapi.com/png/" + countryCode;

        // https://flagpedia.net/download/api
        return String.format("https://flagcdn.com/w160/%s.png", countryCode.toLowerCase());
    }

    public static void showPlayerControls(Context context, boolean show) {
        PlaybackView view = PlaybackPresenter.instance(context).getView();
        if (view != null) {
            view.showOverlay(show);
        }
    }

    public static int toSec(long ms) {
        return (int) (ms / 1_000);
    }

    public static boolean isFirstRun(Context context) {
        VideoStateService stateService = VideoStateService.instance(context);

        return stateService.isEmpty();
    }

    public static boolean isPresetSupported(VideoPreset preset) {
        if (preset.isVP9Preset() && !Helpers.isVP9ResolutionSupported(preset.getHeight())) {
            return false;
        }

        if (preset.isAV1Preset() && !Helpers.isAV1ResolutionSupported(preset.getHeight())) {
            return false;
        }

        return true;
    }

    public static boolean isFormatSupported(MediaTrack mediaTrack) {
        if (mediaTrack.isVP9Codec() && !Helpers.isVP9ResolutionSupported(TrackSelectorUtil.getRealHeight(mediaTrack.format))) {
            return false;
        }

        if (mediaTrack.isAV1Codec() && !Helpers.isAV1ResolutionSupported(TrackSelectorUtil.getRealHeight(mediaTrack.format))) {
            return false;
        }

        // There's a bug. The player hangs at the black screen.
        // opus and others audio codecs require hardware acceleration
        //if (mediaTrack instanceof AudioTrack && !mediaTrack.isMP4ACodec() && !Helpers.isVP9Supported()) {
        //    return false;
        //}

        return true;
    }

    public static void enableScreensaver(Context activity, boolean enable) {
        if (activity instanceof MotherActivity) {
            ScreensaverManager screensaver = ((MotherActivity) activity).getScreensaverManager();
            if (enable) {
                screensaver.enable();
            } else {
                screensaver.disable();
            }
        }
    }

    public static boolean isScreenOff(Context activity) {
        if (activity instanceof MotherActivity) {
            ScreensaverManager manager = ((MotherActivity) activity).getScreensaverManager();

            return manager != null && manager.isScreenOff();
        }

        return false;
    }

    public static boolean isHardScreenOff(Context context) {
        if (context == null) {
            return false;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        
        if (Build.VERSION.SDK_INT < 20) {
            return !pm.isScreenOn();
        } else {
            return !pm.isInteractive();
        }
    }

    public static int getColor(Context context, int colorResId, int dimPercents) {
        int color = ContextCompat.getColor(context, colorResId);
        color = ColorUtils.setAlphaComponent(color, (int)(255f / 100 * dimPercents));

        return color;
    }

    /**
     * https://stackoverflow.com/questions/11288147/get-resources-from-another-apk
     */
    public static Drawable getDrawable(Context context, String packageName, String drawableName) {
        if (context == null || packageName == null || drawableName == null) {
            return null;
        }

        Drawable result = null;

        try {
            PackageManager manager = context.getPackageManager();
            Resources resources = manager.getResourcesForApplication(packageName);
            int drawableResId = resources.getIdentifier(drawableName, "drawable", packageName);

            if (drawableResId == 0) {
                drawableResId = resources.getIdentifier(drawableName, "mipmap", packageName);
            }

            result = resources.getDrawable(drawableResId);
        } catch (NameNotFoundException | NotFoundException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static boolean isOculusQuest() {
        return Helpers.getDeviceName().startsWith("Oculus Quest");
    }

    /**
     * Finish the app but remain running services
     */
    public static void properlyFinishTheApp(Context context) {
        ViewManager.instance(context).properlyFinishTheApp(context);
        //forceFinishTheApp();
    }

    /**
     * Simply kills the app.
     */
    public static void forceFinishTheApp() {
        Runtime.getRuntime().exit(0);
    }

    public static void updateChannels(Context context) {
        startReceiver(context, UPDATE_CHANNELS_RECEIVER_CLASS_NAME);
    }

    public static void startRemoteControl(Context context) {
        startReceiver(context, REMOTE_CONTROL_RECEIVER_CLASS_NAME);
    }

    public static void restartTheApp(Context context, Intent intent) {
        ProcessPhoenix.triggerRebirth(context, intent);
    }

    public static void restartTheApp(Context context) {
        try {
            Intent intent = new Intent(context, Class.forName(BOOTSTRAP_ACTIVITY_CLASS_NAME));
            intent.putExtra(GlobalConstants.INTERNAL_INTENT, true);
            ProcessPhoenix.triggerRebirth(context, intent);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void restartTheApp(Context context, String videoId) {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=" + videoId),
                    context,
                    Class.forName(BOOTSTRAP_ACTIVITY_CLASS_NAME)
            );
            intent.putExtra(GlobalConstants.INTERNAL_INTENT, true);
            ProcessPhoenix.triggerRebirth(context, intent);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void startReceiver(Context context, String receiverClassName) {
        // Can't use class directly! ATV module is disabled for some flavors.
        Class<?> clazz = null;

        try {
            clazz = Class.forName(receiverClassName);
        } catch (ClassNotFoundException e) {
            // NOP
        }

        if (clazz != null) {
            if (context != null) {
                Log.d(TAG, "Starting channels receiver...");
                Intent intent = new Intent(context, clazz);
                try {
                    context.sendBroadcast(intent);
                } catch (Exception e) {
                    // NullPointerException on MX9Pro (rk3328  7.1.2)
                }
            }
        } else {
            Log.e(TAG, "Channels receiver class not found: " + receiverClassName);
        }
    }

    private static void exitToHome(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * More info: https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
     */
    private static void triggerRebirth(Context context, Class<?> rootActivity) {
        Intent intent = new Intent(context, rootActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        if (context instanceof MotherActivity) {
            ((MotherActivity) context).finishReally();
        }
        Runtime.getRuntime().exit(0);
    }

    /**
     * More info: https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
     */
    private static void triggerRebirth2(Context context, Class<?> rootActivity) {
        Intent mStartActivity = new Intent(context, rootActivity);
        int mPendingIntentId = 123456;
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            // IllegalArgumentException fix: Targeting S+ (version 31 and above) requires that one of FLAG_IMMUTABLE...
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent mPendingIntent = PendingIntent.getActivity(context, mPendingIntentId, mStartActivity, flags);
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    public static void triggerRebirth3(Context context, Class<?> myClass) {
        Intent intent = new Intent(context, myClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        Runtime.getRuntime().exit(0);
    }

    public static String updateTooltip(Context context, String tooltip) {
        return GeneralData.instance(context).isFirstUseTooltipEnabled() ?
                String.format("%s (%s)", tooltip, context.getString(R.string.long_press_for_options)) : tooltip;
    }

    private static String createTransactionID() {
        return UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
    }

    /**
     * https://stackoverflow.com/a/5626208/1279056<br/>
     * https://stackoverflow.com/a/40237325/1279056
     */
    public static String getUniqueId(Context context) {
        String uniqueId = HiddenPrefs.instance(context).getUniqueId();

        if (uniqueId == null) {
            UUID uuid = null;
            @SuppressLint("HardwareIds")
            final String androidId = Secure.getString(
                    context.getContentResolver(), Secure.ANDROID_ID);
            // Use the Android ID unless it's broken, in which case
            // fallback on deviceId,
            // unless it's not available, then fallback on a random
            // number which we store to a prefs file
            try {
                if (!"9774d56d682e549c".equals(androidId)) {
                    uuid = UUID.nameUUIDFromBytes(androidId
                            .getBytes("utf8"));
                } else {
                    @SuppressLint("HardwareIds")
                    final String deviceId = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
                    uuid = deviceId != null ? UUID
                            .nameUUIDFromBytes(deviceId
                                    .getBytes("utf8")) : UUID
                            .randomUUID();
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, e.getMessage());
            }

            uniqueId = uuid != null ? uuid.toString() : createTransactionID();
            HiddenPrefs.instance(context).setUniqueId(uniqueId);
        }

        return uniqueId;
    }

    public static <T> boolean chainProcess(List<T> listeners, ChainProcessor<T> processor) {
        boolean result = false;

        for (T listener : listeners) {
            result = processor.process(listener);

            if (result) {
                break;
            }
        }

        return result;
    }

    public interface ChainProcessor<T> {
        boolean process(T listener);
    }

    public static <T> void process(List<T> listeners, Processor<T> processor) {
        for (T listener : listeners) {
            processor.process(listener);
        }
    }

    public interface Processor<T> {
        void process(T listener);
    }

    public static boolean skipCronet() {
        // Android 6 and below may crash running Cronet???
        return VERSION.SDK_INT <= 23 || Helpers.equals(BuildConfig.FLAVOR, "strtarmenia");
    }

    public static boolean isEnoughRam(Context context) {
        return VERSION.SDK_INT > 21 && Helpers.getDeviceRam(context) > 1_500_000_000; // 1.5 GB
    }

    public static String getStackTraceAsString(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        result.append(throwable.getMessage()).append("\n");
        StackTraceElement[] elements = throwable.getStackTrace();
        if (elements.length > 0) {
            StackTraceElement topElement = elements[0];
            result.append(topElement.getMethodName());
            result.append("(").append(topElement.getFileName()).append(":").append(topElement.getLineNumber()).append(")");
        } else {
            result.append("No stack trace available");
        }

        return result.toString();
    }

    public static int getRandomIndex(int currentIdx, int playlistSize) {
        if (playlistSize <= 1) {
            return -1;
        }

        int randomIndex = -1;

        for (int i = 0; i < RANDOM_FAIL_REPEAT_TIMES; i++) {
            randomIndex = Helpers.getRandomIndex(playlistSize);
            if (randomIndex != currentIdx) {
                break;
            }
        }

        return randomIndex;
    }
}
