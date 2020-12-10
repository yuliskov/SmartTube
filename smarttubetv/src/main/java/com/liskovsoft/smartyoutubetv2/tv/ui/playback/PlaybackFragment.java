package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection.Factory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.V3.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.DebugInfoManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.StoryboardSeekDataProvider;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoEventsOverrideFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue.OnActionClickedListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to
 * {@link VideoPlayerGlue}.
 */
public class PlaybackFragment extends VideoEventsOverrideFragment implements PlaybackView, PlaybackController {
    private static final String TAG = PlaybackFragment.class.getSimpleName();
    private static final int UPDATE_DELAY_MS = 16;
    private VideoPlayerGlue mPlayerGlue;
    private SimpleExoPlayer mPlayer;
    private PlaybackPresenter mPlaybackPresenter;
    private ArrayObjectAdapter mRowsAdapter;
    private Map<Integer, VideoGroupObjectAdapter> mMediaGroupAdapters;
    private PlayerEventListener mEventListener;
    private PlayerController mExoPlayerController;
    private ExoPlayerInitializer mPlayerInitializer;
    private SubtitleManager mSubtitleManager;
    private DebugInfoManager mDebugInfoManager;
    private UriBackgroundManager mBackgroundManager;
    private RowsSupportFragment mRowsSupportFragment;
    private final boolean mIsAnimationEnabled = true;
    private boolean mIsEngineBlocked;
    private boolean mIsPIPEnabled;
    private boolean mIsPlayBehindEnabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // trying to fix bug with presets

        mMediaGroupAdapters = new HashMap<>();
        mBackgroundManager = getLeanbackActivity().getBackgroundManager();
        mBackgroundManager.setBackgroundColor(ContextCompat.getColor(getLeanbackActivity(), R.color.player_background));
        mPlayerInitializer = new ExoPlayerInitializer(getContext());
        mExoPlayerController = new ExoPlayerController(getContext());

        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupPlayerBackground();

        mPlaybackPresenter.onViewInitialized();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // ProgressBar.setRootView already called at this moment
        ProgressBarManager.setup(getProgressBarManager(), (ViewGroup) view);

