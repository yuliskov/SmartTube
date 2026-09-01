package com.google.android.exoplayer2.source.sabr.manifest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.PlaybackCookie;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.source.sabr.session.SabrSessionCoordinator;

import org.junit.Test;

import java.util.Collections;

public class SabrManifestSchedulingTest {
    @Test
    public void serverBackoffDoesNotSuppressVodFollowUpRequest() {
        SabrManifest manifest = createManifest(false);
        manifest.getSessionCoordinator().getScheduler().onPolicy(
                NextRequestPolicy.newBuilder().setBackoffTimeMs(4_000).build());

        assertTrue(manifest.shouldIssueRequest(C.TRACK_TYPE_VIDEO, 0));
    }

    @Test
    public void serverBackoffStillGatesDynamicManifestRequest() {
        SabrManifest manifest = createManifest(true);
        manifest.getSessionCoordinator().getScheduler().onPolicy(
                NextRequestPolicy.newBuilder().setBackoffTimeMs(4_000).build());

        assertFalse(manifest.shouldIssueRequest(C.TRACK_TYPE_VIDEO, 0));
    }

    @Test
    public void vodKeepsResponseContextTrackLocal() {
        SabrManifest manifest = createManifest(false);
        SabrSessionCoordinator audio = manifest.getSabrStream(C.TRACK_TYPE_AUDIO)
                .getSessionCoordinator();
        SabrSessionCoordinator video = manifest.getSabrStream(C.TRACK_TYPE_VIDEO)
                .getSessionCoordinator();

        audio.processNextRequestPolicy(policyWithCookie());

        assertNotSame(audio, video);
        assertTrue(audio.getNextRequestPolicy().hasPlaybackCookie());
        assertFalse(video.getNextRequestPolicy().hasPlaybackCookie());
    }

    @Test
    public void dynamicManifestSharesSessionContextAcrossTracks() {
        SabrManifest manifest = createManifest(true);
        SabrSessionCoordinator audio = manifest.getSabrStream(C.TRACK_TYPE_AUDIO)
                .getSessionCoordinator();
        SabrSessionCoordinator video = manifest.getSabrStream(C.TRACK_TYPE_VIDEO)
                .getSessionCoordinator();

        assertSame(manifest.getSessionCoordinator(), audio);
        assertSame(audio, video);
    }

    private static NextRequestPolicy policyWithCookie() {
        return NextRequestPolicy.newBuilder()
                .setPlaybackCookie(PlaybackCookie.newBuilder().setResolution(999_999).build())
                .build();
    }

    private static SabrManifest createManifest(boolean dynamic) {
        return new SabrManifest(
                C.TIME_UNSET,
                120_000,
                1_500,
                dynamic,
                dynamic ? 1_000 : C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                Collections.emptyList(),
                "https://example.test/sabr",
                "",
                null,
                "video-id",
                ClientInfo.getDefaultInstance(),
                false);
    }
}
