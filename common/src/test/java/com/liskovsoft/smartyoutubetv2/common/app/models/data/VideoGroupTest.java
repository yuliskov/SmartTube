package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.youtubeapi.browse.v2.BrowseApiHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupTest {
    private VideoStateService mStates;

    @Before
    public void setUp() {
        GlobalPreferences.instance(RuntimeEnvironment.getApplication());
        mStates = VideoStateService.instance(RuntimeEnvironment.getApplication());
        mStates.clear();
        resetOtherFilters();
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, true);
    }

    @After
    public void tearDown() {
        mStates.clear();
        resetOtherFilters();
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, false);
    }

    @Test
    public void subscriptionsHideLocallyWatchedVideoWithoutServerProgress() {
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("watched", 0))).getSize());
    }

    @Test
    public void subscriptionsHideLocalProgressDespiteStaleServerProgress() {
        mStates.save(new State(video("watched", 0), 90_000, 100_000));
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("watched", 20))).getSize());
    }

    @Test
    public void subscriptionsHideServerWatchedVideoWithoutLocalHistory() {
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("watched", 90))).getSize());
    }

    @Test
    public void unknownDurationDoesNotHideVideo() {
        mStates.save(new State(video("unknown", 0), 100_000, -1));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("unknown", 0))).getSize());
    }

    @Test
    public void subscriptionsKeepUnwatchedAndPartiallyWatchedVideos() {
        mStates.save(new State(video("partial", 0), 80_000, 100_000));
        VideoGroup group = VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("partial", 0)));
        group.add(video("unwatched", 0));
        assertEquals(2, group.getSize());
    }

    @Test
    public void disabledOptionKeepsLocallyWatchedVideo() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, false);
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("watched", 0))).getSize());
    }

    @Test
    public void historyKeepsLocallyWatchedVideo() {
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_HISTORY, video("watched", 0))).getSize());
    }

    @Test
    public void subscriptionsKeepLiveStreams() {
        mStates.save(new State(video("live", 0), 100_000, 100_000));
        Video live = video("live", 100);
        live.isLive = true;
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, live)).getSize());
    }

    @Test
    public void continuationFiltersLocalHistoryAndRetainsPageMetadata() {
        VideoGroup group = VideoGroup.from(page(MediaGroup.TYPE_SUBSCRIPTIONS, video("unwatched", 0)));
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        MediaGroup continuation = page(MediaGroup.TYPE_SUBSCRIPTIONS, video("watched", 0));
        VideoGroup.from(group, continuation);
        assertEquals(1, group.getSize());
        assertSame(continuation, group.getMediaGroup());
    }

    @Test
    public void homeFiltersLocalHistoryInEveryRowWhenEnabled() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, true);
        mStates.save(new State(video("watched", 0), 90_000, 100_000));
        for (int row = 0; row < 3; row++) {
            assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_HOME, video("watched", 0)), null, row).getSize());
        }
    }

    @Test
    public void homeKeepsLocalHistoryWhenOptionDisabled() {
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_HOME, video("watched", 0))).getSize());
    }

    @Test
    public void watchLaterFiltersLocalHistoryByPlaylistId() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, true);
        mStates.save(new State(video("watched", 0), 90_000, 100_000));
        Video watched = video("watched", 0);
        watched.playlistId = BrowseApiHelper.WATCH_LATER_PLAYLIST;
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_CHANNEL_UPLOADS, watched)).getSize());
    }

    @Test
    public void watchLaterFiltersByGroupIdAndOnContinuation() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, true);
        mStates.save(new State(video("watched", 0), 90_000, 100_000));
        VideoGroup group = VideoGroup.from(page(MediaGroup.TYPE_CHANNEL_UPLOADS, video("watched", 0),
                BrowseApiHelper.WATCH_LATER_CHANNEL_ID));
        assertEquals(0, group.getSize());
        VideoGroup.from(group, page(MediaGroup.TYPE_CHANNEL_UPLOADS, video("watched", 0),
                BrowseApiHelper.WATCH_LATER_CHANNEL_ID));
        assertEquals(0, group.getSize());
    }

    @Test
    public void watchLaterOptionDoesNotHideVideosInOtherPlaylists() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, true);
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        Video watched = video("watched", 0);
        watched.playlistId = "otherPlaylist";
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_CHANNEL_UPLOADS, watched, "VLotherPlaylist")).getSize());
    }

    @Test
    public void watchLaterKeepsLocalHistoryWhenOptionDisabled() {
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        Video watched = video("watched", 0);
        watched.playlistId = BrowseApiHelper.WATCH_LATER_PLAYLIST;
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_CHANNEL_UPLOADS, watched)).getSize());
    }

    @Test
    public void notificationsHideAnyLocalPlaybackWhenEnabled() {
        GeneralData.instance(RuntimeEnvironment.getApplication()).setHideWatchedFromNotificationsEnabled(true);
        mStates.save(new State(video("watched", 0), 1_000, 100_000));
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_NOTIFICATIONS, video("watched", 0))).getSize());
        mStates.save(new State(video("unknownDuration", 0), 1_000, -1));
        assertEquals(0, VideoGroup.from(page(MediaGroup.TYPE_NOTIFICATIONS, video("unknownDuration", 0))).getSize());
    }

    @Test
    public void notificationsKeepVideosWithoutPlayback() {
        GeneralData.instance(RuntimeEnvironment.getApplication()).setHideWatchedFromNotificationsEnabled(true);
        mStates.save(new State(video("unwatched", 0), 0, 100_000));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_NOTIFICATIONS, video("unwatched", 0))).getSize());
    }

    @Test
    public void notificationsKeepLocalHistoryWhenOptionDisabled() {
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        assertEquals(1, VideoGroup.from(page(MediaGroup.TYPE_NOTIFICATIONS, video("watched", 0))).getSize());
    }

    @Test
    public void enabledFiltersDoNotHideLocalHistoryFromSearchOrHistory() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, true);
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, true);
        GeneralData.instance(RuntimeEnvironment.getApplication()).setHideWatchedFromNotificationsEnabled(true);
        mStates.save(new State(video("watched", 0), 100_000, 100_000));
        for (int type : new int[]{MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_CHANNEL_UPLOADS}) {
            assertEquals(1, VideoGroup.from(page(type, video("watched", 0))).getSize());
        }
    }

    private static void resetOtherFilters() {
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, false);
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, false);
        GeneralData.instance(RuntimeEnvironment.getApplication()).setHideWatchedFromNotificationsEnabled(false);
    }

    private static Video video(String id, float percentWatched) {
        Video video = new Video();
        video.videoId = id;
        video.percentWatched = percentWatched;
        return video;
    }

    private static MediaGroup page(int type, Video video) {
        return page(type, video, null);
    }

    private static MediaGroup page(int type, Video video, String channelId) {
        return (MediaGroup) Proxy.newProxyInstance(MediaGroup.class.getClassLoader(),
                new Class<?>[]{MediaGroup.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getType": return type;
                        case "getChannelId": return channelId;
                        case "getMediaItems": return Collections.singletonList(SimpleMediaItem.from(video));
                        case "isEmpty": return false;
                        default: return null;
                    }
                });
    }
}
