package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainUIDataModernUiTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void modernUi_enabledByDefault() throws Exception {
        resetSingleton();
        assertTrue(MainUIData.instance(mContext).isModernUiEnabled());
    }

    @Test
    public void modernUi_survivesReload() throws Exception {
        resetSingleton();
        MainUIData data = MainUIData.instance(mContext);
        data.setModernUiEnabled(false);
        data.persistNow();
        ShadowLooper.idleMainLooper();

        resetSingleton();
        assertFalse(MainUIData.instance(mContext).isModernUiEnabled());
    }

    private static void resetSingleton() throws Exception {
        Field field = MainUIData.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
