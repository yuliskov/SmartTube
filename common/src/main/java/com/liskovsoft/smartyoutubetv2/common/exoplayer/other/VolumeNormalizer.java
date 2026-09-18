package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.media.audiofx.DynamicsProcessing;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * Lightweight real-time compressor/limiter attached to ExoPlayer's audio session.
 * No samples are recorded, persisted, or sent outside the device.
 */
public final class VolumeNormalizer implements AudioListener {
    private static final String TAG = VolumeNormalizer.class.getSimpleName();
    private final SimpleExoPlayer mPlayer;
    private boolean mEnabled;
    private int mIntensity;
    private int mCurrentSessionId = -1;
    private DynamicsProcessing mProcessor;

    public VolumeNormalizer(SimpleExoPlayer player) {
        mPlayer = player;
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (audioSessionId <= 0) {
            releaseProcessor();
            mCurrentSessionId = -1;
            return;
        }

        if (audioSessionId == mCurrentSessionId && mProcessor != null) {
            return;
        }

        releaseProcessor();
        mCurrentSessionId = audioSessionId;
        createProcessorIfNeeded();
    }

    public void update(boolean enabled, int intensity) {
        boolean changed = mEnabled != enabled || mIntensity != intensity;
        mEnabled = enabled;
        mIntensity = intensity;

        if (!changed) {
            return;
        }

        releaseProcessor();
        createProcessorIfNeeded();
    }

    public void release() {
        releaseProcessor();
        mCurrentSessionId = -1;
    }

    private void createProcessorIfNeeded() {
        if (!mEnabled || mCurrentSessionId <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        try {
            mProcessor = Api28Impl.create(mCurrentSessionId, getChannelCount(), mIntensity);
            mProcessor.setEnabled(true);
            Log.i(TAG, "Volume normalization enabled on audio session %s", mCurrentSessionId);
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "Volume normalization unavailable: %s", error.getMessage());
            releaseProcessor();
        }
    }

    private int getChannelCount() {
        Format audioFormat = mPlayer != null ? mPlayer.getAudioFormat() : null;
        int channelCount = audioFormat != null ? audioFormat.channelCount : Format.NO_VALUE;
        return channelCount > 0 && channelCount <= 8 ? channelCount : 2;
    }

    private void releaseProcessor() {
        if (mProcessor == null) {
            return;
        }

        try {
            mProcessor.setEnabled(false);
        } catch (RuntimeException ignored) {
            // The audio session may already have been released by ExoPlayer.
        }

        mProcessor.release();
        mProcessor = null;
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static final class Api28Impl {
        private static final float MAX_FREQUENCY_HZ = 20_000f;
        private static final float NOISE_GATE_DB = -90f;

        static DynamicsProcessing create(int audioSessionId, int channelCount, int intensity) {
            VolumeNormalizerPreset preset = VolumeNormalizerPreset.fromIntensity(intensity);
            DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, 1);
            mbc.setBand(0, new DynamicsProcessing.MbcBand(
                    true,
                    MAX_FREQUENCY_HZ,
                    preset.attackMs,
                    preset.releaseMs,
                    preset.ratio,
                    preset.thresholdDb,
                    preset.kneeDb,
                    NOISE_GATE_DB,
                    1f,
                    0f,
                    preset.postGainDb));

            DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1f,
                    100f,
                    20f,
                    preset.limiterThresholdDb,
                    0f);

            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    // The frequency-resolution variant is broadly supported on Android TV.
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channelCount,
                    false,
                    0,
                    true,
                    1,
                    false,
                    0,
                    true)
                    .setMbcAllChannelsTo(mbc)
                    .setLimiterAllChannelsTo(limiter)
                    .build();

            return new DynamicsProcessing(0, audioSessionId, config);
        }
    }
}
