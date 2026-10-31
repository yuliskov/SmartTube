package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BrowsePresenterHistoryTest {
    @Before
    @After
    public void clearPlaylistState() {
        Playlist.instance().clear();
        Playlist.instance().onNewSession();
    }

    @Test
    public void accountSwitchDiscardsPendingWatchedMarkersEvenWithoutABrowseView() {
        BrowsePresenter presenter = BrowsePresenter.instance(RuntimeEnvironment.getApplication());
        Video video = new Video();
        video.videoId = "synthetic-video";
        video.percentWatched = 50;
        Playlist.instance().setCurrent(video);
        Playlist.instance().sync(video);

        presenter.onAccountChanged(null);

        assertTrue(Playlist.instance().getChangedItems().isEmpty());
        assertSame(video, Playlist.instance().getCurrent());
    }
}
