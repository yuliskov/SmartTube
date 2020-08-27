package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.MediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.data.old.VideoContract;
import com.liskovsoft.smartyoutubetv2.tv.model.Playlist;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.tv.model.old.VideoCursorMapper;
import com.liskovsoft.smartyoutubetv2.tv.player.VideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VideoDetailsActivity;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

//import static com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment.VideoLoaderCallbacks.RELATED_VIDEOS_LOADER;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to
 * {@link VideoPlayerGlue}.
 */
public class PlaybackFragment extends VideoSupportFragment implements PlaybackView {
    private static final String TAG = PlaybackFragment.class.getSimpleName();
    private static final int UPDATE_DELAY = 16;
    private VideoPlayerGlue mPlayerGlue;
    private LeanbackPlayerAdapter mPlayerAdapter;
    private SimpleExoPlayer mPlayer;
    private TrackSelector mTrackSelector;
    private PlaylistActionListener mPlaylistActionListener;

    private Video mVideo;
    private Playlist mPlaylist;
    //private VideoLoaderCallbacks mVideoLoaderCallbacks;
    //private CursorObjectAdapter mVideoCursorAdapter;
    private PlaybackPresenter mPlaybackPresenter;
    private ArrayObjectAdapter mRowsAdapter;
    private Map<Integer, VideoGroupObjectAdapter> mMediaGroupAdapters;
    private MediaSourceFactory mMediaSourceFactory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMediaGroupAdapters = new HashMap<>();

        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.register(this);

        mMediaSourceFactory = MediaSourceFactory.instance(getContext());

        mVideo = getActivity().getIntent().getParcelableExtra(VideoDetailsActivity.VIDEO);
        mPlaylist = new Playlist();
        mPlaylist.add(mVideo); // TODO: only one video in playlist

        //mVideoLoaderCallbacks = new VideoLoaderCallbacks(mPlaylist);

        // Loads the playlist.
        //Bundle args = new Bundle();
        //args.putString(VideoContract.VideoEntry.COLUMN_CATEGORY, mVideo.category);
        //LoaderManager.getInstance(this)
        //        .initLoader(VideoLoaderCallbacks.QUEUE_VIDEOS_LOADER, args, mVideoLoaderCallbacks);

        //mVideoCursorAdapter = setupRelatedVideosCursor();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPlaybackPresenter.onInitDone();
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

        mPlayer = ExoPlayerFactory.newSimpleInstance(getActivity(), mTrackSelector);

        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY); // !!!

        mPlaylistActionListener = new PlaylistActionListener(mPlaylist);
        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter, mPlaylistActionListener);
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        mPlayerGlue.playWhenPrepared();

        play(mVideo);

        mRowsAdapter = initializeRelatedVideosRow();
        setAdapter(mRowsAdapter);

        updateRelatedVideosRow();
    }

    @Override
    public void updateRelatedVideos(VideoGroup group) {
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
    public Video getVideo() {
        return mVideo;
    }

    @Override
    public void loadDashStream(InputStream dashManifest) {
        prepareMediaForPlaying(dashManifest);
        mPlayerGlue.play();
    }

    private void updateRelatedVideosRow() {
        //if (mRowsAdapter == null) {
        //    Log.e(TAG, "Related videos row not initialized yet.");
        //    return;
        //}
        //
        //HeaderItem header = new HeaderItem(getString(R.string.related_movies));
        //ListRow row = new ListRow(header, mVideoCursorAdapter); // TODO: related videos
        //mRowsAdapter.add(row);
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

    private void play(Video video) {
        mPlayerGlue.setTitle(video.title);
        mPlayerGlue.setSubtitle(video.description);
        //prepareMediaForPlaying(Uri.parse(video.videoUrl));
        //mPlayerGlue.play();
    }

    private void prepareMediaForPlaying(Uri mediaSourceUri) {
        String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource =
                new ExtractorMediaSource(
                        mediaSourceUri,
                        new DefaultDataSourceFactory(getActivity(), userAgent),
                        new DefaultExtractorsFactory(),
                        null,
                        null);

        mPlayer.prepare(mediaSource);
    }

    private void prepareMediaForPlaying(InputStream dashManifest) {
        String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        MediaSource mediaSource = mMediaSourceFactory.fromDashManifest(dashManifest);
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

        rowsAdapter.add(mPlayerGlue.getControlsRow());

        setOnItemViewClickedListener(new ItemViewClickedListener());

        return rowsAdapter;
    }

    //private CursorObjectAdapter setupRelatedVideosCursor() {
    //    CursorObjectAdapter videoCursorAdapter = new CursorObjectAdapter(new CardPresenter());
    //    videoCursorAdapter.setMapper(new VideoCursorMapper());
    //
    //    Bundle args = new Bundle();
    //    args.putString(VideoContract.VideoEntry.COLUMN_CATEGORY, mVideo.category);
    //    getLoaderManager().initLoader(RELATED_VIDEOS_LOADER, args, mVideoLoaderCallbacks);
    //
    //    return videoCursorAdapter;
    //}

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

    /** Opens the video details page when a related video has been clicked. */
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;

                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                        getActivity(),
                                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                        VideoDetailsActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }

    ///** Loads a playlist with videos from a cursor and also updates the related videos cursor. */
    //protected class VideoLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
    //
    //    static final int RELATED_VIDEOS_LOADER = 1;
    //    static final int QUEUE_VIDEOS_LOADER = 2;
    //
    //    private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();
    //
    //    private final Playlist playlist;
    //
    //    private VideoLoaderCallbacks(Playlist playlist) {
    //        this.playlist = playlist;
    //    }
    //
    //    @Override
    //    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    //        // When loading related videos or videos for the playlist, query by category.
    //        String category = args.getString(VideoContract.VideoEntry.COLUMN_CATEGORY);
    //        return new CursorLoader(
    //                getActivity(),
    //                VideoContract.VideoEntry.CONTENT_URI,
    //                null,
    //                VideoContract.VideoEntry.COLUMN_CATEGORY + " = ?",
    //                new String[] {category},
    //                null);
    //    }
    //
    //    @Override
    //    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    //        if (cursor == null || !cursor.moveToFirst()) {
    //            return;
    //        }
    //        int id = loader.getId();
    //        if (id == QUEUE_VIDEOS_LOADER) {
    //            playlist.clear();
    //            do {
    //                Video video = (Video) mVideoCursorMapper.convert(cursor);
    //
    //                // Set the current position to the selected video.
    //                if (video.id == mVideo.id) {
    //                    playlist.setCurrentPosition(playlist.size());
    //                }
    //
    //                playlist.add(video);
    //
    //            } while (cursor.moveToNext());
    //        } else if (id == RELATED_VIDEOS_LOADER) {
    //            mVideoCursorAdapter.changeCursor(cursor);
    //        }
    //    }
    //
    //    @Override
    //    public void onLoaderReset(Loader<Cursor> loader) {
    //        mVideoCursorAdapter.changeCursor(null);
    //    }
    //}

    class PlaylistActionListener implements VideoPlayerGlue.OnActionClickedListener {

        private Playlist mPlaylist;

        PlaylistActionListener(Playlist playlist) {
            this.mPlaylist = playlist;
        }

        @Override
        public void onPrevious() {
            play(mPlaylist.previous());
        }

        @Override
        public void onNext() {
            play(mPlaylist.next());
        }
    }
}
