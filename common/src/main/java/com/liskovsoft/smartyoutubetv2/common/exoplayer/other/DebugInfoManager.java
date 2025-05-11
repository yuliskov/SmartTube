package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build.VERSION;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Pair;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.UhdHelper;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.app.models.AppInfo;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.chromium.net.ApiVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// NOTE: original file taken from
// https://github.com/google/ExoPlayer/blob/release-v2/library/ui/src/main/java/com/google/android/exoplayer2/ui/DebugTextViewHelper.java

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * a {@link SimpleExoPlayer}.
 */
public final class DebugInfoManager implements Runnable, Player.EventListener {
    private static final String TAG = DebugInfoManager.class.getSimpleName();
    private static final int REFRESH_INTERVAL_MS = 1000;
    private static final String NOT_AVAILABLE = "none";
    private final float mTextSize;

    private final SimpleExoPlayer mPlayer;
    private final ViewGroup mDebugViewGroup;
    private final Activity mContext;

    private boolean mStarted;
    private LinearLayout column1;
    private LinearLayout column2;
    private UhdHelper mUhdHelper;
    private final List<Pair<String, String>> mVideoInfo = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayModeId = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayInfo = new ArrayList<>();
    private final String mAppVersion;

    /**
     * @param activity context
     * @param player   The {@link SimpleExoPlayer} from which debug information should be obtained.
     * @param resLayoutId The {@link TextView} that should be updated to display the information.
     */
    public DebugInfoManager(Activity activity, SimpleExoPlayer player, int resLayoutId) {
        mPlayer = player;
        mDebugViewGroup = activity.findViewById(resLayoutId);
        mContext = activity;
        mTextSize = activity.getResources().getDimension(R.dimen.debug_text_size);
        mAppVersion = String.format("%s Version", mContext.getString(R.string.app_name));
        inflate();
    }

    private void inflate() {
        mDebugViewGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(R.layout.debug_view, mDebugViewGroup, true);
        column1 = mDebugViewGroup.findViewById(R.id.debug_view_column1);
        column2 = mDebugViewGroup.findViewById(R.id.debug_view_column2);
    }

    public void show(boolean show) {
        if (show) {
            create();
        } else {
            destroy();
        }
    }

    public boolean isShown() {
        return mStarted;
    }

    /**
     * Starts periodic updates of the {@link TextView}. Must be called from the application's main
     * thread.
     */
    private void create() {
        if (mStarted) {
            return;
        }

        mStarted = true;
        mDebugViewGroup.setVisibility(View.VISIBLE);
        mUhdHelper = new UhdHelper(mContext);
        mPlayer.addListener(this);
        updateAndPost();
    }

    /**
     * Stops periodic updates of the {@link TextView}. Must be called from the application's main
     * thread.
     */
    private void destroy() {
        if (!mStarted) {
            return;
        }

        mStarted = false;
        mDebugViewGroup.setVisibility(View.GONE);
        mPlayer.removeListener(this);
        mDebugViewGroup.removeCallbacks(this);
        mUhdHelper = null;
    }

