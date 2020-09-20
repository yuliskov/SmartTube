package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.trackselection.TrackSelection.Factory;
import com.liskovsoft.sharedutils.mylogger.Log;

public class RestoreTrackSelector extends DefaultTrackSelector {
    private static final String TAG = RestoreTrackSelector.class.getSimpleName();
    private final Context mContext;
    //private final PlayerStateManagerBase mStateManager;
    private boolean mAlreadyRestored;
    private TrackSelectorCallback mCallback;

    public RestoreTrackSelector(Factory trackSelectionFactory, Context context) {
        super(trackSelectionFactory);
        mContext = context;
        //mStateManager = new PlayerStateManagerBase(context);
    }

    // Ver 2.9.6
    //@Nullable
    //@Override
    //protected TrackSelection selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
    //                                          Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //
    //    // Restore state before video starts playing
    //    boolean isAuto = !params.hasSelectionOverride(ExoPlayerFragment.RENDERER_INDEX_VIDEO, groups);
    //
    //    if (isAuto && !mAlreadyRestored) {
    //        mAlreadyRestored = true;
    //        restoreVideoTrack(groups);
    //    }
    //
    //    return super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //}

    // Ver 2.10.4
    @Nullable
    @Override
    protected TrackSelection.Definition selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
                                              Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {

        //// Restore state before video starts playing
        //boolean isAuto = !params.hasSelectionOverride(ExoPlayerFragment.RENDERER_INDEX_VIDEO, groups);
        //
        //if (isAuto && !mAlreadyRestored) {
        //    mAlreadyRestored = true;
        //    restoreVideoTrack(groups);
        //}

        if (mCallback != null) {
            TrackSelection.Definition definition = mCallback.onSelectVideoTrack(groups, params);
            if (definition != null) {
                return definition;
            }
        }

        // mTrackSelectorManager.applyPendingSelection(groups);

        Log.d(TAG, "selectVideoTrack: " + getCurrentMappedTrackInfo());

        return super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, enableAdaptiveTrackSelection);
    }

    //@Override
    //protected Definition[] selectAllTracks(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports,
    //                                       int[] rendererMixedMimeTypeAdaptationSupports, Parameters params) throws ExoPlaybackException {
    //
    //    return super.selectAllTracks(mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);
    //}

    //private void restoreVideoTrack(TrackGroupArray groups) {
    //    MyFormat format = mStateManager.findPreferredVideoFormat(groups);
    //
    //    if (format != null) {
    //        setParameters(buildUponParameters().setSelectionOverride(
    //                ExoPlayerFragment.RENDERER_INDEX_VIDEO,
    //                groups,
    //                new SelectionOverride(format.pair.first, format.pair.second)
    //        ));
    //    }
    //}

    private void unlockAllVideoFormats(int[][] formatSupports) {
        final int videoTrackIndex = 0;

        for (int j = 0; j < formatSupports[videoTrackIndex].length; j++) {
            if (formatSupports[videoTrackIndex][j] == 19) { // video format not supported by system decoders
                formatSupports[videoTrackIndex][j] = 52; // force support of video format
            }
        }
    }

    public void setTrackSelectCallback(TrackSelectorCallback callback) {
        mCallback = callback;
    }

    public interface TrackSelectorCallback {
        Definition onSelectVideoTrack(TrackGroupArray groups, Parameters params);
        //void onSelectAllTracks(MappedTrackInfo trackInfo, Parameters params);
    }
}
