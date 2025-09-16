package com.google.android.exoplayer2.source.sabr.parser;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPDecoder;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPart;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.liskovsoft.sharedutils.mylogger.Log;

public class ResponseParser {
    private static final String TAG = ResponseParser.class.getSimpleName();
    private final UMPDecoder decoder;
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

    public ResponseParser(@NonNull ExtractorInput extractorInput) {
        decoder = new UMPDecoder(extractorInput);
    }

    public SabrPart parse() {
        UMPPart part = nextKnownUMPPart();

        if (part == null) {
            return null;
        }

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
                return processSabrRedirect(part);
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
        return null;
    }

    private SabrPart processMedia(UMPPart part) {
        return null;
    }

    private SabrPart processMediaEnd(UMPPart part) {
        return null;
    }

    private SabrPart processStreamProtectionStatus(UMPPart part) {
        return null;
    }

    private SabrPart processSabrRedirect(UMPPart part) {
        return null;
    }

    private SabrPart processFormatInitializationMetadata(UMPPart part) {
        return null;
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
}
