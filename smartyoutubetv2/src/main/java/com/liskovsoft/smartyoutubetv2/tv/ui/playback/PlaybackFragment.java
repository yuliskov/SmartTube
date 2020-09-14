package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.DirectExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to
 * {@link VideoPlayerGlue}.
 */
public class PlaybackFragment extends VideoSupportFragment implements PlaybackView, PlayerController {
    private static final String TAG = PlaybackFragment.class.getSimpleName();
    private static final int UPDATE_DELAY = 16;
    private VideoPlayerGlue mPlayerGlue;
    private LeanbackPlayerAdapter mPlayerAdapter;
    private SimpleExoPlayer mPlayer;
    private DefaultTrackSelector mTrackSelector;
    private PlayerActionListener mPlaylistActionListener;
    private PlaybackPresenter mPlaybackPresenter;
    private ArrayObjectAdapter mRowsAdapter;
    private Map<Integer, VideoGroupObjectAdapter> mMediaGroupAdapters;
    private PlayerEventListener mEventListener;
    private ExoPlayerController mExoPlayerController;
    private UriBackgroundManager mBackgroundManager;
    private final boolean mEnableAnimation = true;
    private RowsSupportFragment mRowsSupportFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMediaGroupAdapters = new HashMap<>();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mBackgroundManager.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.player_background));

        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.register(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();

            mPlaybackPresenter.onInitDone();

            mEventListener.onEngineInitialized();
            mEventListener.onViewResumed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();

            mPlaybackPresenter.onInitDone();

            mEventListener.onEngineInitialized();
            mEventListener.onViewResumed();
        }
    }

    /** Pauses the player. */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onPause() {
        super.onPause();

        if (Util.SDK_INT <= 23) {
            mEventListener.onEngineReleased();
            mEventListener.onViewPaused();
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            mEventListener.onEngineReleased();
            mEventListener.onViewPaused();
            releasePlayer();
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

        if (mRowsSupportFragment != null) {
            selectedPosition = mRowsSupportFragment.getVerticalGridView().getSelectedPosition();
        }

        return selectedPosition;
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mTrackSelector = null;
            mPlayerGlue = null;
            mPlayerAdapter = null;
            mPlaylistActionListener = null;
            mExoPlayerController = null;
        }
    }

    private void initializePlayer() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        mTrackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        // TODO: testing max bitrate
        mTrackSelector.setParameters(mTrackSelector.getParameters().buildUpon().setForceHighestSupportedBitrate(true));

        mPlayer = ExoPlayerFactory.newSimpleInstance(getActivity(), mTrackSelector);

        mExoPlayerController = new DirectExoPlayerController(mPlayer, mTrackSelector, getContext());

        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);

        mPlaylistActionListener = new PlayerActionListener();
        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter, mPlaylistActionListener);
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        mPlayerGlue.setSeekEnabled(true);
        mPlayerGlue.setControlsOverlayAutoHideEnabled(false); // don't show controls on some player events like play/pause/end
        hideControlsOverlay(mEnableAnimation); // hide controls upon fragment creation

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
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());

        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(presenterSelector);

        // player controls row
        rowsAdapter.add(mPlayerGlue.getControlsRow());

        setOnItemViewClickedListener(new ItemViewClickedListener());

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
                mEventListener.onSuggestionItemClicked((Video) item);
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
        public void onKeyDown(int keyCode) {
            mEventListener.onKeyDown(keyCode);
        }

        @Override
        public void setRepeatMode(int modeIndex) {
            mEventListener.onRepeatModeClicked(modeIndex);
        }
    }

    /* Begin PlayerController */

    @Override
    public void resetSuggestedPosition() {
        if (mRowsSupportFragment != null) {
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
    public void setVideo(Video video) {
        mExoPlayerController.setVideo(video);
        mPlayerGlue.setTitle(video.title);
        mPlayerGlue.setSubtitle(video.description);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        mExoPlayerController.openHls(hlsPlaylistUrl);
    }

    @Override
    public void setEventListener(PlayerEventListener listener) {
        mEventListener = listener;
        mExoPlayerController.setEventListener(listener);
    }

    @Override
    public PlayerController getController() {
        return this;
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPosition();
    }

    @Override
    public void setPositionMs(long positionMs) {
        if (positionMs >= 0) {
            mExoPlayerController.setPosition(positionMs);
        }
    }

    @Override
    public long getLengthMs() {
        return mExoPlayerController.getLengthMs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mEventListener.onViewDestroyed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlaybackPresenter.unregister(this);
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController.getVideo();
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
    public boolean isSuggestionsShown() {
        return isControlsOverlayVisible() && getSuggestedRowIndex() != 0;
    }

    @Override
    public void showControls(boolean show) {
        if (show) {
            showControlsOverlay(mEnableAnimation);
        } else {
            hideControlsOverlay(mEnableAnimation);
        }
    }

    @Override
    public void setRepeatMode(int modeIndex) {
        mExoPlayerController.setRepeatMode(modeIndex);
    }

    @Override
    public void setRepeatButtonState(int modeIndex) {
        mPlayerGlue.setRepeatButtonState(modeIndex);
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
}
