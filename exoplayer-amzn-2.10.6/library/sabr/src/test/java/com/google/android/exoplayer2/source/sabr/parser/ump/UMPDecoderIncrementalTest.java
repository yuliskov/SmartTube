package com.google.android.exoplayer2.source.sabr.parser.ump;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class UMPDecoderIncrementalTest {
    private static final int MAX_PART_SIZE = 1024;

    @Test
    public void decodesOneThroughFiveByteCustomVarints() throws Exception {
        long[] partIds = {0x7F, 0x3FFF, 0x1F_FFFF, 0x0FFF_FFFF, 0xFFFF_FFFFL};
        for (long partId : partIds) {
            UMPDecoder decoder = decoder();
            List<UMPDecoder.Frame> frames = decoder.feed(UmpTestFixtures.frame(partId, new byte[] {7}));
            decoder.finish();

            assertEquals(1, frames.size());
            assertEquals(partId, frames.get(0).getPartId());
            assertArrayEquals(new byte[] {7}, frames.get(0).getPayload());
        }
    }

    @Test
    public void preservesTypeSizeAndPayloadAcrossEveryBoundary() throws Exception {
        byte[] encoded = UmpTestFixtures.frame(0x1F_FFFF, new byte[] {1, 2, 3, 4});
        for (int split = 1; split < encoded.length; split++) {
            UMPDecoder decoder = decoder();
            List<UMPDecoder.Frame> frames = new ArrayList<>();
            frames.addAll(decoder.feed(encoded, 0, split));
            frames.addAll(decoder.feed(encoded, split, encoded.length - split));
            decoder.finish();

            assertEquals("split=" + split, 1, frames.size());
            assertEquals(0x1F_FFFF, frames.get(0).getPartId());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, frames.get(0).getPayload());
        }
    }

    @Test
    public void decodesEmptyAndMultiplePartsAndRetainsTrailingPartialPart() throws Exception {
        byte[] first = UmpTestFixtures.frame(1, new byte[0]);
        byte[] second = UmpTestFixtures.frame(2, new byte[] {8, 9});
        byte[] third = UmpTestFixtures.frame(3, new byte[] {10, 11, 12});
        byte[] firstRead = UmpTestFixtures.concat(first, second, new byte[] {third[0], third[1], third[2]});
        UMPDecoder decoder = decoder();

        List<UMPDecoder.Frame> frames = new ArrayList<>(decoder.feed(firstRead));
        frames.addAll(decoder.feed(third, 3, third.length - 3));
        decoder.finish();

        assertEquals(3, frames.size());
        assertArrayEquals(new byte[0], frames.get(0).getPayload());
        assertArrayEquals(new byte[] {8, 9}, frames.get(1).getPayload());
        assertArrayEquals(new byte[] {10, 11, 12}, frames.get(2).getPayload());
    }

    @Test
    public void acceptsFutureHighPartIdWithoutArbitraryThreshold() throws Exception {
        UMPDecoder decoder = decoder();
        List<UMPDecoder.Frame> frames = decoder.feed(UmpTestFixtures.frame(10_000, new byte[] {1}));

        assertEquals(10_000, frames.get(0).getPartId());
    }

    @Test
    public void rejectsDeclaredSizeAboveConfiguredMaximumBeforeAllocation() throws Exception {
        UMPDecoder decoder = new UMPDecoder(4, 4);
        byte[] headerOnly = UmpTestFixtures.concat(UmpTestFixtures.varint(1), UmpTestFixtures.varint(5));

        assertProtocolError(UMPProtocolException.Reason.PART_TOO_LARGE,
                () -> decoder.feed(headerOnly));
    }

    @Test
    public void rejectsUnsignedDeclaredSizeThatCannotBeRepresented() throws Exception {
        UMPDecoder decoder = decoder();
        byte[] headerOnly = UmpTestFixtures.concat(
                UmpTestFixtures.varint(1), UmpTestFixtures.varint(0xFFFF_FFFFL));

        assertProtocolError(UMPProtocolException.Reason.SIZE_OVERFLOW,
                () -> decoder.feed(headerOnly));
    }

    @Test
    public void finishRejectsTruncatedTypeSizeAndPayload() throws Exception {
        assertTruncated(new byte[] {(byte) 0x80});
        assertTruncated(UmpTestFixtures.concat(UmpTestFixtures.varint(1), new byte[] {(byte) 0x80}));
        byte[] frame = UmpTestFixtures.frame(1, new byte[] {1, 2});
        assertTruncated(java.util.Arrays.copyOf(frame, frame.length - 1));
    }

    @Test
    public void cancellationClearsPartialPayloadAndRejectsFurtherInput() throws Exception {
        UMPDecoder decoder = decoder();
        byte[] frame = UmpTestFixtures.frame(1, new byte[] {1, 2, 3});
        assertTrue(decoder.feed(frame, 0, frame.length - 1).isEmpty());

        decoder.cancel();

        assertProtocolError(UMPProtocolException.Reason.CANCELLED,
                () -> decoder.feed(frame, frame.length - 1, 1));
        assertEquals(0, decoder.getBufferedByteCount());
    }

    private static UMPDecoder decoder() {
        return new UMPDecoder(MAX_PART_SIZE, MAX_PART_SIZE);
    }

    private static void assertTruncated(byte[] bytes) throws Exception {
        UMPDecoder decoder = decoder();
        decoder.feed(bytes);
        assertProtocolError(UMPProtocolException.Reason.TRUNCATED, decoder::finish);
    }

    private static void assertProtocolError(
            UMPProtocolException.Reason expected,
            ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected " + expected);
        } catch (UMPProtocolException error) {
            assertEquals(expected, error.getReason());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
