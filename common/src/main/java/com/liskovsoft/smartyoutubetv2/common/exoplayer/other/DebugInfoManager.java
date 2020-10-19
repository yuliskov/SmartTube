package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.util.Pair;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.UhdHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

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
    private static final String DEFAULT = "default";
    private float mTextSize = 15;

    private final SimpleExoPlayer mPlayer;
    private final ViewGroup mDebugViewGroup;
    private final Activity mContext;

    private boolean started;
    private LinearLayout column1;
    private LinearLayout column2;
    private UhdHelper mUhdHelper;
    private final List<Pair<String, String>> mVideoInfo = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayModeId = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayInfo = new ArrayList<>();
    private final String mAppVersion;

    /**
     * @param player   The {@link SimpleExoPlayer} from which debug information should be obtained.
     * @param resLayoutId The {@link TextView} that should be updated to display the information.
     * @param ctx context
     */
    public DebugInfoManager(SimpleExoPlayer player, int resLayoutId, Activity ctx) {
        mPlayer = player;
        mDebugViewGroup = ctx.findViewById(resLayoutId);
        mContext = ctx;
        mTextSize = ctx.getResources().getDimension(R.dimen.debug_text_size);
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

    /**
     * Starts periodic updates of the {@link TextView}. Must be called from the application's main
     * thread.
     */
    private void create() {
        if (started) {
            return;
        }

        started = true;
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
        if (!started) {
            return;
        }

        started = false;
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
        appendDisplayModeId();
        appendDisplayInfo();
        //appendPlayerWindowIndex();
        appendVersion();
        appendDeviceName();

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
                "%s%s/%s%s",
                video.sampleMimeType.replace("video/", ""),
                getFormatId(video),
                audio.sampleMimeType.replace("audio/", ""),
                getFormatId(audio)
        )));
        mVideoInfo.add(new Pair<>("Video/Audio Bitrate", String.format(
                "%s/%s",
                toHumanReadable(video.bitrate),
                toHumanReadable(audio.bitrate)
        )));
        String par = video.pixelWidthHeightRatio == Format.NO_VALUE ||
                video.pixelWidthHeightRatio == 1f ?
                DEFAULT : String.format(Locale.US, "%.02f", video.pixelWidthHeightRatio);
        mVideoInfo.add(new Pair<>("Aspect Ratio", par));
        mVideoInfo.add(new Pair<>("Hardware Accelerated", String.valueOf(isHardwareAccelerated(video))));
    }

    /**
     * <a href="https://github.com/google/ExoPlayer/issues/4757">More info</a>
     * @param format format
     * @return is accelerated
     */
    private boolean isHardwareAccelerated(Format format) {
        if (format == null) {
            return false;
        }

        try {
            // Ver 2.10.4
            MediaCodecInfo info = MediaCodecUtil.getDecoderInfo(format.sampleMimeType, false, false);

            // Ver 2.9.6
            //MediaCodecInfo info = MediaCodecUtil.getDecoderInfo(format.sampleMimeType, false);

            if (info == null) {
                return false;
            }

            for (String name : new String[]{"omx.google.", "c2.android."}) {
                if (info.name.toLowerCase().startsWith(name)) {
                    return false;
                }
            }
        } catch (DecoderQueryException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void appendRuntimeInfo() {
        DecoderCounters counters = mPlayer.getVideoDecoderCounters();
        if (counters == null)
            return;

        counters.ensureUpdated();
        appendRow("Dropped/Rendered Frames",
                counters.droppedBufferCount + counters.skippedOutputBufferCount
                    + "/" +
                    counters.renderedOutputBufferCount);
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

    private void updateDisplayModeId() {
        mDisplayModeId.clear();

        Mode currentMode = mUhdHelper.getCurrentMode();
        mDisplayModeId.add(new Pair<>("Display Mode ID", currentMode != null ? String.valueOf(currentMode.getModeId()) : NOT_AVAILABLE));

        Mode[] supportedModes = mUhdHelper.getSupportedModes();
        mDisplayModeId.add(new Pair<>("Display Modes Length", supportedModes != null ? String.valueOf(supportedModes.length) : NOT_AVAILABLE));
    }

    private void appendDisplayInfo() {
        for (Pair<String, String> pair : mDisplayInfo) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateDisplayInfo() {
        mDisplayInfo.clear();

        String defaultMode = AppPrefs.instance(mContext).getDefaultDisplayMode();
        defaultMode = defaultMode != null ? defaultMode : NOT_AVAILABLE;
        String currentMode = AppPrefs.instance(mContext).getCurrentDisplayMode();
        currentMode = currentMode != null ? currentMode : defaultMode;
        mDisplayInfo.add(new Pair<>("Display dpi", String.valueOf(Helpers.getDeviceDpi(mContext))));
        mDisplayInfo.add(new Pair<>("Display Resolution", currentMode));
        mDisplayInfo.add(new Pair<>("Default Resolution", defaultMode));
    }

    private void appendPlayerWindowIndex() {
        appendRow("Window Index", mPlayer.getCurrentWindowIndex());
    }

    private void appendVersion() {
        appendRow("ExoPlayer Version", ExoPlayerLibraryInfo.VERSION);
        appendRow(mAppVersion, AppInfoHelpers.getAppVersionName(mContext));
    }

    private void appendDeviceName() {
        appendRow("Device Name", Helpers.getDeviceName());
    }

    private void appendRow(String name, boolean val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendRow(String name, String val) {
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

    private TextView createTextView(String name) {
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
        return String.format(Locale.ENGLISH, "%.2fMbit", mbit);
    }

    private String getFormatId(Format video) {
        String id = video.id;
        if (Helpers.isNumeric(id)) {
            return String.format("(%s)", id);
        }
        return "";
    }

    private String getVideoResolution(Format video) {
        String result = video.width + "x" + video.height;
        if (video.frameRate > 0) {
            result += "@" + ((int) video.frameRate);
        }
        return result;
    }
}
