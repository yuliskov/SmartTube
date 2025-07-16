package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.media.audiofx.LoudnessEnhancer;
import android.os.Build.VERSION;

import com.google.android.exoplayer2.audio.AudioListener;
import com.liskovsoft.sharedutils.mylogger.Log;

public class VolumeBooster implements AudioListener {
    private static final String TAG = VolumeBooster.class.getSimpleName();
    private boolean mIsEnabled;
    private final float mVolume;
    private LoudnessEnhancer mBooster;
    private boolean mIsSupported;
    private int mCurrentSessionId = -1;

    public VolumeBooster(boolean enabled, float volume) {
        mIsEnabled = enabled;
        mVolume = volume;
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (VERSION.SDK_INT < 19 || mVolume <= 1) {
            return;
        }

        Log.d(TAG, "Audio session id is %s, supported gain %s", audioSessionId, LoudnessEnhancer.PARAM_TARGET_GAIN_MB);

        if (audioSessionId == mCurrentSessionId) {
            return; // Already initialized for this session
        }

        mCurrentSessionId = audioSessionId;

        if (mBooster != null) {
            mBooster.release();
        }

        try {
            mBooster = new LoudnessEnhancer(audioSessionId);
            mBooster.setEnabled(mIsEnabled);
            double log2 = Math.log(mVolume) / Math.log(2);
            double gainMb = 10 * log2 * 100;
            //double gainMb = 20 * Math.log10(mVolume) * 100;
            //mBooster.setTargetGain((int) (1000 * mVolume));
            mBooster.setTargetGain((int) gainMb);
            mIsSupported = true;
        } catch (RuntimeException | UnsatisfiedLinkError | NoClassDefFoundError | NoSuchFieldError e) { // Cannot initialize effect engine
            e.printStackTrace();
            mIsSupported = false;
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
}
