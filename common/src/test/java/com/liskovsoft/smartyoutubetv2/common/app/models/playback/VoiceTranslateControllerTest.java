package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VoiceTranslateController;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VoiceTranslateControllerTest {
    @Test
    public void volumeKeysUseSavedLevelInsteadOfDuckedLevel() throws Exception {
        VoiceTranslateController controller = new VoiceTranslateController();
        controller.setAltContext(RuntimeEnvironment.getApplication());
        PlayerData data = PlayerData.instance(RuntimeEnvironment.getApplication());
        data.setPlayerVolume(0.8f);

        assertEquals(0.1f, controller.volumeForControls(0.1f), 0.0001f);
        assertFalse(controller.setVolumeFromControls(0.81f));

        Field ducked = VoiceTranslateController.class.getDeclaredField("mDucked");
        ducked.setAccessible(true);
        ducked.setBoolean(controller, true);
        Field previousVolume = VoiceTranslateController.class.getDeclaredField("mPreviousVolume");
        previousVolume.setAccessible(true);
        previousVolume.setFloat(controller, 0.8f);
        assertEquals(0.8f, controller.volumeForControls(0.1f), 0.0001f);
        assertTrue(controller.setVolumeFromControls(0.81f));
        assertEquals(0.8f, data.getPlayerVolume(), 0.0001f);
        assertEquals(0.81f, controller.volumeForControls(0.05f), 0.0001f);
    }
}
