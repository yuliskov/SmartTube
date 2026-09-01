package com.google.android.exoplayer2.source.sabr.parser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrMediaCorrelationException;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPDecoder;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.parser.ump.UmpTestFixtures;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SabrResponseDemuxerTest {
    private static final FormatId AUDIO = FormatId.newBuilder().setItag(140).build();
    private static final FormatId VIDEO = FormatId.newBuilder().setItag(248).build();

    @Test
    public void correlatesMultipleMediaPartsAndInterleavedTracks() {
        List<Long> controls = new ArrayList<>();
        SabrResponseDemuxer demuxer = demuxer((generation, frame) -> controls.add(frame.getPartId()));
        demuxer.consume(1, headerFrame(header(10, AUDIO, 1, false, 3)));
        demuxer.consume(1, headerFrame(header(20, VIDEO, 2, false, 2)));
        demuxer.consume(1, mediaFrame(10, new byte[] {1}));
        demuxer.consume(1, frames(UmpTestFixtures.frame(
                UMPPartId.LIVE_METADATA, new byte[] {9})).get(0));
        demuxer.consume(1, mediaFrame(20, new byte[] {4, 5}));
        demuxer.consume(1, mediaFrame(10, new byte[] {2, 3}));

        SabrResponseDemuxer.CompletedSegment video = demuxer.consume(1, endFrame(20)).get(0);
        SabrResponseDemuxer.CompletedSegment audio = demuxer.consume(1, endFrame(10)).get(0);

        assertArrayEquals(new byte[] {4, 5}, video.getData());
        assertArrayEquals(new byte[] {1, 2, 3}, audio.getData());
        assertEquals(1, controls.size());
        assertEquals((Long) (long) UMPPartId.LIVE_METADATA, controls.get(0));
    }

    @Test
    public void validatesDuplicateMissingAndContentLength() {
        SabrResponseDemuxer duplicate = demuxer(null);
        duplicate.consume(1, headerFrame(header(1, VIDEO, 1, false, 1)));
        assertReason(SabrMediaCorrelationException.Reason.DUPLICATE_HEADER,
                () -> duplicate.consume(1, headerFrame(header(1, VIDEO, 1, false, 1))));

        assertReason(SabrMediaCorrelationException.Reason.MISSING_HEADER,
                () -> demuxer(null).consume(1, mediaFrame(99, new byte[] {1})));

        SabrResponseDemuxer mismatch = demuxer(null);
        mismatch.consume(1, headerFrame(header(2, VIDEO, 1, false, 2)));
        mismatch.consume(1, mediaFrame(2, new byte[] {1}));
        assertReason(SabrMediaCorrelationException.Reason.CONTENT_LENGTH_MISMATCH,
                () -> mismatch.consume(1, endFrame(2)));
    }

    @Test
    public void staleGenerationCannotAppendAfterSeek() {
        SabrResponseDemuxer demuxer = demuxer(null);
        demuxer.consume(1, headerFrame(header(1, VIDEO, 1, false, 1)));
        demuxer.setGeneration(2);

        assertTrue(demuxer.consume(1, mediaFrame(1, new byte[] {1})).isEmpty());
        assertEquals(0, demuxer.getPartialCount());
        assertEquals(0, demuxer.getStoredByteCount());
    }

    @Test
    public void cachesInitializationAcrossGenerationAndBoundsCompletedQueue() {
        SabrResponseDemuxer demuxer = new SabrResponseDemuxer(
                "video-id", 1, 4, 1, 64, null);
        demuxer.consume(1, headerFrame(header(1, VIDEO, -1, true, 1)));
        demuxer.consume(1, mediaFrame(1, new byte[] {7}));
        SabrResponseDemuxer.CompletedSegment init = demuxer.consume(1, endFrame(1)).get(0);
        demuxer.setGeneration(2);
        assertArrayEquals(new byte[] {7}, demuxer.getInitialization(VIDEO).getData());

        complete(demuxer, 2, 2, VIDEO, 1, new byte[] {1});
        complete(demuxer, 2, 3, VIDEO, 2, new byte[] {2});
        assertEquals(1, demuxer.getCompletedCount());
        assertArrayEquals(new byte[] {2}, demuxer.poll(VIDEO).getData());
        assertNull(demuxer.poll(VIDEO));
    }

    @Test
    public void closeReleasesAllStoredBytes() {
        SabrResponseDemuxer demuxer = demuxer(null);
        demuxer.consume(1, headerFrame(header(1, VIDEO, 1, false, 2)));
        demuxer.consume(1, mediaFrame(1, new byte[] {1}));
        demuxer.close();

        assertEquals(0, demuxer.getStoredByteCount());
        assertReason(SabrMediaCorrelationException.Reason.CLOSED,
                () -> demuxer.consume(1, endFrame(1)));
    }

    private static SabrResponseDemuxer demuxer(SabrResponseDemuxer.ControlHandler handler) {
        return new SabrResponseDemuxer("video-id", 1, 4, 4, 64, handler);
    }

    private static MediaHeader header(
            int id, FormatId formatId, int sequence, boolean init, int contentLength) {
        MediaHeader.Builder builder = MediaHeader.newBuilder()
                .setHeaderId(id)
                .setVideoId("video-id")
                .setFormatId(formatId)
                .setItag(formatId.getItag())
                .setIsInitSeg(init)
                .setContentLength(contentLength);
        if (!init) {
            builder.setSequenceNumber(sequence);
        }
        return builder.build();
    }

    private static UMPDecoder.Frame headerFrame(MediaHeader header) {
        return frames(UmpTestFixtures.frame(UMPPartId.MEDIA_HEADER, header.toByteArray())).get(0);
    }

    private static UMPDecoder.Frame mediaFrame(long headerId, byte[] data) {
        return frames(UmpTestFixtures.frame(UMPPartId.MEDIA,
                UmpTestFixtures.concat(UmpTestFixtures.varint(headerId), data))).get(0);
    }

    private static UMPDecoder.Frame endFrame(long headerId) {
        return frames(UmpTestFixtures.frame(
                UMPPartId.MEDIA_END, UmpTestFixtures.varint(headerId))).get(0);
    }

    private static List<UMPDecoder.Frame> frames(byte[] bytes) {
        try {
            return new UMPDecoder(1024, 1024).feed(bytes);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void complete(
            SabrResponseDemuxer demuxer, long generation, int id, FormatId format,
            int sequence, byte[] bytes) {
        demuxer.consume(generation, headerFrame(header(id, format, sequence, false, bytes.length)));
        demuxer.consume(generation, mediaFrame(id, bytes));
        demuxer.consume(generation, endFrame(id));
    }

    private static void assertReason(
            SabrMediaCorrelationException.Reason expected, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + expected);
        } catch (SabrMediaCorrelationException error) {
            assertEquals(expected, error.getReason());
        }
    }
}
