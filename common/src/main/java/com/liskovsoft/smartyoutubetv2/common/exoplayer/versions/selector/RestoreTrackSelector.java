package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.trackselection.TrackSelection.Factory;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

public class RestoreTrackSelector extends DefaultTrackSelector {
    private static final String TAG = RestoreTrackSelector.class.getSimpleName();
    private static final int FORMAT_NOT_SUPPORTED = 19;
    private static final int FORMAT_FORCE_SUPPORT = 52;
    private TrackSelectorCallback mCallback;

    public interface TrackSelectorCallback {
        Pair<Definition, MediaTrack> onSelectVideoTrack(TrackGroupArray groups, Parameters params);
        Pair<Definition, MediaTrack> onSelectAudioTrack(TrackGroupArray groups, Parameters params);
        Pair<Definition, MediaTrack> onSelectSubtitleTrack(TrackGroupArray groups, Parameters params);
        void updateVideoTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
        void updateAudioTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
        void updateSubtitleTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
    }

    public RestoreTrackSelector(Factory trackSelectionFactory) {
        super(trackSelectionFactory);
        // Could help with Shield resolution bug?
        //setParameters(buildUponParameters().setForceHighestSupportedBitrate(true));
    }

    public void setOnTrackSelectCallback(TrackSelectorCallback callback) {
        mCallback = callback;
    }

    // Exo 2.9
    //@Nullable
    //@Override
    //protected TrackSelection selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
    //                                          Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectVideoTrack(groups, params);
    //
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectVideoTrack: choose custom video processing");
    //            return resultPair.first.toSelection();
    //        }
    //    }
    //
    //    Log.d(TAG, "selectVideoTrack: choose default video processing");
    //
    //    TrackSelection trackSelection = super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && trackSelection != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_VIDEO, groups)) {
    //        mCallback.updateVideoTrackSelection(groups, params, Definition.from(trackSelection));
    //    }
    //
    //    return trackSelection;
    //}

    // Exo 2.9
    //@Nullable
    //@Override
    //protected Pair<TrackSelection, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupports,
    //                                                                 int mixedMimeTypeAdaptationSupports, Parameters params,
    //                                                                 @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectAudioTrack(groups, params);
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectVideoTrack: choose custom audio processing");
    //            return new Pair<>(resultPair.first.toSelection(), new AudioTrackScore(resultPair.second.format, params, RendererCapabilities.FORMAT_HANDLED));
    //        }
    //    }
    //
    //    Log.d(TAG, "selectAudioTrack: choose default audio processing");
    //
    //    Pair<TrackSelection, AudioTrackScore> selectionPair =
    //            super.selectAudioTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && selectionPair != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups)) {
    //        mCallback.updateAudioTrackSelection(groups, params, Definition.from(selectionPair.first));
    //    }
    //
    //    return selectionPair;
    //}

    // Exo 2.9
    //@Nullable
    //@Override
    //protected Pair<TrackSelection, Integer> selectTextTrack(TrackGroupArray groups, int[][] formatSupport, Parameters params) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectSubtitleTrack(groups, params);
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectTextTrack: choose custom text processing");
    //            return new Pair<>(resultPair.first.toSelection(), 10);
    //        }
    //    }
    //
    //    Log.d(TAG, "selectTextTrack: choose default text processing");
    //
    //    Pair<TrackSelection, Integer> selectionPair = super.selectTextTrack(groups, formatSupport, params);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && selectionPair != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_SUBTITLE, groups)) {
    //        mCallback.updateSubtitleTrackSelection(groups, params, Definition.from(selectionPair.first));
    //    }
    //
    //    return selectionPair;
    //}

    //@Override
    //public void setParameters(Parameters parameters) {
    //    // Fix dropping to 144p by disabling any overrides.
    //    invalidate();
    //}

    // Exo 2.10 and up
    @Nullable
    @Override
    protected Definition selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
                                              Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
        if (mCallback != null) {
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectVideoTrack(groups, params);

            if (resultPair != null) {
                Log.d(TAG, "selectVideoTrack: choose custom video processing");
                return resultPair.first;
            } else {
                return null; // video disabled
            }
        }

        Log.d(TAG, "selectVideoTrack: choose default video processing");

        Definition definition = super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, false);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && definition != null) {
            mCallback.updateVideoTrackSelection(groups, params, definition);
        }

        return definition;
    }

    // Exo 2.10 and up
    @Nullable
    @Override
    protected Pair<Definition, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupports,
                                                                 int mixedMimeTypeAdaptationSupports, Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
        if (mCallback != null) {
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectAudioTrack(groups, params);
            if (resultPair != null) {
                Log.d(TAG, "selectVideoTrack: choose custom audio processing");
                return new Pair<>(resultPair.first, new AudioTrackScore(resultPair.second.format, params, RendererCapabilities.FORMAT_HANDLED));
            } else {
                return null; // audio disabled
            }
        }

        Log.d(TAG, "selectAudioTrack: choose default audio processing");

        Pair<Definition, AudioTrackScore> definitionPair = super.selectAudioTrack(groups, formatSupports,
                mixedMimeTypeAdaptationSupports, params, false);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && definitionPair != null) {
            mCallback.updateAudioTrackSelection(groups, params, definitionPair.first);
        }

        return definitionPair;
    }

    // Exo 2.10 and up
    @Nullable
    @Override
    protected Pair<Definition, TextTrackScore> selectTextTrack(TrackGroupArray groups, int[][] formatSupport, Parameters params,
                                                               @Nullable String selectedAudioLanguage) throws ExoPlaybackException {
        if (mCallback != null) {
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectSubtitleTrack(groups, params);
            if (resultPair != null) {
                Log.d(TAG, "selectTextTrack: choose custom text processing");
                return new Pair<>(resultPair.first, new TextTrackScore(resultPair.second.format, params, RendererCapabilities.FORMAT_HANDLED, ""));
            }
        }

        Log.d(TAG, "selectTextTrack: choose default text processing");

        Pair<Definition, TextTrackScore> definitionPair = super.selectTextTrack(groups, formatSupport, params, selectedAudioLanguage);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && definitionPair != null) {
            mCallback.updateSubtitleTrackSelection(groups, params, definitionPair.first);
        }

        return definitionPair;
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
