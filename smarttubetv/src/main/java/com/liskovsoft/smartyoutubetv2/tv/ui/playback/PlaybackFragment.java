package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.app.Activity;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.DebugInfoManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.SeekModePlaybackFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.SurfacePlaybackFragmentGlueHost;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.BackboneQueueNavigator;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.previewtimebar.StoryboardSeekDataProvider;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue.OnActionClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat.LiveChatView;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time.DateTimeView;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time.EndingTimeView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to
 * {@link VideoPlayerGlue}.
 */
public class PlaybackFragment extends SeekModePlaybackFragment implements PlaybackView, PlayerManager {
    private static final String TAG = PlaybackFragment.class.getSimpleName();
    private static final int UPDATE_DELAY_MS = 100;
    private static final int SUGGESTIONS_START_INDEX = 1;
    private VideoPlayerGlue mPlayerGlue;
    private SimpleExoPlayer mPlayer;
    private PlaybackPresenter mPlaybackPresenter;
    private ArrayObjectAdapter mRowsAdapter;
    private ListRowPresenter mRowPresenter;
    private VideoCardPresenter mCardPresenter;
    private ShortsCardPresenter mShortsPresenter;
    private Map<Integer, VideoGroupObjectAdapter> mMediaGroupAdapters;
    private PlayerEventListener mEventListener;
    private PlayerController mExoPlayerController;
    private ExoPlayerInitializer mPlayerInitializer;
    private SubtitleManager mSubtitleManager;
    private DebugInfoManager mDebugInfoManager;
    private UriBackgroundManager mBackgroundManager;
    private RowsSupportFragment mRowsSupportFragment;
    private boolean mIsUIAnimationsEnabled = false;
    private boolean mIsLikesCounterEnabled = true;
    private int mPlaybackMode = PlayerEngine.BACKGROUND_MODE_DEFAULT;
    private MediaSessionCompat mMediaSession;
    private MediaSessionConnector mMediaSessionConnector;
    private long mResumeTimeMs;
    private Boolean mIsControlsShownPreviously;
    private Video mPendingFocus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // trying to fix bug with presets

