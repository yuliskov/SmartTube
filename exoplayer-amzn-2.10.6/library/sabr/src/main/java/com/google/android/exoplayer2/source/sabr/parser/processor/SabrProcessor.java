package com.google.android.exoplayer2.source.sabr.parser.processor;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.sabr.parser.exceptions.MediaSegmentMismatchError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrStreamError;
import com.google.android.exoplayer2.source.sabr.parser.models.ConsumedRange;
import com.google.android.exoplayer2.source.sabr.parser.models.InitializedFormat;
import com.google.android.exoplayer2.source.sabr.parser.models.Segment;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientAbrState;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus.Status;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.TimeRange;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class SabrProcessor {
    private static final String TAG = SabrProcessor.class.getSimpleName();
    public static final int NO_VALUE = -1;
    private final int liveSegmentTargetDurationToleranceMs;
    private final int liveSegmentTargetDurationSec;
    private ClientAbrState clientAbrState;
    private String videoId;
    private final Map<Integer, Segment> partialSegments;
    private final Map<String, InitializedFormat> initializedFormats;
    private Status streamProtectionStatus;
    private String poToken;

    public SabrProcessor() {
        this(NO_VALUE, NO_VALUE);
    }

    public SabrProcessor(
            int liveSegmentTargetDurationSec,
            int liveSegmentTargetDurationToleranceMs
    ) {
        this.liveSegmentTargetDurationSec = liveSegmentTargetDurationSec != NO_VALUE ? liveSegmentTargetDurationSec : 5;
        this.liveSegmentTargetDurationToleranceMs = liveSegmentTargetDurationToleranceMs != NO_VALUE ? liveSegmentTargetDurationToleranceMs : 100;
        if (this.liveSegmentTargetDurationToleranceMs >= (this.liveSegmentTargetDurationSec * 1_000) / 2) {
            throw new IllegalArgumentException("liveSegmentTargetDurationToleranceMs must be less than half of liveSegmentTargetDurationSec in milliseconds");
        }
        partialSegments = new HashMap<>();
        initializedFormats = new HashMap<>();
        // TODO: add more values
        initializeClientAbrState();
    }

    private void initializeClientAbrState() {
        // TODO: initialize builder
        clientAbrState = ClientAbrState.newBuilder()
                .build();
    }

    public ProcessMediaHeaderResult processMediaHeader(MediaHeader mediaHeader) {
        if (mediaHeader.hasVideoId() && videoId != null && !Helpers.equals(mediaHeader.getVideoId(), videoId)) {
            throw new SabrStreamError(
                    String.format("Received unexpected MediaHeader for video %s (expecting %s)", mediaHeader.getVideoId(), videoId));
        }

        if (!mediaHeader.hasFormatId()) {
            throw new SabrStreamError(String.format("FormatId not found in MediaHeader (media_header=%s)", mediaHeader));
        }

        // Guard. This should not happen, except if we don't clear partial segments
        if (partialSegments.containsKey(mediaHeader.getHeaderId())) {
            throw new SabrStreamError(String.format("Header ID %s already exists", mediaHeader.getHeaderId()));
        }

        InitializedFormat initializedFormat = initializedFormats.get(mediaHeader.getFormatId().toString());

        if (initializedFormat == null) {
            throw new SabrStreamError(String.format("Initialized format not found for %s", mediaHeader.getFormatId()));
        }

        if (mediaHeader.hasCompression()) {
            // Unknown when this is used, but it is not supported currently
            throw new SabrStreamError(String.format("Compression not supported in MediaHeader (media_header=%s)", mediaHeader));
        }

        long sequenceNumber = mediaHeader.hasSequenceNumber() ? mediaHeader.getSequenceNumber() : NO_VALUE;
        boolean isInitSegment = mediaHeader.getIsInitSegment();

        if (sequenceNumber == NO_VALUE && !isInitSegment) {
            throw new SabrStreamError(String.format("Sequence number not found in MediaHeader (media_header=%s)", mediaHeader));
        }

        initializedFormat.sequenceLmt = mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE;

        // Need to keep track of if we discard due to be consumed or not
        // for processing down the line (MediaEnd)
        boolean consumed = false;
        boolean discard = initializedFormat.discard;

        // Guard: Check if sequence number is within any existing consumed range
        // The server should not send us any segments that are already consumed
        // However, if retrying a request, we may get the same segment again
        if (!isInitSegment &&
                Helpers.findFirst(initializedFormat.consumedRanges,
                        cr -> cr.startSequenceNumber <= sequenceNumber && sequenceNumber <= cr.endSequenceNumber) == null) {
            Log.d(TAG, "%s segment %s already consumed, marking segment as consumed", initializedFormat.formatId, sequenceNumber);
            consumed = true;
        }

        // Validate that the segment is in order.
        // Note: If the format is to be discarded, we do not care about the order
        // and can expect uncommanded seeks as the consumer does not know about it.
        // Note: previous segment should never be an init segment.
        Segment previousSegment = initializedFormat.currentSegment;
        if (previousSegment != null && !isInitSegment && !previousSegment.discard && !discard
            && !consumed && sequenceNumber != previousSegment.sequenceNumber + 1) {
            // Bail out as the segment is not in order when it is expected to be
            throw new MediaSegmentMismatchError(mediaHeader.getFormatId(), previousSegment.sequenceNumber + 1, sequenceNumber);
        }

        if (initializedFormat.initSegment != null && isInitSegment) {
            Log.d(TAG, "Init segment %s already seen for format %s, marking segment as consumed",
                    sequenceNumber, initializedFormat.formatId);
            consumed = true;
        }

        TimeRange timeRange = mediaHeader.hasTimeRange() ? mediaHeader.getTimeRange() : null;
        int startMs = mediaHeader.hasStartMs() ? mediaHeader.getStartMs()
                : timeRange != null && timeRange.hasStartTicks() && timeRange.hasTimescale()
                    ? Utils.ticksToMs(timeRange.getStartTicks(), timeRange.getTimescale())
                : 0;

        // Calculate duration of this segment
        // For videos, either duration_ms or time_range should be present
        // For live streams, calculate segment duration based on live metadata target segment duration
        int actualDurationMs = mediaHeader.hasDurationMs() ? mediaHeader.getDurationMs()
                : timeRange != null && timeRange.hasDurationTicks() && timeRange.hasTimescale()
                    ? Utils.ticksToMs(timeRange.getDurationTicks(), timeRange.getTimescale())
                : NO_VALUE;

        int estimatedDurationMs = NO_VALUE;
        if (isLive()) {
            // Underestimate the duration of the segment slightly as
            // the real duration may be slightly shorter than the target duration.
            estimatedDurationMs = (getLiveSegmentTargetDurationSec() * 1_000) - getLiveSegmentTargetDurationToleranceMs();
        } else if (isInitSegment) {
            estimatedDurationMs = 0;
        }

        int durationMs = actualDurationMs != NO_VALUE ? actualDurationMs : estimatedDurationMs;

        // Guard: Bail out if we cannot determine the duration, which we need to progress.
        if (durationMs == NO_VALUE) {
            throw new SabrStreamError(
                    String.format("Cannot determine duration of segment %s (media_header=%s)", sequenceNumber, mediaHeader));
        }

        long estimatedContentLength = NO_VALUE;
        if (isLive() && !mediaHeader.hasContentLength() && mediaHeader.hasBitrateBps()) {
            estimatedContentLength = (long) Math.ceil(mediaHeader.getBitrateBps() * ((double) durationMs / 1_000));
        }

        Segment segment = new Segment(
                mediaHeader.getFormatId(),
                isInitSegment,
                durationMs,
                mediaHeader.hasStartDataRange() ? mediaHeader.getStartDataRange() : NO_VALUE,
                sequenceNumber,
                mediaHeader.hasContentLength() ? mediaHeader.getContentLength() : estimatedContentLength,
                estimatedContentLength != NO_VALUE,
                startMs,
                initializedFormat,
                actualDurationMs == 0 || actualDurationMs == NO_VALUE,
                discard || consumed,
                consumed,
                mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE
        );

        partialSegments.put(mediaHeader.getHeaderId(), segment);

        ProcessMediaHeaderResult result = new ProcessMediaHeaderResult();

        if (!segment.discard) {
            result.sabrPart = new MediaSegmentInitSabrPart(
                    segment.initializedFormat.formatSelector,
                    segment.formatId,
                    clientAbrState.hasPlayerTimeMs() ? clientAbrState.getPlayerTimeMs() : NO_VALUE,
                    segment.sequenceNumber,
                    segment.initializedFormat.totalSegments,
                    segment.durationMs,
                    segment.durationEstimated,
                    segment.startDataRange,
                    segment.startMs,
                    segment.isInitSegment,
                    segment.contentLength,
                    segment.contentLengthEstimated
            );
        }

        Log.d(TAG, "Initialized Media Header %s for sequence %s. Segment: %s",
                mediaHeader.getHeaderId(), sequenceNumber, segment);

        return result;
    }

    public ProcessMediaResult processMedia(int headerId, int contentLength, ByteArrayInputStream data) {
        Segment segment = partialSegments.get(headerId);
        if (segment == null) {
            Log.d(TAG, "Header ID %s not found", headerId);
            throw new SabrStreamError(String.format("Header ID %s not found in partial segments", headerId));
        }

        int segmentStartBytes = segment.receivedDataLength;
        segment.receivedDataLength += contentLength;

        ProcessMediaResult result = new ProcessMediaResult();

        if (!segment.discard) {
            result.sabrPart = new MediaSegmentDataSabrPart(
                    segment.initializedFormat.formatSelector,
                    segment.formatId,
                    segment.sequenceNumber,
                    segment.isInitSegment,
                    segment.initializedFormat.totalSegments,
                    Utils.readAllBytes(data),
                    contentLength,
                    segmentStartBytes
            );
        }

        return result;
    }

    public ProcessMediaEndResult processMediaEnd(int headerId) {
        Segment segment = partialSegments.remove(headerId);
        if (segment == null) {
            Log.d(TAG, "Header ID %s not found", headerId);
            throw new SabrStreamError(String.format("Header ID %s not found in partial segments", headerId));
        }

        Log.d(TAG, "MediaEnd for %s (sequence %s, data length = %s)",
                segment.formatId, segment.sequenceNumber, segment.receivedDataLength);

        if (segment.contentLength != -1 && segment.receivedDataLength != segment.contentLength) {
            if (segment.contentLengthEstimated) {
                Log.d(TAG, "Content length for %s (sequence %s) was estimated, " +
                        "estimated %s bytes, got %s bytes",
                        segment.formatId, segment.sequenceNumber, segment.contentLength, segment.receivedDataLength);
            } else {
                throw new SabrStreamError(String.format("Content length mismatch for %s (sequence %s): " +
                        "expected %s bytes, got %s bytes",
                        segment.formatId, segment.sequenceNumber, segment.contentLength, segment.receivedDataLength));
            }
        }

        ProcessMediaEndResult result = new ProcessMediaEndResult();

        // Only count received segments as new segments if they are not consumed.
        // Discarded segments that are not consumed are considered new segments.
        if (!segment.consumed) {
            result.isNewSegment = true;
        }

        // Return the segment here instead of during MEDIA part(s) because:
        // 1. We can validate that we received the correct data length
        // 2. In the case of a retry during segment media, the partial data is not sent to the consumer
        if (!segment.discard) {
            // This needs to be yielded AFTER we have processed the segment
            // So the consumer can see the updated consumed ranges and use them for e.g. syncing between concurrent streams
            result.sabrPart = new MediaSegmentEndSabrPart(
                    segment.initializedFormat.formatSelector,
                    segment.formatId,
                    segment.sequenceNumber,
                    segment.isInitSegment,
                    segment.initializedFormat.totalSegments
            );
        } else {
            Log.d(TAG, "Discarding media for %s", segment.initializedFormat.formatId);
        }

        if (segment.isInitSegment) {
            segment.initializedFormat.initSegment = segment;
            // Do not create a consumed range for init segments
            return result;
        }

        if (segment.initializedFormat.currentSegment != null && isLive()) {
            Segment previousSegment = segment.initializedFormat.currentSegment;
            Log.d(TAG, "Previous segment %s for format %s " +
                    "estimated duration difference from this segment (%s): %sms",
                    previousSegment.sequenceNumber, segment.formatId, segment.sequenceNumber,
                    segment.startMs - (previousSegment.startMs + previousSegment.durationMs));
        }

        segment.initializedFormat.currentSegment = segment;

        if (segment.consumed) {
            // Segment is already consumed, do not create a new consumed range. It was probably discarded.
            // This can be expected to happen in the case of video-only, where we discard the audio track (and mark it as entirely buffered)
            // We still want to create/update consumed range for discarded media IF it is not already consumed
            Log.d(TAG, "%s} segment %s already consumed, not creating or updating consumed range (discard=%s)",
                    segment.formatId, segment.sequenceNumber, segment.discard);
            return result;
        }

        // Try to find a consumed range for this segment in sequence
        ConsumedRange consumedRange =
                Helpers.findFirst(segment.initializedFormat.consumedRanges, cr -> cr.endSequenceNumber == segment.sequenceNumber - 1);

        if (consumedRange == null) {
            // Create a new consumed range starting from this segment
            segment.initializedFormat.consumedRanges.add(new ConsumedRange(
                    segment.startMs,
                    segment.durationMs,
                    segment.sequenceNumber,
                    segment.sequenceNumber
            ));
            Log.d(TAG, "Created new consumed range for %s %s",
                    segment.initializedFormat.formatId, segment.initializedFormat.consumedRanges.get(segment.initializedFormat.consumedRanges.size() - 1));
            return result;
        }

        // Update the existing consumed range to include this segment
        consumedRange.endSequenceNumber = segment.sequenceNumber;
        consumedRange.durationMs = (segment.startMs - consumedRange.startTimeMs) + segment.durationMs;

        // TODO: Conduct a seek on consumed ranges

        return result;
    }

    public ProcessStreamProtectionStatusResult processStreamProtectionStatus(StreamProtectionStatus streamProtectionStatus) {
        this.streamProtectionStatus = streamProtectionStatus.hasStatus() ? streamProtectionStatus.getStatus() : null;
        Status status = streamProtectionStatus.getStatus();
        String poToken = this.poToken;
        PoTokenStatus resultStatus = null;

        if (status == StreamProtectionStatus.Status.OK) {
            resultStatus = poToken != null ? PoTokenStatusSabrPart.PoTokenStatus.OK : PoTokenStatusSabrPart.PoTokenStatus.NOT_REQUIRED;
        } else if (status == StreamProtectionStatus.Status.ATTESTATION_PENDING) {
            resultStatus = poToken != null ? PoTokenStatusSabrPart.PoTokenStatus.PENDING : PoTokenStatusSabrPart.PoTokenStatus.PENDING_MISSING;
        } else if (status == StreamProtectionStatus.Status.ATTESTATION_REQUIRED) {
            resultStatus = poToken != null ? PoTokenStatusSabrPart.PoTokenStatus.INVALID : PoTokenStatusSabrPart.PoTokenStatus.MISSING;
        } else {
            Log.w(TAG, "Received an unknown StreamProtectionStatus: %s", streamProtectionStatus);
        }

        ProcessStreamProtectionStatusResult result = new ProcessStreamProtectionStatusResult();

        if (resultStatus != null) {
            result.sabrPart = new PoTokenStatusSabrPart(resultStatus);
        }

        return result;
    }

    public boolean isLive() {
        return false;
    }

    @NonNull
    public ClientAbrState getClientAbrState() {
        return clientAbrState;
    }

    public void setClientAbrState(@NonNull ClientAbrState state) {
        clientAbrState = state;
    }

    public int getLiveSegmentTargetDurationToleranceMs() {
        return liveSegmentTargetDurationToleranceMs;
    }

    public int getLiveSegmentTargetDurationSec() {
        return liveSegmentTargetDurationSec;
    }
}
