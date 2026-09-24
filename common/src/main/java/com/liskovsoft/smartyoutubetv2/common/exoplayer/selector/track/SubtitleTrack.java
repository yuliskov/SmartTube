package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.videoinfo.models.TranslatedCaptionTrack;

import java.util.Locale;
import java.util.regex.Pattern;

public class SubtitleTrack extends MediaTrack {
    private static final Pattern AUTO_PATTERN = Pattern.compile(" \\(.*\\)$"); // May have mismatches e.g. 'English (United Kingdom)'
    private static final Pattern TRIM_PATTERN1 = Pattern.compile(" \\(.*\\) - .*");
    private static final Pattern TRIM_PATTERN2 = Pattern.compile(" - .*");
    private static final Pattern MARKER_PATTERN = Pattern.compile(".$");

    public SubtitleTrack(int rendererIndex) {
        super(rendererIndex);
    }

    @Override
    public int inBounds(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        if (track2 == null || track2.format == null) {
            return 1;
        }

        // Exact match by format ID takes highest priority
        if (format.id != null && track2.format.id != null) {
            if (format.id.equalsIgnoreCase(track2.format.id)) {
                return 0;
            }
        }

        // Exact full language match (case-insensitive)
        if (format.language != null && track2.format.language != null
                && format.language.equalsIgnoreCase(track2.format.language)) {
            boolean thisAuto = isAuto(format.language);
            boolean otherAuto = isAuto(track2.format.language);
            if (thisAuto == otherAuto) {
                return 0;
            }
        }

        // Check if candidate matches base language or language code (e.g. English, English (United Kingdom), en-GB, en)
        if (isLanguageMatch(format.language, track2.format.language)
                || isLanguageMatch(format.id, track2.format.language)
                || isLanguageMatch(format.language, track2.format.id)
                || isLanguageMatch(format.id, track2.format.id)) {
            boolean originAuto = isAuto(format.language);
            boolean candidateAuto = isAuto(track2.format.language);

            // If user originally wanted non-auto subs, prefer non-auto candidate
            if (!originAuto && candidateAuto) {
                return 1; // Fallback match
            }

            return 0; // High-priority match
        }

        // Fallback partial prefix match
        if (format.language != null && track2.format.language != null) {
            String trimmedOrigin = trim(format.language);
            String trimmedCandidate = trim(track2.format.language);
            if (Helpers.startsWith(trimmedCandidate, trimmedOrigin) || Helpers.startsWith(trimmedOrigin, trimmedCandidate)) {
                return !isAuto(track2.format.language) ? 0 : 1;
            }
        }

        return -1;
    }

    @Override
    public int compare(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        if (track2 == null || track2.format == null) {
            return 1;
        }

        boolean thisAuto = isAuto(format.language);
        boolean otherAuto = isAuto(track2.format.language);

        // 1. Prefer manual/creator subs over auto-generated
        if (!thisAuto && otherAuto) {
            return 1;
        } else if (thisAuto && !otherAuto) {
            return -1;
        }

        // 2. Prefer animated subs over non-animated subs
        boolean thisAnimated = isAnimated(format.language);
        boolean otherAnimated = isAnimated(track2.format.language);

        if (thisAnimated && !otherAnimated) {
            return 1;
        } else if (!thisAnimated && otherAnimated) {
            return -1;
        }

        // 3. Prefer SRV3 format over others
        boolean thisSrv3 = MimeTypes.APPLICATION_YTSRV3.equals(format.sampleMimeType);
        boolean otherSrv3 = MimeTypes.APPLICATION_YTSRV3.equals(track2.format.sampleMimeType);

        if (thisSrv3 && !otherSrv3) {
            return 1;
        } else if (!thisSrv3 && otherSrv3) {
            return -1;
        }

        return 0;
    }

