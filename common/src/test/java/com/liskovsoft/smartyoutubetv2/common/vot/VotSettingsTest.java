package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VotSettingsTest {
    @Test
    public void acceptsOnlyTokensAndYandexOAuthResultLinks() {
        String token = "y0_abcdefghijklmnopqrstuvwxyz1234567890";
        assertEquals(token, VotSettings.oauthToken(token));
        assertEquals(token, VotSettings.oauthToken("OAuth " + token));
        assertEquals(token, VotSettings.oauthToken("https://oauth.yandex.ru/verification_code#access_token="
                + token + "&expires_in=3600"));
        assertNull(VotSettings.oauthToken("https://example.com/verification_code#access_token=" + token));
        assertNull(VotSettings.oauthToken("https://oauth.yandex.ru/verification_code#other=" + token));

        VotSettings settings = new VotSettings(RuntimeEnvironment.getApplication());
        settings.setOAuthToken(token);
        assertEquals(token, new VotSettings(RuntimeEnvironment.getApplication()).getOAuthToken());
        assertFalse(settings.setOAuthToken("invalid"));
        assertEquals(token, settings.getOAuthToken());
        settings.setOAuthToken(null);
        assertNull(settings.getOAuthToken());
    }

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
