package com.google.android.exoplayer2.source.sabr.parser.core;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.MediaSegmentMismatchError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrStreamError;
import com.google.android.exoplayer2.source.sabr.parser.misc.Utils;
import com.google.android.exoplayer2.source.sabr.parser.models.AudioSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.CaptionSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.VideoSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.FormatInitializedSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSeekSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.RefreshPlayerResponseSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.source.sabr.parser.results.ProcessFormatInitializationMetadataResult;
import com.google.android.exoplayer2.source.sabr.parser.results.ProcessMediaEndResult;
import com.google.android.exoplayer2.source.sabr.parser.results.ProcessMediaHeaderResult;
import com.google.android.exoplayer2.source.sabr.parser.results.ProcessMediaResult;
import com.google.android.exoplayer2.source.sabr.parser.results.ProcessStreamProtectionStatusResult;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPDecoder;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPart;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ReloadPlayerResponse;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextSendingPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextUpdate;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrError;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrRedirect;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrSeek;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryString;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SabrStream {
    private static final String TAG = SabrStream.class.getSimpleName();
    private final int[] KNOWN_PARTS = {
            UMPPartId.MEDIA_HEADER,
            UMPPartId.MEDIA,
            UMPPartId.MEDIA_END,
            UMPPartId.STREAM_PROTECTION_STATUS,
            UMPPartId.SABR_REDIRECT,
            UMPPartId.FORMAT_INITIALIZATION_METADATA,
            UMPPartId.NEXT_REQUEST_POLICY,
            //UMPPartId.LIVE_METADATA,
            //UMPPartId.SABR_SEEK,
            UMPPartId.SABR_ERROR,
            UMPPartId.SABR_CONTEXT_UPDATE,
            UMPPartId.SABR_CONTEXT_SENDING_POLICY,
            UMPPartId.RELOAD_PLAYER_RESPONSE,
            //UMPPartId.SNACKBAR_MESSAGE // ???
    };
    private final int[] IGNORED_PARTS = {
            UMPPartId.REQUEST_IDENTIFIER,
            UMPPartId.REQUEST_CANCELLATION_POLICY,
            UMPPartId.PLAYBACK_START_POLICY,
            UMPPartId.ALLOWED_CACHED_FORMATS,
            UMPPartId.PAUSE_BW_SAMPLING_HINT,
            UMPPartId.START_BW_SAMPLING_HINT,
            UMPPartId.REQUEST_PIPELINING,
            UMPPartId.SELECTABLE_FORMATS,
            UMPPartId.PREWARM_CONNECTION,
    };
    private final UMPDecoder decoder;
    private final SabrProcessor processor;
    private final NoSegmentsTracker noNewSegmentsTracker;
    private final Set<Integer> unknownPartTypes;
    private int sqMismatchForwardCount;
    private int sqMismatchBacktrackCount;
    private boolean receivedNewSegments;
    private String url;
    private List<? extends  SabrPart> multiResult = null;

    private static class NoSegmentsTracker {
        public int consecutiveRequests = 0;
        public float timestampStarted = -1;
        public int liveHeadSegmentStarted = -1;

        public void reset() {
             consecutiveRequests = 0;
             timestampStarted = -1;
             liveHeadSegmentStarted = -1;
        }

        public void increment(int liveHeadSegment) {
            if (consecutiveRequests == 0) {
                timestampStarted = System.currentTimeMillis() * 1_000;
                liveHeadSegmentStarted = liveHeadSegment;
            }
            consecutiveRequests += 1;
        }
    }

    public SabrStream(
            @NonNull String serverAbrStreamingUrl,
            @NonNull String videoPlaybackUstreamerConfig,
            @NonNull ClientInfo clientInfo,
            int liveSegmentTargetDurationSec,
            int liveSegmentTargetDurationToleranceMs,
            long startTimeMs,
            String poToken,
            boolean postLive,
            String videoId,
            long durationMs) {
        decoder = new UMPDecoder();
        processor = new SabrProcessor(
                videoPlaybackUstreamerConfig,
                clientInfo,
                liveSegmentTargetDurationSec,
                liveSegmentTargetDurationToleranceMs,
                startTimeMs,
                poToken,
                postLive,
                videoId,
                durationMs
        );
        url = serverAbrStreamingUrl;

        // Whether we got any new (not consumed) segments in the request
        noNewSegmentsTracker = new NoSegmentsTracker();
        unknownPartTypes = new HashSet<>();

        sqMismatchBacktrackCount = 0;
        sqMismatchForwardCount = 0;
    }


    public SabrPart parse(@NonNull ExtractorInput extractorInput) {
        SabrPart result = null;

        while (result == null && (multiResult == null || multiResult.isEmpty())) {
            UMPPart part = nextKnownUMPPart(extractorInput);

            if (part == null) {
                break;
            }

            result = parsePart(part);

            if (result == null) {
                multiResult = parseMultiPart(part);
            }
        }

        return result != null ? result : multiResult != null && !multiResult.isEmpty() ? multiResult.remove(0) : null;
    }

    public void reset() {
        noNewSegmentsTracker.reset();
    }

    public void reset(int iTag) {
        processor.reset(iTag);
    }

    public FormatSelector getFormatSelector() {
        return processor.getFormatSelector();
    }

    public void setFormatSelector(FormatSelector formatSelector) {
        processor.setFormatSelector(formatSelector);
    }

    public long getSegmentStartTimeMs(int iTag) {
        return processor.getSegmentStartTimeMs(iTag);
    }

    public long getSegmentDurationMs(int iTag) {
        return processor.getSegmentDurationMs(iTag);
    }

    public MediaHeader getInitializedFormat(int iTag) {
        return processor.getInitializedFormats().get(iTag);
    }

    public StreamerContext createStreamerContext() {
        return processor.createStreamerContext();
    }

    private SabrPart parsePart(UMPPart part) {
        switch (part.partId) {
            case UMPPartId.MEDIA_HEADER:
                return processMediaHeader(part);
            case UMPPartId.MEDIA:
                return processMedia(part);
            case UMPPartId.MEDIA_END:
                return processMediaEnd(part);
            case UMPPartId.STREAM_PROTECTION_STATUS:
                return processStreamProtectionStatus(part);
            case UMPPartId.SABR_REDIRECT:
                processSabrRedirect(part);
                return null;
            case UMPPartId.FORMAT_INITIALIZATION_METADATA:
                return processFormatInitializationMetadata(part);
            case UMPPartId.NEXT_REQUEST_POLICY:
                processNextRequestPolicy(part);
                return null;
            case UMPPartId.SABR_ERROR:
                processSabrError(part);
                return null;
            case UMPPartId.SABR_CONTEXT_UPDATE:
                processSabrContextUpdate(part);
                return null;
            case UMPPartId.SABR_CONTEXT_SENDING_POLICY:
                processSabrContextSendingPolicy(part);
                return null;
            case UMPPartId.RELOAD_PLAYER_RESPONSE:
                return processReloadPlayerResponse(part);
        }

        if (!contains(IGNORED_PARTS, part.partId)) {
            unknownPartTypes.add(part.partId);
        }

        Log.d(TAG, "Unhandled part type %s", part.partId);

        return null;
    }

    private List<? extends SabrPart> parseMultiPart(UMPPart part) {
        switch (part.partId) {
            case UMPPartId.LIVE_METADATA:
                return processLiveMetadata(part);
            case UMPPartId.SABR_SEEK:
                return processSabrSeek(part);
        }

        return null;
    }

    private MediaSegmentInitSabrPart processMediaHeader(UMPPart part) {
        MediaHeader mediaHeader;

        try {
            mediaHeader = MediaHeader.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            ProcessMediaHeaderResult result = processor.processMediaHeader(mediaHeader);

            return result.sabrPart;
        } catch (MediaSegmentMismatchError e) {
            // For livestreams, the server may not know the exact segment for a given player time.
            // For segments near stream head, it estimates using segment duration, which can cause off-by-one segment mismatches.
            // If a segment is much longer or shorter than expected, the server may return a segment ahead or behind.
            // In such cases, retry with an adjusted player time to resync.
            if (processor.isLive() && e.receivedSequenceNumber == e.expectedSequenceNumber - 1) {
                // The segment before the previous segment was possibly longer than expected.
                // Move the player time forward to try to adjust for this.;
                processor.setPlayerTimeMs(processor.getPlayerTimeMs() + processor.getLiveSegmentTargetDurationToleranceMs());
                sqMismatchForwardCount += 1;
                return null;
            } else if (processor.isLive() && e.receivedSequenceNumber == e.expectedSequenceNumber + 2) {
                // The previous segment was possibly shorter than expected
                // Move the player time backwards to try to adjust for this.
                processor.setPlayerTimeMs(Math.max(0, processor.getPlayerTimeMs() - processor.getLiveSegmentTargetDurationToleranceMs()));
                sqMismatchBacktrackCount += 1;
                return null;
            }

            throw e;
        }
    }

    private MediaSegmentDataSabrPart processMedia(UMPPart part) {
        try {
            long position = part.data.getPosition();
            long headerId = decoder.readVarInt(part.data);
            long offset = part.data.getPosition() - position;
            int contentLength = part.size - (int) offset;

            ProcessMediaResult result = processor.processMedia(headerId, contentLength, part.data);

            return result.sabrPart;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private MediaSegmentEndSabrPart processMediaEnd(UMPPart part) {
        try {
            long headerId = decoder.readVarInt(part.data);
            Log.d(TAG, "Header ID: %s", headerId);

            ProcessMediaEndResult result = processor.processMediaEnd(headerId);

            if (result.isNewSegment) {
                receivedNewSegments = true;
            }

            return result.sabrPart;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private PoTokenStatusSabrPart processStreamProtectionStatus(UMPPart part) {
        StreamProtectionStatus sps;

        try {
            sps = StreamProtectionStatus.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process StreamProtectionStatus: %s", sps);
        ProcessStreamProtectionStatusResult result = processor.processStreamProtectionStatus(sps);

        return result.sabrPart;
    }

    private void processSabrRedirect(UMPPart part) {
        SabrRedirect sabrRedirect;

        try {
            sabrRedirect = SabrRedirect.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process SabrRedirect: %s", sabrRedirect);

        if (!sabrRedirect.hasRedirectUrl()) {
            Log.d(TAG, "Server requested to redirect to an invalid URL");
            return;
        }

        setUrl(sabrRedirect.getRedirectUrl());
    }

    private FormatInitializedSabrPart processFormatInitializationMetadata(UMPPart part) {
        FormatInitializationMetadata fmtInitMetadata;

        try {
            fmtInitMetadata = FormatInitializationMetadata.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process FormatInitializationMetadata: %s", fmtInitMetadata);
        ProcessFormatInitializationMetadataResult result = processor.processFormatInitializationMetadata(fmtInitMetadata);

        return result.sabrPart;
    }

    private void processNextRequestPolicy(UMPPart part) {
        NextRequestPolicy nextRequestPolicy;

        try {
            nextRequestPolicy = NextRequestPolicy.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process NextRequestPolicy: %s", nextRequestPolicy);
        processor.processNextRequestPolicy(nextRequestPolicy);
    }

    private void processSabrError(UMPPart part) {
        SabrError sabrError;

        try {
            sabrError = SabrError.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process SabrError: %s", sabrError);
        throw new SabrStreamError(String.format("SABR Protocol Error: %s", sabrError));
    }

    private void processSabrContextUpdate(UMPPart part) {
        SabrContextUpdate sabrCtxUpdate;

        try {
            sabrCtxUpdate = SabrContextUpdate.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process SabrContextUpdate: %s", sabrCtxUpdate);
        processor.processSabrContextUpdate(sabrCtxUpdate);
    }

    private void processSabrContextSendingPolicy(UMPPart part) {
        SabrContextSendingPolicy sabrCtxSendingPolicy;

        try {
            sabrCtxSendingPolicy = SabrContextSendingPolicy.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process SabrContextSendingPolicy: %s", sabrCtxSendingPolicy);
        processor.processSabrContextSendingPolicy(sabrCtxSendingPolicy);
    }

    private RefreshPlayerResponseSabrPart processReloadPlayerResponse(UMPPart part) {
        ReloadPlayerResponse reloadPlayerResponse;

        try {
            reloadPlayerResponse = ReloadPlayerResponse.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process ReloadPlayerResponse: %s", reloadPlayerResponse);
        return new RefreshPlayerResponseSabrPart(
                RefreshPlayerResponseSabrPart.Reason.SABR_RELOAD_PLAYER_RESPONSE,
                reloadPlayerResponse.hasReloadPlaybackParams() && reloadPlayerResponse.getReloadPlaybackParams().hasToken()
                        ? reloadPlayerResponse.getReloadPlaybackParams().getToken() : null
        );
    }

    private List<MediaSeekSabrPart> processLiveMetadata(UMPPart part) {
        LiveMetadata liveMetadata;

        try {
            liveMetadata = LiveMetadata.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process LiveMetadata: %s", liveMetadata);
        return processor.processLiveMetadata(liveMetadata).seekSabrParts;
    }

    private List<MediaSeekSabrPart> processSabrSeek(UMPPart part) {
        SabrSeek sabrSeek;

        try {
            sabrSeek = SabrSeek.parseFrom(part.toStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Log.d(TAG, "Process SabrSeek: %s", sabrSeek);
        return processor.processSabrSeek(sabrSeek).seekSabrParts;
    }

    private static boolean contains(int[] array, int value) {
        for (int num : array) {
            if (num == value) {
                return true;
            }
        }
        return false;
    }

    private UMPPart nextKnownUMPPart(@NonNull ExtractorInput extractorInput) {
        UMPPart part;

        while (true) {
            part = decoder.decode(extractorInput);

            if (part == null) {
                Log.d(TAG, "The UMP stream is ended.");
                break;
            }

            // Normal reading: 47, 58. 52, 53, 42, 35, 20, 21, 22, 20...
            if (contains(KNOWN_PARTS, part.partId)) {
                Log.d(TAG, "Found known part: id=%s, size=%s, position=%s", part.partId, part.size, part.data.getPosition());
                break;
            } else {
                Log.e(TAG, "Unknown part encountered: id=%s, size=%s, position=%s", part.partId, part.size, part.data.getPosition());
                part.skip(); // an essential part to continue reading
            }

            // Debug
            //Log.e(TAG, "Unknown part encountered. id: %s, size: %s, position: %s", part.partId, part.size, part.data.getPosition());
            //part.skip(); // an essential part to continue reading
        }

        return part;
    }

    public String getUrl() {
        return this.url;
    }

    private void setUrl(String url) {
        Log.d(TAG, "New URL: %s", url);
        UrlQueryString newQueryString = UrlQueryStringFactory.parse(url);
        UrlQueryString oldQueryString = UrlQueryStringFactory.parse(this.url);
        String bn = newQueryString.get("id");
        String bc = oldQueryString.get("id");
        if (processor.isLive() && this.url != null && !Helpers.equals(bn, bc)) {
            throw new SabrStreamError(String.format("Broadcast ID changed from %s to %s. The download will need to be restarted.", bc, bn));
        }
        this.url = url;
        if (Helpers.equals(newQueryString.get("source"), "yt_live_broadcast")) {
            processor.setLive(true);
        }
    }
}
