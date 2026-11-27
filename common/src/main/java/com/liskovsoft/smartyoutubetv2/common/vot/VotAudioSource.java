package com.liskovsoft.smartyoutubetv2.common.vot;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import java.util.Locale;

/** The best directly downloadable, non-DRC audio-only stream exposed by the player. */
public final class VotAudioSource {
    public final String url;
    public final String itag;
    public final long size;

    private VotAudioSource(MediaFormat format) {
        url = format.getUrl();
        itag = format.getITag();
        size = number(format.getClen());
    }

    public static VotAudioSource best(MediaItemFormatInfo info, String sourceLanguage) {
        if (info == null || info.getAdaptiveFormats() == null) return null;
        MediaFormat best = null;
        int bestLanguageRank = -1;
        long bestBitrate = -1;
        boolean hasKnownLanguage = false;
        for (MediaFormat format : info.getAdaptiveFormats()) {
            String mime = format.getMimeType();
            if (format.getFormatType() != MediaFormat.FORMAT_TYPE_DASH || format.isDrc() || format.isOtf()
                    || mime == null || !mime.startsWith("audio/") || format.getUrl() == null
                    || format.getUrl().isEmpty()) continue;
            String language = languageCode(format.getLanguage());
            if (language != null) hasKnownLanguage = true;
            if (language != null && !language.equals(sourceLanguage)) continue;
            int languageRank = language == null ? 0 : isOriginal(format) ? 2 : 1;
            long bitrate = number(format.getBitrate());
            if (languageRank > bestLanguageRank
                    || (languageRank == bestLanguageRank && bitrate > bestBitrate)) {
                best = format;
                bestLanguageRank = languageRank;
                bestBitrate = bitrate;
            }
        }
        return best == null || bestLanguageRank == 0 && hasKnownLanguage
                ? null : new VotAudioSource(best);
    }

    /** Uses the original audio tag, a single audio language, or unique auto-generated captions. */
    public static String originalLanguage(MediaItemFormatInfo info) {
        if (info == null) return null;
        String onlyLanguage = null;
        boolean ambiguous = false;
        if (info.getAdaptiveFormats() != null) {
            for (MediaFormat format : info.getAdaptiveFormats()) {
                if (format.getMimeType() == null || !format.getMimeType().startsWith("audio/")) continue;
                String language = languageCode(format.getLanguage());
                if (language == null) continue;
                if (isOriginal(format)) return language;
                if (onlyLanguage != null && !onlyLanguage.equals(language)) ambiguous = true;
                else onlyLanguage = language;
            }
        }
        if (!ambiguous && onlyLanguage != null) return onlyLanguage;

        // Translated ASR captions retain the original VSS id (for example, ru/a.en).
        if (info.getSubtitles() == null) return null;
        onlyLanguage = null;
        for (MediaSubtitle subtitle : info.getSubtitles()) {
            String vssId = subtitle.getVssId();
            if (vssId == null || !vssId.regionMatches(true, 0, "a.", 0, 2)) continue;
            String language = languageCode(vssId.substring(2));
            if (language == null) continue;
            if (onlyLanguage != null && !onlyLanguage.equals(language)) return null;
            onlyLanguage = language;
        }
        return onlyLanguage;
    }

    private static boolean isOriginal(MediaFormat format) {
        String language = format.getLanguage();
        return language != null && language.contains("(original)");
    }

    static String languageCode(String language) {
        if (language == null) return null;
        int end = 0;
        while (end < language.length() && Character.isLetter(language.charAt(end))) end++;
        return end >= 2 && end <= 3 ? language.substring(0, end).toLowerCase(Locale.US) : null;
    }

    private static long number(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return 0; }
    }
}
