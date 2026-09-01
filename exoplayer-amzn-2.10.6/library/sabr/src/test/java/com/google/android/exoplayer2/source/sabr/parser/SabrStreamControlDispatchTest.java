package com.google.android.exoplayer2.source.sabr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.parser.ump.UmpTestFixtures;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrSeek;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;
import com.google.android.exoplayer2.source.sabr.session.SabrSessionCoordinator;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;

import org.junit.Test;

public class SabrStreamControlDispatchTest {
    @Test
    public void liveMetadataAndSabrSeekReachSharedSessionHandlers() {
        SabrSessionCoordinator coordinator = coordinator();
        SabrStream stream = stream(coordinator);
        LiveMetadata live = LiveMetadata.newBuilder()
                .setVideoId("video-id")
                .setHeadSequenceNumber(10)
                .setHeadSequenceTimeMs(100_000)
                .setMinSeekableTimeTicks(40_000)
                .setMinSeekableTimescale(1_000)
                .setMaxSeekableTimeTicks(100_000)
                .setMaxSeekableTimescale(1_000)
                .build();
        SabrSeek seek = SabrSeek.newBuilder()
                .setSeekMediaTime(50_000)
                .setSeekMediaTimescale(1_000)
                .build();
        FakeExtractorInput input = new FakeExtractorInput.Builder().setData(
                UmpTestFixtures.concat(
                        UmpTestFixtures.frame(UMPPartId.LIVE_METADATA, live.toByteArray()),
                        UmpTestFixtures.frame(UMPPartId.SABR_SEEK, seek.toByteArray())))
                .build();

        assertNull(stream.parse(input));

        assertEquals(100_000,
                coordinator.snapshot().liveWindow.getLiveHeadMs());
        assertTrue(coordinator.getGeneration() > 1);
    }

    @Test
    public void futureHighNumberedPartIsSkippedWithoutFailure() {
        SabrSessionCoordinator coordinator = coordinator();
        SabrStream stream = stream(coordinator);
        LiveMetadata live = LiveMetadata.newBuilder()
                .setHeadSequenceTimeMs(10_000)
                .setMinSeekableTimeTicks(0)
                .setMinSeekableTimescale(1_000)
                .build();
        FakeExtractorInput input = new FakeExtractorInput.Builder().setData(
                UmpTestFixtures.concat(
                        UmpTestFixtures.frame(10_000, new byte[] {1, 2, 3}),
                        UmpTestFixtures.frame(UMPPartId.LIVE_METADATA, live.toByteArray())))
                .build();

        assertNull(stream.parse(input));
        assertEquals(10_000, coordinator.snapshot().liveWindow.getLiveHeadMs());
    }

    private static SabrStream stream(SabrSessionCoordinator coordinator) {
        return new SabrStream(
                "https://media.example.test/sabr?id=broadcast&source=yt_live_broadcast",
                "",
                StreamerContext.ClientInfo.newBuilder().build(),
                5, 100, 0, null, false, "video-id", -1, coordinator);
    }

    private static SabrSessionCoordinator coordinator() {
        return new SabrSessionCoordinator(
                "https://media.example.test/sabr?id=broadcast&source=yt_live_broadcast",
                null, "video-id", false, () -> 1_000);
    }
}
