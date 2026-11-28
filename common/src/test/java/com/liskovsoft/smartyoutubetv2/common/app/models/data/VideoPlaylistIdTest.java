package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoPlaylistIdTest {
    @Test
    public void resolvesSearchPlaylistBrowseId() {
        Video video = new Video();
        video.channelId = "VLPLabcdEFGH1234";
        assertEquals("PLabcdEFGH1234", video.resolvePlaylistId());
        assertFalse(video.isSearchPlaylistCard());

        VideoGroup group = VideoGroup.from(video);
        group.setType(MediaGroup.TYPE_SEARCH);
        video.setGroup(group);
        assertTrue(video.isSearchPlaylistCard());
        video.videoId = "video";
        assertFalse(video.isSearchPlaylistCard());

        video.playlistId = "PLexplicit";
        assertEquals("PLexplicit", video.resolvePlaylistId());
        video.videoId = null;
        video.playlistId = null;
        video.channelId = "UCxxxx";
        video.playlistParams = "params/token";
        assertNull(video.resolvePlaylistId());
        assertFalse(video.isSearchPlaylistCard());
    }
}
