package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.Paint;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.sharedutils.helpers.Helpers;
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
import static org.junit.Assert.assertArrayEquals;
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
    @Test
    public void legacyPreferencesMigrateWithoutEnablingUpstreamOptions() {
        Object[] legacy = new Object[65];
        legacy[61] = 2500;
        legacy[62] = true;
        legacy[63] = true;
        legacy[64] = 3000;
        AppPrefs prefs = AppPrefs.instance(mContext);
        prefs.setProfileData("nightlight_data", " ");
        prefs.setProfileData("video_player_tweaks_data", Helpers.mergeData(legacy));
        mTweaks.onProfileChanged();
        assertEquals(2500, mTweaks.getNightlightWarmth());
        assertEquals(3000, mTweaks.getNightlightLastWarmth());
        assertTrue(mTweaks.isNightlightOnUi());
        assertTrue(mTweaks.isNightlightOnHdr());
        assertFalse(mTweaks.isLiveEdgeEnabled());
        assertFalse(mTweaks.isQuickSkipChaptersEnabled());
        ReflectionHelpers.callInstanceMethod(mTweaks, "persistDataInt");
        mTweaks.onProfileChanged();
        assertEquals(2500, mTweaks.getNightlightWarmth());
        assertTrue(mTweaks.isNightlightOnUi());
        assertEquals(0, Helpers.parseInt(Helpers.splitData(prefs.getProfileData("video_player_tweaks_data")), 61, 0));
    }

    @Test
    public void upstreamPreferencesDoNotBecomeNightlightSettings() {
        Object[] upstream = new Object[64];
        upstream[62] = true;
        upstream[63] = true;
        AppPrefs prefs = AppPrefs.instance(mContext);
        prefs.setProfileData("nightlight_data", " ");
        prefs.setProfileData("video_player_tweaks_data", Helpers.mergeData(upstream));
        mTweaks.onProfileChanged();
        assertEquals(Utils.NIGHTLIGHT_NEUTRAL, mTweaks.getNightlightWarmth());
        assertFalse(mTweaks.isNightlightOnUi());
        assertFalse(mTweaks.isNightlightOnHdr());
        assertTrue(mTweaks.isLiveEdgeEnabled());
        assertTrue(mTweaks.isQuickSkipChaptersEnabled());
    }
    @Test
    public void refactorMatchesPreviousColorTransformExactly() {
        // Exhaust the active integer-Kelvin range, plus off/invalid boundaries.
        for (int kelvin = -1; kelvin <= 6600; kelvin++) {
            float[] rgb = legacyKelvinToRgb(kelvin);
            assertArrayEquals("RGB at " + kelvin, rgb, Utils.kelvinToRgb(kelvin), 0f);
            Paint paint = Utils.kelvinToPaint(kelvin);
            if (kelvin <= 0 || kelvin >= 6500) {
                assertNull(paint);
                continue;
            }
            ColorMatrix matrix = ReflectionHelpers.getField(
                    Shadow.extract(paint.getColorFilter()), "matrix");
            assertArrayEquals("Matrix at " + kelvin, new float[] {
                    rgb[0], 0f, 0f, 0f, 0f,
                    0f, rgb[1], 0f, 0f, 0f,
                    0f, 0f, rgb[2], 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            }, matrix.getArray(), 0f);
        }
    }

    // Frozen pre-refactor calculation from 5b73039cb: guards against color drift.
    private static float[] legacyKelvinToRgb(int kelvin) {
        if (kelvin >= 6500 || kelvin <= 0) {
            return new float[] {1f, 1f, 1f};
        }

        float k = kelvin / 100f;
        float r = k <= 66f ? 255f : 329.698727446f * (float) Math.pow(k - 60f, -0.1332047592);
        float g = k <= 66f ? 99.4708025861f * (float) Math.log(k) - 161.1195681661f
                           : 288.1221695283f * (float) Math.pow(k - 60f, -0.0755148492);
        float b = k >= 66f ? 255f : k <= 19f ? 0f : 138.5177312231f * (float) Math.log(k - 10f) - 305.0447927307f;

        return new float[] {legacyClamp(r / 255f), legacyClamp(g / 255f), legacyClamp(b / 255f)};
    }

    private static float legacyClamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
