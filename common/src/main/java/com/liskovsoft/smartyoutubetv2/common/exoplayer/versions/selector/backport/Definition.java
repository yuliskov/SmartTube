package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.backport;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;

// Backport from Exo 2.10 to 2.9
/** Contains of a subset of selected tracks belonging to a {@link TrackGroup}. */
public final class Definition {
    /** The {@link TrackGroup} which tracks belong to. */
    public final TrackGroup group;
    /** The indices of the selected tracks in {@link #group}. */
    public final int[] tracks;
    /** The track selection reason. One of the {@link C} SELECTION_REASON_ constants. */
    public final int reason;
    /** Optional data associated with this selection of tracks. */
    @Nullable public final Object data;

    /**
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     */
    public Definition(TrackGroup group, int... tracks) {
        this(group, tracks, C.SELECTION_REASON_UNKNOWN, /* data= */ null);
    }

    /**
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     * @param reason The track selection reason. One of the {@link C} SELECTION_REASON_ constants.
     * @param data Optional data associated with this selection of tracks.
     */
    public Definition(TrackGroup group, int[] tracks, int reason, @Nullable Object data) {
        this.group = group;
        this.tracks = tracks;
        this.reason = reason;
        this.data = data;
    }

    // Exo 2.10
    //public static Definition from(TrackSelection selection) {
    //    return new Definition(selection.getTrackGroup(), selection.getSelectedIndex());
    //}

    // Exo 2.10
    //@SuppressWarnings("deprecation")
    //public TrackSelection toSelection() {
    //    return new FixedTrackSelection.Factory().createTrackSelection(group, null, tracks);
    //}
}
