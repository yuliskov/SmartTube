package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

final class VolumeNormalizerPreset {
    final float attackMs;
    final float releaseMs;
    final float ratio;
    final float thresholdDb;
    final float kneeDb;
    final float postGainDb;
    final float limiterThresholdDb;

    private VolumeNormalizerPreset(float attackMs, float releaseMs, float ratio,
                                   float thresholdDb, float kneeDb, float postGainDb,
                                   float limiterThresholdDb) {
        this.attackMs = attackMs;
        this.releaseMs = releaseMs;
        this.ratio = ratio;
        this.thresholdDb = thresholdDb;
        this.kneeDb = kneeDb;
        this.postGainDb = postGainDb;
        this.limiterThresholdDb = limiterThresholdDb;
    }

    static VolumeNormalizerPreset fromIntensity(int intensity) {
        switch (intensity) {
            case PlayerData.VOLUME_NORMALIZATION_SOFT:
                return new VolumeNormalizerPreset(12f, 500f, 2.5f, -18f, 6f, 2f, -2f);
            case PlayerData.VOLUME_NORMALIZATION_STRONG:
                return new VolumeNormalizerPreset(3f, 280f, 7f, -28f, 8f, 7f, -3f);
            case PlayerData.VOLUME_NORMALIZATION_BALANCED:
            default:
                return new VolumeNormalizerPreset(6f, 380f, 4.5f, -23f, 7f, 4.5f, -2.5f);
        }
    }
}
