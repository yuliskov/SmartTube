package com.google.android.exoplayer2.source.sabr.parser.misc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrExtractorInputTest {
    /** UMP framing that precedes the MEDIA payload in each response of this test. */
    private static final int HEADER_LENGTH = 3;
    private static final int CANCELED_PAYLOAD_LENGTH = 10;
    private static final int NEXT_PAYLOAD_LENGTH = 8;

    @Test
    public void chunkCanceledMidReadDoesNotFeedTheNextResponseToItsMediaPart() throws Exception {
        // The chunks of a track share one DataSource. The first response is canceled after 4 of its
        // 10 payload bytes, so the next bytes that DataSource serves belong to the second response.
        byte[] responses = Util.getUtf8Bytes("hhh" + "oooo" + "HHH" + "nnnnnnnn");
        ByteArrayDataSource dataSource = new ByteArrayDataSource(responses);
        dataSource.open(new DataSpec(Uri.EMPTY));
        ExtractorInput canceledChunk = new DefaultExtractorInput(dataSource, 0, C.LENGTH_UNSET);
        ExtractorInput nextChunk = new DefaultExtractorInput(dataSource, 0, C.LENGTH_UNSET);

        // Each response holds a header followed by a single MEDIA part.
        SabrStream sabrStream = mock(SabrStream.class);
        when(sabrStream.parse(any())).thenAnswer(invocation -> {
            ExtractorInput chunk = invocation.getArgument(0);
            chunk.skipFully(HEADER_LENGTH);
            int contentLength = chunk == canceledChunk ? CANCELED_PAYLOAD_LENGTH : NEXT_PAYLOAD_LENGTH;
            return new MediaSegmentDataSabrPart(null, null, 0, false, 0, 0, chunk, contentLength, 0);
        });

        SabrExtractorInput input = new SabrExtractorInput(sabrStream);
        input.init(canceledChunk);
        assertEquals("oooo", readFully(input, 4));

        // A seek cancels the chunk without interrupting a read, so the adapter doesn't dispose the input.
        input.init(nextChunk);

        assertEquals("nnnnnnnn", readFully(input, NEXT_PAYLOAD_LENGTH));
    }

    private static String readFully(ExtractorInput input, int length) throws IOException, InterruptedException {
        byte[] buffer = new byte[length];
        input.readFully(buffer, 0, length);
        return Util.fromUtf8Bytes(buffer);
    }
}
