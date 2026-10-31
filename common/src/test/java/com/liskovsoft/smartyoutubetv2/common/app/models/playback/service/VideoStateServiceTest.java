package com.liskovsoft.smartyoutubetv2.common.app.models.playback.service;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateController;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class VideoStateServiceTest {
    private AppPrefs mPrefs;
    private VideoStateService mService;
    private final Account mAccountA = account("alpha");
    private final Account mAccountB = account("bravo");

    @Before
    public void setUp() throws Exception {
        Utils.sHandler.removeCallbacksAndMessages(null);
        resetSingleton(VideoStateService.class);
        resetSingleton(AppPrefs.class);
        Context context = RuntimeEnvironment.getApplication();
        mPrefs = AppPrefs.instance(context);
        mPrefs.onAccountChanged(mAccountA);
        mPrefs.setStateUpdaterData("");
        mService = VideoStateService.instance(context);
    }

    @After
    public void tearDown() throws Exception {
        Utils.sHandler.removeCallbacksAndMessages(null);
        mPrefs.removeListener(mService);
        resetSingleton(VideoStateService.class);
        resetSingleton(AppPrefs.class);
    }

    @Test
    public void switchingBeforePersistDelayFlushesToTheOldAccount() {
        VideoStateService.State stateA = state("video-alpha", 12_000);
        mService.save(stateA);
        String keyA = mPrefs.getStateUpdaterKey();

        mPrefs.onAccountChanged(mAccountB);

        assertEquals(stateA.toString(), mPrefs.getStateUpdaterData(keyA));
        assertTrue(mService.isEmpty());
        mService.save(state("video-bravo", 24_000));
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));

        mPrefs.onAccountChanged(mAccountA);
        assertEquals(12_000, mService.getByVideoId("video-alpha").positionMs);
        assertNull(mService.getByVideoId("video-bravo"));
        assertEquals(stateA.toString(), mPrefs.getStateUpdaterData(keyA));
    }

    @Test
    public void switchingBeforePendingClearDoesNotRestoreDeletedHistory() {
        mService.save(state("video-alpha", 12_000));
        mService.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        mService.clear();

        mPrefs.onAccountChanged(mAccountB);
        mPrefs.onAccountChanged(mAccountA);

        assertTrue(mService.isEmpty());
        assertEquals("", mPrefs.getStateUpdaterData());
    }

    @Test
    public void changingSettingsProfilesDoesNotDiscardPendingHistory() {
        mService.save(state("video-alpha", 12_000));
        long generation = mService.getSessionGeneration();

        mPrefs.enableMultiProfiles(!mPrefs.isMultiProfilesEnabled());

        assertEquals(12_000, mService.getByVideoId("video-alpha").positionMs);
        assertEquals(generation, mService.getSessionGeneration());
    }

    @Test
    public void switchingAwayAndBackInvalidatesOldPlaybackSession() {
        long generation = mService.getSessionGeneration();

        mPrefs.onAccountChanged(mAccountB);
        mPrefs.onAccountChanged(mAccountA);

        assertTrue(mService.getSessionGeneration() > generation);
    }

    @Test
    public void delayedPauseSavesPositionForTheOriginalAccount() throws Exception {
        TestController controller = new TestController(mService);
        controller.onPause();

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));

        assertEquals(25_000, mService.getByVideoId("playing-video").positionMs);
    }

    @Test
    public void delayedPauseDoesNotWriteOldVideoIntoTheNewAccount() throws Exception {
        TestController controller = new TestController(mService);
        controller.onPause();
        mPrefs.onAccountChanged(mAccountB);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));

        assertTrue(mService.isEmpty());
        mPrefs.onAccountChanged(mAccountA);
        assertNull(mService.getByVideoId("playing-video"));
    }

    @Test
    public void retryingTheOldVideoDoesNotTransferItsHistoryToTheNewAccount() throws Exception {
        TestController controller = new TestController(mService);
        mPrefs.onAccountChanged(mAccountB);

        controller.onNewVideo(controller.getVideo());
        controller.onPause();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));

        assertTrue(mService.isEmpty());
    }

    @Test
    public void explicitlySelectingTheSameQueuedVideoStartsHistoryForTheNewAccount() throws Exception {
        TestController controller = new TestController(mService);
        mPrefs.onAccountChanged(mAccountB);

        controller.getVideo().fromQueue = true;
        controller.prepareNewHistorySession();
        controller.onNewVideo(controller.getVideo());
        controller.onPause();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));

        assertEquals(25_000, mService.getByVideoId("playing-video").positionMs);
        mPrefs.onAccountChanged(mAccountA);
        assertNull(mService.getByVideoId("playing-video"));
    }

    private static class TestController extends VideoStateController {
        private final VideoStateService mService;
        private final Video mVideo = new Video();
        private final PlaybackView mPlayer;

        private TestController(VideoStateService service) throws Exception {
            mService = service;
            mVideo.videoId = "playing-video";
            mPlayer = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(),
                    new Class<?>[]{PlaybackView.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "containsMedia":
                            case "isEmbed": return true;
                            case "getPositionMs": return 25_000L;
                            case "getDurationMs": return 120_000L;
                            case "getSpeed":
                            case "getVolume": return 1.0f;
                            default: return null;
                        }
                    });
            // Start an embedded playback session, which saves locally without contacting history APIs.
            setField("mHistorySessionGeneration", service.getSessionGeneration());
            setField("mNewVideoTimeMs", System.currentTimeMillis());
        }

        @Override
        protected VideoStateService getStateService() {
            return mService;
        }

        @Override
        public Context getContext() {
            return RuntimeEnvironment.getApplication();
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }

        private void setField(String name, long value) throws Exception {
            Field field = VideoStateController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(this, value);
        }
    }

    private static VideoStateService.State state(String id, long positionMs) {
        Video video = new Video();
        video.videoId = id;
        return new VideoStateService.State(video, positionMs, 120_000);
    }

    private static Account account(String id) {
        return (Account) Proxy.newProxyInstance(Account.class.getClassLoader(), new Class<?>[]{Account.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getProfileId": return "youtube:email:" + id + "@example.test";
                        case "getName": return id;
                        case "getEmail": return id + "@example.test";
                        case "getId": return 0;
                        case "isSelected": return true;
                        case "isEmpty": return false;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: return null;
                    }
                });
    }

    private static void resetSingleton(Class<?> type) throws Exception {
        Field field = type.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
