package com.google.android.exoplayer2.source.sabr.parser;

import static org.junit.Assert.assertEquals;

import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentInitSabrPart;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrSeek;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;

import org.junit.Test;

public class SabrProcessorMediaTest {
    @Test
    public void liveBitrateEstimateConvertsBitsToBytes() {
        FormatId formatId = FormatId.newBuilder().setItag(248).build();
        SabrProcessor processor = new SabrProcessor(
                "", StreamerContext.ClientInfo.newBuilder().build(),
                5, 100, 0, null, false, "video-id", -1);
        processor.setLive(true);
        processor.setFormatSelector(new FormatSelector("video", false, formatId));
        processor.processFormatInitializationMetadata(FormatInitializationMetadata.newBuilder()
                .setVideoId("video-id")
                .setFormatId(formatId)
                .setMimeType("video/webm")
                .build());

        MediaSegmentInitSabrPart result = processor.processMediaHeader(MediaHeader.newBuilder()
                .setHeaderId(1)
                .setVideoId("video-id")
                .setFormatId(formatId)
                .setItag(248)
                .setSequenceNumber(1)
                .setDurationMs(1_000)
                .setBitrateBps(8_000)
                .build()).sabrPart;

        assertEquals(1_000, result.contentLength);
        assertEquals(true, result.contentLengthEstimate);
    }

    @Test
    public void serverSeekPreservesInFlightMediaCorrelation() {
        FormatId formatId = FormatId.newBuilder().setItag(248).build();
        SabrProcessor processor = new SabrProcessor(
                "", StreamerContext.ClientInfo.newBuilder().build(),
                5, 100, 0, null, false, "video-id", -1);
        processor.setFormatSelector(new FormatSelector("video", false, formatId));
        processor.processFormatInitializationMetadata(FormatInitializationMetadata.newBuilder()
                .setVideoId("video-id")
                .setFormatId(formatId)
                .setMimeType("video/webm")
                .build());
        processor.processMediaHeader(MediaHeader.newBuilder()
                .setHeaderId(0)
                .setVideoId("video-id")
                .setFormatId(formatId)
                .setItag(248)
                .setIsInitSeg(true)
                .setContentLength(0)
                .build());

        processor.processSabrSeek(SabrSeek.newBuilder()
                .setSeekMediaTime(0)
                .setSeekMediaTimescale(1_000)
                .build());

        processor.processMediaEnd(0);
    }
}
