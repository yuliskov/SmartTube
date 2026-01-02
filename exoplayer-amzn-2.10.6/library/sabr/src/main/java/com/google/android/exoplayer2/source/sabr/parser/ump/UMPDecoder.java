package com.google.android.exoplayer2.source.sabr.parser.ump;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;

public class UMPDecoder {
    public UMPPart decode(@NonNull ExtractorInput extractorInput) {
        try {
            int partType = (int) readVarInt(extractorInput);
            if (partType == -1) {
                return null;
            }

            int partSize = (int) readVarInt(extractorInput);
            if (partSize == -1) {
                throw new IllegalStateException("Unexpected EOF while reading part size");
            }

            return new UMPPart(partType, partSize, extractorInput);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
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

        for (int i : Helpers.range(1, size - 1, 1)) {
            success = input.readFully(buffer, 0, 1, true);
            if (!success) {
                return -1;
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

    private int varIntSize(long byteInt) {
        return byteInt < 128 ? 1 : byteInt < 192 ? 2 : byteInt < 224 ? 3 : byteInt < 240 ? 4 : 5;
    }

    private interface StreamWrapper {
        boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
                throws IOException, InterruptedException;
    }
}
