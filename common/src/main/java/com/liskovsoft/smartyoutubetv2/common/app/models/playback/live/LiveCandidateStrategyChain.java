package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

/** Executes strategies serially so ordering and request budgets remain deterministic. */
final class LiveCandidateStrategyChain {
    private final List<LiveCandidateStrategy> strategies;
    private final long timeoutSeconds;

    LiveCandidateStrategyChain(List<LiveCandidateStrategy> strategies, long timeoutSeconds) {
        this.strategies = strategies;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    Observable<List<LiveCandidateStrategy.Result>> execute(LiveCandidateStrategy.Query query) {
        return Observable.fromIterable(strategies)
                .concatMap(strategy -> strategy.find(query)
                        .timeout(timeoutSeconds, TimeUnit.SECONDS)
                        .take(1)
                        .switchIfEmpty(Observable.just(LiveCandidateStrategy.Result.resolutionError(
                                strategy.name(), "Strategy completed without a result")))
                        .onErrorReturn(error -> LiveCandidateStrategy.Result.networkError(
                                strategy.name(), "Strategy request failed")))
                .toList()
                .toObservable();
    }
}
