package com.google.android.exoplayer2.source.sabr.parser;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.MediaSegmentMismatchError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrStreamError;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.source.sabr.parser.processor.ProcessFormatInitializationMetadataResult;
import com.google.android.exoplayer2.source.sabr.parser.processor.ProcessMediaEndResult;
import com.google.android.exoplayer2.source.sabr.parser.processor.ProcessMediaHeaderResult;
import com.google.android.exoplayer2.source.sabr.parser.processor.ProcessMediaResult;
import com.google.android.exoplayer2.source.sabr.parser.processor.ProcessStreamProtectionStatusResult;
import com.google.android.exoplayer2.source.sabr.parser.processor.SabrProcessor;
import com.google.android.exoplayer2.source.sabr.parser.processor.Utils;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPDecoder;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPart;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientAbrState;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrRedirect;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.protobuf.InvalidProtocolBufferException;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryString;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SabrStreamParser {
    private static final String TAG = SabrStreamParser.class.getSimpleName();
    private final int[] KNOWN_PARTS = {
            UMPPartId.MEDIA_HEADER,
            UMPPartId.MEDIA,
            UMPPartId.MEDIA_END,
            UMPPartId.STREAM_PROTECTION_STATUS,
            UMPPartId.SABR_REDIRECT,
            UMPPartId.FORMAT_INITIALIZATION_METADATA,
            UMPPartId.NEXT_REQUEST_POLICY,
            UMPPartId.LIVE_METADATA,
            UMPPartId.SABR_SEEK,
            UMPPartId.SABR_ERROR,
            UMPPartId.SABR_CONTEXT_UPDATE,
            UMPPartId.SABR_CONTEXT_SENDING_POLICY,
            UMPPartId.RELOAD_PLAYER_RESPONSE
    };
    private final UMPDecoder decoder;
    private final SabrProcessor processor;
    private int sqMismatchForwardCount;
    private int sqMismatchBacktrackCount;
    private boolean receivedNewSegments;
    private String url;

    public SabrStreamParser(@NonNull ExtractorInput extractorInput) {
        decoder = new UMPDecoder(extractorInput);
        processor = new SabrProcessor();
    }

    public SabrPart parse() {
        SabrPart result = null;

        while (true) {
            UMPPart part = nextKnownUMPPart();

            if (part == null) {
                break;
            }

            result = parsePart(part);

            if (result != null) {
                break;
            }
        }

        return result;
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
                return processNextRequestPolicy(part);
            case UMPPartId.LIVE_METADATA:
                return processLiveMetadata(part);
            case UMPPartId.SABR_SEEK:
                return processSabrSeek(part);
            case UMPPartId.SABR_ERROR:
                return processSabrError(part);
            case UMPPartId.SABR_CONTEXT_UPDATE:
                return processSabrContextUpdate(part);
            case UMPPartId.SABR_CONTEXT_SENDING_POLICY:
                return processSabrContextSendingPolicy(part);
            case UMPPartId.RELOAD_PLAYER_RESPONSE:
                return processReloadPlayerResponse(part);
        }

        return null;
    }

    private SabrPart processMediaHeader(UMPPart part) {
        MediaHeader mediaHeader;

        try {
            mediaHeader = MediaHeader.parseFrom(part.data);
        } catch (InvalidProtocolBufferException e) {
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
                // Move the player time forward to try to adjust for this.
                ClientAbrState state = processor.getClientAbrState().toBuilder()
                        .setPlayerTimeMs(processor.getClientAbrState().getPlayerTimeMs() + processor.getLiveSegmentTargetDurationToleranceMs())
                        .build();
                processor.setClientAbrState(state);
                sqMismatchForwardCount += 1;
                return null;
            } else if (processor.isLive() && e.receivedSequenceNumber == e.expectedSequenceNumber + 2) {
                // The previous segment was possibly shorter than expected
                // Move the player time backwards to try to adjust for this.
                ClientAbrState state = processor.getClientAbrState().toBuilder()
                        .setPlayerTimeMs(Math.max(0, processor.getClientAbrState().getPlayerTimeMs() - processor.getLiveSegmentTargetDurationToleranceMs()))
                        .build();
                processor.setClientAbrState(state);
                sqMismatchBacktrackCount += 1;
                return null;
            }

            throw e;
        }
    }

    private SabrPart processMedia(UMPPart part) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(part.data)) {
            int headerId = decoder.readVarInt(inputStream);
            int contentLength = inputStream.available();

            ProcessMediaResult result = processor.processMedia(headerId, contentLength, inputStream);

            return result.sabrPart;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private SabrPart processMediaEnd(UMPPart part) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(part.data)) {
            int headerId = decoder.readVarInt(inputStream);
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

    private SabrPart processStreamProtectionStatus(UMPPart part) {
        StreamProtectionStatus sps;

        try {
            sps = StreamProtectionStatus.parseFrom(part.data);
            Log.d(TAG, "Status: %s", sps);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }

        ProcessStreamProtectionStatusResult result = processor.processStreamProtectionStatus(sps);

        return result.sabrPart;
    }

    private void processSabrRedirect(UMPPart part) {
        SabrRedirect sabrRedirect;

        try {
            sabrRedirect = SabrRedirect.parseFrom(part.data);
            Log.d(TAG, "Process SabrRedirect: %s", sabrRedirect);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }

        if (!sabrRedirect.hasRedirectUrl()) {
            Log.d(TAG, "Server requested to redirect to an invalid URL");
            return;
        }

        setUrl(sabrRedirect.getRedirectUrl());
    }

    private SabrPart processFormatInitializationMetadata(UMPPart part) {
        FormatInitializationMetadata fmtInitMetadata;

        try {
            fmtInitMetadata = FormatInitializationMetadata.parseFrom(part.data);
            Log.d(TAG, "Process FormatInitializationMetadata: %s", fmtInitMetadata);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }

        ProcessFormatInitializationMetadataResult result = processor.processFormatInitializationMetadata(fmtInitMetadata);

        return result.sabrPart;
    }

    private SabrPart processNextRequestPolicy(UMPPart part) {
        return null;
    }

    private SabrPart processLiveMetadata(UMPPart part) {
        return null;
    }

    private SabrPart processSabrSeek(UMPPart part) {
        return null;
    }

    private SabrPart processSabrError(UMPPart part) {
        return null;
    }

    private SabrPart processSabrContextUpdate(UMPPart part) {
        return null;
    }

    private SabrPart processSabrContextSendingPolicy(UMPPart part) {
        return null;
    }

    private SabrPart processReloadPlayerResponse(UMPPart part) {
        return null;
    }

    public static boolean contains(int[] array, int value) {
        for (int num : array) {
            if (num == value) {
                return true;
            }
        }
        return false;
    }

    private UMPPart nextKnownUMPPart() {
        UMPPart part;

        while (true) {
            part = decoder.decode();

            if (part == null) {
                break;
            }

            if (contains(KNOWN_PARTS, part.partId)) {
                break;
            } else {
                Log.d(TAG, "Unknown part encountered: %s", part.partId);
            }
        }

        return part;
    }

    private String getUrl() {
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
