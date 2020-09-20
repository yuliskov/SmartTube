package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.util.Pair;
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
    private static final int FORMAT_NOT_SUPPORTED = 19;
    private static final int FORMAT_FORCE_SUPPORT = 52;
    private TrackSelectorCallback mCallback;

    public interface TrackSelectorCallback {
        Definition onSelectVideoTrack(TrackGroupArray groups, Parameters params);
        void updateVideoTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
        void updateAudioTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
    }

    public RestoreTrackSelector(Factory trackSelectionFactory) {
        super(trackSelectionFactory);
    }

    public void setTrackSelectCallback(TrackSelectorCallback callback) {
        mCallback = callback;
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
        if (mCallback != null) {
            Log.d(TAG, "selectVideoTrack: choose custom processing");
            TrackSelection.Definition definition = mCallback.onSelectVideoTrack(groups, params);
            if (definition != null) {
                return definition;
            }
        }

        Log.d(TAG, "selectVideoTrack: choose default processing");

        Definition definition = super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, enableAdaptiveTrackSelection);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_VIDEO, groups)) {
            mCallback.updateVideoTrackSelection(groups, params, definition);
        }

        return definition;
    }

    @Nullable
    @Override
    protected Pair<Definition, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupports,
                                                                 int mixedMimeTypeAdaptationSupports, Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
        Pair<Definition, AudioTrackScore> trackScorePair = super.selectAudioTrack(groups, formatSupports,
                mixedMimeTypeAdaptationSupports, params, enableAdaptiveTrackSelection);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && trackScorePair != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups)) {
            mCallback.updateAudioTrackSelection(groups, params, trackScorePair.first);
        }

        return trackScorePair;
    }

    private void unlockAllVideoFormats(int[][] formatSupports) {
        final int videoTrackIndex = 0;

        for (int j = 0; j < formatSupports[videoTrackIndex].length; j++) {
            if (formatSupports[videoTrackIndex][j] == FORMAT_NOT_SUPPORTED) { // video format not supported by system decoders
                formatSupports[videoTrackIndex][j] = FORMAT_FORCE_SUPPORT; // force support of video format
            }
        }
    }
}
