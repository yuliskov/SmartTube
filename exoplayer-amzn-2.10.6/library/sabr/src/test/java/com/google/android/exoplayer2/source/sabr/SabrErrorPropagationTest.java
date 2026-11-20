package com.google.android.exoplayer2.source.sabr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.Period;
import com.google.android.exoplayer2.source.sabr.manifest.Representation;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.source.sabr.parser.SabrProcessor;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrBackoffError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrPoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrStreamError;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus.Status;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrErrorPropagationTest {

    @Test
    public void repeatedPoTokenFailuresPreserveFirstErrorInstance() {
        SabrProcessor processor = createProcessor();

        processor.processStreamProtectionStatus(StreamProtectionStatus.newBuilder()
                .setStatus(Status.ATTESTATION_REQUIRED)
                .build());
        SabrStreamError first = processor.getPendingFatalError();
        assertTrue(first instanceof SabrPoTokenError);

        processor.processStreamProtectionStatus(StreamProtectionStatus.newBuilder()
                .setStatus(Status.ATTESTATION_REQUIRED)
                .build());
        assertSame(first, processor.getPendingFatalError());

        processor.processStreamProtectionStatus(StreamProtectionStatus.newBuilder()
                .setStatus(Status.OK)
                .build());
        assertNull(processor.getPendingFatalError());
    }

    @Test
    public void getNextChunkStopsWhenSabrStreamHasFatalError() {
        Format video = Format.createVideoContainerFormat(
                "136", null, MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, null, null,
                1_000_000, 1280, 720, 25f, null, 0, 0);
        SabrManifest manifest = createManifest(video);
        SabrStream mockStream = mock(SabrStream.class);
        SabrPoTokenError expectedError = new SabrPoTokenError("PoToken error: INVALID");
        when(mockStream.getFatalError()).thenReturn(expectedError);

        SabrManifest spyManifest = new SabrManifest(
                C.TIME_UNSET, 120_000, C.TIME_UNSET, false, C.TIME_UNSET, C.TIME_UNSET,
                C.TIME_UNSET, C.TIME_UNSET, manifest.periods,
                "https://rr1---sn-test.googlevideo.com/videoplayback",
                "", null, "testVideo", ClientInfo.getDefaultInstance(), null) {
            @Override
            public SabrStream getSabrStream(int trackType) {
                return mockStream;
            }
        };

        DefaultSabrChunkSource source = new DefaultSabrChunkSource(
                new LoaderErrorThrower.Dummy(),
                spyManifest,
                0,
                new int[] {0},
                new FixedTrackSelection(new TrackGroup(video), 0),
                C.TRACK_TYPE_VIDEO,
                new ByteArrayDataSource(new byte[0]),
                0,
                1,
                false,
                Collections.emptyList(),
                null);

        ChunkHolder out = new ChunkHolder();
        source.getNextChunk(0, 0, Collections.emptyList(), out);

        assertNull(out.chunk);
        try {
            source.maybeThrowError();
            fail("Expected SabrPoTokenError");
        } catch (SabrPoTokenError e) {
            assertSame(expectedError, e);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void emptyVodResponsesWithPendingPoTokenErrorPropagateAfterThreshold() throws Exception {
        SabrStream mockStream = mock(SabrStream.class);
        SabrPoTokenError pendingError = new SabrPoTokenError("PoToken error: INVALID");
        when(mockStream.parse(any(ExtractorInput.class))).thenReturn(null);
        when(mockStream.isLive()).thenReturn(false);
        when(mockStream.getBackoffTimeMs()).thenReturn(0);
        when(mockStream.getFatalError()).thenReturn(null);
        when(mockStream.getPendingFatalError()).thenReturn(pendingError);

        SabrExtractorInput extractorInput = new SabrExtractorInput(mockStream);
        byte[] buf = new byte[16];

        for (int i = 0; i < 5; i++) {
            ExtractorInput raw = mock(ExtractorInput.class);
            extractorInput.init(raw);
            assertEquals(C.RESULT_END_OF_INPUT, extractorInput.read(buf, 0, buf.length));
            extractorInput.dispose();
        }

        ExtractorInput sixthRaw = mock(ExtractorInput.class);
        extractorInput.init(sixthRaw);
        try {
            extractorInput.read(buf, 0, buf.length);
            fail("Expected SabrPoTokenError on 6th consecutive empty VOD response");
        } catch (SabrPoTokenError e) {
            assertSame(pendingError, e);
        } finally {
            extractorInput.dispose();
        }
    }

    @Test
    public void emptyVodResponsesWithoutPoTokenErrorThrowSabrBackoffErrorAndResetOnMedia() throws Exception {
        SabrStream mockStream = mock(SabrStream.class);
        when(mockStream.isLive()).thenReturn(false);
        when(mockStream.getBackoffTimeMs()).thenReturn(0);
        when(mockStream.getFatalError()).thenReturn(null);
        when(mockStream.getPendingFatalError()).thenReturn(null);

        SabrExtractorInput extractorInput = new SabrExtractorInput(mockStream);
        byte[] buf = new byte[4];

        // 4 empty responses
        when(mockStream.parse(any(ExtractorInput.class))).thenReturn(null);
        for (int i = 0; i < 4; i++) {
            extractorInput.init(mock(ExtractorInput.class));
            assertEquals(C.RESULT_END_OF_INPUT, extractorInput.read(buf, 0, buf.length));
            extractorInput.dispose();
        }

        // 1 response with media resets the counter and clears any transient fatal error
        ExtractorInput dataRaw = mock(ExtractorInput.class);
        when(dataRaw.read(any(byte[].class), any(Integer.class), any(Integer.class))).thenReturn(4);
        MediaSegmentDataSabrPart dataPart = new MediaSegmentDataSabrPart(
                new FormatSelector("selected_video", false),
                FormatId.getDefaultInstance(),
                1, false, 10, 0, dataRaw, 4, 0);
        when(mockStream.parse(any(ExtractorInput.class))).thenReturn(dataPart, (MediaSegmentDataSabrPart) null);

        extractorInput.init(dataRaw);
        assertEquals(4, extractorInput.read(buf, 0, buf.length));
        assertEquals(C.RESULT_END_OF_INPUT, extractorInput.read(buf, 0, buf.length));
        extractorInput.dispose();
        verify(mockStream).clearFatalError();

        // Now it must take another 6 consecutive empty responses to throw SabrBackoffError
        when(mockStream.parse(any(ExtractorInput.class))).thenReturn(null);
        for (int i = 0; i < 5; i++) {
            extractorInput.init(mock(ExtractorInput.class));
            assertEquals(C.RESULT_END_OF_INPUT, extractorInput.read(buf, 0, buf.length));
            extractorInput.dispose();
        }

        extractorInput.init(mock(ExtractorInput.class));
        try {
            extractorInput.read(buf, 0, buf.length);
            fail("Expected SabrBackoffError on 6th consecutive empty VOD response");
        } catch (SabrBackoffError e) {
            assertNotNull(e.getMessage());
        } finally {
            extractorInput.dispose();
        }
    }

    @Test
    public void staleFatalErrorThrowsImmediatelyOnEmptyResponse() throws Exception {
        SabrStream mockStream = mock(SabrStream.class);
        SabrPoTokenError staleError = new SabrPoTokenError("PoToken error: INVALID");
        when(mockStream.parse(any(ExtractorInput.class))).thenReturn(null);
        when(mockStream.isLive()).thenReturn(false);
        when(mockStream.getBackoffTimeMs()).thenReturn(2_000);
        when(mockStream.getFatalError()).thenReturn(staleError);

        SabrExtractorInput extractorInput = new SabrExtractorInput(mockStream);
        extractorInput.init(mock(ExtractorInput.class));
        try {
            extractorInput.read(new byte[8], 0, 8);
            fail("Expected stale SabrPoTokenError immediately");
        } catch (SabrPoTokenError e) {
            assertSame(staleError, e);
        } finally {
            extractorInput.dispose();
        }
    }

    private static SabrProcessor createProcessor() {
        return new SabrProcessor(
                "",
                ClientInfo.getDefaultInstance(),
                5,
                100,
                0,
                "testPoToken",
                false,
                "testVideo",
                120_000);
    }

    private static SabrManifest createManifest(Format format) {
        Representation representation = Representation.newInstance(
                format,
                "https://rr1---sn-test.googlevideo.com/videoplayback",
                new SingleSegmentBase());
        AdaptationSet set = new AdaptationSet(
                0, C.TRACK_TYPE_VIDEO, Collections.singletonList(representation));
        Period period = new Period(null, 0, Collections.singletonList(set));
        return new SabrManifest(
                C.TIME_UNSET,
                120_000,
                C.TIME_UNSET,
                false,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                Collections.singletonList(period),
                "https://rr1---sn-test.googlevideo.com/videoplayback",
                "",
                null,
                "testVideo",
                ClientInfo.getDefaultInstance(),
                null);
    }
}
