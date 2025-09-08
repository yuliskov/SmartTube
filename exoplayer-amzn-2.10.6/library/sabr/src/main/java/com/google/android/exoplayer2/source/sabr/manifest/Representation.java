package com.google.android.exoplayer2.source.sabr.manifest;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.sabr.SabrSegmentIndex;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.MultiSegmentBase;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SingleSegmentBase;

import java.util.List;

/**
 * A SABR representation.
 */
public abstract class Representation {
    /**
     * A default value for {@link #revisionId}.
     */
    public static final long REVISION_ID_DEFAULT = -1;

    /**
     * Identifies the revision of the media contained within the representation. If the media can
     * change over time (e.g. as a result of it being re-encoded), then this identifier can be set to
     * uniquely identify the revision of the media. The timestamp at which the media was encoded is
     * often a suitable.
     */
    public final long revisionId;
    /**
     * The format of the representation.
     */
    public final Format format;
    /**
     * The base URL of the representation.
     */
    public final String baseUrl;
    /**
     * The offset of the presentation timestamps in the media stream relative to media time.
     */
    public final long presentationTimeOffsetUs;

    private final RangedUri initializationUri;

    public static Representation newInstance(
            Format format,
            String baseUrl,
            SegmentBase segmentBase) {
        return newInstance(REVISION_ID_DEFAULT, format, baseUrl, segmentBase, null);
    }

    public static Representation newInstance(
            long revisionId,
            Format format,
            String baseUrl,
            SegmentBase segmentBase) {
        return newInstance(revisionId, format, baseUrl, segmentBase, null);
    }

    public static Representation newInstance(
            long revisionId,
            Format format,
            String baseUrl,
            SegmentBase segmentBase,
            String cacheKey) {
        if (segmentBase instanceof SingleSegmentBase) {
            return new SingleSegmentRepresentation(
                    revisionId,
                    format,
                    baseUrl,
                    (SingleSegmentBase) segmentBase,
                    cacheKey,
                    C.LENGTH_UNSET);
        } else if (segmentBase instanceof MultiSegmentBase) {
            return new MultiSegmentRepresentation(
                    revisionId, format, baseUrl, (MultiSegmentBase) segmentBase);
        } else {
            throw new IllegalArgumentException("segmentBase must be of type SingleSegmentBase or "
                    + "MultiSegmentBase");
        }
    }

    private Representation(
            long revisionId,
            Format format,
            String baseUrl,
            SegmentBase segmentBase) {
        this.revisionId = revisionId;
        this.format = format;
        this.baseUrl = baseUrl;
        initializationUri = segmentBase.getInitialization(this);
        presentationTimeOffsetUs = segmentBase.getPresentationTimeOffsetUs();
    }

    /**
     * Returns a {@link RangedUri} defining the location of the representation's initialization data,
     * or null if no initialization data exists.
     */
    public RangedUri getInitializationUri() {
        return initializationUri;
    }

    /**
     * Returns a {@link RangedUri} defining the location of the representation's segment index, or
     * null if the representation provides an index directly.
     */
    public abstract RangedUri getIndexUri();

    /**
     * Returns an index if the representation provides one directly, or null otherwise.
     */
    public abstract SabrSegmentIndex getIndex();

    /** Returns a cache key for the representation if set, or null. */
    public abstract String getCacheKey();

    /**
     * A DASH representation consisting of a single segment.
     */
    public static class SingleSegmentRepresentation extends Representation {

        /**
         * The uri of the single segment.
         */
        public final Uri uri;

        /**
         * The content length, or {@link C#LENGTH_UNSET} if unknown.
         */
        public final long contentLength;

        private final String cacheKey;
        private final RangedUri indexUri;
        private final SingleSegmentIndex segmentIndex;
        
        public static SingleSegmentRepresentation newInstance(
                long revisionId,
                Format format,
                String uri,
                long initializationStart,
                long initializationEnd,
                long indexStart,
                long indexEnd,
                String cacheKey,
                long contentLength) {
            RangedUri rangedUri = new RangedUri(null, initializationStart,
                    initializationEnd - initializationStart + 1);
            SingleSegmentBase segmentBase = new SingleSegmentBase(rangedUri, 1, 0, indexStart,
                    indexEnd - indexStart + 1);
            return new SingleSegmentRepresentation(
                    revisionId, format, uri, segmentBase, cacheKey, contentLength);
        }

        public SingleSegmentRepresentation(
                long revisionId,
                Format format,
                String baseUrl,
                SingleSegmentBase segmentBase,
                String cacheKey,
                long contentLength) {
            super(revisionId, format, baseUrl, segmentBase);
            this.uri = Uri.parse(baseUrl);
            this.indexUri = segmentBase.getIndex();
            this.cacheKey = cacheKey;
            this.contentLength = contentLength;
            // If we have an index uri then the index is defined externally, and we shouldn't return one
            // directly. If we don't, then we can't do better than an index defining a single segment.
            segmentIndex = indexUri != null ? null
                    : new SingleSegmentIndex(new RangedUri(null, 0, contentLength));
        }

        @Override
        public RangedUri getIndexUri() {
            return indexUri;
        }

        @Override
        public SabrSegmentIndex getIndex() {
            return segmentIndex;
        }

        @Override
        public String getCacheKey() {
            return cacheKey;
        }

    }

    /**
     * A DASH representation consisting of multiple segments.
     */
    public static class MultiSegmentRepresentation extends Representation
            implements SabrSegmentIndex {

        private final MultiSegmentBase segmentBase;

        /**
         * @param revisionId Identifies the revision of the content.
         * @param format The format of the representation.
         * @param baseUrl The base URL of the representation.
         * @param segmentBase The segment base underlying the representation.
         */
        public MultiSegmentRepresentation(
                long revisionId,
                Format format,
                String baseUrl,
                MultiSegmentBase segmentBase) {
            super(revisionId, format, baseUrl, segmentBase);
            this.segmentBase = segmentBase;
        }

        @Override
        public RangedUri getIndexUri() {
            return null;
        }

        @Override
        public SabrSegmentIndex getIndex() {
            return this;
        }

        @Override
        public String getCacheKey() {
            return null;
        }

        // DashSegmentIndex implementation.

        @Override
        public RangedUri getSegmentUrl(long segmentIndex) {
            return segmentBase.getSegmentUrl(this, segmentIndex);
        }

        @Override
        public long getSegmentNum(long timeUs, long periodDurationUs) {
            return segmentBase.getSegmentNum(timeUs, periodDurationUs);
        }

        @Override
        public long getTimeUs(long segmentIndex) {
            return segmentBase.getSegmentTimeUs(segmentIndex);
        }

        @Override
        public long getDurationUs(long segmentIndex, long periodDurationUs) {
            return segmentBase.getSegmentDurationUs(segmentIndex, periodDurationUs);
        }

        @Override
        public long getFirstSegmentNum() {
            return segmentBase.getFirstSegmentNum();
        }

        @Override
        public int getSegmentCount(long periodDurationUs) {
            return segmentBase.getSegmentCount(periodDurationUs);
        }

        @Override
        public boolean isExplicit() {
            return segmentBase.isExplicit();
        }

    }
}
