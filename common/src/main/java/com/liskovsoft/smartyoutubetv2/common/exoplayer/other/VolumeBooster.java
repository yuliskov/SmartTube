package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.media.audiofx.LoudnessEnhancer;
import android.os.Build.VERSION;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;

public class VolumeBooster implements AudioListener, TickleListener {
    private static final String TAG = VolumeBooster.class.getSimpleName();
    private static final int DRIFT_FIX_INTERVAL_MINUTES = 5;
    private boolean mIsEnabled;
    private final float mVolume;
    private final SimpleExoPlayer mPlayer;
    private LoudnessEnhancer mBooster;
    private boolean mIsSupported;
    private int mCurrentSessionId = -1;
    private int mGainMb;
    private int mTickleCount;

    public VolumeBooster(boolean enabled, float volume, @Nullable SimpleExoPlayer player) {
        mIsEnabled = enabled;
        mVolume = volume;
        mPlayer = player;
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (VERSION.SDK_INT < 19 || mVolume <= 1) {
            return;
        }

        // NOTE: 5.1 audio cannot be boosted (format isn't supported error)
        if (mPlayer != null && mPlayer.getAudioFormat() != null && mPlayer.getAudioFormat().channelCount > 2) {
            return;
        }

        Log.d(TAG, "Audio session id is %s, supported gain %s", audioSessionId, LoudnessEnhancer.PARAM_TARGET_GAIN_MB);

        if (mBooster != null && audioSessionId == mCurrentSessionId) {
            return; // Already initialized for this session
        }

        mCurrentSessionId = audioSessionId;

        release();

        try {
            mBooster = new LoudnessEnhancer(audioSessionId);
            mBooster.setEnabled(mIsEnabled);

            //double log2 = Math.log(mVolume) / Math.log(2);
            //double gainMb = 10 * log2 * 100;
            //mBooster.setTargetGain((int) gainMb);

            double gainMb = 20 * Math.log10(mVolume * 3) * 100;
            mGainMb = (int) gainMb;
            mBooster.setTargetGain(mGainMb);

            //mBooster.setTargetGain((int) (1000 * mVolume));

            mIsSupported = true;

            // DRIFT FIX: periodically reset the native compressor state.
            TickleManager.instance().addListener(this);
            mTickleCount = 0;
        } catch (RuntimeException | UnsatisfiedLinkError | NoClassDefFoundError | NoSuchFieldError e) { // Cannot initialize effect engine
            e.printStackTrace();
            mIsSupported = false;
        }
    }

    /**
     * DRIFT FIX: AOSP's le_fx::AdaptiveDynamicRangeCompression accumulates compressor_gain_ <br/>
     * multiplicatively using a Taylor-approximated exp(), so it drifts downward over long <br/>
     * sessions and never recovers. Re-applying the target gain triggers <br/>
     * EFFECT_CMD_SET_PARAM -> LE_reset() -> Initialize(), which resets compressor_gain_ to 1.0f.
     */
    @RequiresApi(19)
    @Override
    public void onTickle() {
        if (++mTickleCount > DRIFT_FIX_INTERVAL_MINUTES && mBooster != null && mIsSupported) {
            mTickleCount = 0;
            try {
                mBooster.setTargetGain(mGainMb);
                Log.d(TAG, "Drift fix: re-applied target gain %s mB", mGainMb);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
        if (mBooster != null) {
            mBooster.setEnabled(enabled);
        }
    }

    public boolean isSupported() {
        return mIsSupported;
    }

    public void release() {
        TickleManager.instance().removeListener(this);
        if (mBooster != null) {
            mBooster.release();
            mBooster = null;
        }
    }
}
