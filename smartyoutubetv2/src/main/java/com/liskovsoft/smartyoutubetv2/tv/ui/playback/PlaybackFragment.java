package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
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
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.playback.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.playback.exoplayer.ExoStateManager;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.player.VideoPlayerGlue;

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
    private ExoMediaSourceFactory mMediaSourceFactory;
    private ExoStateManager mStateManager;
    private PlayerEventListener mEventListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMediaGroupAdapters = new HashMap<>();

        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.register(this);

        mMediaSourceFactory = ExoMediaSourceFactory.instance(getContext());

        mStateManager = ExoStateManager.instance(getContext());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();
        }
    }

    /** Pauses the player. */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onPause() {
        super.onPause();

        if (mPlayerGlue != null && mPlayerGlue.isPlaying()) {
            mPlayerGlue.pause();
        }
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
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

        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);

        mPlaylistActionListener = new PlayerActionListener();
        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter, mPlaylistActionListener);
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        mPlayerGlue.playWhenPrepared();

        mRowsAdapter = initializeRelatedVideosRow();
        setAdapter(mRowsAdapter);

        mPlaybackPresenter.onInitDone();
    }

    @Override
    public void updateRelated(VideoGroup group) {
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

    @Override
    public void openDash(InputStream dashManifest) {
        prepareMediaForPlaying(dashManifest);
        mPlayerGlue.play();
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {
        prepareMediaForPlaying(hlsPlaylistUrl);
        mPlayerGlue.play();
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mTrackSelector = null;
            mPlayerGlue = null;
            mPlayerAdapter = null;
            mPlaylistActionListener = null;
        }
    }

    @Override
    public void initTitle(Video video) {
        mPlayerGlue.setTitle(video.title);
        mPlayerGlue.setSubtitle(video.description);
    }

    //private void prepareMediaForPlaying(Uri mediaSourceUri) {
    //    String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
    //    MediaSource mediaSource =
    //            new ExtractorMediaSource(
    //                    mediaSourceUri,
    //                    new DefaultDataSourceFactory(getActivity(), userAgent),
    //                    new DefaultExtractorsFactory(),
    //                    null,
    //                    null);
    //
    //    mPlayer.prepare(mediaSource);
    //}

    private void prepareMediaForPlaying(InputStream dashManifest) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
        mPlayer.prepare(mediaSource);
    }

    private void prepareMediaForPlaying(String hlsPlaylistUrl) {
        //String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromHlsPlaylist(Uri.parse(hlsPlaylistUrl));
        mPlayer.prepare(mediaSource);
    }

    private ArrayObjectAdapter initializeRelatedVideosRow() {
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

    @Override
    public void clearRelated() {
        mMediaGroupAdapters.clear();
        mRowsAdapter.clear();

        // player controls row
        mRowsAdapter.add(mPlayerGlue.getControlsRow());
    }

    private class PlayerActionListener implements VideoPlayerGlue.OnActionClickedListener {
        @Override
        public void onPrevious() {
            mEventListener.onPrevious();
        }

        @Override
        public void onNext() {
            mEventListener.onNext();
        }
    }

    @Override
    public void setListener(PlayerEventListener listener) {
        mEventListener = listener;
    }

    @Override
    public PlayerController getController() {
        return this;
    }
}
