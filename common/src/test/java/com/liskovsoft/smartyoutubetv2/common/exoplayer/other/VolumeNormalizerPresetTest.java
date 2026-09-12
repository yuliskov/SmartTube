package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VolumeNormalizerPresetTest {
    @Test
    public void strongerPresetCompressesMoreAndReleasesFaster() {
        VolumeNormalizerPreset soft = VolumeNormalizerPreset.fromIntensity(PlayerData.VOLUME_NORMALIZATION_SOFT);
        VolumeNormalizerPreset strong = VolumeNormalizerPreset.fromIntensity(PlayerData.VOLUME_NORMALIZATION_STRONG);

        assertTrue(strong.ratio > soft.ratio);
        assertTrue(strong.thresholdDb < soft.thresholdDb);
        assertTrue(strong.releaseMs < soft.releaseMs);
        assertTrue(strong.postGainDb > soft.postGainDb);
    }

    @Test
    public void unknownIntensityFallsBackToBalanced() {
        VolumeNormalizerPreset balanced = VolumeNormalizerPreset.fromIntensity(PlayerData.VOLUME_NORMALIZATION_BALANCED);
        VolumeNormalizerPreset fallback = VolumeNormalizerPreset.fromIntensity(Integer.MAX_VALUE);

        assertEquals(balanced.attackMs, fallback.attackMs, 0f);
        assertEquals(balanced.releaseMs, fallback.releaseMs, 0f);
        assertEquals(balanced.ratio, fallback.ratio, 0f);
        assertEquals(balanced.thresholdDb, fallback.thresholdDb, 0f);
        assertEquals(balanced.postGainDb, fallback.postGainDb, 0f);
    }
}
