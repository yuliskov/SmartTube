package com.liskovsoft.smartyoutubetv2.common.vot;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VotAudioSourceTest {
    @Test
    public void selectsHighestBitrateCompleteEnglishAudio() {
        MediaItemFormatInfo info = formats(
                format("low", "audio/webm", "en", "64000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("high", "audio/webm", "en", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("drc", "audio/webm", "en", "256000", true, false, MediaFormat.FORMAT_TYPE_DASH),
                format("otf", "audio/webm", "en", "256000", false, true, MediaFormat.FORMAT_TYPE_DASH),
                format("sabr", "audio/webm", "en", "256000", false, false, MediaFormat.FORMAT_TYPE_SABR),
                format("other-language", "audio/webm", "ru", "256000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("video", "video/webm", "en", "256000", false, false, MediaFormat.FORMAT_TYPE_DASH));

        VotAudioSource source = VotAudioSource.best(info, "en");
        assertEquals("high", source.url);
        assertEquals("251", source.itag);
        assertEquals(1234, source.size);
    }

    @Test
    public void returnsNoSourceWhenOnlySegmentedAudioExists() {
        assertNull(VotAudioSource.best(formats(
                format("otf", "audio/webm", "en", "128000", false, true, MediaFormat.FORMAT_TYPE_DASH)), "en"));
    }

    @Test
    public void usesTheSelectedSourceLanguageForUpload() {
        MediaItemFormatInfo info = formats(
                format("unmarked", "audio/webm", null, "320000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("english", "audio/webm", "en", "256000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("spanish", "audio/webm", "es-ES", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH));

        assertEquals("spanish", VotAudioSource.best(info, "es").url);
    }

    @Test
    public void rejectsUnmarkedAudioWhenKnownTracksDoNotMatch() {
        MediaItemFormatInfo info = formats(
                format("unmarked", "audio/webm", null, "256000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("english", "audio/webm", "en", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH));

        assertNull(VotAudioSource.best(info, "es"));
        assertEquals("unmarked", VotAudioSource.best(formats(
                format("unmarked", "audio/webm", null, "256000", false, false,
                        MediaFormat.FORMAT_TYPE_DASH)), "es").url);
    }

    @Test
    public void acceptsNumberedLanguageVariants() {
        MediaItemFormatInfo info = formats(
                format("english-variant", "audio/webm", "en.4", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("unmarked", "audio/webm", null, "256000", false, false, MediaFormat.FORMAT_TYPE_DASH));

        assertEquals("english-variant", VotAudioSource.best(info, "en").url);
    }

    @Test
    public void prefersOriginalAudioOverHigherBitrateDub() {
        MediaItemFormatInfo info = formats(
                format("dub", "audio/webm", "en (dubbed)", "256000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("original", "audio/webm", "en (original)", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("russian", "audio/webm", "ru (dubbed)", "320000", false, false, MediaFormat.FORMAT_TYPE_DASH));

        assertEquals("en", VotAudioSource.originalLanguage(info));
        assertEquals("original", VotAudioSource.best(info, "en").url);
    }

    @Test
    public void ambiguousLanguagesDoNotOverrideFallback() {
        assertNull(VotAudioSource.originalLanguage(formats(
                format("english", "audio/webm", "en", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH),
                format("russian", "audio/webm", "ru", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH))));

        assertEquals("ru", VotAudioSource.originalLanguage(formats(
                format("russian-original", "audio/webm", "ru (original)", "128000", false, false, MediaFormat.FORMAT_TYPE_DASH))));
    }

    @Test
    public void russianAutoCaptionsIdentifyUnmarkedAudio() {
        assertEquals("ru", VotAudioSource.originalLanguage(formatsWithSubtitles(
                new MediaFormat[]{format("audio", "audio/webm", null, "128000", false, false,
                        MediaFormat.FORMAT_TYPE_DASH)}, subtitle("ru-RU", "asr", "a.ru"),
                subtitle("be", "asr", "a.ru"))));
        assertEquals("en", VotAudioSource.originalLanguage(formatsWithSubtitles(
                new MediaFormat[]{format("audio", "audio/webm", null, "128000", false, false,
                        MediaFormat.FORMAT_TYPE_DASH)}, subtitle("en", "asr", "a.en"),
                subtitle("ru", "asr", "a.en"))));
        assertEquals("en", VotAudioSource.originalLanguage(formatsWithSubtitles(
                new MediaFormat[]{format("audio", "audio/webm", null, "128000", false, false,
                        MediaFormat.FORMAT_TYPE_DASH)}, subtitle("en", "asr", "A.en"))));
    }

    @Test
    public void unknownOrTranslatedCaptionsDoNotPretendToIdentifySpeech() {
        MediaFormat unmarked = format("audio", "audio/webm", null, "128000", false, false,
                MediaFormat.FORMAT_TYPE_DASH);
        assertNull(VotAudioSource.originalLanguage(formats(unmarked)));
        assertNull(VotAudioSource.originalLanguage(formatsWithSubtitles(
                new MediaFormat[]{unmarked}, subtitle("ru", null, ".ru"))));
        assertNull(VotAudioSource.originalLanguage(formatsWithSubtitles(
                new MediaFormat[]{unmarked}, subtitle("ru", "asr", "a.ru"),
                subtitle("en", "asr", "a.en"))));
    }

    @Test
    public void russianRuleDoesNotTreatBelarusianAsRussian() {
        assertEquals("be", VotAudioSource.originalLanguage(formats(
                format("belarusian", "audio/webm", "be (original)", "128000", false, false,
                        MediaFormat.FORMAT_TYPE_DASH))));
    }

    private static MediaItemFormatInfo formats(MediaFormat... values) {
        return formatsWithSubtitles(values);
    }

    private static MediaItemFormatInfo formatsWithSubtitles(MediaFormat[] values, MediaSubtitle... subtitles) {
        return (MediaItemFormatInfo) Proxy.newProxyInstance(MediaItemFormatInfo.class.getClassLoader(),
                new Class[]{MediaItemFormatInfo.class}, (proxy, method, args) -> {
                    if ("getAdaptiveFormats".equals(method.getName())) return Arrays.asList(values);
                    if ("getSubtitles".equals(method.getName())) return Arrays.asList(subtitles);
                    return null;
                });
    }

    private static MediaSubtitle subtitle(String language, String type, String vssId) {
        return (MediaSubtitle) Proxy.newProxyInstance(MediaSubtitle.class.getClassLoader(),
                new Class[]{MediaSubtitle.class}, (proxy, method, args) -> {
                    if ("getLanguageCode".equals(method.getName())) return language;
                    if ("getType".equals(method.getName())) return type;
                    if ("getVssId".equals(method.getName())) return vssId;
                    return null;
                });
    }

    private static MediaFormat format(String url, String mime, String language, String bitrate,
                                      boolean drc, boolean otf, int type) {
        return (MediaFormat) Proxy.newProxyInstance(MediaFormat.class.getClassLoader(),
                new Class[]{MediaFormat.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUrl": return url;
                        case "getMimeType": return mime;
                        case "getLanguage": return language;
                        case "getBitrate": return bitrate;
                        case "getITag": return "251";
                        case "getClen": return "1234";
                        case "isDrc": return drc;
                        case "isOtf": return otf;
                        case "getFormatType": return type;
                        default: return null;
                    }
                });
    }
}
