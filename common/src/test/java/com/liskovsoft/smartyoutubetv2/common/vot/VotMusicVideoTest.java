package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VotMusicVideoTest {
    @Test
    public void recognizesMusicFromWebMetadataWhenTvMetadataIsMissing() throws Exception {
        assertTrue(VotMusicVideo.isMusicResponse("{\"microformat\":{\"playerMicroformatRenderer\":"
                + "{\"category\":\"Music\"}}}"));
        assertTrue(VotMusicVideo.isMusicResponse("{\"videoDetails\":{\"musicVideoType\":"
                + "\"MUSIC_VIDEO_TYPE_OMV\"}}"));
        assertFalse(VotMusicVideo.isMusicResponse("{\"microformat\":{\"playerMicroformatRenderer\":"
                + "{\"category\":\"Education\"}}}"));
        assertFalse(VotMusicVideo.isMusicResponse("{}"));
    }
}
