package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SponsorBlockData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class SponsorBlockControllerTest {
    private SponsorBlockData mData;
    private TestController mController;
    private TestDialogPresenter mDialog;
    private TestScheduler mScheduler;
    private List<SponsorSegment> mSegments;
    private final List<Set<String>> mRequests = new ArrayList<>();
    private Observable<List<SponsorSegment>> mResponse;

    @Before
    public void setUp() throws Exception {
        resetSingleton(AppPrefs.class);
        resetSingleton(SponsorBlockData.class);
        resetSingleton(PlayerTweaksData.class);
        GlobalPreferences.instance(RuntimeEnvironment.getApplication());
        mData = SponsorBlockData.instance(RuntimeEnvironment.getApplication());
        mData.setSponsorBlockEnabled(true);
        mData.enableAltServer(false);
        mData.setIgnoredDurationMs(0);
        for (String category : mData.getAllCategories()) {
            mData.setAction(category, SponsorBlockData.ACTION_DO_NOTHING);
            mData.disableColorMarker(category);
        }
        mData.setAction(SponsorSegment.CATEGORY_SPONSOR, SponsorBlockData.ACTION_SKIP_ONLY);

        mScheduler = new TestScheduler();
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> mScheduler);
        RxJavaPlugins.setNewThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> mScheduler);

        mDialog = new TestDialogPresenter();
        setField(AppDialogPresenter.class, null, "sInstance", mDialog);
        mController = new TestController();
        mSegments = Collections.singletonList(segment(SponsorSegment.CATEGORY_SPONSOR, 10_000, 30_000));
        MediaItemService service = (MediaItemService) Proxy.newProxyInstance(MediaItemService.class.getClassLoader(),
                new Class<?>[]{MediaItemService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getSponsorSegmentsObserve")) {
                        @SuppressWarnings("unchecked") Set<String> categories = (Set<String>) args[1];
                        mRequests.add(new HashSet<>(categories));
                        if (mResponse != null) {
                            return mResponse;
                        }
                        List<SponsorSegment> result = new ArrayList<>();
                        for (SponsorSegment segment : mSegments) {
                            if (categories.contains(segment.getCategory())) {
                                result.add(segment);
                            }
                        }
                        return Observable.just(result);
                    }
                    throw new AssertionError("Unexpected service call: " + method.getName());
                });
        setField(SponsorBlockController.class, mController, "mMediaItemService", service);
    }

    @After
    public void tearDown() throws Exception {
        if (mController != null) {
            mController.onEngineReleased();
        }
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        resetSingleton(AppDialogPresenter.class);
        resetSingleton(SponsorBlockData.class);
        resetSingleton(PlayerTweaksData.class);
        resetSingleton(AppPrefs.class);
    }

    @Test
    public void markerOnlyCategoryDoesNotSkipInPictureInPicture() {
        mController.mPictureInPicture = true;
        loadMarkerOnlySegment();
        tick();

        assertTrue(mController.mSeeks.isEmpty());
        assertEquals(1, mController.mMarkers.size());
    }

    @Test
    public void markerOnlyCategoryDoesNotSkipInEmbeddedPlayback() {
        mController.mEmbedded = true;
        loadMarkerOnlySegment();
        tick();

        assertTrue(mController.mSeeks.isEmpty());
    }

    @Test
    public void markerOnlyCategoryDoesNotHideAnOverlappingEnabledSkip() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_FILLER);
        mSegments = Arrays.asList(segment(SponsorSegment.CATEGORY_FILLER, 10_000, 40_000),
                segment(SponsorSegment.CATEGORY_SPONSOR, 10_000, 30_000));
        load();
        tick();

        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void manualButtonStillSkipsMarkerOnlyIntervals() {
        loadMarkerOnlySegment();

        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_ON);

        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void unchangedRequestReusesCacheButEnablingACategoryFetchesAgain() {
        load();
        load();
        assertEquals(1, mRequests.size());
        assertFalse(mRequests.get(0).contains(SponsorSegment.CATEGORY_FILLER));

        mData.enableColorMarker(SponsorSegment.CATEGORY_FILLER);
        load();

        assertEquals(2, mRequests.size());
        assertTrue(mRequests.get(1).contains(SponsorSegment.CATEGORY_FILLER));
    }

    @Test
    public void changingServerFetchesAgainForTheSameVideo() {
        load();
        mData.enableAltServer(true);
        load();

        assertEquals(2, mRequests.size());
    }

    @Test
    public void failedRequestCanBeRetriedForTheSameVideo() {
        mResponse = Observable.error(new IllegalStateException("Synthetic request failure"));
        load();
        mResponse = null;
        load();
        tick();

        assertEquals(2, mRequests.size());
        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void failureAfterUnsubscribingIsRetriedWhenSponsorBlockIsEnabledAgain() {
        PublishSubject<List<SponsorSegment>> response = PublishSubject.create();
        mResponse = response;
        load();
        mData.setSponsorBlockEnabled(false);
        load();
        response.onError(new IllegalStateException("Synthetic request failure"));

        mResponse = null;
        mData.setSponsorBlockEnabled(true);
        load();
        tick();

        assertEquals(2, mRequests.size());
        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void oldRequestFailureDoesNotInvalidateAReplacementCache() {
        PublishSubject<List<SponsorSegment>> response = PublishSubject.create();
        mResponse = response;
        load();
        mData.enableAltServer(true);
        mResponse = null;
        load();

        response.onError(new IllegalStateException("Synthetic old request failure"));
        load();
        tick();

        assertEquals(2, mRequests.size());
        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void newExcludedVideoDoesNotReuseThePreviousVideosManualSkip() {
        load();
        mController.mVideo = video("second-video", "second-channel");
        mData.excludeChannel("second-channel");
        mController.onNewVideo(mController.mVideo);
        load();

        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_OFF);

        assertTrue(mController.mSeeks.isEmpty());
        assertFalse(mData.isChannelExcluded("second-channel"));
    }

    @Test
    public void disablingSponsorBlockClearsThePreviousManualSkip() {
        load();
        mData.setSponsorBlockEnabled(false);
        load();

        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_OFF);

        assertTrue(mController.mSeeks.isEmpty());
        assertTrue(mData.isSponsorBlockEnabled());
    }

    @Test
    public void lateChannelExclusionClearsSegmentsAndStopsPolling() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        load();
        mData.excludeChannel("first-channel");
        MediaItemMetadata metadata = (MediaItemMetadata) Proxy.newProxyInstance(MediaItemMetadata.class.getClassLoader(),
                new Class<?>[]{MediaItemMetadata.class}, (proxy, method, args) ->
                        method.getName().equals("getChannelId") ? "first-channel" : null);

        mController.onMetadata(metadata);
        tick();

        assertTrue(mController.mMarkers.isEmpty());
        assertTrue(mController.mSeeks.isEmpty());
        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_OFF);
        assertTrue(mController.mSeeks.isEmpty());
        assertFalse(mData.isChannelExcluded("first-channel"));
    }

    @Test
    public void previousVideosLateResponseDoesNotRestoreSegments() {
        PublishSubject<List<SponsorSegment>> response = PublishSubject.create();
        mResponse = response;
        load();
        mController.mVideo = video("second-video", "second-channel");
        mController.onNewVideo(mController.mVideo);

        response.onNext(mSegments);
        tick();

        assertTrue(mController.mMarkers.isEmpty());
        assertTrue(mController.mSeeks.isEmpty());
        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_ON);
        assertFalse(mData.isSponsorBlockEnabled());
    }

    @Test
    public void changingVideoClosesItsSkipDialogAndInvalidatesTheOldButton() {
        mData.setAction(SponsorSegment.CATEGORY_SPONSOR, SponsorBlockData.ACTION_SHOW_DIALOG);
        load();
        tick();
        assertTrue(mDialog.mShown);
        OptionItem oldAccept = mDialog.mOption;

        mController.mVideo = video("second-video", "second-channel");
        mController.onNewVideo(mController.mVideo);
        assertFalse(mDialog.mShown);
        oldAccept.onSelect(true);

        assertTrue(mController.mSeeks.isEmpty());
    }

    @Test
    public void clearingSegmentsDoesNotCloseAnUnrelatedDialog() {
        mDialog.mShown = true;
        mDialog.setId(999);

        mController.onNewVideo(mController.mVideo);

        assertTrue(mDialog.mShown);
    }

    @Test
    public void refreshingMarkersKeepsChaptersWithoutDuplicatingThem() throws Exception {
        ChapterItem chapter = (ChapterItem) Proxy.newProxyInstance(ChapterItem.class.getClassLoader(),
                new Class<?>[]{ChapterItem.class}, (proxy, method, args) ->
                        method.getName().equals("getStartTimeMs") ? 60_000L : null);
        setField(SuggestionsController.class, mController.mSuggestions, "mChapters", Collections.singletonList(chapter));
        mData.enableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        load();
        assertEquals(2, mController.mMarkers.size());

        mData.disableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        load();
        load();

        assertEquals(1, mController.mMarkers.size());
        assertEquals(0.5f, mController.mMarkers.get(0).startProgress, 0.0001f);
    }

    @Test
    public void highlightIsAVisiblePointAndNeverAnAutomaticOrManualSkip() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_POI_HIGHLIGHT);
        mSegments = Collections.singletonList(segment(SponsorSegment.CATEGORY_POI_HIGHLIGHT, 10_000, 10_000));
        load();
        tick();
        assertEquals(1, mController.mMarkers.size());
        SeekBarSegment marker = mController.mMarkers.get(0);
        assertTrue(marker.endProgress > marker.startProgress);
        assertTrue(marker.endProgress <= 1);

        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_ON);

        assertTrue(mController.mSeeks.isEmpty());
    }

    @Test
    public void unsupportedActionsCannotSeekButMissingLegacyActionStillCan() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        mSegments = Collections.singletonList(segment(SponsorSegment.CATEGORY_SPONSOR, 10_000, 30_000, "mute"));
        load();
        tick();
        assertTrue(mController.mMarkers.isEmpty());
        mController.onButtonClicked(R.id.action_content_block, PlayerUI.BUTTON_ON);
        assertTrue(mController.mSeeks.isEmpty());

        mData.setSponsorBlockEnabled(true);
        mController.mVideo = video("legacy-video", "first-channel");
        mController.onNewVideo(mController.mVideo);
        mSegments = Collections.singletonList(segment(SponsorSegment.CATEGORY_SPONSOR, 10_000, 30_000, null));
        load();
        tick();
        assertEquals(Collections.singletonList(30_000L), mController.mSeeks);
    }

    @Test
    public void markersRejectInvalidRangesAndClampToKnownDuration() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_SPONSOR);
        mSegments = Arrays.asList(segment(SponsorSegment.CATEGORY_SPONSOR, -1, 30_000),
                segment(SponsorSegment.CATEGORY_SPONSOR, 20_000, 10_000),
                segment(SponsorSegment.CATEGORY_SPONSOR, 120_000, 130_000),
                segment(SponsorSegment.CATEGORY_SPONSOR, 100_000, 130_000));
        load();

        assertEquals(1, mController.mMarkers.size());
        assertEquals(1f, mController.mMarkers.get(0).endProgress, 0);
        mController.mDurationMs = 0;
        load();
        assertTrue(mController.mMarkers.isEmpty());
        tick();
        assertTrue(mController.mSeeks.isEmpty());
    }

    private void loadMarkerOnlySegment() {
        mData.enableColorMarker(SponsorSegment.CATEGORY_FILLER);
        mSegments = Collections.singletonList(segment(SponsorSegment.CATEGORY_FILLER, 10_000, 30_000));
        load();
    }

    private void load() {
        mController.onVideoLoaded(mController.mVideo);
    }

    private void tick() {
        mScheduler.advanceTimeBy(1, TimeUnit.SECONDS);
    }

    private static class TestController extends SponsorBlockController {
        private Video mVideo = video("first-video", "first-channel");
        private long mPositionMs = 10_000;
        private long mDurationMs = 120_000;
        private boolean mPictureInPicture;
        private boolean mEmbedded;
        private final List<Long> mSeeks = new ArrayList<>();
        private final List<SeekBarSegment> mMarkers = new ArrayList<>();
        private final PlaybackView mPlayer = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(),
                new Class<?>[]{PlaybackView.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isPlaying": return true;
                        case "isInPIPMode": return mPictureInPicture;
                        case "isEmbed": return mEmbedded;
                        case "isSuggestionsShown": return false;
                        case "getPositionMs": return mPositionMs;
                        case "getDurationMs": return mDurationMs;
                        case "getSpeed": return 1f;
                        case "setPositionMs":
                            mPositionMs = (long) args[0];
                            mSeeks.add(mPositionMs);
                            return null;
                        case "setSeekBarSegments":
                            if (args[0] == null) {
                                mMarkers.clear();
                            } else {
                                @SuppressWarnings("unchecked") List<SeekBarSegment> markers = (List<SeekBarSegment>) args[0];
                                mMarkers.addAll(markers);
                            }
                            return null;
                        default: return null;
                    }
                });
        private final SuggestionsController mSuggestions = new SuggestionsController() {
            @Override
            public PlaybackView getPlayer() {
                return mPlayer;
            }

            @Override
            public Context getContext() {
                return RuntimeEnvironment.getApplication();
            }
        };

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }

        @Override
        public Context getContext() {
            return RuntimeEnvironment.getApplication();
        }

        @Override
        protected <T extends PlayerEventListener> T getController(Class<T> clazz) {
            return clazz == SuggestionsController.class ? clazz.cast(mSuggestions) : null;
        }
    }

    private static class TestDialogPresenter extends AppDialogPresenter {
        private boolean mShown;
        private OptionItem mOption;

        private TestDialogPresenter() {
            super(RuntimeEnvironment.getApplication());
        }

        @Override
        public boolean isDialogShown() {
            return mShown;
        }

        @Override
        public void appendSingleButton(OptionItem option) {
            mOption = option;
        }

        @Override
        public void showDialog(CharSequence title) {
            mShown = true;
        }

        @Override
        public void closeDialog() {
            mShown = false;
        }

        @Override
        public void goBack() {
            mShown = false;
        }
    }

    private static Video video(String id, String channel) {
        Video video = new Video();
        video.videoId = id;
        video.channelId = channel;
        return video;
    }

    private static SponsorSegment segment(String category, long startMs, long endMs) {
        return segment(category, startMs, endMs, SponsorSegment.ACTION_SKIP);
    }

    private static SponsorSegment segment(String category, long startMs, long endMs, String action) {
        return new SponsorSegment() {
            @Override public long getStartMs() { return startMs; }
            @Override public long getEndMs() { return endMs; }
            @Override public String getCategory() { return category; }
            @Override public String getAction() { return action; }
        };
    }

    private static void resetSingleton(Class<?> type) throws Exception {
        setField(type, null, "sInstance", null);
    }

    private static void setField(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
