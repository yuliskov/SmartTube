package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;

/** One bounded and reorderable channel-to-live-candidate lookup path. */
public interface LiveCandidateStrategy {
    String name();

    Observable<Result> find(Query query);

    final class Query {
        public final String channelReference;
        public final String canonicalChannelId;

        public Query(String channelReference, String canonicalChannelId) {
            this.channelReference = channelReference;
            this.canonicalChannelId = canonicalChannelId;
        }
    }

    final class Result {
        public enum Status { SUCCESS, NETWORK_ERROR, RESOLUTION_ERROR }

        public final String strategy;
        public final Status status;
        public final List<LiveChannelResolver.Candidate> candidates;
        public final boolean authoritative;
        public final String reason;

        private Result(String strategy, Status status,
                       List<LiveChannelResolver.Candidate> candidates,
                       boolean authoritative, String reason) {
            this.strategy = strategy;
            this.status = status;
            this.candidates = candidates != null ? candidates : Collections.emptyList();
            this.authoritative = authoritative;
            this.reason = reason;
        }

        public static Result success(String strategy,
                                     List<LiveChannelResolver.Candidate> candidates,
                                     boolean authoritative) {
            return new Result(strategy, Status.SUCCESS, candidates, authoritative, null);
        }

        public static Result networkError(String strategy, String reason) {
            return new Result(strategy, Status.NETWORK_ERROR, null, false, reason);
        }

        public static Result resolutionError(String strategy, String reason) {
            return new Result(strategy, Status.RESOLUTION_ERROR, null, false, reason);
        }
    }
}
