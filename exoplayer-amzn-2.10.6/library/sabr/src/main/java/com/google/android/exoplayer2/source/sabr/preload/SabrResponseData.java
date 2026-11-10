package com.google.android.exoplayer2.source.sabr.preload;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Body of a recorded SABR response.<br/>
 * Kept as a list of small blocks filled right from the network: no growing buffer and no final copy
 * (a single array needs up to 3x of the response size while it's being read), and no need
 * for a big continuous block of memory.
 */
final class SabrResponseData {
    private static final int BLOCK_SIZE = 64 * 1024;
    private final List<byte[]> blocks = new ArrayList<>();
    private int length;

    private SabrResponseData() {
    }

    /**
     * Read the opened source to the end
     * @param maxBytes fail if the response is bigger
     */
    static SabrResponseData readFrom(DataSource source, int maxBytes) throws IOException {
        SabrResponseData result = new SabrResponseData();
        byte[] block = null;
        int blockPosition = BLOCK_SIZE;

        while (true) {
            if (blockPosition == BLOCK_SIZE) { // the block is full
                if (result.length >= maxBytes) {
                    throw new IOException("The response is too big to keep in memory");
                }

                block = new byte[BLOCK_SIZE];
                result.blocks.add(block);
                blockPosition = 0;
            }

            int read = source.read(block, blockPosition, BLOCK_SIZE - blockPosition);

            if (read == C.RESULT_END_OF_INPUT) {
                break;
            }

            blockPosition += read;
            result.length += read;
        }

        return result;
    }

    int length() {
        return length;
    }

    /**
     * @return number of bytes read or {@link C#RESULT_END_OF_INPUT}
     */
    int read(int position, byte[] buffer, int offset, int readLength) {
        if (readLength == 0) {
            return 0;
        }

        if (position >= length) {
            return C.RESULT_END_OF_INPUT;
        }

        int blockPosition = position % BLOCK_SIZE;
        // Up to the end of the block. The caller asks for the rest with the next call.
        int count = Math.min(readLength, Math.min(BLOCK_SIZE - blockPosition, length - position));
        System.arraycopy(blocks.get(position / BLOCK_SIZE), blockPosition, buffer, offset, count);

        return count;
    }

    /**
     * To parse the response the same way as a response from the network
     */
    DataSource createDataSource() {
        return new DataSource() {
            private int position;

            @Override
            public void addTransferListener(TransferListener transferListener) {
                // NOP
            }

            @Override
            public long open(DataSpec dataSpec) {
                position = 0;
                return length;
            }

            @Override
            public int read(byte[] buffer, int offset, int readLength) {
                int read = SabrResponseData.this.read(position, buffer, offset, readLength);

                if (read > 0) {
                    position += read;
                }

                return read;
            }

            @Nullable
            @Override
            public Uri getUri() {
                return Uri.EMPTY;
            }

            @Override
            public void close() {
                // NOP
            }
        };
    }
}
