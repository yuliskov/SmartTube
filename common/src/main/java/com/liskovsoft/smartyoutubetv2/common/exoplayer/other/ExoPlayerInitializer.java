package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerController;

public class ExoPlayerInitializer {
    private final int mDeviceRam;
    private int mBufferType = PlayerController.BUFFER_MED;

    public ExoPlayerInitializer(Context activity) {
        int deviceRam = Helpers.getDeviceRam(activity);

        // If ram is too big, bigger then max int value DeviceRam will return a negative number...
        // use 196MB as that can only happens if device has more than 17GB of RAM, so 196 is enough and safe
        // https://github.com/yuliskov/SmartYouTubeTV/issues/532
        mDeviceRam = deviceRam < 0 ? 196000000 : deviceRam;
    }

    public SimpleExoPlayer createPlayer(Context activity, DefaultRenderersFactory renderersFactory, DefaultTrackSelector trackSelector) {
        DefaultLoadControl loadControl = createLoadControl();

        // HDR fix?
        //trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingAudioSessionId(C.generateAudioSessionIdV21(activity)));

        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(activity, renderersFactory, trackSelector, loadControl);
        enableAudioFocus(player);

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

        if (PlayerController.BUFFER_HIGH == mBufferType) {
            int minBufferMs = 30000; // 30 seconds
            int maxBufferMs = 36000000; // technical infinity, recommended here a very high number, the max will be based on setTargetBufferBytes() value
            int bufferForPlaybackMs = 500; // half a seconds can be lower as lowe as 250
            int bufferForPlaybackAfterRebufferMs = 3000; // 3 seconds
            baseBuilder
                    .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                    .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                    .setTargetBufferBytes(mDeviceRam);
        } else if (PlayerController.BUFFER_LOW == mBufferType) {
            baseBuilder
                    .setBufferDurationsMs(
                            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 3,
                            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 3,
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 3,
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 3);
        }

        // Normal buffer is a default one

        return baseBuilder.createDefaultLoadControl();
    }

    public void setBuffer(int bufferType) {
        mBufferType = bufferType;
    }

    public int getBuffer() {
        return mBufferType;
    }
}
