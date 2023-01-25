package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

public class ExoPlayerInitializer {
    private final int mDeviceRam;
    private final PlayerData mPlayerData;

    public ExoPlayerInitializer(Context context) {
        mPlayerData = PlayerData.instance(context);

        int deviceRam = Helpers.getDeviceRam(context);

        // If ram is too big, bigger then max int value DeviceRam will return a negative number...
        // use 196MB as that can only happens if device has more than 17GB of RAM, so 196 is enough and safe
        // https://github.com/yuliskov/SmartYouTubeTV/issues/532
        mDeviceRam = deviceRam < 0 ? 196000000 : deviceRam;
    }

    public SimpleExoPlayer createPlayer(Context context, DefaultRenderersFactory renderersFactory, DefaultTrackSelector trackSelector) {
        DefaultLoadControl loadControl = createLoadControl();

        // HDR fix?
        //trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context)));

        // Old initializer
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, loadControl);

        // New initializer
        //SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(
        //        context, renderersFactory, trackSelector, loadControl,
        //        null, new DummyBandwidthMeter(), new AnalyticsCollector.Factory(), Util.getLooper()
        //);

        enableAudioFocus(player);

        // Lead to numbered errors
        //player.setRepeatMode(Player.REPEAT_MODE_ONE);

        return player;
    }

    private void enableAudioFocus(SimpleExoPlayer player) {
        if (player != null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build();

            player.setAudioAttributes(audioAttributes, true);
        }
    }

    /**
     * Increase player's min/max buffer size to 60 secs
     * @return load control
     */
    private DefaultLoadControl createLoadControl() {
        DefaultLoadControl.Builder baseBuilder = new DefaultLoadControl.Builder();

        // Default values
        //DefaultLoadControl.DEFAULT_MIN_BUFFER_MS // 15_000
        //DefaultLoadControl.DEFAULT_MAX_BUFFER_MS // 50_000
        //DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS // 2_500
        //DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS // 5_000

        // Medium buffer is default one
        int minBufferMs = 50_000;
        int maxBufferMs = 50_000;
        int bufferForPlaybackMs = 2_500;
        int bufferForPlaybackAfterRebufferMs = 5_000;

        switch (mPlayerData.getVideoBufferType()) {
            case PlaybackEngineController.BUFFER_HIGH:
                minBufferMs = 50_000;
                maxBufferMs = 36_000_000; // technical infinity, recommended here a very high number, the max will be based on setTargetBufferBytes() value
                baseBuilder
                        .setTargetBufferBytes(mDeviceRam);
                break;
            case PlaybackEngineController.BUFFER_LOW:
                minBufferMs = 30_000;
                maxBufferMs = 30_000;
                break;
            case PlaybackEngineController.BUFFER_NONE:
                minBufferMs = 1_000;
                maxBufferMs = 1_000;
                bufferForPlaybackMs = 1_000;
                bufferForPlaybackAfterRebufferMs = 0;
                break;
        }

        baseBuilder
                .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs);

        return baseBuilder.createDefaultLoadControl();
    }

    private static final class DummyBandwidthMeter implements BandwidthMeter {
        @Override
        public long getBitrateEstimate() {
            return 0;
        }

        @Nullable
        @Override
        public TransferListener getTransferListener() {
            return null;
        }

        @Override
        public void addEventListener(Handler eventHandler, EventListener eventListener) {
            // Do nothing.
        }

        @Override
        public void removeEventListener(EventListener eventListener) {
            // Do nothing.
        }
    }
}