        return view;
    }

    private void setupPlayerBackground() {
        // Make player controls more distinguished on white background
        //setBackgroundType(BG_NONE);

        View backgroundView = (View) Helpers.getField(this, "mBackgroundView");

        if (backgroundView != null) {
            backgroundView.setBackgroundResource(R.drawable.player_background);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
            mEventListener.onViewResumed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();
            mEventListener.onViewResumed();
        }
    }

    /** Pauses the player. */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onPause() {
        super.onPause();

        if (Util.SDK_INT <= 23) {
            releasePlayer();
            mEventListener.onViewPaused();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
            mEventListener.onViewPaused();
        }
    }

    public void onDispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (mPlayerGlue != null) {
                mPlayerGlue.syncControlsState();
            }
        }
    }

    public void skipToNext() {
        mPlayerGlue.next();
    }

    public void skipToPrevious() {
        mPlayerGlue.previous();
    }

    public void rewind() {
        mPlayerGlue.rewind();
    }

    public void fastForward() {
        mPlayerGlue.fastForward();
    }

    private int getSuggestedRowIndex() {
        int selectedPosition = 0;

        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null) {
            selectedPosition = mRowsSupportFragment.getVerticalGridView().getSelectedPosition();
        }

        return selectedPosition;
    }

    @Override
    public void restartEngine() {
        if (mPlayer != null) {
            mEventListener.onEngineReleased();
        }

        destroyPlayerObjects();
        createPlayerObjects();

        if (mPlayer != null) {
            mEventListener.onEngineInitialized();
        }
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mEventListener.onEngineReleased();
            mEventListener.onEngineInitialized();
        }
    }

    private void releasePlayer() {
        if (isEngineBlocked()) {
            Log.d(TAG, "releasePlayer: Engine release is blocked. Exiting...");
            return;
        }

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

    private void testHardwareAcceleration() {
        if (getSurfaceView() != null && !getSurfaceView().isHardwareAccelerated()) {
            MessageHelpers.showMessage(getContext(), "Oops. Seems that video hardware acceleration is disabled!");
        }
    }

    private void destroyPlayerObjects() {
        // Fix access calls when player isn't initialized
        mExoPlayerController.release();
        mPlayer = null;
        mPlayerGlue = null;
        mSubtitleManager = null;
        mDebugInfoManager = null;
    }

    private void createPlayerObjects() {
        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(getActivity());

        // Use default or pass your bandwidthMeter here: bandwidthMeter = new DefaultBandwidthMeter.Builder(getContext()).build()
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new Factory());
        mPlayer = mPlayerInitializer.createPlayer(getActivity(), renderersFactory, trackSelector);

        PlayerAdapter playerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY_MS);

        OnActionClickedListener playerActionListener = new PlayerActionListener();
        mPlayerGlue = new VideoPlayerGlue(getActivity(), playerAdapter, playerActionListener);
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        mPlayerGlue.setSeekEnabled(true);
        mPlayerGlue.setControlsOverlayAutoHideEnabled(false); // don't show controls on some player events like play/pause/end
        StoryboardSeekDataProvider.setSeekProvider(mPlayerGlue);
        hideControlsOverlay(mIsAnimationEnabled); // hide controls upon fragment creation

        mExoPlayerController.setPlayer(mPlayer);
        mExoPlayerController.setTrackSelector(trackSelector);
        mExoPlayerController.setEventListener(mEventListener);
        mExoPlayerController.setPlayerView(mPlayerGlue);

        mSubtitleManager = new SubtitleManager(getActivity(), R.id.leanback_subtitles);

        // subs renderer
        if (mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
        }

        mDebugInfoManager = new DebugInfoManager(mPlayer, R.id.debug_view_group, getActivity());

        mRowsAdapter = initializeSuggestedVideosRow();
        setAdapter(mRowsAdapter);

        mRowsSupportFragment = (RowsSupportFragment) getChildFragmentManager().findFragmentById(
                R.id.playback_controls_dock);
    }

    private ArrayObjectAdapter initializeSuggestedVideosRow() {
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
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter() {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
                super.onBindRowViewHolder(holder, item);

                // Set position of item inside first row (playlist items)
                if (getVideo() != null && getVideo().playlistIndex > 0 &&
                    mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView().getSelectedPosition() == 0) {
                    ViewHolder vh = (ListRowPresenter.ViewHolder) holder;
                    vh.getGridView().setSelectedPosition(getVideo().playlistIndex);
                }
            }
        });

        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(presenterSelector);

        // player controls row
        rowsAdapter.add(mPlayerGlue.getControlsRow());

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());

        return rowsAdapter;
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                boolean longClick = getLeanbackActivity().isLongClick();
                Log.d(TAG, "Is long click: " + longClick);

                if (longClick) {
                    mEventListener.onSuggestionItemLongClicked((Video) item);
                } else {
                    mEventListener.onSuggestionItemClicked((Video) item);
                }
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
                        mEventListener.onScrollEnd(adapter.getGroup());
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
        public void onSubscribe(boolean subscribed) {
            mEventListener.onSubscribeClicked(subscribed);
        }

        @Override
        public void onThumbsDown(boolean thumbsDown) {
            mEventListener.onThumbsDownClicked(thumbsDown);
        }

        @Override
        public void onThumbsUp(boolean thumbsUp) {
            mEventListener.onThumbsUpClicked(thumbsUp);
        }

        @Override
        public void onChannel() {
            mEventListener.onChannelClicked();
        }

        @Override
        public void onClosedCaptions() {
            mEventListener.onSubtitlesClicked();
        }

        @Override
        public void onPlaylistAdd() {
            mEventListener.onPlaylistAddClicked();
        }

        @Override
        public void onVideoStats(boolean enabled) {
            mEventListener.onVideoStatsClicked(enabled);
        }

        @Override
        public void onVideoSpeed() {
            mEventListener.onVideoSpeedClicked();
        }

        @Override
        public void onSearch() {
            mEventListener.onSearchClicked();
        }

        @Override
        public void onTopEdgeFocused() {
            showControls(false);
        }

        @Override
        public boolean onKeyDown(int keyCode) {
            return mEventListener.onKeyDown(keyCode);
        }
    }

    // Begin Ui events

    @Override
    public void resetSuggestedPosition() {
        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null) {
            mRowsSupportFragment.getVerticalGridView().setSelectedPosition(0);
        }
    }

    @Override
    public void clearSuggestions() {
        if (mRowsAdapter.size() > 1) {
            mRowsAdapter.removeItems(1, mRowsAdapter.size() - 1);
        }

        mMediaGroupAdapters.clear();
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return mRowsAdapter.size() <= 1;
    }

    @Override
    public void setVideo(Video video) {
        mExoPlayerController.setVideo(video);
        mPlayerGlue.setTitle(video.title);
        mPlayerGlue.setSubtitle(video.description);
    }

    @Override
    public void loadStoryboard() {
        if (mPlayerGlue.getSeekProvider() instanceof StoryboardSeekDataProvider) {
            ((StoryboardSeekDataProvider) mPlayerGlue.getSeekProvider()).setVideo(getVideo(), mExoPlayerController.getLengthMs());
        }
    }

    // End Ui events

    // Begin Engine Events

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        mExoPlayerController.openHls(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
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
    public long getLengthMs() {
        return mExoPlayerController.getLengthMs();
    }

    @Override
    public void setPlay(boolean play) {
        mExoPlayerController.setPlay(play);
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
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
    public void selectFormat(FormatItem option) {
        // Android 4.4 fix for format selection dialog (player destroyed when dialog is focused)
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void enablePIP(boolean enable) {
        mIsPIPEnabled = enable;
    }

    @Override
    public boolean isPIPEnabled() {
        return mIsPIPEnabled;
    }

    @Override
    public boolean isInPIPMode() {
        PlaybackActivity playbackActivity = (PlaybackActivity) getActivity();

        if (playbackActivity == null) {
            return false;
        }

        // Old api fix
        return playbackActivity.isInPIPMode();
    }

    @Override
    public void enablePlayBehind(boolean enable) {
        mIsPlayBehindEnabled = enable;
    }

    @Override
    public boolean isPlayBehindEnabled() {
        return mIsPlayBehindEnabled;
    }

    @Override
    public boolean hasNoMedia() {
        return mExoPlayerController.hasNoMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setBuffer(int bufferType) {
        mPlayerInitializer.setBuffer(bufferType);
    }

    @Override
    public int getBuffer() {
        return mPlayerInitializer.getBuffer();
    }

    // End Engine Events

    @Override
    public void setEventListener(PlayerEventListener listener) {
        mEventListener = listener;
        mExoPlayerController.setEventListener(listener);
    }

    @Override
    public PlaybackController getController() {
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
        blockEngine(false);
        releasePlayer();

        mPlaybackPresenter.onViewDestroyed();
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController.getVideo();
    }

    @Override
    public void exit() {
        getLeanbackActivity().finish();
    }

    @Override
    public boolean isSuggestionsShown() {
        return isControlsOverlayVisible() && getSuggestedRowIndex() != 0;
    }

    @Override
    public boolean isControlsShown() {
        return isControlsOverlayVisible();
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        super.showControlsOverlay(runAnimation);

        if (mPlayerGlue != null) {
            mPlayerGlue.onControlsVisibilityChange(true);
        }

        mEventListener.onControlsShown(true);
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(runAnimation);

        if (mPlayerGlue != null) {
            mPlayerGlue.onControlsVisibilityChange(false);
        }

        mEventListener.onControlsShown(false);
    }

    @Override
    public void showControls(boolean show) {
        if (show) {
            showControlsOverlay(mIsAnimationEnabled);
        } else {
            hideControlsOverlay(mIsAnimationEnabled);
        }
    }

    @Override
    public void setRepeatButtonState(int modeIndex) {
        mPlayerGlue.setRepeatActionState(modeIndex);
    }

    @Override
    public void setLikeButtonState(boolean like) {
        mPlayerGlue.setThumbsUpActionState(like);
    }

    @Override
    public void setDislikeButtonState(boolean dislike) {
        mPlayerGlue.setThumbsDownActionState(dislike);
    }

    @Override
    public void setSubscribeButtonState(boolean subscribe) {
        mPlayerGlue.setSubscribeActionState(subscribe);
    }

    @Override
    public void setDebugButtonState(boolean show) {
        mPlayerGlue.setVideoStatsActionState(show);
    }

    @Override
    public void showDebugView(boolean show) {
        mDebugInfoManager.show(show);
    }

    @Override
    public List<SubtitleStyle> getSubtitleStyles() {
        return mSubtitleManager.getSubtitleStyles();
    }

    @Override
    public void setSubtitleStyle(SubtitleStyle subtitleStyle) {
        mSubtitleManager.setSubtitleStyle(subtitleStyle);
    }

    @Override
    public SubtitleStyle getSubtitleStyle() {
        return mSubtitleManager.getSubtitleStyle();
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (mRowsAdapter == null) {
            Log.e(TAG, "Related videos row not initialized yet.");
            return;
        }

        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mMediaGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group);

            mMediaGroupAdapters.put(mediaGroupId, mediaGroupAdapter);

            ListRow row = new ListRow(rowHeader, mediaGroupAdapter);
            mRowsAdapter.add(row);
        } else {
            existingAdapter.append(group); // continue row
        }
    }

    /* End PlayerController */

    private LeanbackActivity getLeanbackActivity() {
        return (LeanbackActivity) getActivity();
    }
}
