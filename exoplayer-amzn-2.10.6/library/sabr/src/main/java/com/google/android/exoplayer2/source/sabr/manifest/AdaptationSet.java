package com.google.android.exoplayer2.source.sabr.manifest;

import java.util.Collections;
import java.util.List;

/**
 * Represents a set of interchangeable encoded versions of a media content component.
 */
public class AdaptationSet {

    /**
     * Value of {@link #id} indicating no value is set.=
     */
    public static final int ID_UNSET = -1;

    /**
     * A non-negative identifier for the adaptation set that's unique in the scope of its containing
     * period, or {@link #ID_UNSET} if not specified.
     */
    public final int id;

    /**
     * The type of the adaptation set. One of the {@link com.google.android.exoplayer2.C}
     * {@code TRACK_TYPE_*} constants.
     */
    public final int type;

    /**
     * {@link Representation}s in the adaptation set.
     */
    public final List<Representation> representations;

    /**
     * @param id A non-negative identifier for the adaptation set that's unique in the scope of its
     *     containing period, or {@link #ID_UNSET} if not specified.
     * @param type The type of the adaptation set. One of the {@link com.google.android.exoplayer2.C}
     *     {@code TRACK_TYPE_*} constants.
     * @param representations {@link Representation}s in the adaptation set.
     */
    public AdaptationSet(int id, int type, List<Representation> representations) {
        this.id = id;
        this.type = type;
        this.representations = Collections.unmodifiableList(representations);
    }
}
