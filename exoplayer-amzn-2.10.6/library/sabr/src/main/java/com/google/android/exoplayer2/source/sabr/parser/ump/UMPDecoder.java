package com.google.android.exoplayer2.source.sabr.parser.ump;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UMPDecoder {
    private static final int DEFAULT_MAX_PART_SIZE_BYTES = 16 * 1024 * 1024;

    private enum Stage {
        TYPE,
        SIZE,
        PAYLOAD
    }

    /** An immutable, completely decoded UMP frame. */
    public static final class Frame {
        private final long partId;
        private final byte[] payload;

        private Frame(long partId, byte[] payload) {
            this.partId = partId;
            this.payload = payload;
        }

        public long getPartId() {
            return partId;
        }

        public byte[] getPayload() {
            return Arrays.copyOf(payload, payload.length);
        }

        public int getPayloadSize() {
            return payload.length;
        }
    }

    private final int maxPartSizeBytes;
    private final int maxBufferedPayloadBytes;
    private Stage stage = Stage.TYPE;
    private long currentPartId;
    private byte[] payload;
    private int payloadPosition;
    private int varintExpectedBytes;
    private int varintReadBytes;
    private int varintShift;
    private long varintValue;
    private boolean cancelled;
    private boolean finished;

    public UMPDecoder() {
        this(DEFAULT_MAX_PART_SIZE_BYTES, DEFAULT_MAX_PART_SIZE_BYTES);
    }

    public UMPDecoder(int maxPartSizeBytes, int maxBufferedPayloadBytes) {
        if (maxPartSizeBytes < 0 || maxBufferedPayloadBytes < 0) {
            throw new IllegalArgumentException("UMP byte limits must be non-negative");
        }
        this.maxPartSizeBytes = maxPartSizeBytes;
        this.maxBufferedPayloadBytes = maxBufferedPayloadBytes;
    }

    public UMPPart decode(@NonNull ExtractorInput extractorInput) {
        try {
            long decodedPartType = readVarInt(extractorInput);
            int partType = (int) decodedPartType;
            if (partType == -1) {
                return null;
            }

            long decodedPartSize = readVarInt(extractorInput);
            if (decodedPartSize < 0) {
                throw new EOFException("Unexpected EOF while reading UMP part size");
            }
            if (decodedPartSize > Integer.MAX_VALUE) {
                throw new UMPProtocolException(
                        UMPProtocolException.Reason.SIZE_OVERFLOW,
                        "UMP part size cannot be represented by this decoder");
            }
            if (decodedPartSize > maxPartSizeBytes) {
                throw new UMPProtocolException(
                        UMPProtocolException.Reason.PART_TOO_LARGE,
                        "UMP part exceeds the configured maximum");
            }

            return new UMPPart(partType, (int) decodedPartSize, extractorInput);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Incrementally consumes arbitrary network chunks. Complete frames are returned in wire order;
     * incomplete type, size, and payload bytes remain in this decoder for the next call.
     */
    public synchronized List<Frame> feed(byte[] bytes) throws UMPProtocolException {
        return feed(bytes, 0, bytes.length);
    }

    public synchronized List<Frame> feed(byte[] bytes, int offset, int length)
            throws UMPProtocolException {
        if (cancelled) {
            throw protocolError(UMPProtocolException.Reason.CANCELLED, "UMP decoding was cancelled");
        }
        if (finished) {
            throw protocolError(UMPProtocolException.Reason.FINISHED, "UMP decoder is already finished");
        }
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException("Invalid UMP input range");
        }
        if (length == 0) {
            return Collections.emptyList();
        }

        List<Frame> frames = new ArrayList<>();
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            int value = bytes[index] & 0xFF;
            if (stage == Stage.PAYLOAD) {
                payload[payloadPosition++] = (byte) value;
                if (payloadPosition == payload.length) {
                    frames.add(new Frame(currentPartId, payload));
                    resetFrame();
                }
                continue;
            }

            Long completedVarint = consumeVarintByte(value);
            if (completedVarint == null) {
                continue;
            }
            if (stage == Stage.TYPE) {
                currentPartId = completedVarint;
                stage = Stage.SIZE;
                continue;
            }

            long partSize = completedVarint;
            if (partSize > Integer.MAX_VALUE) {
                throw protocolError(
                        UMPProtocolException.Reason.SIZE_OVERFLOW,
                        "UMP part size exceeds signed integer range");
            }
            if (partSize > maxPartSizeBytes) {
                throw protocolError(
                        UMPProtocolException.Reason.PART_TOO_LARGE,
                        "UMP part exceeds configured part-size limit");
            }
            if (partSize > maxBufferedPayloadBytes) {
                throw protocolError(
                        UMPProtocolException.Reason.BUFFER_LIMIT_EXCEEDED,
                        "UMP part exceeds configured buffered-payload limit");
            }

            payload = new byte[(int) partSize];
            payloadPosition = 0;
            if (partSize == 0) {
                frames.add(new Frame(currentPartId, payload));
                resetFrame();
            } else {
                stage = Stage.PAYLOAD;
            }
        }
        return frames;
    }

    /** Marks clean end-of-input, rejecting any incomplete frame with a typed error. */
    public synchronized void finish() throws UMPProtocolException {
        if (cancelled) {
            throw protocolError(UMPProtocolException.Reason.CANCELLED, "UMP decoding was cancelled");
        }
        if (stage != Stage.TYPE || varintReadBytes != 0) {
            throw protocolError(UMPProtocolException.Reason.TRUNCATED, "Truncated UMP frame");
        }
        finished = true;
    }

    /** Cancels decoding, drops the partial frame, and prevents any later bytes from being accepted. */
    public synchronized void cancel() {
        cancelled = true;
        clearVarint();
        resetFrame();
    }

    public synchronized int getBufferedByteCount() {
        return payloadPosition + varintReadBytes;
    }

    private Long consumeVarintByte(int value) throws UMPProtocolException {
        if (varintReadBytes == 0) {
            varintExpectedBytes = varIntSize(value);
            varintReadBytes = 1;
            if (varintExpectedBytes == 1) {
                clearVarint();
                return (long) value;
            }
            if (varintExpectedBytes == 5) {
                varintValue = 0;
                varintShift = 0;
            } else {
                int firstPayloadBits = 8 - varintExpectedBytes;
                varintValue = value & ((1L << firstPayloadBits) - 1);
                varintShift = firstPayloadBits;
            }
            return null;
        }

        if (varintShift >= 32) {
            throw protocolError(
                    UMPProtocolException.Reason.MALFORMED_VARINT,
                    "UMP variable-length integer overflow");
        }
        varintValue |= ((long) value) << varintShift;
        varintShift += 8;
        varintReadBytes++;
        if (varintReadBytes != varintExpectedBytes) {
            return null;
        }

        long result = varintValue & 0xFFFF_FFFFL;
        clearVarint();
        return result;
    }

    private void resetFrame() {
        stage = Stage.TYPE;
        currentPartId = 0;
        payload = null;
        payloadPosition = 0;
    }

    private void clearVarint() {
        varintExpectedBytes = 0;
        varintReadBytes = 0;
        varintShift = 0;
        varintValue = 0;
    }

    private static UMPProtocolException protocolError(
            UMPProtocolException.Reason reason, String message) {
        return new UMPProtocolException(reason, message);
    }

    private long readVarInt(StreamWrapper input) throws IOException, InterruptedException {
        // https://web.archive.org/web/20250430054327/https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md
        // https://web.archive.org/web/20250429151021/https://github.com/davidzeng0/innertube/blob/main/googlevideo/ump.md
        byte[] buffer = new byte[1];
        boolean success = input.readFully(buffer, 0, 1, true);
        if (!success) {
            // Expected EOF
            return -1;
        }

        long byteInt = buffer[0] & 0xFF; // convert to unsigned (0..255)
        int size = varIntSize(byteInt);
        long result = 0;
        int shift = 0;

        if (size != 5) {
            shift = 8 - size;
            int mask = (1 << shift) - 1;
            result |= byteInt & mask;
        }

        for (int i = 1; i < size; i++) {
            success = input.readFully(buffer, 0, 1, true);
            if (!success) {
                throw new EOFException("Unexpected EOF in UMP variable-length integer");
            }
            byteInt = buffer[0] & 0xFF; // convert to unsigned (0..255)
            result |= byteInt << shift;
            shift += 8;
        }

        return result;
    }

    //private long readVarInt(StreamWrapper input) throws IOException, InterruptedException {
    //    // https://web.archive.org/web/20250430054327/https://github.com/gsuberland/UMP_Format/blob/main/UMP_Format.md
    //    // https://web.archive.org/web/20250429151021/https://github.com/davidzeng0/innertube/blob/main/googlevideo/ump.md
    //    byte[] buffer = new byte[1];
    //    if (!input.readFully(buffer, 0, 1, true)) {
    //        return -1; // clean EOF before reading anything
    //    }
    //
    //    long first = buffer[0] & 0xFF;
    //    int size = varIntSize(first);
    //
    //    if (size < 1 || size > 5) {
    //        throw new IOException("Invalid VarInt size: " + size);
    //    }
    //
    //    int payloadBits = 8 - (size + 1);
    //    long result = first & ((1L << payloadBits) - 1);
    //    int shift = payloadBits;
    //
    //    for (int i = 1; i < size; i++) {
    //        if (!input.readFully(buffer, 0, 1, true)) {
    //            throw new EOFException("Unexpected EOF in VarInt");
    //        }
    //        long b = buffer[0] & 0xFF;
    //        result |= b << shift;
    //        shift += 8;
    //    }
    //
    //    return result;
    //}

    public long readVarInt(ExtractorInput input) throws IOException, InterruptedException {
        return readVarInt(input::readFully);
    }

    public long readVarInt(ByteArrayInputStream inputStream) throws IOException, InterruptedException {
        return readVarInt((target, offset, length, allowEndOfInput) -> {
            int numRead = inputStream.read(target, offset, length);
            return numRead != -1;
        });
    }

    private static int varIntSize(long byteInt) {
        return byteInt < 128 ? 1 : byteInt < 192 ? 2 : byteInt < 224 ? 3 : byteInt < 240 ? 4 : 5;
    }

    private interface StreamWrapper {
        boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
                throws IOException, InterruptedException;
    }
}
