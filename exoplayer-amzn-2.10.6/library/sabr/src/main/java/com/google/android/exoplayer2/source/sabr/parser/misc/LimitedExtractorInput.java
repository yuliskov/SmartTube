package com.google.android.exoplayer2.source.sabr.parser.misc;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.EOFException;
import java.io.IOException;

public final class LimitedExtractorInput implements ExtractorInput {
    private ExtractorInput input;
    private int remaining;

    /** Should be called before passing the extractor to a handler */
    public void init(ExtractorInput input, int length) {
        this.input = input;
        this.remaining = length;
    }

    public void dispose() {
        input = null;
        remaining = 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        if (remaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        int toRead = Math.min(length, remaining);
        int read = input.read(buffer, offset, toRead);
        if (read > 0) remaining -= read;
        return read;
    }

    @Override
    public void readFully(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        if (length > remaining) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }
        input.readFully(buffer, offset, length);
        remaining -= length;
    }

    @Override
    public boolean readFully(
            byte[] buffer,
            int offset,
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {
        length = (int) Math.min(length, remaining);
        boolean ok = input.readFully(buffer, offset, length, allowEndOfInput);
        if (ok) remaining -= length;
        return ok;
    }

    @Override
    public int skip(int length) throws IOException, InterruptedException {
        int toSkip = (int) Math.min(length, remaining);
        int skipped = input.skip(toSkip);
        if (skipped > 0) remaining -= skipped;
        return skipped;
    }

    @Override
    public void skipFully(int length) throws IOException, InterruptedException {
        if (length > remaining) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }
        input.skipFully(length);
        remaining -= length;
    }

    @Override
    public long getPosition() {
        return input.getPosition();
    }

    @Override
    public long getLength() {
        return remaining;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long p, E e) throws E {
        input.setRetryPosition(p, e);
    }

    @Override
    public boolean skipFully(
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {

        if (length > remaining) {
            if (allowEndOfInput) {
                return false;
            }
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        boolean ok = input.skipFully(length, allowEndOfInput);
        if (ok) {
            remaining -= length;
        }
        return ok;
    }

    @Override
    public boolean peekFully(
            byte[] target,
            int offset,
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {

        if (length > remaining) {
            if (allowEndOfInput) {
                return false;
            }
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        return input.peekFully(target, offset, length, allowEndOfInput);
    }

    @Override
    public void peekFully(
            byte[] target,
            int offset,
            int length) throws IOException, InterruptedException {

        if (length > remaining) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        input.peekFully(target, offset, length);
    }

    @Override
    public boolean advancePeekPosition(
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {

        if (length > remaining) {
            if (allowEndOfInput) {
                return false;
            }
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        return input.advancePeekPosition(length, allowEndOfInput);
    }

    @Override
    public void advancePeekPosition(int length)
            throws IOException, InterruptedException {

        if (length > remaining) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        input.advancePeekPosition(length);
    }

    @Override
    public void resetPeekPosition() {
        input.resetPeekPosition();
    }

    @Override
    public long getPeekPosition() {
        return input.getPeekPosition();
    }
}
