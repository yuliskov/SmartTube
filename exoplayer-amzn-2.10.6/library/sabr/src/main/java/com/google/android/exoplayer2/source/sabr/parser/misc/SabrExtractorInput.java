package com.google.android.exoplayer2.source.sabr.parser.misc;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.EOFException;
import java.io.IOException;

public final class SabrExtractorInput implements ExtractorInput {
    private static final String TAG = SabrExtractorInput.class.getSimpleName();
    private final SabrStream sabrStream;
    private ExtractorInput input;
    private long position;
    private long startPosition;
    private int remaining;
    private MediaSegmentDataSabrPart data;

    public SabrExtractorInput(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
    }

    /** Should be called before passing the extractor to a handler */
    public void init(ExtractorInput input) {
        if (this.input == input) {
            return;
        }

        if (this.input != null) {
            throw new IllegalStateException("The input should be disposed before initializing");
        }

        this.input = input;
        position = input.getPosition();
        startPosition = position;
        remaining = C.LENGTH_UNSET;
    }

    public void dispose() {
        if (data != null) {
            if (getAdvance() != data.contentLength) {
                throw new IllegalStateException("The SABR read isn't finished yet");
            }
        }

        startPosition = 0;
        input = null;
        data = null;
        position = C.POSITION_UNSET;
        remaining = C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        if (remaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        int read = readInt(buffer, offset, length);
        if (remaining != C.LENGTH_UNSET && read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public void readFully(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        readFullyInt(buffer, offset, length);
        if (remaining != C.LENGTH_UNSET) {
            remaining -= length;
        }
        if (exceeded) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }
    }

    @Override
    public boolean readFully(
            byte[] buffer,
            int offset,
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        boolean ok = readFullyInt(buffer, offset, length, allowEndOfInput);
        if (remaining != C.LENGTH_UNSET) {
            remaining -= length;
        }
        if (exceeded) {
            if (allowEndOfInput) {
                ok = false;
            } else {
                throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
            }
        }
        return ok;
    }

    @Override
    public int skip(int length) throws IOException, InterruptedException {
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        int skipped = skipInt(length);
        if (remaining != C.LENGTH_UNSET && skipped > 0) {
            remaining -= skipped;
        }
        return skipped;
    }

    @Override
    public void skipFully(int length) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        skipFullyInt(length);
        if (remaining != C.LENGTH_UNSET) {
            remaining -= length;
        }
        if (exceeded) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }
    }

    @Override
    public long getPosition() {
        return getPositionInt();
    }

    @Override
    public long getLength() {
        return remaining;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long p, E e) throws E {
        setRetryPositionInt(p, e);
    }

    @Override
    public boolean skipFully(
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        boolean ok = skipFullyInt(length, allowEndOfInput);
        if (remaining != C.LENGTH_UNSET) {
            remaining -= length;
        }
        if (exceeded) {
            if (allowEndOfInput) {
                ok = false;
            } else {
                throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
            }
        }
        return ok;
    }

