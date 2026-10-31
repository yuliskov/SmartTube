package com.google.android.exoplayer2.source.sabr.manifest;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates media content components over a contiguous period of time.
 */
public class Period {
    /**
     * The period identifier, if one exists.
     */
    @Nullable
    public final String id;

    /**
     * The start time of the period in milliseconds.
     */
    public final long startMs;

    /**
     * The adaptation sets belonging to the period.
     */
    public final List<AdaptationSet> adaptationSets;

    /**
     * @param id The period identifier. May be null.
     * @param startMs The start time of the period in milliseconds.
     * @param adaptationSets The adaptation sets belonging to the period.
     */
    public Period(@Nullable String id, long startMs, List<AdaptationSet> adaptationSets) {
        this.id = id;
        this.startMs = startMs;
        this.adaptationSets = Collections.unmodifiableList(adaptationSets);
    }

    /**
     * Returns the index of the first adaptation set of a given type, or {@link C#INDEX_UNSET} if no
     * adaptation set of the specified type exists.
     *
     * @param type An adaptation set type.
     * @return The index of the first adaptation set of the specified type, or {@link C#INDEX_UNSET}.
     */
    public int getAdaptationSetIndex(int type) {
        int adaptationCount = adaptationSets.size();
        for (int i = 0; i < adaptationCount; i++) {
            if (adaptationSets.get(i).type == type) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }
}
