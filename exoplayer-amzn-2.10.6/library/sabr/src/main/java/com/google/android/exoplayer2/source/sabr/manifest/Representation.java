package com.google.android.exoplayer2.source.sabr.manifest;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;

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

    public static Representation newInstance(
            Format format,
            String baseUrl) {
        return newInstance(REVISION_ID_DEFAULT, format, baseUrl, null);
    }

    public static Representation newInstance(
            long revisionId,
            Format format,
            String baseUrl,
            String cacheKey) {
        // TODO: add SubtitleRepresentation
        return new AdaptiveRepresentation(
                revisionId,
                format,
                baseUrl,
                cacheKey,
                C.LENGTH_UNSET);
    }

    private Representation(
            long revisionId,
            Format format,
            String baseUrl) {
        this.revisionId = revisionId;
        this.format = format;
        this.baseUrl = baseUrl;
        presentationTimeOffsetUs = 0;
    }

    /** Returns a cache key for the representation if set, or null. */
    public abstract String getCacheKey();

    /**
     * A DASH representation consisting of a single segment.
     */
    public static class AdaptiveRepresentation extends Representation {

        /**
         * The uri of the single segment.
         */
        public final Uri uri;

        /**
         * The content length, or {@link C#LENGTH_UNSET} if unknown.
         */
        public final long contentLength;

        private final String cacheKey;
        
        public static AdaptiveRepresentation newInstance(
                long revisionId,
                Format format,
                String uri,
                long initializationStart,
                long initializationEnd,
                long indexStart,
                long indexEnd,
                String cacheKey,
                long contentLength) {
            return new AdaptiveRepresentation(
                    revisionId, format, uri, cacheKey, contentLength);
        }

        public AdaptiveRepresentation(
                long revisionId,
                Format format,
                String baseUrl,
                String cacheKey,
                long contentLength) {
            super(revisionId, format, baseUrl);
            this.uri = Uri.parse(baseUrl);
            this.cacheKey = cacheKey;
            this.contentLength = contentLength;
            // If we have an index uri then the index is defined externally, and we shouldn't return one
            // directly. If we don't, then we can't do better than an index defining a single segment.
            //segmentIndex = indexUri != null ? null
            //        : new SingleSegmentIndex(new RangedUri(null, 0, contentLength));
        }

        @Override
        public String getCacheKey() {
            return cacheKey;
        }

    }
}
