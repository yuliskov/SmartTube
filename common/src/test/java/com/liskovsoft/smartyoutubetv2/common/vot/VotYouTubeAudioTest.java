package com.liskovsoft.smartyoutubetv2.common.vot;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VotYouTubeAudioTest {
    @Test
    public void selectsTargetDubAndRestoresOriginalTrack() {
        FormatItem original = audio("en (original)");
        FormatItem autoDub = audio("ru (dubbed-auto)");
        FormatItem authorDub = audio("ru (dubbed)");
        List<FormatItem> tracks = Arrays.asList(original, autoDub, authorDub);

        assertSame(authorDub, VotYouTubeAudio.dub(tracks, "ru"));
        assertSame(original, VotYouTubeAudio.original(tracks, original, "ru"));
        assertSame(original, VotYouTubeAudio.original(tracks, autoDub, "ru"));
        assertNull(VotYouTubeAudio.dub(tracks, "kk"));
        assertNull(VotYouTubeAudio.dub(tracks, "en"));
    }

    @Test
    public void doesNotClaimReversibleVoiceOverWithoutAnOriginalTrack() {
        FormatItem dub = audio("ru (dubbed-auto)");
        assertNull(VotYouTubeAudio.original(Arrays.asList(dub), dub, "ru"));
    }

    @Test
    public void restoresLegacyUntaggedOriginalFromSelectedDub() {
        FormatItem original = audio("en");
        FormatItem dub = audio("ru (dubbed-auto)");

        assertSame(original, VotYouTubeAudio.original(Arrays.asList(original, dub), dub, "ru"));
    }

    @Test
    public void doesNotGuessBetweenDifferentUntaggedLanguages() {
        FormatItem dub = audio("ru (dubbed-auto)");
        assertNull(VotYouTubeAudio.original(Arrays.asList(audio("en"), audio("es"), dub), dub, "ru"));
    }

    private static FormatItem audio(String language) {
        return ExoFormatItem.from(FormatItem.TYPE_AUDIO,
                TrackSelectorManager.RENDERER_INDEX_AUDIO, language, "mp4a.40.2",
                0, 0, 0, language, false, 128000, false);
    }
}