    @Override
    public boolean peekFully(
            byte[] target,
            int offset,
            int length,
            boolean allowEndOfInput) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        boolean ok = peekFullyInt(target, offset, length, allowEndOfInput);
        if (exceeded) {
            if (allowEndOfInput) {
                ok = false;
            } else {
                throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
            }
        }
        return ok;
    }

    @Override
    public void peekFully(
            byte[] target,
            int offset,
            int length) throws IOException, InterruptedException {
        boolean exceeded = remaining != C.LENGTH_UNSET && length > remaining;
        if (remaining != C.LENGTH_UNSET) {
            length = Math.min(length, remaining);
        }
        peekFullyInt(target, offset, length);
        if (exceeded) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }
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

        return advancePeekPositionInt(length, allowEndOfInput);
    }

    @Override
    public void advancePeekPosition(int length)
            throws IOException, InterruptedException {

        if (length > remaining) {
            throw new EOFException("LimitedExtractorInput: chunk boundary exceeded");
        }

        advancePeekPositionInt(length);
    }

    @Override
    public void resetPeekPosition() {
        resetPeekPositionInt();
    }

    @Override
    public long getPeekPosition() {
        return getPeekPositionInt();
    }

    private void fetchData() {
        while (true) {
            if (data != null) {
                long advance = getAdvance();
                int length = data.contentLength;
                if (advance < length) {
                    break;
                } else if (advance == length) {
                    data = null;
                } else {
                    throw new IllegalStateException("Reading past the SABR part boundaries");
                }
            }

            SabrPart sabrPart = sabrStream.parse(input);

            if (sabrPart == null) {
                break;
            }

            // Debug
            //if (sabrPart instanceof MediaSegmentDataSabrPart) {
            //    MediaSegmentDataSabrPart data = (MediaSegmentDataSabrPart) sabrPart;
            //    Log.e(TAG, "Consumed contentLength: " + data.contentLength);
            //    data.data.skipFully(data.contentLength);
            //    continue;
            //}

            if (sabrPart instanceof MediaSegmentDataSabrPart) {
                data = (MediaSegmentDataSabrPart) sabrPart;
                startPosition = position;
                break;
            }
        }
    }

    private int readInt(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        int read = C.RESULT_END_OF_INPUT;

        fetchData();

        if (data == null) {
            return read;
        }

        int toRead = Math.min(getRemaining(), length);
        read = data.data.read(buffer, offset, toRead);

        if (read > 0) {
            position += read;
        }

        return read;
    }

    private void readFullyInt(byte[] buffer, int offset, int length) throws IOException, InterruptedException {
        int total = 0;

        while (true) {
            fetchData();

            if (data == null) {
                if (total > 0) {
                    throwEOFException();
                }
                break;
            }

            int toRead = Math.min(getRemaining(), length - total);
            data.data.readFully(buffer, offset + total, toRead);

            position += toRead;

            if (toRead == length - total) {
                break;
            }

            total += toRead;

            Log.e(TAG, "Continue readFully: offset=%s, length=%s", offset + total, length - total);
        }
    }

    private boolean readFullyInt(byte[] buffer, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        boolean result = false;
        int total = 0;

        while (true) {
            fetchData();

            if (data == null) {
                if (total > 0) {
                    throwEOFException();
                }

                break;
            }

            int toRead = Math.min(getRemaining(), length - total);
            result = data.data.readFully(buffer, offset + total, toRead, allowEndOfInput);

            if (!result) {
                throwEOFException();
            }

            position += toRead;

            if (toRead == length - total) {
                break;
            }

            total += toRead;

            Log.e(TAG, "Continue readFully: offset=%s, length=%s", offset + total, length - total);
        }

        return result;
    }

    private int skipInt(int length) throws IOException, InterruptedException {
        int skip = C.RESULT_END_OF_INPUT;

        fetchData();

        if (data == null) {
            return skip;
        }

        int toRead = Math.min(getRemaining(), length);
        skip = data.data.skip(toRead);

        if (skip > 0) {
            position += skip;
        }

        return skip;
    }

    private void skipFullyInt(int length) throws IOException, InterruptedException {
        int total = 0;

        while (true) {
            fetchData();

            if (data == null) {
                if (total > 0) {
                    throwEOFException();
                }
                break;
            }

            int toSkip = Math.min(getRemaining(), length - total);
            data.data.skipFully(toSkip);

            position += toSkip;

            if (toSkip == length - total) {
                break;
            }

            total += toSkip;

            Log.e(TAG, "Continue skipFully: length=%s", length - total);
        }
    }

    private boolean skipFullyInt(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        boolean result = false;
        int total = 0;

        while (true) {
            fetchData();

            if (data == null) {
                if (total > 0) {
                    throwEOFException();
                }
                break;
            }

            int toSkip = Math.min(getRemaining(), length - total);
            result = data.data.skipFully(toSkip, allowEndOfInput);

            if (!result) {
                throwEOFException();
            }

            position += toSkip;

            if (toSkip == length - total) {
                break;
            }

            total += toSkip;

            Log.e(TAG, "Continue skipFully: length=%s, allowEndOfInput=%s", length - total, allowEndOfInput);
        }

        return result;
    }

    private long getPositionInt() {
        return position;
    }

    private int getRemaining() {
        return data.contentLength - (int) getAdvance();
    }

    private long getAdvance() {
        return position - startPosition;
    }

    private static void throwEOFException() throws EOFException {
        throw new EOFException("EOF should never happened when reading SABR part");
    }

    // NOTE: The below methods not used!

    private boolean peekFullyInt(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private void peekFullyInt(byte[] target, int offset, int length) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private boolean advancePeekPositionInt(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private void advancePeekPositionInt(int length) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private void resetPeekPositionInt() {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private long getPeekPositionInt() {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }

    private <E extends Throwable> void setRetryPositionInt(long p, E e) throws E {
        throw new UnsupportedOperationException("This method shouldn't be called");
    }
}
