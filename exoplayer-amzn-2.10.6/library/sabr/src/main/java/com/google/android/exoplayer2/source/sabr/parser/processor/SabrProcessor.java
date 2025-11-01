package com.google.android.exoplayer2.source.sabr.parser.processor;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.MediaSegmentMismatchError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrStreamError;
import com.google.android.exoplayer2.source.sabr.parser.models.AudioSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.CaptionSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.ConsumedRange;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.InitializedFormat;
import com.google.android.exoplayer2.source.sabr.parser.models.Segment;
import com.google.android.exoplayer2.source.sabr.parser.models.VideoSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.FormatInitializedSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSeekSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientAbrState;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientInfo;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextSendingPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextUpdate;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrSeek;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus.Status;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.TimeRange;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SabrProcessor {
    private static final String TAG = SabrProcessor.class.getSimpleName();
    public static final int NO_VALUE = -1;
    private final String videoPlaybackUstreamerConfig;
    private final ClientInfo clientInfo;
    private VideoSelector videoFormatSelector;
    private AudioSelector audioFormatSelector;
    private CaptionSelector captionFormatSelector;
    private final int liveSegmentTargetDurationToleranceMs;
    private final int liveSegmentTargetDurationSec;
    private final long startTimeMs;
    private final String poToken;
    private final boolean postLive;
    private final String videoId;
    private ClientAbrState clientAbrState;
    private final Map<Long, Segment> partialSegments;
    private final Map<String, InitializedFormat> initializedFormats;
    private Status streamProtectionStatus;
    private boolean isLive;
    private LiveMetadata liveMetadata;
    private long totalDurationMs;
    private NextRequestPolicy nextRequestPolicy;
    private final Map<Integer, SabrContextUpdate> sabrContextUpdates;
    private final Set<Integer> sabrContextsToSend;
    private List<FormatId> selectedAudioFormatIds;
    private List<FormatId> selectedVideoFormatIds;
    private List<FormatId> selectedCaptionFormatIds;

    public SabrProcessor(
            @NonNull String videoPlaybackUstreamerConfig,
            @NonNull ClientInfo clientInfo,
            AudioSelector audioSelection,
            VideoSelector videoSelection,
            CaptionSelector captionSelection,
            int liveSegmentTargetDurationSec,
            int liveSegmentTargetDurationToleranceMs,
            long startTimeMs,
            String poToken,
            boolean postLive,
            String videoId
    ) {
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.poToken = poToken;
        this.clientInfo = clientInfo;
        this.liveSegmentTargetDurationSec = liveSegmentTargetDurationSec != NO_VALUE ? liveSegmentTargetDurationSec : 5;
        this.liveSegmentTargetDurationToleranceMs = liveSegmentTargetDurationToleranceMs != NO_VALUE ? liveSegmentTargetDurationToleranceMs : 100;
        if (this.liveSegmentTargetDurationToleranceMs >= (this.liveSegmentTargetDurationSec * 1_000) / 2) {
            throw new IllegalArgumentException("liveSegmentTargetDurationToleranceMs must be less than half of liveSegmentTargetDurationSec in milliseconds");
        }
        this.startTimeMs = startTimeMs != NO_VALUE ? startTimeMs : 0;
        if (this.startTimeMs < 0) {
            throw new IllegalArgumentException("start_time_ms must be greater than or equal to 0");
        }

        this.postLive = postLive;
        isLive = false;
        this.videoId = videoId;

        audioFormatSelector = audioSelection;
        videoFormatSelector = videoSelection;
        captionFormatSelector = captionSelection;

        // IMPORTANT: initialized formats is assumed to contain only ACTIVE formats
        initializedFormats = new HashMap<>();

        partialSegments = new HashMap<>();
        totalDurationMs = NO_VALUE;
        sabrContextsToSend = new HashSet<>();
        sabrContextUpdates = new HashMap<>();
        initializeClientAbrState();
    }

    private void initializeClientAbrState() {
        if (videoFormatSelector == null) {
            videoFormatSelector = new VideoSelector("video_ignore", true);
        }

        if (audioFormatSelector == null) {
            audioFormatSelector = new AudioSelector("audio_ignore", true);
        }

        if (captionFormatSelector == null) {
            captionFormatSelector = new CaptionSelector("caption_ignore", true);
        }

        int enabledTrackTypesBitfield = 0;  // Audio+Video

        if (videoFormatSelector.discardMedia) {
            enabledTrackTypesBitfield = 1; // Audio only
        }

        if (!captionFormatSelector.discardMedia) {
            // SABR does not support caption-only or audio+captions only - can only get audio+video with captions
            // If audio or video is not selected, the tracks will be initialized but marked as buffered.
            enabledTrackTypesBitfield = 7;
        }

        selectedAudioFormatIds = audioFormatSelector.formatIds;
        selectedVideoFormatIds = videoFormatSelector.formatIds;
        selectedCaptionFormatIds = captionFormatSelector.formatIds;

        Log.d(TAG, "Starting playback at: %sms", startTimeMs);
        clientAbrState = ClientAbrState.newBuilder()
                .setPlayerTimeMs(startTimeMs)
                .setEnabledTrackTypesBitfield(enabledTrackTypesBitfield)
                .setDrcEnabled(true) // Required to stream DRC formats
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
        if (partialSegments.containsKey(Utils.toLong(mediaHeader.getHeaderId()))) {
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

        partialSegments.put(Utils.toLong(mediaHeader.getHeaderId()), segment);

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
                Utils.toLong(mediaHeader.getHeaderId()), sequenceNumber, segment);

        return result;
    }

    public ProcessMediaResult processMedia(long headerId, int contentLength, ExtractorInput data) {
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
                    segment.startMs,
                    data,
                    contentLength,
                    segmentStartBytes
            );
        }

        return result;
    }

    public ProcessMediaEndResult processMediaEnd(long headerId) {
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

    public ProcessFormatInitializationMetadataResult processFormatInitializationMetadata(FormatInitializationMetadata formatInitMetadata) {
        ProcessFormatInitializationMetadataResult result = new ProcessFormatInitializationMetadataResult();

        if (formatInitMetadata.hasFormatId() && initializedFormats.containsKey(formatInitMetadata.getFormatId().toString())) {
            Log.d(TAG, "Format %s already initialized", formatInitMetadata.getFormatId());
            return result;
        }

        if (formatInitMetadata.hasVideoId() && videoId != null && !formatInitMetadata.getVideoId().equals(videoId)) {
            throw new SabrStreamError(String.format("Received unexpected Format Initialization Metadata for video" +
                    "  %s (expecting %s)", formatInitMetadata.getVideoId(), videoId));
        }

        FormatSelector formatSelector = matchFormatSelector(formatInitMetadata);

        if (formatSelector == null) {
            // Should not happen. If we ignored the format the server may refuse to send us any more data
            throw new SabrStreamError(String.format("Received format %s but it does not match any format selector", formatInitMetadata.getFormatId()));
        }

        // Guard: Check if the format selector is already in use by another initialized format.
        // This can happen when the server changes the format to use (e.g. changing quality).
        //
        // Changing a format will require adding some logic to handle inactive formats.
        // Given we only provide one FormatId currently, and this should not occur in this case,
        // we will mark this as not currently supported and bail.
        for (InitializedFormat izf : initializedFormats.values()) {
            if (izf.formatSelector == formatSelector) {
                throw new SabrStreamError("Server changed format. Changing formats is not currently supported");
            }
        }

        int durationMs = Utils.ticksToMs(
                formatInitMetadata.hasDurationTicks() ? formatInitMetadata.getDurationTicks() : -1,
                formatInitMetadata.hasDurationTimescale() ? formatInitMetadata.getDurationTimescale() : -1
        );

        int totalSegments = formatInitMetadata.hasTotalSegments() ? formatInitMetadata.getTotalSegments() : -1;

        if (totalSegments == -1 && liveMetadata != null && liveMetadata.hasHeadSequenceNumber()) {
            totalSegments = liveMetadata.getHeadSequenceNumber();
        }

        InitializedFormat initializedFormat = new InitializedFormat(
                formatInitMetadata.hasFormatId() ? formatInitMetadata.getFormatId() : null,
                durationMs,
                formatInitMetadata.hasEndTimeMs() ? formatInitMetadata.getEndTimeMs() : -1,
                formatInitMetadata.hasMimeType() ? formatInitMetadata.getMimeType() : null,
                formatInitMetadata.hasVideoId() ? formatInitMetadata.getVideoId() : null,
                formatSelector,
                totalSegments,
                formatSelector.isDiscardMedia()
        );

        totalDurationMs = Math.max(
                totalDurationMs != -1 ? totalDurationMs : 0,
                Math.max(formatInitMetadata.hasEndTimeMs() ? formatInitMetadata.getEndTimeMs() : 0, durationMs != -1 ? durationMs : 0)
        );

        if (initializedFormat.discard) {
            // Mark the entire format as buffered into oblivion if we plan to discard all media.
            // This stops the server sending us any more data for this format.
            // Note: Using JS_MAX_SAFE_INTEGER but could use any maximum value as long as the server accepts it.
            initializedFormat.consumedRanges.clear();
            initializedFormat.consumedRanges.add(new ConsumedRange(
                    0,
                    ((long) Math.pow(2, 53)) - 1,
                    0,
                    ((long) Math.pow(2, 53)) - 1
            ));
        }

        if (formatInitMetadata.hasFormatId()) {
            initializedFormats.put(formatInitMetadata.getFormatId().toString(), initializedFormat);
            Log.d(TAG, "Initialized Format: %s", initializedFormat);
        }

        if (!initializedFormat.discard) {
            result.sabrPart = new FormatInitializedSabrPart(
                    formatInitMetadata.hasFormatId() ? formatInitMetadata.getFormatId() : null,
                    formatSelector
            );
        }

        return result;
    }

    public void processNextRequestPolicy(NextRequestPolicy nextRequestPolicy) {
        this.nextRequestPolicy = nextRequestPolicy;
        Log.d(TAG, "Registered new NextRequestPolicy: %s", nextRequestPolicy);
    }

    public ProcessLiveMetadataResult processLiveMetadata(LiveMetadata liveMetadata) {
        this.liveMetadata = liveMetadata;

        if (liveMetadata.hasHeadSequenceTimeMs()) {
            totalDurationMs = liveMetadata.getHeadSequenceTimeMs();
        }

        // If we have a head sequence number, we need to update the total sequences for each initialized format
        // For livestreams, it is not available in the format initialization metadata
        if (liveMetadata.hasHeadSequenceNumber()) {
            for (InitializedFormat izf : initializedFormats.values()) {
                izf.totalSegments = liveMetadata.getHeadSequenceNumber();
            }
        }

        ProcessLiveMetadataResult result = new ProcessLiveMetadataResult();

        // If the current player time is less than the min dvr time, simulate a server seek to the min dvr time.
        // The server SHOULD send us a SABR_SEEK part in this case, but it does not always happen (e.g. ANDROID_VR)
        // The server SHOULD NOT send us segments before the min dvr time, so we should assume that the player time is correct.
        int minSeekableTimeMs = Utils.ticksToMs(liveMetadata.hasMinSeekableTimeTicks() ? liveMetadata.getMinSeekableTimeTicks() : -1,
                liveMetadata.hasMinSeekableTimescale() ? liveMetadata.getMinSeekableTimescale() : -1);
        if (minSeekableTimeMs != -1 && clientAbrState.hasPlayerTimeMs() && clientAbrState.getPlayerTimeMs() < minSeekableTimeMs) {
            Log.d(TAG, "Player time %s is less than min seekable time %s, simulating server seek",
                    clientAbrState.getPlayerTimeMs(), minSeekableTimeMs);
            clientAbrState = clientAbrState.toBuilder().setPlayerTimeMs(minSeekableTimeMs).build();
            for (InitializedFormat izf : initializedFormats.values()) {
                izf.currentSegment = null; // Clear the current segment as we expect segments to no longer be in order.
                result.seekSabrParts.add(
                        new MediaSeekSabrPart(
                                MediaSeekSabrPart.Reason.SERVER_SEEK,
                                izf.formatId,
                                izf.formatSelector
                        )
                );
            }
        }

        return result;
    }

    public ProcessSabrSeekResult processSabrSeek(SabrSeek sabrSeek) {
        int seekTo = Utils.ticksToMs(sabrSeek.hasSeekTimeTicks() ? sabrSeek.getSeekTimeTicks() : -1, sabrSeek.hasTimescale() ? sabrSeek.getTimescale() : -1);
        if (seekTo == -1) {
            throw new SabrStreamError(String.format("Server sent a SabrSeek part that is missing required seek data: %s", sabrSeek));
        }
        Log.d(TAG, "Seeking to %sms", seekTo);
        clientAbrState = clientAbrState.toBuilder().setPlayerTimeMs(seekTo).build();

        ProcessSabrSeekResult result = new ProcessSabrSeekResult();

        // Clear latest segment of each initialized format
        // as we expect them to no longer be in order.
        for (InitializedFormat initializedFormat : initializedFormats.values()) {
            initializedFormat.currentSegment = null;
            result.seekSabrParts.add(
                    new MediaSeekSabrPart(
                            MediaSeekSabrPart.Reason.SERVER_SEEK,
                            initializedFormat.formatId,
                            initializedFormat.formatSelector
                    )
            );
        }
        return result;
    }

    public void processSabrContextUpdate(SabrContextUpdate sabrCtxUpdate) {
        if (!sabrCtxUpdate.hasType() || !sabrCtxUpdate.hasValue() || !sabrCtxUpdate.hasWritePolicy()) {
            Log.w(TAG, "Received an invalid SabrContextUpdate, ignoring");
            return;
        }

        if (sabrCtxUpdate.getWritePolicy() == SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_KEEP_EXISTING
                && sabrContextUpdates.containsKey(sabrCtxUpdate.getType())) {
            Log.d(TAG, "Received a SABR Context Update with write_policy=KEEP_EXISTING" +
                    " matching an existing SABR Context Update. Ignoring update");
            return;
        }

        Log.w(TAG, "Received a SABR Context Update. YouTube is likely trying to force ads on the client. " +
                "This may cause issues with playback.");

        sabrContextUpdates.put(sabrCtxUpdate.getType(), sabrCtxUpdate);
        if (sabrCtxUpdate.hasSendByDefault()) {
            sabrContextsToSend.add(sabrCtxUpdate.getType());
        }
        Log.d(TAG, "Registered SabrContextUpdate %s", sabrCtxUpdate);
    }

    public void processSabrContextSendingPolicy(SabrContextSendingPolicy sabrCtxSendingPolicy) {
        for (int startType : sabrCtxSendingPolicy.getStartPolicyList()) {
            if (!sabrContextsToSend.contains(startType)) {
                Log.d(TAG, "Server requested to enable SABR Context Update for type %s", startType);
                sabrContextsToSend.add(startType);
            }
        }

        for (int stopType : sabrCtxSendingPolicy.getStopPolicyList()) {
            if (!sabrContextsToSend.contains(stopType)) {
                Log.d(TAG, "Server requested to disable SABR Context Update for type %s", stopType);
                sabrContextsToSend.remove(stopType);
            }
        }

        for (int discardType : sabrCtxSendingPolicy.getDiscardPolicyList()) {
            if (!sabrContextsToSend.contains(discardType)) {
                Log.d(TAG, "Server requested to discard SABR Context Update for type %s", discardType);
                sabrContextUpdates.remove(discardType);
            }
        }
    }

    public boolean isLive() {
        return liveMetadata != null || isLive;
    }

    public void setLive(boolean isLive) {
        this.isLive = isLive;
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

    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {
        for (FormatSelector formatSelector : new FormatSelector[]{videoFormatSelector, audioFormatSelector, captionFormatSelector}) {
            if (formatSelector == null) {
                continue;
            }

            if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {
                return formatSelector;
            }
        }

        return null;
    }
}
