package com.liskovsoft.smartyoutubetv2.common.playback.exoplayer.state;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class TrackSelectionManager {
    public static final int RENDERER_INDEX_VIDEO = 0;
    public static final int RENDERER_INDEX_AUDIO = 1;
    public static final int RENDERER_INDEX_SUBTITLE = 2;
    private static final TrackSelection.Factory FIXED_FACTORY = new FixedTrackSelection.Factory();
    private static final TrackSelection.Factory RANDOM_FACTORY = new RandomTrackSelection.Factory();

    private final DefaultTrackSelector mSelector;
    private final TrackSelection.Factory mTrackSelectionFactory;
    
    private int mRendererIndex;
    private TrackGroupArray mTrackGroups;
    private boolean[] mTrackGroupsAdaptive;
    private boolean mIsDisabled;
    private SelectionOverride mOverride;
    
    private MediaTrack[][] mMediaTracks;
    private TreeSet<MediaTrack> mSortedTrackList;

    private static class MediaTrack {
        public Format format;
        public int groupIndex;
        public int trackIndex;
        public boolean isSelected;
    }

    private static class MediaTrackFormatComparator implements Comparator<MediaTrack> {
        @Override
        public int compare(MediaTrack mediaTrack1, MediaTrack mediaTrack2) {
            Format format1 = mediaTrack1.format;
            Format format2 = mediaTrack2.format;

            // sort subtitles by language code
            if (format1.language != null && format2.language != null) {
                return format1.language.compareTo(format2.language);
            }

            int leftVal = format2.width + (int) format2.frameRate + (format2.codecs != null && format2.codecs.contains("avc") ? 31 : 0);
            int rightVal = format1.width + (int) format1.frameRate + (format1.codecs != null && format1.codecs.contains("avc") ? 31 : 0);

            int delta = leftVal - rightVal;
            if (delta == 0) {
                return format2.bitrate - format1.bitrate;
            }

            return leftVal - rightVal;
        }
    }

    /**
     * @param selector The track selector.
     * @param trackSelectionFactory A factory for adaptive {@link TrackSelection}s, or null
     *                              if the selection helper should not support adaptive tracks.
     */
    public TrackSelectionManager(DefaultTrackSelector selector, TrackSelection.Factory trackSelectionFactory) {
        mSelector = selector;
        mTrackSelectionFactory = trackSelectionFactory;
    }

    /**
     * Shows the selection dialog for a given renderer.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     */
    public void showAvailableTracks(int rendererIndex) {
        MappedTrackInfo trackInfo = mSelector.getCurrentMappedTrackInfo();
        mRendererIndex = rendererIndex;

        mTrackGroups = trackInfo.getTrackGroups(rendererIndex);
        mTrackGroupsAdaptive = new boolean[mTrackGroups.length];
        for (int i = 0; i < mTrackGroups.length; i++) {
            mTrackGroupsAdaptive[i] = mTrackSelectionFactory != null && trackInfo.getAdaptiveSupport(rendererIndex, i, false) !=
                    RendererCapabilities.ADAPTIVE_NOT_SUPPORTED && mTrackGroups.get(i).length > 1;
        }
        mIsDisabled = mSelector.getParameters().getRendererDisabled(rendererIndex);
        mOverride = mSelector.getParameters().getSelectionOverride(rendererIndex, mTrackGroups);

        buildMediaTracks();
    }

    private void buildMediaTracks() {
        //if (mRendererIndex == ExoPlayerFragment.RENDERER_INDEX_SUBTITLE) {
        //    defaultViewTitle = mContext.getResources().getString(R.string.default_subtitle);
        //}

        // Per-track views.
        boolean haveSupportedTracks = false;
        boolean haveAdaptiveTracks = false;
        mMediaTracks = new MediaTrack[mTrackGroups.length][];
        mSortedTrackList = new TreeSet<>(new MediaTrackFormatComparator());
        for (int groupIndex = 0; groupIndex < mTrackGroups.length; groupIndex++) {
            TrackGroup group = mTrackGroups.get(groupIndex);
            mMediaTracks[groupIndex] = new MediaTrack[group.length];

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                boolean groupIsAdaptive = mTrackGroupsAdaptive[groupIndex];
                haveAdaptiveTracks |= groupIsAdaptive;
                haveSupportedTracks = true;
                Format format = group.getFormat(trackIndex);

                MediaTrack mediaTrack = new MediaTrack();
                mediaTrack.format = format;
                mediaTrack.groupIndex = groupIndex;
                mediaTrack.trackIndex = trackIndex;

                mMediaTracks[groupIndex][trackIndex] = mediaTrack;
                mSortedTrackList.add(mediaTrack);
            }
        }
    }

    /**
     * Get the number of tracks with the same resolution.
     * <p>I assume that the tracks already have been sorted in descendants order. <br/>
     * <p>Details: {@code com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.mpdbuilder.MyMPDBuilder}
     * @param group the group
     * @param trackIndex current track in group
     * @return
     */
    private int getRelatedTrackOffsets(TrackGroup group, int trackIndex) {
        int prevHeight = 0;
        int offset = 0;
        for (int i = trackIndex; i > 0; i--) {
            Format format = group.getFormat(i);
            if (prevHeight == 0) {
                prevHeight = format.height;
            } else if (prevHeight == format.height) {
                offset++;
            } else {
                break;
            }
        }
        return offset;
    }

    private void updateMediaTracks() {
        for (int i = 0; i < mMediaTracks.length; i++) {
            for (int j = 0; j < mMediaTracks[i].length; j++) {
                MediaTrack mediaTrack = mMediaTracks[i][j];
                boolean isChecked = mOverride != null && mOverride.groupIndex == i && mOverride.containsTrack(j);
                mediaTrack.isSelected = isChecked;
            }
        }
    }

    public void enableAutoSelection() {
        mIsDisabled = false;
        clearOverride();
    }

    public Set<MediaTrack> getSortedTracks() {
        return mSortedTrackList;
    }

    // old: click on selected track
    public void selectMediaTrack(MediaTrack track) {
        // change quality
        mIsDisabled = false;

        int groupIndex = track.groupIndex;
        int trackIndex = track.trackIndex;

        // The group being modified is adaptive and we already have a non-null override.
        if (!track.isSelected) {
            setOverride(groupIndex, trackIndex);
        }

        // Update the views with the new state.
        updateMediaTracks();

        // save immediately
        applySelection();
    }

    private void applySelection() {
        mSelector.setParameters(mSelector.buildUponParameters().setRendererDisabled(mRendererIndex, mIsDisabled));

        if (mOverride != null) {
            mSelector.setParameters(mSelector.buildUponParameters().setSelectionOverride(mRendererIndex, mTrackGroups, mOverride));
        } else {
            mSelector.setParameters(mSelector.buildUponParameters().clearSelectionOverrides(mRendererIndex)); // Auto quality button selected
        }

        // TODO: attempt to play after selection
        //mPlayerFragment.retryIfNeeded();
    }

    private void clearOverride() {
        if (canSwitchFormats()) {
            mOverride = null;
        }
    }

    private void setOverride(int groupIndex, int... tracks) {
        if (canSwitchFormats()) {
            mOverride = new SelectionOverride(groupIndex, tracks);
        }
    }

    private boolean canSwitchFormats() {
        return true; // allow user to switch format temporally (until next video)

        //if (mRendererIndex != ExoPlayerFragment.RENDERER_INDEX_VIDEO) {
        //    return true;
        //}
        //
        //ExoPreferences prefs = ExoPreferences.instance(mContext);
        //
        //if (prefs.getPreferredFormat().equals(ExoPreferences.FORMAT_ANY)) {
        //    return true;
        //}
        //
        //MessageHelpers.showMessage(mContext, R.string.toast_format_restricted);
        //
        //return false;
    }

    private void setOverride(int group, int[] tracks, boolean enableRandomAdaptation) {
        TrackSelection.Factory factory = tracks.length == 1 ? FIXED_FACTORY : (enableRandomAdaptation ? RANDOM_FACTORY : mTrackSelectionFactory);
        mOverride = new SelectionOverride(group, tracks);
    }

    // Track array manipulation.
    private static int[] getTracksAdding(SelectionOverride override, int addedTrack) {
        int[] tracks = override.tracks;
        tracks = Arrays.copyOf(tracks, tracks.length + 1);
        tracks[tracks.length - 1] = addedTrack;
        return tracks;
    }

    private static int[] getTracksRemoving(SelectionOverride override, int removedTrack) {
        int[] tracks = new int[override.length - 1];
        int trackCount = 0;
        for (int i = 0; i < tracks.length + 1; i++) {
            int track = override.tracks[i];
            if (track != removedTrack) {
                tracks[trackCount++] = track;
            }
        }
        return tracks;
    }
}
