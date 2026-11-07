package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerDataTest {
    private static final String DUTCH_CHANNEL = "UC_dutch_channel";
    private static final String ENGLISH_CHANNEL = "UC_english_channel";
    private static final String UNKNOWN_CHANNEL = "UC_unknown_channel";
    private static final FormatItem DUTCH = subtitle("nl", "Dutch");
    private static final FormatItem ENGLISH_AUTO = subtitle("en", "English (auto-generated)");
    private PlayerData mPlayerData;

    @Before
    public void setUp() {
        mPlayerData = PlayerData.instance(RuntimeEnvironment.getApplication());
        mPlayerData.setSubtitlesPerChannelEnabled(true);
    }

    @Test
    public void eachChannelRestoresItsOwnSubtitleTrack() {
        selectSubtitle(DUTCH_CHANNEL, DUTCH);
        selectSubtitle(ENGLISH_CHANNEL, ENGLISH_AUTO);

        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormat(DUTCH_CHANNEL));
        assertTrack(ENGLISH_AUTO, mPlayerData.getLastSubtitleFormat(ENGLISH_CHANNEL));
        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormats(DUTCH_CHANNEL).get(0));
    }

    @Test
    public void channelWithoutSavedTrackFallsBackToLastUsedTrack() {
        selectSubtitle(DUTCH_CHANNEL, DUTCH);

        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormat(UNKNOWN_CHANNEL));
        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormat(null));
    }

    @Test
    public void disablingChannelSticksAndKeepsItsTrack() {
        // Select twice. Used to add the channel twice so disabling didn't stick.
        selectSubtitle(DUTCH_CHANNEL, DUTCH);
        selectSubtitle(DUTCH_CHANNEL, DUTCH);
        selectSubtitle(ENGLISH_CHANNEL, ENGLISH_AUTO);

        mPlayerData.disableSubtitlesPerChannel(DUTCH_CHANNEL);

        assertFalse(mPlayerData.isSubtitlesPerChannelEnabled(DUTCH_CHANNEL));
        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormat(DUTCH_CHANNEL));
    }

    @Test
    public void channelTracksSurviveRestore() {
        selectSubtitle(DUTCH_CHANNEL, DUTCH);
        selectSubtitle(ENGLISH_CHANNEL, ENGLISH_AUTO);

        mPlayerData.persistNow();
        shadowOf(Looper.getMainLooper()).idle();
        mPlayerData.onProfileChanged(); // re-reads the persisted data

        assertTrue(mPlayerData.isSubtitlesPerChannelEnabled(DUTCH_CHANNEL));
        assertTrack(DUTCH, mPlayerData.getLastSubtitleFormat(DUTCH_CHANNEL));
        assertTrack(ENGLISH_AUTO, mPlayerData.getLastSubtitleFormat(ENGLISH_CHANNEL));
    }

    /**
     * Same calls as the player makes when the user picks a track
     */
    private void selectSubtitle(String channelId, FormatItem format) {
        mPlayerData.enableSubtitlesPerChannel(channelId, format);
        mPlayerData.setFormat(format);
    }

    private static void assertTrack(FormatItem expected, FormatItem actual) {
        assertEquals(expected.getLanguage(), actual.getLanguage());
    }

    private static FormatItem subtitle(String id, String language) {
        return ExoFormatItem.from(FormatItem.TYPE_SUBTITLE, TrackSelectorManager.RENDERER_INDEX_SUBTITLE,
                id, null, -1, -1, -1, language, false, -1, false);
    }
}
