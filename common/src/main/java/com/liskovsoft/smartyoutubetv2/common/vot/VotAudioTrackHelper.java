package com.liskovsoft.smartyoutubetv2.common.vot;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import java.util.List;
import java.util.Locale;

/**
 * Parses YouTube audio track labels (e.g. {@code en-US (dubbed-auto)}, {@code ru (original)}).
 */
public final class VotAudioTrackHelper {
    private VotAudioTrackHelper() {
    }

    public static final class TrackInfo {
        @Nullable
        public final FormatItem format;
        @Nullable
        public final String rawLabel;
        @Nullable
        public final String langCode;
        @Nullable
        public final String acont;

        public TrackInfo(@Nullable FormatItem format, @Nullable String rawLabel,
                         @Nullable String langCode, @Nullable String acont) {
            this.format = format;
            this.rawLabel = rawLabel;
            this.langCode = langCode;
            this.acont = acont;
        }
    }

    public static TrackInfo from(@Nullable FormatItem format) {
        if (format == null) {
            return new TrackInfo(null, null, null, null);
        }
        return parse(format, pickRawLabel(format));
    }

    @Nullable
    private static String pickRawLabel(@Nullable FormatItem format) {
        if (format == null) {
            return null;
        }
        String lang = format.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return lang;
        }
        CharSequence title = format.getTitle();
        if (title == null) {
            return null;
        }
        String t = title.toString().toLowerCase(Locale.US);
        if (t.contains("english") || t.contains("original") || t.contains("dubbed")
                || t.matches(".*\\ben[- ]?(us|gb)?\\b.*")) {
            return title.toString();
        }
        return null;
    }

    private static TrackInfo parse(@Nullable FormatItem format, @Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return new TrackInfo(format, raw, null, null);
        }
        String acont = null;
        String langPart = raw;
        int open = raw.indexOf('(');
        if (open > 0 && raw.endsWith(")")) {
            langPart = raw.substring(0, open).trim();
            acont = raw.substring(open + 1, raw.length() - 1).trim().toLowerCase(Locale.US);
        }
        String langCode = extractLangCode(langPart);
        return new TrackInfo(format, raw, langCode, acont);
    }

    public static TrackInfo resolveCurrent(@Nullable FormatItem current,
                                           @Nullable List<FormatItem> allFormats) {
        TrackInfo info = from(current);
        if (info.langCode != null || info.acont != null) {
            return info;
        }
        TrackInfo fromList = resolveFromList(allFormats);
        return fromList != null ? fromList : info;
    }

    @Nullable
    public static TrackInfo resolveFromList(@Nullable List<FormatItem> formats) {
        if (formats == null || formats.isEmpty()) {
            return null;
        }
        for (FormatItem format : formats) {
            if (format == null) {
                continue;
            }
            if (format.isSelected() || format.isDefault()) {
                TrackInfo info = from(format);
                if (info.langCode != null || info.acont != null) {
                    return info;
                }
            }
        }
        for (FormatItem format : formats) {
            if (format != null) {
                TrackInfo info = from(format);
                if (info.langCode != null || info.acont != null) {
                    return info;
                }
            }
        }
        return null;
    }

    public static boolean isYoutubeAutoDub(@Nullable TrackInfo info) {
        return info != null && info.acont != null && info.acont.contains("dubbed-auto");
    }

    public static boolean isDubbed(@Nullable TrackInfo info) {
        return info != null && info.acont != null
                && (info.acont.contains("dubbed") || info.acont.contains("dubbed-auto"));
    }

    public static boolean isOriginalTrack(@Nullable TrackInfo info) {
        if (info == null) {
            return false;
        }
        if (info.acont == null) {
            return !isDubbed(info);
        }
        return "original".equals(info.acont) || "descriptive".equals(info.acont);
    }

    public static boolean isRussianLang(@Nullable String langCode) {
        return langCode != null && langCode.startsWith("ru");
    }

    public static boolean isEnglishLang(@Nullable String langCode) {
        return langCode != null && langCode.startsWith("en");
    }

    /** Russian source audio that does not need Yandex (original ru track). */
    public static boolean isRussianOriginal(@Nullable TrackInfo info) {
        if (info == null || !isRussianLang(info.langCode)) {
            return false;
        }
        if (isYoutubeAutoDub(info)) {
            return false;
        }
        return isOriginalTrack(info) || !isDubbed(info);
    }

    /** Content already in Russian via YouTube auto-dub. */
    public static boolean isYoutubeRussianAutoDub(@Nullable TrackInfo info) {
        return info != null && isRussianLang(info.langCode) && isYoutubeAutoDub(info);
    }

    public static boolean shouldStartYandex(@Nullable TrackInfo info) {
        if (info == null) {
            return false;
        }
        if (isRussianOriginal(info)) {
            return false;
        }
        if (isYoutubeRussianAutoDub(info)) {
            return false;
        }
        return info.langCode != null && !isRussianLang(info.langCode);
    }

    public static boolean hasNonRussianOriginalCandidate(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return false;
        }
        for (FormatItem format : formats) {
            TrackInfo info = from(format);
            if (info.langCode != null && !isRussianLang(info.langCode) && isOriginalTrack(info)) {
                return true;
            }
            if (info.langCode != null && !isRussianLang(info.langCode) && !isDubbed(info)) {
                return true;
            }
            // Legacy stream: bare "en" without xtags / (original) — still foreign source audio.
            if (isEnglishLang(info.langCode) && !hasRussianOriginalInList(formats)
                    && !hasYoutubeRussianAutoDubInList(formats)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasYoutubeRussianAutoDubInList(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return false;
        }
        for (FormatItem format : formats) {
            if (isYoutubeRussianAutoDub(from(format))) {
                return true;
            }
        }
        return false;
    }

    /**
     * YouTube served a single-language English stream (no xtags, no ru dub branch).
     * Typical yt-dlp label: {@code [en]} only — SmartTube Exo often has empty {@code Format.language}.
     */
    public static boolean isLegacyEnglishStream(@Nullable TrackInfo current,
                                                @Nullable List<FormatItem> formats) {
        if (formats == null || formats.isEmpty()) {
            return false;
        }
        if (hasRussianOriginalInList(formats) || hasYoutubeRussianAutoDubInList(formats)) {
            return false;
        }
        if (current != null && isEnglishLang(current.langCode) && !isYoutubeRussianAutoDub(current)) {
            return true;
        }
        return hasEnglishLangInList(formats);
    }

    /**
     * Last resort when Exo still exposes only bitrate rows without {@code Format.language}.
     */
    public static boolean isMetadataPoorNonRussianAudio(@Nullable List<FormatItem> formats) {
        return isMetadataPoorNonRussianAudioInternal(formats);
    }

    private static boolean hasEnglishLangInList(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return false;
        }
        for (FormatItem format : formats) {
            if (isEnglishLang(from(format).langCode)) {
                return true;
            }
        }
        return false;
    }

    /** Bitrate variants only — no lang in Exo yet, and no Russian markers in labels. */
    private static boolean isMetadataPoorNonRussianAudioInternal(@Nullable List<FormatItem> formats) {
        if (formats == null || formats.isEmpty()) {
            return false;
        }
        boolean sawAudio = false;
        boolean sawLang = false;
        for (FormatItem format : formats) {
            if (format == null || format.getType() != FormatItem.TYPE_AUDIO) {
                continue;
            }
            sawAudio = true;
            TrackInfo info = from(format);
            if (info.langCode != null) {
                sawLang = true;
                if (isRussianLang(info.langCode)) {
                    return false;
                }
            }
            if (info.rawLabel != null) {
                String raw = info.rawLabel.toLowerCase(Locale.US);
                if (raw.contains("russian") || raw.contains(" ru ") || raw.startsWith("ru ")
                        || raw.contains("(ru") || raw.contains("ru (")) {
                    return false;
                }
            }
        }
        return sawAudio && !sawLang;
    }

    /**
     * Mirrors manual button policy: start Yandex unless clearly Russian original content.
     */
    public static boolean shouldAutoStartLikeManual(@Nullable TrackInfo current,
                                                    @Nullable List<FormatItem> formats) {
        if (isLikelyRussianContent(current, formats)) {
            return false;
        }
        if (isRussianOriginal(current)) {
            return false;
        }
        if (isYoutubeRussianAutoDub(current)) {
            return hasNonRussianOriginalCandidate(formats);
        }
        if (shouldStartYandex(current)) {
            return true;
        }
        if (hasNonRussianOriginalCandidate(formats)) {
            return true;
        }
        if (current != null && current.langCode != null && !isRussianLang(current.langCode)) {
            return true;
        }
        if (isLegacyEnglishStream(current, formats)) {
            return true;
        }
        return false;
    }

    /** After metadata retries: same gate as the manual translate button. */
    public static boolean canStartLikeManualButton(@Nullable TrackInfo current) {
        return !isRussianOriginal(current);
    }

    public static boolean hasRussianOriginalInList(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return false;
        }
        for (FormatItem format : formats) {
            if (isRussianOriginal(from(format))) {
                return true;
            }
        }
        return false;
    }

    /** True when auto-translate should not run (Russian source content). */
    public static boolean isLikelyRussianContent(@Nullable TrackInfo current,
                                                 @Nullable List<FormatItem> formats) {
        if (isRussianOriginal(current)) {
            return true;
        }
        if (formats == null || formats.isEmpty()) {
            return false;
        }
        if (hasRussianOriginalInList(formats) && !hasNonRussianOriginalCandidate(formats)) {
            return true;
        }
        return onlyRussianOriginalTracks(formats);
    }

    private static boolean onlyRussianOriginalTracks(@Nullable List<FormatItem> formats) {
        if (formats == null || formats.isEmpty()) {
            return false;
        }
        boolean sawRussianOriginal = false;
        boolean sawNonRussian = false;
        for (FormatItem format : formats) {
            TrackInfo info = from(format);
            if (isRussianOriginal(info)) {
                sawRussianOriginal = true;
            }
            if (info.langCode != null && !isRussianLang(info.langCode)) {
                sawNonRussian = true;
            }
        }
        return sawRussianOriginal && !sawNonRussian;
    }

    @Nullable
    public static String formatTracksForLog(@Nullable List<FormatItem> formats) {
        if (formats == null || formats.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < formats.size(); i++) {
            FormatItem f = formats.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            TrackInfo info = from(f);
            sb.append(info.rawLabel != null ? info.rawLabel : "?");
            if (f != null && f.isSelected()) {
                sb.append("*");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Nullable
    public static FormatItem findYoutubeRussianAutoDub(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return null;
        }
        FormatItem fallbackRuDub = null;
        for (FormatItem format : formats) {
            TrackInfo info = from(format);
            if (isYoutubeRussianAutoDub(info)) {
                return format;
            }
            if (isRussianLang(info.langCode) && isDubbed(info)) {
                fallbackRuDub = format;
            }
        }
        return fallbackRuDub;
    }

    @Nullable
    public static FormatItem findBestOriginalForYandex(@Nullable List<FormatItem> formats) {
        if (formats == null) {
            return null;
        }
        FormatItem originalNonRu = null;
        FormatItem anyNonRu = null;
        FormatItem anyOriginal = null;
        for (FormatItem format : formats) {
            if (format == null) {
                continue;
            }
            TrackInfo info = from(format);
            if (isYoutubeAutoDub(info) || isYoutubeRussianAutoDub(info)) {
                continue;
            }
            if (isOriginalTrack(info) && info.langCode != null && !isRussianLang(info.langCode)) {
                originalNonRu = format;
            }
            if (info.langCode != null && !isRussianLang(info.langCode) && !isDubbed(info)) {
                anyNonRu = format;
            }
            if (isOriginalTrack(info) && anyOriginal == null) {
                anyOriginal = format;
            }
        }
        if (originalNonRu != null) {
            return originalNonRu;
        }
        if (anyNonRu != null) {
            return anyNonRu;
        }
        return anyOriginal;
    }

    public static boolean isSameFormat(@Nullable FormatItem a, @Nullable FormatItem b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getId() == b.getId();
    }

    @Nullable
    private static String extractLangCode(@Nullable String langPart) {
        if (langPart == null || langPart.isEmpty()) {
            return null;
        }
        String trimmed = langPart.trim();
        int dash = trimmed.indexOf('-');
        String code = dash > 0 ? trimmed.substring(0, dash) : trimmed;
        if (code.length() >= 2) {
            return code.toLowerCase(Locale.US);
        }
        return null;
    }
}