    public static String getBaseLanguage(String language) {
        if (language == null) {
            return "";
        }

        String cleaned = trimMarker(language).trim();

        // Remove " - animated", " - standard", " - CC", etc.
        int dashIdx = cleaned.indexOf(" - ");
        if (dashIdx != -1) {
            cleaned = cleaned.substring(0, dashIdx).trim();
        }

        // Remove " (United Kingdom)", " (auto-generated)", " (US)", etc.
        int parenIdx = cleaned.indexOf(" (");
        if (parenIdx != -1) {
            cleaned = cleaned.substring(0, parenIdx).trim();
        }

        // Remove leading dot like .en-GB
        if (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }

        int subTag = cleaned.indexOf('-');
        if (subTag != -1) {
            cleaned = cleaned.substring(0, subTag);
        }

        int subTag2 = cleaned.indexOf('_');
        if (subTag2 != -1) {
            cleaned = cleaned.substring(0, subTag2);
        }

        return cleaned.toLowerCase(Locale.ROOT);
    }

    public static boolean isLanguageMatch(String lang1, String lang2) {
        if (lang1 == null || lang2 == null) {
            return false;
        }

        String base1 = getBaseLanguage(lang1);
        String base2 = getBaseLanguage(lang2);

        if (base1.isEmpty() || base2.isEmpty()) {
            return false;
        }

        if (base1.equals(base2)) {
            return true;
        }

        // Check 2-letter / 3-letter ISO code match (e.g. "en" and "english", "hu" and "hungarian")
        return isIsoCodeMatch(base1, base2) || isIsoCodeMatch(base2, base1);
    }

    private static boolean isIsoCodeMatch(String code, String name) {
        if (code.length() <= 3 && name.length() > 3) {
            try {
                Locale locale = new Locale(code);
                String displayEn = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(Locale.ROOT);
                if (name.startsWith(displayEn) || displayEn.startsWith(name)) {
                    return true;
                }
                String displaySelf = locale.getDisplayLanguage(locale).toLowerCase(Locale.ROOT);
                if (name.startsWith(displaySelf) || displaySelf.startsWith(name)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore locale parse failure
            }
            return name.startsWith(code);
        }
        return false;
    }

    public static boolean isAnimated(String language) {
        if (language == null) {
            return false;
        }
        String lower = language.toLowerCase(Locale.ROOT);
        return lower.contains("animated") || lower.contains("karaoke");
    }

    /**
     * Autogenerated subs by the user <br/>
     * NOTE: Has an exceptions, like English (United Kingdom)
     */
    private static boolean isAutoUser(String language) {
        if (language == null) {
            return false;
        }

        return Helpers.matchAll(language, AUTO_PATTERN);
    }

    /**
     * Remove autogenerated and other stuff
     */
    public static String trim(String language) {
        if (language == null) {
            return null;
        }

        return trimMarker(Helpers.replace(Helpers.replace(language, TRIM_PATTERN1, ""), TRIM_PATTERN2, "")); // english - us bla -> english
    }

    public static String trimIfAuto(String language) {
        return isAuto(language) ? trim(language) : language;
    }

    /**
     * NOTE: Breaks Portuguese (Portugal) but fixes other similar languages
     */
    private static String trimAuto(String language) {
        if (language == null) {
            return null;
        }

        return Helpers.replace(trimMarker(language), AUTO_PATTERN, ""); // english (us) bla -> english
    }

    /**
     * Removes auto translate marker
     */
    private static String trimMarker(String language) {
        if (language != null && language.endsWith(TranslatedCaptionTrack.TRANSLATE_MARKER)) {
            return Helpers.replace(language, MARKER_PATTERN, "");
        } else {
            return language;
        }
    }

    public static boolean isAuto(String language) {
        if (language == null) {
            return false;
        }
        return hasMarker(language)
                || language.toLowerCase(Locale.ROOT).contains("auto-generated")
                || language.toLowerCase(Locale.ROOT).contains("automatically generated");
    }

    private static boolean hasMarker(String language) {
        return language != null && language.endsWith(TranslatedCaptionTrack.TRANSLATE_MARKER);
    }
}
