package com.google.android.exoplayer2.source.sabr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkOutput;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.Period;
import com.google.android.exoplayer2.source.sabr.manifest.RangedUri;
import com.google.android.exoplayer2.source.sabr.manifest.Representation;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class DefaultSabrChunkSourceTest {
    private static final String CAPTION_URL =
            "https://www.youtube.com/api/timedtext?v=test&lang=en&fmt=vtt";
    private static final String SABR_URL =
            "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                    + "mn=sn-primary%2Csn-secondary";
    private static final long DURATION_MS = 60_000;
    private static final byte[] CAPTIONS =
            Util.getUtf8Bytes("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nTest captions\n");

    @Test
    public void webvttUsesCaptionGetInsteadOfSabrContainer() {
        assertRawTextRequest(MimeTypes.TEXT_VTT);
    }

    @Test
    public void ttmlUsesCaptionGetInsteadOfSabrContainer() {
        assertRawTextRequest(MimeTypes.APPLICATION_TTML);
    }

    @Test
    public void textWithoutContainerMimeTypeUsesCaptionGet() {
        assertRawTextRequest(null);
    }

    @Test
    public void enablingSubtitlesMidPlaybackKeepsOriginalCueTimestamps() {
        SabrManifest manifest = createManifest(createTextFormat(MimeTypes.TEXT_VTT), DURATION_MS);
        DefaultSabrChunkSource source = createSource(manifest);
        ChunkHolder out = new ChunkHolder();

        source.getNextChunk(30_000_000, 30_000_000, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof SingleSampleMediaChunk);
        assertEquals(0, out.chunk.startTimeUs);
        assertEquals(DURATION_MS * 1_000, out.chunk.endTimeUs);
    }

    @Test
    public void loadedCaptionFileEndsStreamEvenWhenDurationIsUnknown() {
        SabrManifest manifest = createManifest(createTextFormat(MimeTypes.TEXT_VTT), C.TIME_UNSET);
        DefaultSabrChunkSource source = createSource(manifest);
        ChunkHolder out = new ChunkHolder();
        source.getNextChunk(0, 0, Collections.emptyList(), out);
        MediaChunk captionChunk = (MediaChunk) out.chunk;
        out.clear();

        source.getNextChunk(0, C.TIME_UNSET, Collections.singletonList(captionChunk), out);

        assertTrue(out.endOfStream);
        assertNull(out.chunk);
    }

    @Test
    public void seekingWithEmptyQueueReloadsTheWholeCaptionFile() {
        SabrManifest manifest = createManifest(createTextFormat(MimeTypes.TEXT_VTT), DURATION_MS);
        DefaultSabrChunkSource source = createSource(manifest);
        ChunkHolder out = new ChunkHolder();
        source.getNextChunk(30_000_000, 30_000_000, Collections.emptyList(), out);
        out.clear();

        source.getNextChunk(5_000_000, 5_000_000, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof SingleSampleMediaChunk);
        assertEquals(CAPTION_URL, out.chunk.dataSpec.uri.toString());
        assertEquals(0, out.chunk.startTimeUs);
        assertFalse(out.endOfStream);
    }

    @Test
    public void loadsCaptionBytesAsOneTextSample() throws Exception {
        Format format = createTextFormat(MimeTypes.TEXT_VTT);
        DefaultSabrChunkSource source = createSource(createManifest(format, DURATION_MS));
        ChunkHolder out = new ChunkHolder();
        source.getNextChunk(0, 0, Collections.emptyList(), out);
        assertTrue(out.chunk instanceof SingleSampleMediaChunk);
        SingleSampleMediaChunk chunk = (SingleSampleMediaChunk) out.chunk;
        SampleQueue sampleQueue = new SampleQueue(new DefaultAllocator(true, 1024));
        chunk.init(new BaseMediaChunkOutput(
                new int[] {C.TRACK_TYPE_TEXT}, new SampleQueue[] {sampleQueue}));

        chunk.load();
        source.onChunkLoadCompleted(chunk);

        assertTrue(chunk.isLoadCompleted());
        assertEquals(1, sampleQueue.getWriteIndex());
        FormatHolder formatHolder = new FormatHolder();
        DecoderInputBuffer buffer =
                new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
        assertEquals(C.RESULT_FORMAT_READ, sampleQueue.read(formatHolder, buffer, true, true, 0));
        assertEquals(MimeTypes.TEXT_VTT, formatHolder.format.sampleMimeType);
        assertEquals(C.RESULT_BUFFER_READ, sampleQueue.read(formatHolder, buffer, false, true, 0));
        assertEquals(0, buffer.timeUs);
        buffer.flip();
        byte[] actual = new byte[buffer.data.remaining()];
        buffer.data.get(actual);
        assertArrayEquals(CAPTIONS, actual);
        out.clear();
        source.getNextChunk(0, chunk.endTimeUs, Collections.singletonList(chunk), out);
        assertTrue(out.endOfStream);
        assertNull(out.chunk);
        sampleQueue.reset();
    }

    @Test
    public void captionRequestIncludesVisitorCookieWithoutSabrHeaders() {
        SabrManifest manifest = createManifest(
                DURATION_MS, "VISITOR_INFO1_LIVE=test",
                createAdaptationSet(createTextFormat(MimeTypes.TEXT_VTT), C.TRACK_TYPE_TEXT));
        ChunkHolder out = new ChunkHolder();

        createSource(manifest).getNextChunk(0, 0, Collections.emptyList(), out);

        assertEquals(Collections.singletonMap("Cookie", "VISITOR_INFO1_LIVE=test"),
                out.chunk.dataSpec.httpRequestHeaders);
        assertEquals(CAPTION_URL, out.chunk.dataSpec.uri.toString());
    }

    @Test
    public void enablingCaptionsDoesNotAdvertiseThemAsSabrFormats() {
        Format video = createVideoFormat();
        SabrManifest manifest = createManifest(
                DURATION_MS, null,
                createAdaptationSet(video, C.TRACK_TYPE_VIDEO),
                createAdaptationSet(createTextFormat(MimeTypes.TEXT_VTT), C.TRACK_TYPE_TEXT));
        DefaultSabrChunkSource videoSource = createSource(manifest, 0);
        createSource(manifest, 1);
        ChunkHolder out = new ChunkHolder();

        videoSource.getNextChunk(0, 0, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof ContainerMediaChunk);
        assertEquals(0, manifest.createVideoPlaybackAbrRequest(C.TRACK_TYPE_VIDEO, true)
                .getPreferredSubtitleFormatIdsCount());
        assertEquals(1, manifest.createVideoPlaybackAbrRequest(C.TRACK_TYPE_VIDEO, true)
                .getPreferredVideoFormatIdsCount());
    }

    @Test
    public void captionNetworkErrorsDoNotSwitchSabrCdn() {
        SabrManifest manifest = spy(createManifest(createTextFormat(MimeTypes.TEXT_VTT), DURATION_MS));
        DefaultSabrChunkSource source = createSource(manifest);
        ChunkHolder out = new ChunkHolder();
        source.getNextChunk(0, 0, Collections.emptyList(), out);

        boolean handled = source.onChunkLoadError(
                out.chunk, true, new IOException("caption connection failed"), C.TIME_UNSET);

        assertFalse(handled);
        verify(manifest, never()).maybeUseNextCdn(anyString());
    }

    @Test
    public void videoStillUsesSabrContainerPost() {
        assertSabrRequest(createVideoFormat(), C.TRACK_TYPE_VIDEO);
    }

    @Test
    public void audioStillUsesSabrContainerPost() {
        Format audio = Format.createAudioContainerFormat(
                "140", null, MimeTypes.AUDIO_MP4, MimeTypes.AUDIO_AAC, null, null,
                128_000, 2, 44_100, null, 0, 0, "en");
        assertSabrRequest(audio, C.TRACK_TYPE_AUDIO);
    }

    @Test
    public void videoInitializationStillUsesSabrPost() {
        Representation representation = Representation.newInstance(
                createVideoFormat(), SABR_URL,
                new SingleSegmentBase(new RangedUri(null, 0, 100), 1, 0, 0, 0));
        SabrManifest manifest = createManifest(
                DURATION_MS, null,
                new AdaptationSet(0, C.TRACK_TYPE_VIDEO, Collections.singletonList(representation)));
        ChunkHolder out = new ChunkHolder();

        createSource(manifest).getNextChunk(0, 0, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof InitializationChunk);
        assertEquals(DataSpec.HTTP_METHOD_POST, out.chunk.dataSpec.httpMethod);
        assertEquals(SABR_URL + "&rn=0", out.chunk.dataSpec.uri.toString());
    }

    private static void assertSabrRequest(Format format, int trackType) {
        SabrManifest manifest = createManifest(
                DURATION_MS, "VISITOR_INFO1_LIVE=test", createAdaptationSet(format, trackType));
        ChunkHolder out = new ChunkHolder();

        createSource(manifest).getNextChunk(0, 0, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof ContainerMediaChunk);
        assertEquals(DataSpec.HTTP_METHOD_POST, out.chunk.dataSpec.httpMethod);
        assertEquals(SABR_URL + "&rn=0", out.chunk.dataSpec.uri.toString());
        assertEquals("application/x-protobuf", out.chunk.dataSpec.httpRequestHeaders.get("Content-Type"));
        assertFalse(out.chunk.dataSpec.httpRequestHeaders.containsKey("Cookie"));
        assertTrue(out.chunk.dataSpec.httpBody.length > 0);
    }

    private static void assertRawTextRequest(String containerMimeType) {
        SabrManifest manifest = createManifest(createTextFormat(containerMimeType), DURATION_MS);
        DefaultSabrChunkSource source = createSource(manifest);
        ChunkHolder out = new ChunkHolder();

        source.getNextChunk(0, 0, Collections.emptyList(), out);

        assertTrue(out.chunk instanceof SingleSampleMediaChunk);
        assertEquals(CAPTION_URL, out.chunk.dataSpec.uri.toString());
        assertEquals(DataSpec.HTTP_METHOD_GET, out.chunk.dataSpec.httpMethod);
        assertNull(out.chunk.dataSpec.httpBody);
        assertTrue(out.chunk.dataSpec.httpRequestHeaders.isEmpty());
        assertEquals(-1, manifest.getSabrRequestNumber());
    }

    private static Format createTextFormat(String containerMimeType) {
        return Format.createTextContainerFormat(
                "en", null, containerMimeType,
                containerMimeType == null ? MimeTypes.TEXT_VTT : containerMimeType,
                null, Format.NO_VALUE, 0, C.ROLE_FLAG_SUBTITLE, "en");
    }

    private static SabrManifest createManifest(Format format, long durationMs) {
        return createManifest(durationMs, null, createAdaptationSet(format, C.TRACK_TYPE_TEXT));
    }

    private static Format createVideoFormat() {
        return Format.createVideoContainerFormat(
                "137", null, MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, null, null,
                1_000_000, 1920, 1080, 30, null, 0, 0);
    }

    private static AdaptationSet createAdaptationSet(Format format, int trackType) {
        Representation representation =
                Representation.newInstance(format,
                        trackType == C.TRACK_TYPE_TEXT ? CAPTION_URL : SABR_URL,
                        new SingleSegmentBase());
        return new AdaptationSet(trackType, trackType, Collections.singletonList(representation));
    }

    private static SabrManifest createManifest(
            long durationMs, String visitorCookie, AdaptationSet... adaptationSets) {
        Period period = new Period("test", 0, Arrays.asList(adaptationSets));
        return new SabrManifest(
                C.TIME_UNSET, durationMs, 1_500, false,
                C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
                Collections.singletonList(period), SABR_URL, "", null, "test",
                ClientInfo.getDefaultInstance(), visitorCookie);
    }

    private static DefaultSabrChunkSource createSource(SabrManifest manifest) {
        return createSource(manifest, 0);
    }

    private static DefaultSabrChunkSource createSource(SabrManifest manifest, int adaptationSetIndex) {
        AdaptationSet adaptationSet = manifest.getPeriod(0).adaptationSets.get(adaptationSetIndex);
        Format format = adaptationSet.representations.get(0).format;
        return new DefaultSabrChunkSource(
                new LoaderErrorThrower.Dummy(), manifest, 0, new int[] {adaptationSetIndex},
                new FixedTrackSelection(new TrackGroup(format), 0), adaptationSet.type,
                new ByteArrayDataSource(CAPTIONS), 0, 1, false, Collections.emptyList(), null);
    }
}
