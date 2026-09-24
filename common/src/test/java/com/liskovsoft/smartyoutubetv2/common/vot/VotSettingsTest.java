package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VotSettingsTest {
    @Test
    public void defaultsDisableVoiceOverAndKeepExtensionVolumeLevels() {
        VotSettings settings = new VotSettings(RuntimeEnvironment.getApplication());

        assertFalse(settings.isEnabled());
        assertEquals(VotSettings.SOURCE_YANDEX, settings.getVoiceSource());
        assertTrue(settings.isButtonShown());
        assertFalse(settings.shouldShowButton());
        assertTrue(settings.isStrongDuckingEnabled());
        assertTrue(settings.isAdaptiveVolumeEnabled());
        assertEquals(15, settings.getOriginalVolume());
        assertEquals(100, settings.getTranslationVolume());
        settings.setTargetLanguage("ru");
        assertEquals("en", settings.getSourceLanguage());
        assertTrue(settings.shouldSkipSameLanguage("ru", "ru", true));
        assertFalse(settings.shouldSkipSameLanguage("ru", "ru", false));
        assertEquals("en", VotSettings.defaultTargetLanguage("en"));
        assertEquals("kk", VotSettings.defaultTargetLanguage("kk"));
        assertEquals("ru", VotSettings.defaultTargetLanguage("ru"));

        settings.setTargetLanguage("en");
        assertEquals("en", settings.getTargetLanguage());
        assertEquals("ru", settings.getSourceLanguage());
        settings.setSourceLanguage("en");
        assertTrue(settings.hasSelectedSourceLanguage());
        assertEquals("en", settings.getSourceLanguage());
        assertFalse(settings.shouldSkipSameLanguage("en", "en", true));
        settings.setSourceLanguage(null);
        assertFalse(settings.hasSelectedSourceLanguage());
        assertEquals("ru", settings.getSourceLanguage());
        assertTrue(settings.shouldSkipSameLanguage("en", "en", true));
        settings.setTargetLanguage("kk");
        assertEquals("kk", settings.getTargetLanguage());
        assertEquals("en", settings.getSourceLanguage());

        settings.setEnabled(true);
        assertTrue(settings.isEnabled());
        settings.setTargetLanguage("ru");
        settings.setVoiceSource(VotSettings.SOURCE_AUTO);
        settings.setButtonShown(false);
        VotSettings reloaded = new VotSettings(RuntimeEnvironment.getApplication());
        assertTrue(reloaded.isEnabled());
        assertEquals("ru", reloaded.getTargetLanguage());
        assertEquals(VotSettings.SOURCE_AUTO, reloaded.getVoiceSource());
        assertFalse(reloaded.isButtonShown());
        assertFalse(reloaded.shouldShowButton());

        settings.setButtonShown(true);
        assertTrue(reloaded.shouldShowButton());
        settings.setEnabled(false);
        assertFalse(reloaded.shouldShowButton());

        settings.setOriginalVolume(-10);
        settings.setTranslationVolume(150);
        settings.setLivelyVoice(false);
        settings.setAdaptiveVolumeEnabled(false);
        settings.setStrongDuckingEnabled(false);

        reloaded = new VotSettings(RuntimeEnvironment.getApplication());
        assertEquals(0, reloaded.getOriginalVolume());
        assertEquals(100, reloaded.getTranslationVolume());
        assertFalse(reloaded.useLivelyVoice());
        assertFalse(reloaded.isAdaptiveVolumeEnabled());
        assertFalse(reloaded.isStrongDuckingEnabled());
    }
}
