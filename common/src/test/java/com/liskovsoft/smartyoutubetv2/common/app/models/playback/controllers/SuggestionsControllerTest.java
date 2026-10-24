package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SuggestionsControllerTest {
    @Before
    public void setUp() {
        Playlist.instance().clear();
    }

    @After
    public void tearDown() {
        Playlist.instance().clear();
    }

    @Test
    public void mixAutoplayPrefersInMixItemOverSection() throws Exception {
        Video current = video("current", "RDxxxx");
        Video mixNext = video("mixNext", "RDxxxx");
        current.nextMediaItem = SimpleMediaItem.from(mixNext);

        TestController controller = new TestController(current);
        controller.onNewVideo(current);

        Field field = SuggestionsController.class.getDeclaredField("mNextSectionVideo");
        field.setAccessible(true);
        field.set(controller, video("section", null));

        assertEquals("mixNext", controller.getNext().videoId);
    }

    private static Video video(String videoId, String playlistId) {
        Video video = new Video();
        video.videoId = videoId;
        video.playlistId = playlistId;
        return video;
    }

    private static class TestController extends SuggestionsController {
        private final PlaybackView mPlayer;
        private final Video mVideo;

        private TestController(Video video) {
            mPlayer = (PlaybackView) Proxy.newProxyInstance(
                    PlaybackView.class.getClassLoader(),
                    new Class<?>[]{PlaybackView.class},
                    (proxy, method, args) -> null);
            mVideo = video;
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }
    }
}
