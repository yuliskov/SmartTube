package com.google.android.exoplayer2.source.sabr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrPropagationError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrServerError;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrError;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;

import java.io.ByteArrayOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrStreamErrorHandlingTest {
    private static final String SABR_URL = "https://rr1---sn-primary.googlevideo.com/videoplayback";

    @Test
    public void sabrErrorPartIsPropagatedWithTypeAndAction() {
        SabrStream stream = createStream();
        byte[] response = umpPart(UMPPartId.SABR_ERROR, SabrError.newBuilder()
                .setType("sabr.no_video_selected")
                .setAction(2)
                .build()
                .toByteArray());

        try {
            stream.parse(input(response));
            fail("SabrError part must not be ignored");
        } catch (SabrServerError e) {
            // Must be a propagation error, otherwise SabrMatroskaAdapter swallows it and the request is repeated in a loop
            assertTrue(e instanceof SabrPropagationError);
            assertEquals("sabr.no_video_selected", e.type);
            assertEquals(2, e.action);
        }
    }

    private static SabrStream createStream() {
        return new SabrStream(
                SABR_URL,
                "",
                ClientInfo.newBuilder().build(),
                -1,
                -1,
                -1,
                "dGVzdA",
                false,
                "videoId",
                60_000);
    }

    /** Single-byte UMP varints are enough for part ids and payloads below 128 bytes. */
    private static byte[] umpPart(int partId, byte[] payload) {
        if (partId >= 128 || payload.length >= 128) {
            throw new IllegalArgumentException("Test helper only supports single-byte varints");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(partId);
        out.write(payload.length);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private static FakeExtractorInput input(byte[] data) {
        return new FakeExtractorInput.Builder().setData(data).build();
    }
}
