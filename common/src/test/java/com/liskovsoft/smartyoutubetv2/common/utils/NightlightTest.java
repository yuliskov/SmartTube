package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.Paint;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NightlightTest {
    private Context mContext;
    private PlayerTweaksData mTweaks;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mTweaks = PlayerTweaksData.instance(mContext);
        mTweaks.setNightlightWarmth(Utils.NIGHTLIGHT_NEUTRAL);
        mTweaks.setNightlightOnHdr(false);
        mTweaks.setNightlightHdrActive(false);
        mTweaks.setTextureViewEnabled(false);
        mTweaks.setTunneledPlaybackEnabled(true);
        PlayerData.instance(mContext).setRotationAngle(0);
        PlayerData.instance(mContext).setVideoFlipEnabled(false);
    }

    @Test
    public void everyWarmthPresetPreservesOpaqueBlack() {
        for (int kelvin : Utils.NIGHTLIGHT_PRESETS) {
            Paint paint = Utils.kelvinToPaint(kelvin);
            if (kelvin == Utils.NIGHTLIGHT_NEUTRAL) {
                assertNull(paint);
                continue;
            }

            assertNotNull(paint);
            // Robolectric 4.6.1 stores the matrix in its shadow constructor.
            ColorMatrix matrix = ReflectionHelpers.getField(
                    Shadow.extract(paint.getColorFilter()), "matrix");
            float[] values = matrix.getArray();
            float[] black = {0f, 0f, 0f, 255f};
            for (int row = 0; row < 4; row++) {
                float result = values[row * 5 + 4];
                for (int column = 0; column < 4; column++) {
                    result += values[row * 5 + column] * black[column];
                }
                assertEquals("Black at " + kelvin + "K, channel " + row,
                        black[row], result, 0f);
            }
            assertTrue(values[6] < values[0]);
            assertTrue(values[12] < values[6]);
        }
    }

    @Test
    public void nightlightUsesTextureViewEvenWhenTunnelingIsPreferred() {
        assertFalse(Utils.isTextureViewRequired(mContext));
        mTweaks.setNightlightWarmth(3000);
        assertTrue(Utils.isTextureViewRequired(mContext));
        assertTrue(mTweaks.isTunneledPlaybackEnabled());
    }

    @Test
    public void turningNightlightOffRestoresSurfaceViewWithoutChangingPreferences() {
        mTweaks.setNightlightWarmth(3000);
        assertTrue(Utils.isTextureViewRequired(mContext));
        mTweaks.setNightlightWarmth(Utils.NIGHTLIGHT_NEUTRAL);
        assertFalse(Utils.isTextureViewRequired(mContext));
        assertTrue(mTweaks.isTunneledPlaybackEnabled());
    }

    @Test
    public void hdrTrackChangesDoNotSwitchRenderersWhileNightlightIsEnabled() {
        mTweaks.setNightlightWarmth(3000);
        mTweaks.setNightlightHdrActive(true);
        assertFalse(mTweaks.isNightlightActive());
        assertTrue(Utils.isTextureViewRequired(mContext));
        mTweaks.setNightlightHdrActive(false);
        assertTrue(mTweaks.isNightlightActive());
        assertTrue(Utils.isTextureViewRequired(mContext));
    }

    @Test
    public void explicitTextureViewRotationAndFlipRemainSupportedWithNightlightOff() {
        mTweaks.setTextureViewEnabled(true);
        assertTrue(Utils.isTextureViewRequired(mContext));
        mTweaks.setTextureViewEnabled(false);
        PlayerData.instance(mContext).setRotationAngle(90);
        assertTrue(Utils.isTextureViewRequired(mContext));
        PlayerData.instance(mContext).setRotationAngle(0);
        PlayerData.instance(mContext).setVideoFlipEnabled(true);
        assertTrue(Utils.isTextureViewRequired(mContext));
    }
}
