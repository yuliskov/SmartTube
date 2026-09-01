package com.liskovsoft.smartyoutubetv2.tv.ui.debug;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.exoplayer2.source.sabr.SabrLiveFeatureFlags;
import com.liskovsoft.mediaserviceinterfaces.data.PlaybackDebugMode;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LiveChannelResolution;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LiveChannelResolver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LiveInput;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.MediaServiceLiveChannelProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.YouTubeLiveInputParser;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

/**
 * Debug-only live input resolver. Playback deliberately uses SmartTube's normal playback activity
 * so lifecycle, D-pad/media keys, captions, track selection, overlays, and media-session behavior
 * exercise the same code as production playback.
 */
public final class SabrLivePlayerActivity extends Activity {
    public static final String EXTRA_LIVE_INPUT = "live_input";
    public static final String EXTRA_VIDEO_ID = "video_id"; // Backward-compatible adb extra.
    private static final String RECENT_PREFS = "debug_live_inputs";
    private static final String RECENT_KEY = "recent";
    private static final int MAX_RECENT_INPUTS = 8;

    private final YouTubeLiveInputParser parser = new YouTubeLiveInputParser();
    private AutoCompleteTextView input;
    private TextView state;
    private TextView detail;
    private Button resolveButton;
    private Button refreshButton;
    private LiveChannelResolver resolver;
    private Disposable resolution = Disposables.disposed();
    private LiveInput lastInput;
    private long requestGeneration;
    private boolean playbackLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sabr_live_player);
        resolver = new LiveChannelResolver(new MediaServiceLiveChannelProvider(
                YouTubeServiceManager.instance().getContentService(),
                YouTubeServiceManager.instance().getMediaItemService()));
        bindViews();
        applyIntent(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playbackLaunched) {
            playbackLaunched = false;
            SabrLiveFeatureFlags.setSabrLiveHarnessEnabledForDebug(false);
            PlaybackDebugMode.clear();
            showState("READY", "Playback closed. Refresh the channel or resolve another input.");
        }
    }

    private void bindViews() {
        input = findViewById(R.id.sabr_input);
        state = findViewById(R.id.sabr_status);
        detail = findViewById(R.id.sabr_detail);
        resolveButton = findViewById(R.id.sabr_load);
        refreshButton = findViewById(R.id.sabr_refresh);

        resolveButton.setOnClickListener(view -> resolve(false));
        refreshButton.setOnClickListener(view -> resolve(true));
        findViewById(R.id.sabr_stop).setOnClickListener(view -> stopResolving());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                resolve(false);
                return true;
            }
            return false;
        });
        input.setOnClickListener(view -> input.showDropDown());
        updateRecentAdapter(loadRecentInputs());
        input.requestFocus();
    }

    private void applyIntent(Intent intent, boolean autoResolve) {
        String supplied = null;
        if (intent != null) {
            supplied = intent.getStringExtra(EXTRA_LIVE_INPUT);
            if (TextUtils.isEmpty(supplied)) supplied = intent.getStringExtra(EXTRA_VIDEO_ID);
            Uri data = intent.getData();
            if (TextUtils.isEmpty(supplied) && data != null) {
                supplied = data.getQueryParameter("input");
            }
        }
        if (!TextUtils.isEmpty(supplied)) {
            input.setText(supplied);
            input.setSelection(input.length());
            if (autoResolve) input.post(() -> resolve(false));
        }
    }

    private void resolve(boolean forceRefresh) {
        cancelResolution();
        final long generation = ++requestGeneration;
        try {
            lastInput = parser.parse(input.getText().toString());
            rememberInput(input.getText().toString().trim());
        } catch (IllegalArgumentException error) {
            lastInput = null;
            showState("INVALID INPUT", error.getMessage());
            return;
        }

        if (lastInput.getType() == LiveInput.Type.VIDEO) {
            launchPlayback(lastInput.getValue(), "Direct video input");
            return;
        }

        setBusy(true);
        showState("RESOLVING", "Looking for the channel's current live broadcast…");
        resolution = resolver.resolve(lastInput, forceRefresh).subscribe(
                result -> runOnUiThread(() -> {
                    if (generation == requestGeneration) presentResolution(result);
                }),
                error -> runOnUiThread(() -> {
                    if (generation == requestGeneration) {
                        setBusy(false);
                        showState("UNAVAILABLE", "Channel resolution failed.");
                    }
                }));
    }

    private void presentResolution(LiveChannelResolution result) {
        setBusy(false);
        switch (result.status) {
            case LIVE:
                showState("LIVE NOW", describe(result, "Opening the normal SmartTube player"));
                launchPlayback(result.videoId, "Resolved channel broadcast");
                break;
            case UPCOMING:
                showState("UPCOMING", describe(result, result.reason));
                break;
            case OFFLINE:
                showState("OFFLINE", result.reason);
                break;
            case UNAVAILABLE:
            default:
                showState("UNAVAILABLE", result.reason);
                break;
        }
    }

    private void launchPlayback(String videoId, String source) {
        if (TextUtils.isEmpty(videoId)) {
            showState("UNAVAILABLE", "The resolved item has no playable video ID.");
            return;
        }
        cancelResolution();
        showState("LIVE PLAYER", source + " · video …" + suffix(videoId));
        SabrLiveFeatureFlags.setSabrLiveHarnessEnabledForDebug(false);
        PlaybackDebugMode.setForDebug(
                PlaybackDebugMode.Mode.FORCE_VISIONOS_HLS_REFERENCE);
        playbackLaunched = true;
        PlaybackPresenter.instance(this).openVideo(videoId);
    }

    private void stopResolving() {
        ++requestGeneration;
        cancelResolution();
        setBusy(false);
        SabrLiveFeatureFlags.setSabrLiveHarnessEnabledForDebug(false);
        PlaybackDebugMode.clear();
        showState("STOPPED", "No channel resolution is active.");
    }

    private void cancelResolution() {
        if (resolution != null && !resolution.isDisposed()) resolution.dispose();
        resolution = Disposables.disposed();
    }

    private void setBusy(boolean busy) {
        resolveButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy && lastInput != null
                && lastInput.getType() == LiveInput.Type.CHANNEL);
    }

    private void showState(String label, String message) {
        state.setText(label);
        detail.setText(!TextUtils.isEmpty(message) ? message : "No additional details.");
    }

    private static String describe(LiveChannelResolution result, String fallback) {
        StringBuilder value = new StringBuilder();
        if (!TextUtils.isEmpty(result.title)) value.append(result.title);
        if (!TextUtils.isEmpty(result.channelName)) {
            if (value.length() > 0) value.append(" · ");
            value.append(result.channelName);
        }
        if (!TextUtils.isEmpty(fallback)) {
            if (value.length() > 0) value.append("\n");
            value.append(fallback);
        }
        return value.toString();
    }

    private static String suffix(String videoId) {
        return videoId.substring(Math.max(0, videoId.length() - 4));
    }

    private List<String> loadRecentInputs() {
        String stored = getSharedPreferences(RECENT_PREFS, MODE_PRIVATE)
                .getString(RECENT_KEY, "");
        List<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(stored)) {
            for (String value : stored.split("\n")) {
                if (!TextUtils.isEmpty(value)) values.add(value);
            }
        }
        return values;
    }

    private void rememberInput(String value) {
        if (TextUtils.isEmpty(value)) return;
        List<String> values = loadRecentInputs();
        values.remove(value);
        values.add(0, value);
        while (values.size() > MAX_RECENT_INPUTS) values.remove(values.size() - 1);
        getSharedPreferences(RECENT_PREFS, MODE_PRIVATE).edit()
                .putString(RECENT_KEY, TextUtils.join("\n", values)).apply();
        updateRecentAdapter(values);
    }

    private void updateRecentAdapter(List<String> values) {
        input.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, values));
        input.setThreshold(0);
    }

    @Override
    protected void onDestroy() {
        ++requestGeneration;
        cancelResolution();
        if (!playbackLaunched) {
            SabrLiveFeatureFlags.setSabrLiveHarnessEnabledForDebug(false);
            PlaybackDebugMode.clear();
        }
        super.onDestroy();
    }
}
