package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;

import static org.junit.Assert.assertEquals;

public class LiveCandidateStrategyChainTest {
    @Test public void strategiesRunInConfiguredOrderAfterFailure() {
        List<String> calls = new ArrayList<>();
        LiveCandidateStrategy first = strategy("LIVE_TAB", calls,
                Observable.error(new IllegalStateException("network")));
        LiveCandidateStrategy second = strategy("LIVE_ROUTE", calls,
                Observable.just(LiveCandidateStrategy.Result.success(
                        "LIVE_ROUTE", Collections.emptyList(), false)));

        List<LiveCandidateStrategy.Result> results = new LiveCandidateStrategyChain(
                Arrays.asList(first, second), 1)
                .execute(new LiveCandidateStrategy.Query("@handle", "channel"))
                .blockingFirst();

        assertEquals(Arrays.asList("LIVE_TAB", "LIVE_ROUTE"), calls);
        assertEquals(LiveCandidateStrategy.Result.Status.NETWORK_ERROR, results.get(0).status);
        assertEquals(LiveCandidateStrategy.Result.Status.SUCCESS, results.get(1).status);
    }

    private static LiveCandidateStrategy strategy(
            String name, List<String> calls,
            Observable<LiveCandidateStrategy.Result> result) {
        return new LiveCandidateStrategy() {
            @Override public String name() { return name; }

            @Override
            public Observable<Result> find(Query query) {
                calls.add(name);
                return result;
            }
        };
    }
}