        mMediaGroupAdapters = new HashMap<>();
        mBackgroundManager = getLeanbackActivity().getBackgroundManager();
        mBackgroundManager.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.player_background));
        mPlayerInitializer = new ExoPlayerInitializer(getContext());
        mExoPlayerController = new ExoPlayerController(getContext());

        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.setView(this);

        initPresenters();
        setupEventListeners();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPlaybackPresenter.onViewInitialized();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        // We should use internal progress manager because it's used in many places like Exo engine etc.
        // ProgressBar.setRootView already called at this moment.
        ProgressBarManager.setup(getProgressBarManager(), (ViewGroup) root);

        return root;
    }

    /**
     * Update background depending what's shown: controls or suggestions
     */
    private void updatePlayerBackground() {
        if (isOverlayShown()) {
            setBackgroundResource(isSuggestionsShown() ? R.drawable.player_background_suggestions : R.drawable.player_background_controls);
        }
    }

    // NOTE: depending of SDK version Start/Stop may be called with delay (SDK_INT > 23) or not called at all (PIP/Dialogs)!

    /**
     * Not called when using PIP or Dialogs on API >= 24
     */
    @Override
    public void onStart() {
        super.onStart();

        // Fix controls pop-up on Activity start/resume.
        // Should be called on earlier stage.
        hideControlsOverlay(true);

        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    /**
     * Not called when using PIP or Dialogs on API >= 24
     */
    @Override
    public void onStop() {
        super.onStop();

        if (Util.SDK_INT > 23) {
            maybeReleasePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();
        }

        // NOTE: don't move this into another place! Multiple components rely on it.
        mEventListener.onViewResumed();

        showHideWidgets(true); // PIP mode fix

        mResumeTimeMs = System.currentTimeMillis();
    }

    @Override
    public void onPause() {
        super.onPause();

        // NOTE: don't move this into another place! Multiple components rely on it.
        mEventListener.onViewPaused();

        if (Util.SDK_INT <= 23) {
            maybeReleasePlayer();
        }

        showHideWidgets(false); // PIP mode fix
    }

    public void onDispatchKeyEvent(KeyEvent event) {
        // NOP
    }

    public void onDispatchTouchEvent(MotionEvent event) {
        applyTickle(event);
    }

    public void onDispatchGenericMotionEvent(MotionEvent event) {
        applyTickle(event);
    }

    private void applyTickle(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            tickle(); // show Player UI
        }

        mEventListener.onKeyDown(-1); // reset ui timer
    }

    public void onFinish() {
        // Fix background play when playing trailers from NUM
        // On API > 23 onStop not immediately occurred after onPause
        if (getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_DEFAULT) {
            if (Util.SDK_INT > 23) {
                maybeReleasePlayer();
            }

            // Bug: history not updated on Android 6.0.1
            // Remote control fix
            // Assuming that user wants to close the player
            // setVideo(null);
        }

        mEventListener.onFinish();
    }

    public void onPIPChanged(boolean isInPIP) {
        if (!isInPIP) {
            // Fix partially disappeared buttons after exit from PIP???
            notifyPlaybackRowChanged();
        }
    }

    public void skipToNext() {
        if (mPlayerGlue != null) {
            mPlayerGlue.next();
        }
    }

    public void skipToPrevious() {
        if (mPlayerGlue != null) {
            mPlayerGlue.previous();
        }
    }

    public void rewind() {
        if (mPlayerGlue != null) {
            mPlayerGlue.rewind();
        }
    }

    public void fastForward() {
        if (mPlayerGlue != null) {
            mPlayerGlue.fastForward();
        }
    }

    private int getPlayerRowIndex() {
        int selectedPosition = 0;

        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null) {
            selectedPosition = mRowsSupportFragment.getVerticalGridView().getSelectedPosition();
        }

        return selectedPosition;
    }

    private void setPlayerRowIndex(int index) {
        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null) {
            mRowsSupportFragment.getVerticalGridView().setSelectedPosition(index);
        }
    }

    @Override
    public void restartEngine() {
        if (isDetached() || getContext() == null) {
            Log.e(TAG, "Can't restart engine. Seems that player activity is being destroyed.");
            return;
        }

        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mEventListener.onEngineReleased();
            mEventListener.onEngineInitialized();
        }
    }

    /**
     * Internal method. Intended for enclosed Activity.
     */
    public void maybeReleasePlayer() {
        // Inside dialogs we could change engine settings on fly
        if (AppDialogPresenter.instance(getContext()).isDialogShown()) {
            Log.d(TAG, "releasePlayer: Engine release is blocked by dialog. Exiting...");
            return;
        }

        // Background audio mode is complicated (null surface error) on Android 9 (api 28) and above. Try avoid it.
        // More info: DebugInfoMediaCodecVideoRenderer#handleMessage
        //if (getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_SOUND &&
        //    !ViewManager.instance(getContext()).isNewViewPending()) {
        //    Log.d(TAG, "releasePlayer: Engine release is blocked by background playback. Exiting...");
        //    return;
        //}

        // Ensure to continue playback in audio mode (activity should be blocked)
        if (getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND &&
                ViewManager.instance(getContext()).getBlockedTop() == PlaybackActivity.class &&
                !isInPIPMode()) {
            Log.d(TAG, "releasePlayer: Playback activity is blocked. Exiting...");
            return;
        }

        releasePlayer();
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            Log.d(TAG, "releasePlayer: Start releasing player engine...");
            mEventListener.onEngineReleased();
            destroyPlayerObjects();
        }
    }

    private void initializePlayer() {
        if (mPlayer != null) {
            Log.d(TAG, "Skip player initialization.");
            return;
        }

        createPlayerObjects();

        mEventListener.onEngineInitialized();
    }

    private void destroyPlayerObjects() {
        // Fix access calls when player isn't initialized
        mExoPlayerController.release();
        if (mMediaSessionConnector != null) {
            mMediaSessionConnector.setPlayer(null);
        }
        if (mMediaSession != null) {
            mMediaSession.release();
        }
        setAdapter(null); // PlayerGlue->LeanbackPlayerAdapter->Context memory leak fix
        mPlayer = null;
        mPlayerGlue = null;
        mRowsAdapter = null;
        mSubtitleManager = null;
        mDebugInfoManager = null;
        mMediaSessionConnector = null;
        mMediaSession = null;
    }

    private void createPlayerObjects() {
        // NOTE: position matters!

        createPlayer();

        createPlayerGlue();

        createSubtitleManager();

        createDebugManager();

        createMediaSession();

        initializePlayerRows();

        initializeGlobalClock();

        initializeGlobalEndingTime();

        initializePixelRatio();
    }

    private void createPlayer() {
        mExoPlayerController.setEventListener(mEventListener);

        // Use default or pass your bandwidthMeter here: bandwidthMeter = new DefaultBandwidthMeter.Builder(getContext()).build()
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(getContext());
        mPlayer = mPlayerInitializer.createPlayer(getContext(), renderersFactory, trackSelector);
        // Try to fix decoder error on Nvidia Shield 2019.
        // Init resources as early as possible.
        //mPlayer.setForegroundMode(true);
        // NOTE: Avoid using seekParameters. ContentBlock hangs because of constant skipping to the segment start.
        // ContentBlock hangs on the last segment: https://www.youtube.com/watch?v=pYymRbfjKv8

        // Fix seeking on TextureView (some devices only)
        if (PlayerTweaksData.instance(getContext()).isTextureViewEnabled()) {
            // Also, live stream (dash) seeking fix
            mPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        }

        mExoPlayerController.setPlayer(mPlayer);
    }

    private void createPlayerGlue() {
        PlayerAdapter playerAdapter = new LeanbackPlayerAdapter(getContext(), mPlayer, UPDATE_DELAY_MS); // NOTE: possible context memory leak

        OnActionClickedListener playerActionListener = new PlayerActionListener();
        mPlayerGlue = new VideoPlayerGlue(getContext(), playerAdapter, playerActionListener); // NOTE: possible context memory leak
        mPlayerGlue.setHost(new SurfacePlaybackFragmentGlueHost(this));
        mPlayerGlue.setSeekEnabled(true);
        mPlayerGlue.setControlsOverlayAutoHideEnabled(false); // don't show controls on some player events like play/pause/end
        StoryboardSeekDataProvider.setSeekProvider(mPlayerGlue);
        hideControlsOverlay(true); // fix player ui not synced correctly

        mIsUIAnimationsEnabled = PlayerTweaksData.instance(getContext()).isUIAnimationsEnabled();
        mIsLikesCounterEnabled = PlayerTweaksData.instance(getContext()).isLikesCounterEnabled();

        mExoPlayerController.setPlayerView(mPlayerGlue);
    }

    private void createSubtitleManager() {
        mSubtitleManager = new SubtitleManager(getActivity(), R.id.leanback_subtitles);

        // subs renderer
        if (mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
        }
    }

    private void createDebugManager() {
        mDebugInfoManager = new DebugInfoManager(getActivity(), mPlayer, R.id.debug_view_group);
    }

    private void initializeGlobalClock() {
        DateTimeView clock = getActivity().findViewById(R.id.global_time);
        clock.showDate(false);
        clock.setVisibility(PlayerData.instance(getContext()).isGlobalClockEnabled() ? View.VISIBLE : View.GONE);
    }

    private void initializeGlobalEndingTime() {
        EndingTimeView endingTime = getActivity().findViewById(R.id.global_ending_time);
        endingTime.setVisibility(PlayerData.instance(getContext()).isGlobalEndingTimeEnabled() ? View.VISIBLE : View.GONE);
    }

    private void initializePixelRatio() {
        setPixelRatio(PlayerTweaksData.instance(getContext()).getPixelRatio());
    }

    private void createMediaSession() {
        if (VERSION.SDK_INT <= 19 || getContext() == null) {
            // Fix Android 4.4 bug: java.lang.IllegalArgumentException: MediaButtonReceiver component may not be null
            return;
        }

        mMediaSession = new MediaSessionCompat(getContext(), getContext().getPackageName());
        mMediaSession.setActive(true);
        mMediaSessionConnector = new MediaSessionConnector(mMediaSession);
        boolean disableNotifications = PlayerTweaksData.instance(getContext()).isPlaybackNotificationsDisabled();

        try {
            mMediaSessionConnector.setPlayer(mPlayer);
        } catch (NoSuchMethodError e) {
            // Android 9, Sony
            // No virtual method setState(IJFJ)Landroid/media/session/PlaybackState$Builder;
            // in class Landroid/media/session/PlaybackState$Builder;
            return;
        }

        mMediaSessionConnector.setMediaMetadataProvider(disableNotifications ? null : player -> {
            if (getVideo() == null) {
                return null;
            }

            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getVideo().title);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, getVideo().title);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getVideo().getAuthor());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, getVideo().secondTitle);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, getVideo().cardImageUrl);
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs());

            return metadataBuilder.build();
        });

        mMediaSessionConnector.setQueueNavigator(new BackboneQueueNavigator() {
            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }

            @Override
            public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
                mEventListener.onPreviousClicked();
            }

            @Override
            public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
                mEventListener.onNextClicked();
            }
        });

        // Fix exoplayer pause when switching AFR. The code seems buggy.
        mMediaSessionConnector.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
                // Fix exoplayer pause after activity is resumed (AFR switching).
                // It's tied to activity state transitioning because window has different mode.
                // NOTE: may be a problems with background playback or bluetooth button events
                if (System.currentTimeMillis() - mResumeTimeMs < 5_000 ||
                        (!isResumed() && !isInPIPMode() && !AppDialogPresenter.instance(getContext()).isDialogShown())
                ) {
                    return false;
                }

                //if (System.currentTimeMillis() - mResumeTimeMs < 5_000) {
                //    return false;
                //}

                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }
        });
    }

    private void initializePlayerRows() {
        mRowsSupportFragment = (RowsSupportFragment) getChildFragmentManager().findFragmentById(
                R.id.playback_controls_dock);

        /*
         * To add a new row to the mPlayerAdapter and not lose the controls row that is provided by the
         * glue, we need to compose a new row with the controls row and our related videos row.
         *
         * We start by creating a new {@link ClassPresenterSelector}. Then add the controls row from
         * the media player glue, then add the related videos row.
         */
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(
                mPlayerGlue.getControlsRow().getClass(), mPlayerGlue.getPlaybackRowPresenter());
        presenterSelector.addClassPresenter(ListRow.class, mRowPresenter);

        mRowsAdapter = new ArrayObjectAdapter(presenterSelector);

        // player controls row
        mRowsAdapter.add(mPlayerGlue.getControlsRow());

        setAdapter(mRowsAdapter);
    }

    private void initPresenters() {
        mRowPresenter = new CustomListRowPresenter() {
            @Override
            protected void onRowViewSelected(RowPresenter.ViewHolder holder, boolean selected) {
                super.onRowViewSelected(holder, selected);

                updatePlayerBackground();

                if (selected) {
                    focusPendingSuggestedItem();
                }
            }
        };

        mCardPresenter = new VideoCardPresenter();
        mShortsPresenter = new ShortsCardPresenter();
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
        mShortsPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(
                Presenter.ViewHolder itemViewHolder,
                Object item) {

            if (item instanceof Video) {
                mEventListener.onSuggestionItemLongClicked((Video) item);
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                mEventListener.onSuggestionItemClicked((Video) item);
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                checkScrollEnd((Video)item);
            }
        }

        private void checkScrollEnd(Video item) {
            for (VideoGroupObjectAdapter adapter : mMediaGroupAdapters.values()) {
                int index = adapter.indexOf(item);

                if (index != -1) {
                    int size = adapter.size();
                    if (index > (size - 4)) {
                        mEventListener.onScrollEnd(item);
                    }
                    break;
                }
            }
        }
    }

    private class PlayerActionListener implements VideoPlayerGlue.OnActionClickedListener {
        @Override
        public void onPrevious() {
            mEventListener.onPreviousClicked();
        }

        @Override
        public void onNext() {
            mEventListener.onNextClicked();
        }

        @Override
        public void onPlay() {
            mEventListener.onPlayClicked();
        }

        @Override
        public void onPause() {
            mEventListener.onPauseClicked();
        }

        @Override
        public void setRepeatMode(int modeIndex) {
            mEventListener.onRepeatModeClicked(modeIndex);
        }

        @Override
        public void onHighQuality() {
            mEventListener.onHighQualityClicked();
        }

        @Override
        public void onThumbsDown(boolean thumbsDown) {
            mEventListener.onDislikeClicked(thumbsDown);
        }

        @Override
        public void onThumbsUp(boolean thumbsUp) {
            mEventListener.onLikeClicked(thumbsUp);
        }

        @Override
        public void onChannel() {
            mEventListener.onChannelClicked();
        }

        @Override
        public void onClosedCaptions(boolean enabled) {
            mEventListener.onSubtitleClicked(enabled);
        }

        @Override
        public void onClosedCaptionsLongPress(boolean enabled) {
            mEventListener.onSubtitleLongClicked(enabled);
        }

        @Override
        public void onPlaylistAdd() {
            mEventListener.onPlaylistAddClicked();
        }

        @Override
        public void onDebugInfo(boolean enabled) {
            mEventListener.onDebugInfoClicked(enabled);
        }

        @Override
        public void onVideoSpeed(boolean enabled) {
            mEventListener.onSpeedClicked(enabled);
        }

        @Override
        public void onVideoSpeedLongPress(boolean enabled) {
            mEventListener.onSpeedLongClicked(enabled);
        }

        @Override
        public void onSeekInterval() {
            mEventListener.onSeekIntervalClicked();
        }

        @Override
        public void onChat(boolean enabled) {
            mEventListener.onChatClicked(enabled);
        }

        @Override
        public void onChatLongPress(boolean enabled) {
            mEventListener.onChatLongClicked(enabled);
        }

        @Override
        public void onVideoInfo() {
            mEventListener.onVideoInfoClicked();
        }

        @Override
        public void onShareLink() {
            mEventListener.onShareLinkClicked();
        }

        @Override
        public void onSearch() {
            mEventListener.onSearchClicked();
        }

        @Override
        public void onVideoZoom() {
            mEventListener.onVideoZoomClicked();
        }

        @Override
        public void onPip() {
            mEventListener.onPipClicked();
        }

        @Override
        public void onPlaybackQueue() {
            mEventListener.onPlaybackQueueClicked();
        }

        @Override
        public void onAction(int actionId, int actionIndex) {
            mEventListener.onButtonClicked(actionId, actionIndex);
        }

        @Override
        public void onLongAction(int actionId, int actionIndex) {
            mEventListener.onButtonLongClicked(actionId, actionIndex);
        }

        @Override
        public void onTopEdgeFocused() {
            showOverlay(false);
        }

        @Override
        public boolean onKeyDown(int keyCode) {
            return mEventListener.onKeyDown(keyCode);
        }
    }

    // Begin Ui events

    @Override
    public void setVideo(Video video) {
        mExoPlayerController.setVideo(video);

        if (mPlayerGlue != null && video != null) {
            // Preserve player formatting
            mPlayerGlue.setTitle(video.getPlayerTitle() != null ? video.getPlayerTitle() : "...");
            mPlayerGlue.setSubtitle(video.getPlayerSecondTitle() != null ? createSecondTitle(video) : "...");
            mPlayerGlue.setVideo(video);
        }
    }

    @Override
    public void showBackground(String url) {
        mBackgroundManager.showBackground(url);
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        mBackgroundManager.showBackgroundColor(colorResId);
    }

    private CharSequence createSecondTitle(Video video) {
        CharSequence result = video.getPlayerSecondTitle();

        if (getContext() != null && video.isLive) {
            result = TextUtils.concat( result, " ", Video.TERTIARY_TEXT_DELIM, " ", Utils.color(getContext().getString(R.string.badge_live), ContextCompat.getColor(getContext(), R.color.red)));
        }

        if (getContext() != null && video.likeCount != null && mIsLikesCounterEnabled) {
            result = TextUtils.concat(result, " ", Video.TERTIARY_TEXT_DELIM, " ", video.likeCount);
        }

        if (getContext() != null && video.dislikeCount != null && mIsLikesCounterEnabled) {
            result = TextUtils.concat(result, " ", Video.TERTIARY_TEXT_DELIM, " ", video.dislikeCount);
        }

        return result;
    }

    @Override
    public void loadStoryboard() {
        if (mPlayerGlue.getSeekProvider() instanceof StoryboardSeekDataProvider) {
            ((StoryboardSeekDataProvider) mPlayerGlue.getSeekProvider()).init(getVideo(), mExoPlayerController.getDurationMs());
        }
    }

    @Override
    public void setTitle(String title) {
        mPlayerGlue.setTitle(title);
    }

    @Override
    public void showProgressBar(boolean show) {
        if (getProgressBarManager() == null) {
            return;
        }

        if (show) {
            getProgressBarManager().show();
        } else {
            getProgressBarManager().hide();
        }
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setSeekBarSegments(segments);
        }
    }

    @Override
    public void updateEndingTime() {
        if (getActivity() != null) {
            EndingTimeView endingTime = getActivity().findViewById(R.id.global_ending_time);
            endingTime.update();
        }
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        if (getActivity() != null) {
            LiveChatView liveChat = getActivity().findViewById(R.id.live_chat);
            liveChat.setChatReceiver(chatReceiver);
        }
    }

    // End Ui events

    // Begin Engine Events

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();

        if (durationMs > Video.MAX_DURATION_MS && getVideo() != null) {
            durationMs = getVideo().getLiveDurationMs();
        }

        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        // Android 4.4 fix for format selection dialog (player destroyed when dialog is focused)
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void setBackgroundMode(int type) {
        Log.d(TAG, "Setting engine block type to %s...", type);
        mPlaybackMode = type;
    }

    @Override
    public int getBackgroundMode() {
        return mPlaybackMode;
    }

    @Override
    public boolean isInPIPMode() {
        PlaybackActivity playbackActivity = (PlaybackActivity) getActivity();

        if (playbackActivity == null) {
            return false;
        }

        // Old api fix
        return playbackActivity.isInPipMode();
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
        // NOTE: Real speed isn't changed immediately, so use supplied speed data
        setSpeedButtonState(speed != 1.0f);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setVideoZoomMode(int mode) {
        setResizeMode(mode);
    }

    @Override
    public int getVideoZoomMode() {
        return getResizeMode();
    }

    @Override
    public void setVideoZoom(int percents) {
        setZoom(percents);
    }

    @Override
    public void setVideoAspectRatio(float ratio) {
        setAspectRatio(ratio);
    }

    @Override
    public void setVideoRotation(int angle) {
        setRotation(angle);
    }

    // End Engine Events

    @Override
    public void setEventListener(PlayerEventListener listener) {
        mEventListener = listener;
        mExoPlayerController.setEventListener(listener);
    }

    @Override
    public PlayerEventListener getEventListener() {
        return mEventListener;
    }

    @Override
    public PlayerManager getPlayer() {
        return this;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mEventListener.onViewDestroyed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "Destroying PlaybackFragment...");

        // Fix situations when engine didn't properly destroyed.
        // E.g. after closing dialogs.
        releasePlayer();

        if (mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.onViewDestroyed();
        }
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController.getVideo();
    }

    @Override
    public void finish() {
        LeanbackActivity activity = getLeanbackActivity();

        if (activity != null) {
            activity.finish();
        }
    }

    /**
     * Force finish (PIP etc)
     */
    @Override
    public void finishReally() {
        LeanbackActivity activity = getLeanbackActivity();

        if (activity != null) {
            activity.finishReally();
        }
    }

    @Override
    public boolean isOverlayShown() {
        return isControlsOverlayVisible();
    }

    @Override
    public boolean isSuggestionsShown() {
        return isControlsOverlayVisible() && getPlayerRowIndex() != 0;
    }

    @Override
    public boolean isControlsShown() {
        return isControlsOverlayVisible() && getPlayerRowIndex() == 0;
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        super.showControlsOverlay(mIsUIAnimationsEnabled);

        // Do throttle. Called so many times. Rely on boxing because initial state is unknown.
        if (mIsControlsShownPreviously != null && mIsControlsShownPreviously) {
            return;
        }

        updatePlayerBackground();

        if (mPlayerGlue != null) {
            mPlayerGlue.setControlsVisibility(true);
        }

        if (mEventListener != null) {
            mEventListener.onControlsShown(true);
        }

        mIsControlsShownPreviously = true;
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(mIsUIAnimationsEnabled);

        // Do throttle. Called so many times. Rely on boxing because initial state is unknown.
        if (mIsControlsShownPreviously != null && !mIsControlsShownPreviously) {
            return;
        }

        if (mPlayerGlue != null) {
            mPlayerGlue.setControlsVisibility(false);
        }

        if (mEventListener != null) {
            mEventListener.onControlsShown(false);
        }

        mIsControlsShownPreviously = false;
    }

    /**
     * Show controls or suggestions: depending what has been shown last.
     */
    @Override
    public void showOverlay(boolean show) {
        if (forbidShowOverlay(show)) {
            return;
        }

        if (show) {
            showControlsOverlay(true);
        } else {
            hideControlsOverlay(true);
        }
    }

    @Override
    public void showSuggestions(boolean show) {
        if (forbidShowOverlay(show)) {
            return;
        }

        showOverlay(show);

        if (show && !isSuggestionsShown() && !isSuggestionsEmpty()) {
            setPlayerRowIndex(1);
        }
    }

    /**
     * The same as {@link #showOverlay(boolean)} but scrolls from suggestions to controls if needed.
     */
    @Override
    public void showControls(boolean show) {
        if (forbidShowOverlay(show)) {
            return;
        }

        showOverlay(show);

        setPlayerRowIndex(0);
    }

    @Override
    public void setRepeatButtonState(int modeIndex) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setRepeatActionState(modeIndex);
        }
    }

    private int getRepeatButtonState() {
        if (mPlayerGlue != null) {
            return mPlayerGlue.getRepeatActionState();
        }

        return 0;
    }

    @Override
    public void setLikeButtonState(boolean like) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setThumbsUpActionState(like);
        }
    }

    @Override
    public void setDislikeButtonState(boolean dislike) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setThumbsDownActionState(dislike);
        }
    }

    @Override
    public void setPlaylistAddButtonState(boolean selected) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setPlaylistAddButtonState(selected);
        }
    }

    @Override
    public void setSubtitleButtonState(boolean selected) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setClosedCaptionsButtonState(selected);
        }
    }

    @Override
    public void setSpeedButtonState(boolean selected) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setSpeedButtonState(selected);
        }
    }

    @Override
    public void setChatButtonState(boolean selected) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setChatButtonState(selected);
        }
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setButtonState(buttonId, buttonState);
        }
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setChannelIcon(iconUrl);
        }
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setSeekPreviewTitle(title);
            mPlayerGlue.setBody(title);
        }
    }

    @Override
    public void setNextTitle(String title) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setNextTitle(title);
        }
    }

    @Override
    public void setDebugButtonState(boolean show) {
        if (mPlayerGlue != null) {
            mPlayerGlue.setDebugInfoActionState(show);
        }
    }

    @Override
    public void showDebugInfo(boolean show) {
        if (mDebugInfoManager != null) {
            mDebugInfoManager.show(show);
        }
    }

    @Override
    public void showSubtitles(boolean show) {
        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    public boolean isDebugInfoShown() {
        return mDebugInfoManager != null && mDebugInfoManager.isShown();
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (mRowsAdapter == null) {
            Log.e(TAG, "Related videos row not initialized yet.");
            return;
        }

        if (group == null || group.isEmpty()) {
            Log.e(TAG, "Suggestions row is empty!");
            return;
        }

        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mMediaGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group, group.isShorts() ? mShortsPresenter : mCardPresenter);

            mMediaGroupAdapters.put(mediaGroupId, mediaGroupAdapter);

            ListRow row = new ListRow(rowHeader, mediaGroupAdapter);

            int newPosition = group.getPosition() + SUGGESTIONS_START_INDEX;
            if (group.getPosition() == -1 || newPosition > mRowsAdapter.size()) {
                mRowsAdapter.add(row);
            } else {
                mRowsAdapter.add(newPosition, row);
            }
        } else {
            freeze(true);

            existingAdapter.add(group); // continue

            freeze(false);
        }
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        VideoGroupObjectAdapter adapter = mMediaGroupAdapters.get(group.getId());

        if (adapter != null) {
            adapter.remove(group);

            if (adapter.isEmpty()) {
                int position = getSuggestionsIndex(group);
                if (position != -1) {
                    mMediaGroupAdapters.remove(group.getId());
                    mRowsAdapter.removeItems(position + SUGGESTIONS_START_INDEX, 1);
                }
            }
        }
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        if (mRowsAdapter == null) {
            Log.e(TAG, "Related videos row not initialized yet.");
            return -1;
        }

        VideoGroupObjectAdapter existingAdapter = mMediaGroupAdapters.get(group.getId());

        int index = getRowAdapterIndex(existingAdapter);

        return index != -1 ? index - SUGGESTIONS_START_INDEX : -1;
    }

    private int getRowAdapterIndex(VideoGroupObjectAdapter adapter) {
        int index = -1;

        for (int i = 0; i < mRowsAdapter.size(); i++) {
            Object row = mRowsAdapter.get(i);

            if (row instanceof ListRow) {
                ObjectAdapter current = ((ListRow) row).getAdapter();

                if (current == adapter) {
                    index = mRowsAdapter.indexOf(row);
                    break;
                }
            }
        }

        return index;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int rowIndex) {
        if (getVideo() == null || !getVideo().hasVideo()) {
            return null;
        }

        // NOTE: skip first row. It's PlaybackControlsRow
        int realIndex = rowIndex + SUGGESTIONS_START_INDEX;
        Object row = mRowsAdapter != null && mRowsAdapter.size() > realIndex ? mRowsAdapter.get(realIndex) : null;

        VideoGroup result = null;

        if (row instanceof ListRow) {
            VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
            result = VideoGroup.from(adapter.getAll());
        }

        return result;
    }

    @Override
    public void focusSuggestedItem(int index) {
        if (mRowsSupportFragment != null) {
            ViewHolder vh = mRowsSupportFragment.getRowViewHolder(SUGGESTIONS_START_INDEX);
            // Skip PlaybackRowPresenter.ViewHolder
            if (vh instanceof ListRowPresenter.ViewHolder) {
                ((ListRowPresenter.ViewHolder) vh).getGridView().setSelectedPosition(index);
            }
        }
    }

    @Override
    public void focusSuggestedItem(Video video) {
        if (video == null || video.getGroup() == null || mPendingFocus != null) {
            return;
        }

        mPendingFocus = video;

        focusPendingSuggestedItem();
    }

    public void focusPendingSuggestedItem() {
        if (mPendingFocus == null || mPendingFocus.getGroup() == null || mRowsSupportFragment == null) {
            return;
        }

        VideoGroupObjectAdapter existingAdapter = mMediaGroupAdapters.get(mPendingFocus.getGroup().getId());

        if (existingAdapter == null) {
            return;
        }

        int rowIndex = getRowAdapterIndex(existingAdapter);

        ViewHolder rowViewHolder = mRowsSupportFragment.getRowViewHolder(rowIndex);

        // Skip PlaybackRowPresenter.ViewHolder
        if (rowViewHolder instanceof ListRowPresenter.ViewHolder) {
            int index = existingAdapter.indexOf(mPendingFocus);
            ((ListRowPresenter.ViewHolder) rowViewHolder).getGridView().setSelectedPosition(index);
            mPendingFocus = null;
        }
    }

    @Override
    public void resetSuggestedPosition() {
        setPlayerRowIndex(0);
    }

    @Override
    public void clearSuggestions() {
        if (mRowsAdapter != null && mRowsAdapter.size() > 1) {
            mRowsAdapter.removeItems(SUGGESTIONS_START_INDEX, mRowsAdapter.size() - 1);
        }

        mMediaGroupAdapters.clear();
        mPendingFocus = null;
    }

    @Override
    public boolean isSuggestionsEmpty() {
        // Ignore first row. It's player controls row.
        return mRowsAdapter == null || mRowsAdapter.size() <= SUGGESTIONS_START_INDEX;
    }

    /**
     * Disable scrolling on partially updated rows. This prevent controls from misbehaving.
     */
    private void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        if (mRowPresenter != null && mRowsSupportFragment != null) {
            ViewHolder vh = mRowsSupportFragment.getRowViewHolder(mRowsSupportFragment.getSelectedPosition());
            // Skip PlaybackRowPresenter.ViewHolder
            if (vh instanceof ListRowPresenter.ViewHolder) {
                mRowPresenter.freeze(vh, freeze);
            }
        }
    }

    /* End PlayerController */

    private LeanbackActivity getLeanbackActivity() {
        return (LeanbackActivity) getActivity();
    }

    /**
     * Simply recreates exoplayer objects (silently) if prev track (current from this perspective) isn't empty<br/>
     * Fixes video artifacts when switching to the next video.<br/>
     * Also could help with memory leaks(??)<br/>
     * Without this also you'll have problems with track quality switching(??).
     */
    @Override
    public void resetPlayerState() {
        mExoPlayerController.resetPlayerState();
        // Hide last frame of the previous video
        showBackgroundColor(R.color.player_background);
        setChatReceiver(null);
        setSeekBarSegments(null);
        setSeekPreviewTitle(null);
    }

    /**
     * PIP mode fix
     */
    private void showHideWidgets(boolean show) {
        Activity activity = getActivity();

        if (activity != null) {
            View overlay = activity.findViewById(R.id.player_overlay_wrapper);

            if (overlay != null) {
                overlay.setVisibility(show ? View.VISIBLE : View.GONE);
            }

            View liveChat = activity.findViewById(R.id.live_chat_wrapper);

            if (liveChat != null) {
                liveChat.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * UI couldn't be properly displayed in PIP mode
     */
    private boolean forbidShowOverlay(boolean show) {
        return show && isInPIPMode();
    }
}
