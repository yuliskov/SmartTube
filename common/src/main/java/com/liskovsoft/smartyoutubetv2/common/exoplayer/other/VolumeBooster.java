package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.TargetApi;
import android.media.audiofx.LoudnessEnhancer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.liskovsoft.sharedutils.mylogger.Log;

@TargetApi(19)
public class VolumeBooster implements AudioListener {
    private static final String TAG = VolumeBooster.class.getSimpleName();
    private boolean mEnabled;
    private final float mVolume;
    private LoudnessEnhancer mBooster;

    public VolumeBooster(boolean enabled, float volume) {
        mEnabled = enabled;
        mVolume = volume;
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        Log.d(TAG, "Audio session id is %s, supported gain %s", audioSessionId, LoudnessEnhancer.PARAM_TARGET_GAIN_MB);

        if (mBooster != null) {
            mBooster.release();
        }

        mBooster = new LoudnessEnhancer(audioSessionId);
        mBooster.setEnabled(mEnabled);
        mBooster.setTargetGain((int) (1500 * mVolume));
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (mBooster != null) {
            mBooster.setEnabled(enabled);
        }
    }
}
