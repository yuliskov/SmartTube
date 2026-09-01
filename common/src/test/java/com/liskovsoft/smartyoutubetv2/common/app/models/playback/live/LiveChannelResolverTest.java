package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;

public class LiveChannelResolverTest {
    private static final LiveInput CHANNEL = new YouTubeLiveInputParser().parse("@SmartTubeApp");

    @Test public void classifiesActiveLiveBeforeUpcoming() {
        LiveChannelResolver resolver = resolver(Observable.just(LiveChannelResolver.ProviderResult.available(
                Arrays.asList(candidate("upcoming", false, true), candidate("active-live", true, false)))));
        LiveChannelResolution result = resolver.resolve(CHANNEL, false).blockingFirst();
        assertEquals(LiveChannelResolution.Status.LIVE, result.status);
        assertEquals("active-live", result.videoId);
    }

    @Test public void classifiesUpcomingWhenNoActiveLive() {
        LiveChannelResolution result = resolver(Observable.just(LiveChannelResolver.ProviderResult.available(
                Collections.singletonList(candidate("upcoming", false, true)))))
                .resolve(CHANNEL, false).blockingFirst();
        assertEquals(LiveChannelResolution.Status.UPCOMING, result.status);
    }

    @Test public void classifiesOfflineChannel() {
        LiveChannelResolution result = resolver(Observable.just(LiveChannelResolver.ProviderResult.available(
                Collections.emptyList()))).resolve(CHANNEL, false).blockingFirst();
        assertEquals(LiveChannelResolution.Status.OFFLINE, result.status);
    }

    @Test public void preservesTypedUnavailableResult() {
        LiveChannelResolution result = resolver(Observable.just(
                LiveChannelResolver.ProviderResult.unavailable("restricted")))
                .resolve(CHANNEL, false).blockingFirst();
        assertEquals(LiveChannelResolution.Status.UNAVAILABLE, result.status);
        assertEquals("restricted", result.reason);
    }

    @Test public void preservesTypedRestrictedResult() {
        LiveChannelResolution result = resolver(Observable.just(
                LiveChannelResolver.ProviderResult.restricted("sign in required")))
                .resolve(CHANNEL, false).blockingFirst();
        assertEquals(LiveChannelResolution.Status.RESTRICTED, result.status);
        assertEquals("sign in required", result.reason);
    }

    @Test public void disposalCancelsResolutionDelivery() {
        PublishSubject<LiveChannelResolver.ProviderResult> subject = PublishSubject.create();
        TestObserver<LiveChannelResolution> observer = resolver(subject).resolve(CHANNEL, false).test();
        observer.dispose();
        subject.onNext(LiveChannelResolver.ProviderResult.available(
                Collections.singletonList(candidate("late-live", true, false))));
        observer.assertNoValues();
    }

    @Test public void networkFailureIsTypedAndNeverCachedAsOffline() {
        AtomicInteger calls = new AtomicInteger();
        LiveChannelResolver resolver = new LiveChannelResolver(reference -> {
            calls.incrementAndGet();
            return Observable.error(new IllegalStateException("network"));
        });

        assertEquals(LiveChannelResolution.Status.NETWORK_ERROR,
                resolver.resolve(CHANNEL, false).blockingFirst().status);
        assertEquals(LiveChannelResolution.Status.NETWORK_ERROR,
                resolver.resolve(CHANNEL, false).blockingFirst().status);
        assertEquals(2, calls.get());
    }

    @Test public void cacheExpiresAndForceRefreshBypassesIt() {
        long[] now = {100};
        AtomicInteger calls = new AtomicInteger();
        LiveChannelResolver resolver = new LiveChannelResolver(reference -> {
            calls.incrementAndGet();
            return Observable.just(LiveChannelResolver.ProviderResult.available(
                    Collections.singletonList(candidate("active-live", true, false))));
        }, () -> now[0], 50);
        resolver.resolve(CHANNEL, false).blockingFirst();
        resolver.resolve(CHANNEL, false).blockingFirst();
        assertEquals(1, calls.get());
        resolver.resolve(CHANNEL, true).blockingFirst();
        assertEquals(2, calls.get());
        now[0] = 151;
        resolver.resolve(CHANNEL, false).blockingFirst();
        assertEquals(3, calls.get());
    }

    private static LiveChannelResolver resolver(Observable<LiveChannelResolver.ProviderResult> response) {
        return new LiveChannelResolver(reference -> response);
    }

    private static LiveChannelResolver.Candidate candidate(String id, boolean live, boolean upcoming) {
        return new LiveChannelResolver.Candidate(id, "channel", "Channel", "Title", null, live, upcoming);
    }
}
