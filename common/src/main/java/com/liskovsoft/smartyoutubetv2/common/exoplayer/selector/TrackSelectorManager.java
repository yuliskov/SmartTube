package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.RestoreTrackSelector.TrackSelectorCallback;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class TrackSelectorManager implements TrackSelectorCallback {
    public static final int RENDERER_INDEX_VIDEO = 0;
    public static final int RENDERER_INDEX_AUDIO = 1;
    public static final int RENDERER_INDEX_SUBTITLE = 2;
    private static final TrackSelection.Factory FIXED_FACTORY = new FixedTrackSelection.Factory();
    private static final TrackSelection.Factory RANDOM_FACTORY = new RandomTrackSelection.Factory();
    private static final String TAG = TrackSelectorManager.class.getSimpleName();

    private final DefaultTrackSelector mSelector;
    private final TrackSelection.Factory mTrackSelectionFactory;

    private final Renderer[] mRenderers;
    private MediaTrack mPendingSelection;

    public void invalidate() {
        if (mRenderers != null) {
            Arrays.fill(mRenderers, null);
        }
    }

    private static class Renderer {
        public TrackGroupArray trackGroups;
        public TreeSet<MediaTrack> sortedTracks;
        public boolean[] trackGroupsAdaptive;
        public boolean isDisabled;
        public SelectionOverride override;
        public MediaTrack[][] mediaTracks;
        public MediaTrack selectedTrack;
    }

    public static class MediaTrack {
        public Format format;
        public int groupIndex = -1;
        public int trackIndex = -1;
        public boolean isSelected;

        public int rendererIndex;
    }

    private static class MediaTrackFormatComparator implements Comparator<MediaTrack> {
        @Override
        public int compare(MediaTrack mediaTrack1, MediaTrack mediaTrack2) {
            Format format1 = mediaTrack1.format;
            Format format2 = mediaTrack2.format;

            if (format1 == null) { // assume it's auto option
                return -1;
            }

            if (format2 == null) { // assume it's auto option
                return 1;
            }

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

    public TrackSelectorManager(DefaultTrackSelector selector) {
        this(selector, null);
    }

    /**
     * @param selector The track selector.
     * @param trackSelectionFactory A factory for adaptive {@link TrackSelection}s, or null
     *                              if the selection helper should not support adaptive tracks.
     */
    public TrackSelectorManager(DefaultTrackSelector selector, TrackSelection.Factory trackSelectionFactory) {
        mSelector = selector;
        mTrackSelectionFactory = trackSelectionFactory;
        mRenderers = new Renderer[3];

        if (selector instanceof RestoreTrackSelector) {
            ((RestoreTrackSelector) selector).setTrackSelectCallback(this);
        }
    }

    /**
     * Shows the selection dialog for a given renderer.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     */
    private Set<MediaTrack> getAvailableTracks(int rendererIndex) {
        initRenderer(rendererIndex);

        if (mRenderers[rendererIndex] == null) {
            return null;
        }

        return mRenderers[rendererIndex].sortedTracks;
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     */
    private void initRenderer(int rendererIndex) {
        if (mRenderers[rendererIndex] != null) {
            return;
        }

        initTrackGroups(rendererIndex, mSelector.getCurrentMappedTrackInfo(), mSelector.getParameters());
        initMediaTracks(rendererIndex);
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     * @param trackInfo supplied externally from {@link RestoreTrackSelector}
     * @param parameters supplied externally from {@link RestoreTrackSelector}
     */
    private void initRenderer(int rendererIndex, MappedTrackInfo trackInfo, Parameters parameters) {
        if (mRenderers[rendererIndex] != null) {
            return;
        }

        initTrackGroups(rendererIndex, trackInfo, parameters);
        initMediaTracks(rendererIndex);
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     * @param groups supplied externally from {@link RestoreTrackSelector}
     * @param parameters supplied externally from {@link RestoreTrackSelector}
     */
    private void initRenderer(int rendererIndex, TrackGroupArray groups, Parameters parameters) {
        if (mRenderers[rendererIndex] != null) {
            return;
        }

        initTrackGroups(rendererIndex, groups, parameters);
        initMediaTracks(rendererIndex);
    }

    private void initTrackGroups(int rendererIndex, MappedTrackInfo trackInfo, Parameters parameters) {
        if (trackInfo == null) {
            Log.e(TAG, "Can't perform track selection. Mapped track info isn't initialized yet!");
            return;
        }

        if (parameters == null) {
            Log.e(TAG, "Can't perform track selection. Track parameters isn't initialized yet!");
            return;
        }

        TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);

        initTrackGroups(rendererIndex, trackGroups, parameters);
    }

    private void initTrackGroups(int rendererIndex, TrackGroupArray trackGroups, Parameters parameters) {
        Renderer renderer = new Renderer();
        mRenderers[rendererIndex] = renderer;

        renderer.trackGroups = trackGroups;
        renderer.trackGroupsAdaptive = new boolean[renderer.trackGroups.length];
        //for (int i = 0; i < renderer.trackGroups.length; i++) {
        //    renderer.trackGroupsAdaptive[i] = mTrackSelectionFactory != null && trackInfo.getAdaptiveSupport(rendererIndex, i, false) !=
        //            RendererCapabilities.ADAPTIVE_NOT_SUPPORTED && renderer.trackGroups.get(i).length > 1;
        //}
        renderer.isDisabled = parameters.getRendererDisabled(rendererIndex);
        renderer.override = parameters.getSelectionOverride(rendererIndex, renderer.trackGroups);
    }

    private void initMediaTracks(int rendererIndex) {
        if (mRenderers[rendererIndex] == null) {
            return;
        }

        boolean haveSupportedTracks = false;
        boolean haveAdaptiveTracks = false;
        Renderer renderer = mRenderers[rendererIndex];
        renderer.mediaTracks = new MediaTrack[renderer.trackGroups.length][];
        renderer.sortedTracks = new TreeSet<>(new MediaTrackFormatComparator());

        // AUTO OPTION: add auto option
        MediaTrack autoTrack = new MediaTrack();
        autoTrack.rendererIndex = rendererIndex;
        renderer.sortedTracks.add(autoTrack);

        boolean hasSelected = false;

        for (int groupIndex = 0; groupIndex < renderer.trackGroups.length; groupIndex++) {
            TrackGroup group = renderer.trackGroups.get(groupIndex);
            renderer.mediaTracks[groupIndex] = new MediaTrack[group.length];

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                boolean groupIsAdaptive = renderer.trackGroupsAdaptive[groupIndex];
                haveAdaptiveTracks |= groupIsAdaptive;
                haveSupportedTracks = true;
                Format format = group.getFormat(trackIndex);

                MediaTrack mediaTrack = new MediaTrack();
                mediaTrack.format = format;
                mediaTrack.groupIndex = groupIndex;
                mediaTrack.trackIndex = trackIndex;
                mediaTrack.rendererIndex = rendererIndex;
                mediaTrack.isSelected =
                        renderer.override != null && renderer.override.groupIndex == groupIndex && renderer.override.containsTrack(trackIndex);

                if (mediaTrack.isSelected) {
                    hasSelected = true;
                    renderer.selectedTrack = mediaTrack;
                }

                renderer.mediaTracks[groupIndex][trackIndex] = mediaTrack;
                renderer.sortedTracks.add(mediaTrack);
            }
        }

        // AUTO OPTION: unselect auto if other is selected
        autoTrack.isSelected = !hasSelected;
        if (autoTrack.isSelected) {
            renderer.selectedTrack = autoTrack;
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

    private void updateSelection(int rendererIndex) {
        boolean hasSelected = false;

        Renderer renderer = mRenderers[rendererIndex];
        for (int groupIndex = 0; groupIndex < renderer.mediaTracks.length; groupIndex++) {
            for (int trackIndex = 0; trackIndex < renderer.mediaTracks[groupIndex].length; trackIndex++) {
                MediaTrack mediaTrack = renderer.mediaTracks[groupIndex][trackIndex];
                mediaTrack.isSelected =
                        renderer.override != null && renderer.override.groupIndex == groupIndex && renderer.override.containsTrack(trackIndex);

                if (mediaTrack.isSelected) {
                    hasSelected = true;
                    renderer.selectedTrack = mediaTrack;
                }
            }
        }

        // AUTO OPTION: unselect auto if other is selected
        MediaTrack autoTrack = renderer.sortedTracks.first();
        autoTrack.isSelected = !hasSelected;
        if (autoTrack.isSelected) {
            renderer.selectedTrack = autoTrack;
        }
    }

    private void enableAutoSelection(int rendererIndex) {
        mRenderers[rendererIndex].isDisabled = false;
        clearOverride(rendererIndex);
    }

    public Set<MediaTrack> getVideoTracks() {
        return getAvailableTracks(RENDERER_INDEX_VIDEO);
    }

    public Set<MediaTrack> getAudioTracks() {
        return getAvailableTracks(RENDERER_INDEX_AUDIO);
    }

    public Set<MediaTrack> getSubtitleTracks() {
        return getAvailableTracks(RENDERER_INDEX_SUBTITLE);
    }

    private Definition applyPendingSelection(TrackGroupArray groups) {
        Definition definition = null;

        if (mPendingSelection != null) {
            MediaTrack matchedTrack = findBestMatch(mPendingSelection);
            if (matchedTrack.groupIndex != -1) {
                definition = new Definition(groups.get(matchedTrack.groupIndex), matchedTrack.trackIndex);
                setOverride(matchedTrack.rendererIndex, matchedTrack.groupIndex, matchedTrack.trackIndex);
                updateSelection(matchedTrack.rendererIndex);
            }
        }

        mPendingSelection = null;
        return definition;
    }

    @Override
    public Definition onSelectVideoTrack(TrackGroupArray groups, Parameters params) {
        initRenderer(RENDERER_INDEX_VIDEO, groups, params);
        return applyPendingSelection(groups);
    }
    
    public void selectTrack(MediaTrack track) {
        if (track == null) {
            return;
        }

        int rendererIndex = track.rendererIndex;

        initRenderer(rendererIndex);

        if (mRenderers[rendererIndex] == null) {
            Log.e(TAG, "Renderer isn't initialized. Create pending selection...");
            mPendingSelection = track;
            return;
        }

        // enable renderer
        mRenderers[rendererIndex].isDisabled = false;

        // The group being modified is adaptive and we already have a non-null override.
        //if (!selectedBefore) {
        //    MediaTrack matchedTrack = findBestMatch(track);
        //
        //    setOverride(matchedTrack.rendererIndex, matchedTrack.groupIndex, matchedTrack.trackIndex);
        //
        //    // Update the items with the new state.
        //    updateSelection(rendererIndex);
        //
        //    // save immediately
        //    applyOverride(rendererIndex);
        //}

        MediaTrack matchedTrack = findBestMatch(track);

        setOverride(matchedTrack.rendererIndex, matchedTrack.groupIndex, matchedTrack.trackIndex);

        // Update the items with the new state.
        updateSelection(rendererIndex);

        // save immediately
        applyOverride(rendererIndex);
    }

    public MediaTrack getVideoTrack() {
        initRenderer(RENDERER_INDEX_VIDEO);

        Renderer renderer = mRenderers[RENDERER_INDEX_VIDEO];

        if (renderer == null) {
            return null;
        }

        return renderer.selectedTrack;
    }

    private MediaTrack findBestMatch(MediaTrack track) {
        Log.d(TAG, "findBestMatch: Starting: " + track.format);

        Renderer renderer = mRenderers[track.rendererIndex];

        MediaTrack result = createAutoSelection(track.rendererIndex);

        if (track.format != null) { // not auto selection
            for (int groupIndex = 0; groupIndex < renderer.mediaTracks.length; groupIndex++) {
                for (int trackIndex = 0; trackIndex < renderer.mediaTracks[groupIndex].length; trackIndex++) {
                    MediaTrack mediaTrack = renderer.mediaTracks[groupIndex][trackIndex];

                    if (TrackSelectorUtil.compare(mediaTrack, track)) {
                        result = mediaTrack;
                        break;
                    }
                }
            }
        }

        Log.d(TAG, "findBestMatch: Found: " + result.format);

        return result;
    }

    private void applyOverride(int rendererIndex) {
        Renderer renderer = mRenderers[rendererIndex];
        mSelector.setParameters(mSelector.buildUponParameters().setRendererDisabled(rendererIndex, renderer.isDisabled));

        if (renderer.override != null) {
            mSelector.setParameters(mSelector.buildUponParameters().setSelectionOverride(rendererIndex, renderer.trackGroups, renderer.override));
        } else {
            mSelector.setParameters(mSelector.buildUponParameters().clearSelectionOverrides(rendererIndex)); // Auto quality button selected
        }
    }

    private MediaTrack createAutoSelection(int rendererIndex) {
        MediaTrack result = new MediaTrack();
        result.rendererIndex = rendererIndex;
        return result;
    }

    private void clearOverride(int rendererIndex) {
        mRenderers[rendererIndex].override = null;
    }

    private void setOverride(int rendererIndex, int groupIndex, int... trackIndexes) {
        if (groupIndex == -1) {
            mRenderers[rendererIndex].override = null; // auto option selected
            return;
        }

        mRenderers[rendererIndex].override = new SelectionOverride(groupIndex, trackIndexes);
    }

    private void setOverride(int rendererIndex, int group, int[] tracks, boolean enableRandomAdaptation) {
        TrackSelection.Factory factory = tracks.length == 1 ? FIXED_FACTORY : (enableRandomAdaptation ? RANDOM_FACTORY : mTrackSelectionFactory);
        mRenderers[rendererIndex].override = new SelectionOverride(group, tracks);
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
