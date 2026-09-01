package com.google.android.exoplayer2.source.sabr.parser.ump;

import java.io.ByteArrayOutputStream;

public final class UmpTestFixtures {
    private UmpTestFixtures() {}

    public static byte[] frame(long partId, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, varint(partId));
        write(output, varint(payload.length));
        write(output, payload);
        return output.toByteArray();
    }

    public static byte[] varint(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("UMP integers are unsigned 32-bit values");
        }
        int size = value < (1L << 7) ? 1
                : value < (1L << 14) ? 2
                : value < (1L << 21) ? 3
                : value < (1L << 28) ? 4 : 5;
        byte[] result = new byte[size];
        if (size == 1) {
            result[0] = (byte) value;
            return result;
        }
        if (size == 5) {
            result[0] = (byte) 0xF0;
            for (int i = 0; i < 4; i++) {
                result[i + 1] = (byte) (value >>> (i * 8));
            }
            return result;
        }

        int firstPayloadBits = 8 - size;
        int prefix = size == 2 ? 0x80 : size == 3 ? 0xC0 : 0xE0;
        result[0] = (byte) (prefix | (value & ((1 << firstPayloadBits) - 1)));
        long remaining = value >>> firstPayloadBits;
        for (int i = 1; i < size; i++) {
            result[i] = (byte) remaining;
            remaining >>>= 8;
        }
        return result;
    }

    public static byte[] concat(byte[]... chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            write(output, chunk);
        }
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, byte[] bytes) {
        output.write(bytes, 0, bytes.length);
    }
}
