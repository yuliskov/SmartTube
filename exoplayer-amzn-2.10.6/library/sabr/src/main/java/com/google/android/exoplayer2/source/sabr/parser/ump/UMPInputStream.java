package com.google.android.exoplayer2.source.sabr.parser.ump;

import com.google.android.exoplayer2.C;

import java.io.IOException;
import java.io.InputStream;

public class UMPInputStream extends InputStream {
    private final UMPPart part;
    private int position = 0; // bytes read so far

    public UMPInputStream(UMPPart part) {
        this.part = part;
    }

    @Override
    public int read() throws IOException {
        if (position >= part.size) return -1;

        byte[] buffer = new byte[1];
        int read;
        try {
            read = part.data.read(buffer, 0, 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupt status
            throw new IOException("Interrupted while reading from ExtractorInput", e);
        }

        if (read == C.RESULT_END_OF_INPUT) return -1;
        position += read;
        return buffer[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (position >= part.size) return -1;

        int toRead = Math.min(len, part.size - position);
        int read;
        try {
            read = part.data.read(b, off, toRead);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading from UMPPart", e);
        }

        if (read == C.RESULT_END_OF_INPUT) return -1;
        position += read;
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        int toSkip = (int) Math.min(n, part.size - position);
        int skipped;
        try {
            skipped = part.data.skip(toSkip);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while skipping in UMPPart", e);
        }
        position += skipped;
        return skipped;
    }

    @Override
    public int available() {
        return part.size - position;
    }
}