    // Player.EventListener implementation.

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // NOP
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // NOP
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // NOP
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        // NOP
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // NOP
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        // Do nothing.
    }

    @Override
    public void onTracksChanged(TrackGroupArray tracks, TrackSelectionArray selections) {
        // NOP
    }

    // Runnable implementation.

    @Override
    public void run() {
        updateAndPost();
    }

    // Private methods.

    @SuppressLint("SetTextI18n")
    private void updateAndPost() {
        column1.removeAllViews();
        column2.removeAllViews();

        updateRareChangedValues();

        appendVideoInfo();
        appendRuntimeInfo();
        appendPlayerState();
        appendDisplayInfo();
        appendDisplayModeId();
        //appendPlayerWindowIndex();
        appendVersion();
        appendDeviceNameSDKCache();
        appendMemoryInfo();
        appendWebViewInfo();
        appendVideoInfoType();
        appendVideoInfoVersion();

        // Schedule next update
        mDebugViewGroup.removeCallbacks(this);
        mDebugViewGroup.postDelayed(this, REFRESH_INTERVAL_MS);
    }

    private void updateRareChangedValues() {
        updateVideoInfo();
        updateDisplayModeId();
        updateDisplayInfo();
    }

    private void appendVideoInfo() {
        for (Pair<String, String> pair : mVideoInfo) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateVideoInfo() {
        mVideoInfo.clear();

        Format video = mPlayer.getVideoFormat();
        Format audio = mPlayer.getAudioFormat();
        if (video == null || audio == null) {
            return;
        }

        String videoRes = getVideoResolution(video);

        mVideoInfo.add(new Pair<>("Video Resolution", videoRes));
        mVideoInfo.add(new Pair<>("Video/Audio Codecs", String.format(
                "%s(%s)/%s(%s)",
                getFormatMimeType(video),
                getFormatId(video),
                getFormatMimeType(audio),
                getFormatId(audio)
        )));
        mVideoInfo.add(new Pair<>("Video/Audio Bitrate", String.format(
                "%s/%s",
                toHumanReadable(video.bitrate),
                toHumanReadable(audio.bitrate)
        )));
        // Aspect info is not valid since we're using custom views
        //String par = video.pixelWidthHeightRatio == Format.NO_VALUE ||
        //        video.pixelWidthHeightRatio == 1f ?
        //        DEFAULT : String.format(Locale.US, "%.02f", video.pixelWidthHeightRatio);
        //mVideoInfo.add(new Pair<>("Aspect Ratio", par));
        String videoCodecName = getVideoDecoderNameV2();
        mVideoInfo.add(new Pair<>("Video Decoder Name", videoCodecName));
        mVideoInfo.add(new Pair<>("Hardware Accelerated", String.valueOf(Helpers.isHardwareAccelerated(videoCodecName))));
    }

    private void appendRuntimeInfo() {
        DecoderCounters counters = mPlayer.getVideoDecoderCounters();
        if (counters == null)
            return;

        counters.ensureUpdated();
        appendRow("Dropped/Rendered Frames", counters.droppedBufferCount + "/" + counters.renderedOutputBufferCount);
        appendRow("Buffer size (seconds)", (int)(mPlayer.getBufferedPosition() - mPlayer.getCurrentPosition()) / 1_000);
    }

    private void appendPlayerState() {
        appendRow("Player Paused", !mPlayer.getPlayWhenReady());

        String text;
        switch (mPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                text = "buffering";
                break;
            case Player.STATE_ENDED:
                text = "ended";
                break;
            case Player.STATE_IDLE:
                text = "idle";
                break;
            case Player.STATE_READY:
                text = "ready";
                break;
            default:
                text = "unknown";
                break;
        }
        appendRow("Playback State", text);
    }

    private void appendDisplayModeId() {
        for (Pair<String, String> pair : mDisplayModeId) {
            appendRow(pair.first, pair.second);
        }
    }

    //private void updateDisplayModeId() {
    //    mDisplayModeId.clear();
    //
    //    Mode currentMode = mUhdHelper.getCurrentMode();
    //    Mode[] supportedModes = mUhdHelper.getSupportedModes();
    //
    //    boolean isAfrSupported = currentMode != null && supportedModes != null && supportedModes.length > 1;
    //
    //    mDisplayModeId.add(new Pair<>("Software Auto Frame Rate", isAfrSupported ? "supported" : NOT_AVAILABLE));
    //
    //    if (isAfrSupported) {
    //        String bootResolution = AppPrefs.instance(mContext).getBootResolution();
    //        String currentResolution = UhdHelper.toResolution(currentMode);
    //        currentResolution = currentResolution != null ? currentResolution : bootResolution;
    //
    //        mDisplayModeId.add(new Pair<>("Current/UI Resolution", currentResolution));
    //        mDisplayModeId.add(new Pair<>("Boot Resolution", bootResolution != null ? bootResolution : NOT_AVAILABLE));
    //
    //        mDisplayModeId.add(new Pair<>("Display Mode ID", String.valueOf(currentMode.getModeId())));
    //        mDisplayModeId.add(new Pair<>("Display Modes Length", String.valueOf(supportedModes.length)));
    //    }
    //}

    private void updateDisplayModeId() {
        if (mUhdHelper == null) {
            return;
        }

        mDisplayModeId.clear();

        Mode currentMode = mUhdHelper.getCurrentMode();
        Mode[] supportedModes = mUhdHelper.getSupportedModes();

        String bootResolution = AppPrefs.instance(mContext).getBootResolution();
        String currentResolution = UhdHelper.toResolution(currentMode);

        mDisplayModeId.add(new Pair<>("UI Resolution", currentResolution != null ? currentResolution : NOT_AVAILABLE));
        mDisplayModeId.add(new Pair<>("Boot Resolution", bootResolution != null ? bootResolution : NOT_AVAILABLE));

        mDisplayModeId.add(new Pair<>("Display Mode ID", currentMode != null ? String.valueOf(currentMode.getModeId()) : NOT_AVAILABLE));
        mDisplayModeId.add(new Pair<>("Display Modes Length", supportedModes != null ? String.valueOf(supportedModes.length) : NOT_AVAILABLE));
    }

    private void appendDisplayInfo() {
        for (Pair<String, String> pair : mDisplayInfo) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateDisplayInfo() {
        mDisplayInfo.clear();

        mDisplayInfo.add(new Pair<>("Display dpi", String.valueOf(Helpers.getDeviceDpi(mContext))));
    }

    private void appendPlayerWindowIndex() {
        appendRow("Window Index", mPlayer.getCurrentWindowIndex());
    }

    private void appendVersion() {
        appendRow("ExoPlayer Version", ExoPlayerLibraryInfo.VERSION);
        appendRow("ExoPlayer DataSource",
                PlayerTweaksData.instance(mContext).getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP ? "OkHttp" :
                        PlayerTweaksData.instance(mContext).getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET ? "Cronet" :
                        "Default");
        appendRow("Cronet version", ApiVersion.getCronetVersion());
        //appendRow("OkHttp version", Version.userAgent());
        appendRow(mAppVersion, AppInfoHelpers.getAppVersionName(mContext));
    }

    private void appendDeviceNameSDKCache() {
        appendRow("Device Name", Helpers.getDeviceName());
        appendRow("Android SDK", VERSION.SDK_INT);
        appendRow("Disk cache size (MB)", String.valueOf(
                (FileHelpers.getDirSize(FileHelpers.getInternalCacheDir(mContext)) + FileHelpers.getDirSize(FileHelpers.getExternalCacheDir(mContext)))
                        / 1024 / 1024
        ));
    }

    private void appendMemoryInfo() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long allocatedMemory = Runtime.getRuntime().totalMemory();
        appendRow("Memory Limit (MB)", (int)(maxMemory / (1024 * 1024))); // Growth Limit
        appendRow("Allocated Memory (MB)", (int)(allocatedMemory / (1024 * 1024)));
    }

    private void appendWebViewInfo() {
        appendRow("WebView supported", MediaServiceData.instance().supportsWebView());
    }

    private void appendVideoInfoType() {
        Pair<Integer, Boolean> videoInfoType = MediaServiceData.instance().getVideoInfoType();
        appendRow("Video info type", videoInfoType != null ? videoInfoType.first : -1);
    }

    private void appendVideoInfoVersion() {
        AppInfo appInfo = Helpers.firstNonNull(MediaServiceData.instance().getFailedAppInfo(), MediaServiceData.instance().getAppInfo());
        String playerUrl = appInfo != null ? appInfo.getPlayerUrl() : null;
        if (playerUrl != null) {
            String playerVersion = UrlQueryStringFactory.parse(Uri.parse(playerUrl)).get("player");
            boolean isFailed = MediaServiceData.instance().getFailedAppInfo() != null;
            appendRow("Video info version", isFailed ? Utils.color(playerVersion, Color.RED) : playerVersion);
        }
    }

    private void appendRow(String name, boolean val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendRow(String name, CharSequence val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendRow(String name, int val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendNameColumn(TextView content) {
        content.setGravity(Gravity.END);
        column1.addView(content);
    }

    private void appendValueColumn(TextView content) {
        column2.addView(content);
    }

    private TextView createTextView(CharSequence name) {
        TextView textView = new TextView(mContext);
        textView.setText(name);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private TextView createTextView(boolean val) {
        TextView textView = new TextView(mContext);
        textView.setText(String.valueOf(val));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private TextView createTextView(int val) {
        TextView textView = new TextView(mContext);
        textView.setText(String.valueOf(val));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private String toHumanReadable(int bitrate) {
        if (bitrate < 0) {
            return NOT_AVAILABLE;
        }

        float mbit = ((float) bitrate) / 1_000_000;
        return String.format(Locale.ENGLISH, "%.2fMbps", mbit);
    }

    private String getFormatId(Format video) {
        return video.id;
    }

    private String getFormatMimeType(Format video) {
        if (video == null || video.sampleMimeType == null) {
            return null;
        }

        return video.sampleMimeType.replace("video/", "").replace("audio/", "");
    }

    private String getVideoResolution(Format video) {
        String result = video.width + "x" + video.height;
        if (video.frameRate > 0) {
            result += "@" + ((int) video.frameRate);
        }
        return result;
    }

    // NOTE: Be aware. This info isn't real! It's like caps or something like that. To get real info use method below.
    private String getVideoDecoderNameV1(Format format) {
        if (format == null) {
            return null;
        }

        MediaCodecInfo info = ExoUtils.getCapsDecoderInfo(format.sampleMimeType);

        return info != null ? info.name : null;
    }

    private String getVideoDecoderNameV2() {
        return ExoUtils.getVideoDecoderName();
    }

    private String getRawDisplayResolution() {
        Display display = mContext.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        float refreshRate = display.getRefreshRate();

        return String.format("%sx%s@%s", size.x, size.y, refreshRate);
    }

    /**
     * Override to hardcoded physical resolution
     */
    private String overrideResolution(String resolution) {
        switch (Helpers.getDeviceName()) {
            case "BRAVIA 4K UR3 (BRAVIA_UR3_EU)":
                return "3840x2160@120";
        }

        return resolution;
    }
}
