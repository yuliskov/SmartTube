package com.liskovsoft.smartyoutubetv2.common.vot;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import java.util.List;

/** Selects a ready YouTube dub and a track to restore when voice-over is turned off. */
public final class VotYouTubeAudio {
    public static FormatItem dub(List<FormatItem> tracks, String language) {
        FormatItem automatic = null;
        if (tracks == null) return null;
        for (FormatItem track : tracks) {
            if (!usable(track) || !language.equals(VotAudioSource.languageCode(track.getLanguage()))
                    || track.getLanguage().contains("(original)")) continue;
            if (track.getLanguage().contains("(dubbed-auto)")) automatic = track;
            else return track; // Prefer an author-provided dub over automatic dubbing.
        }
        return automatic;
    }

    public static FormatItem original(List<FormatItem> tracks, FormatItem selected, String target) {
        if (usable(selected) && !target.equals(VotAudioSource.languageCode(selected.getLanguage())))
            return selected;
        if (tracks != null) for (FormatItem track : tracks) {
            if (usable(track) && track.getLanguage().contains("(original)")) return track;
        }
        return null;
    }

    private static boolean usable(FormatItem track) {
        return track != null && track.getType() == FormatItem.TYPE_AUDIO && track.getTrack() != null
                && track.getLanguage() != null;
    }

    private VotYouTubeAudio() {}
}
