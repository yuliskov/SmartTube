package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LivePlayerResponseRetryPolicyTest {
    @Test public void rotatesTwiceThenStopsAfterThreePlayerResponses() {
        LivePlayerResponseRetryPolicy policy = new LivePlayerResponseRetryPolicy(3);

        policy.onPlayerResponse(11);
        assertTrue(policy.tryRetireForbiddenGeneration(11));
        policy.onPlayerResponse(12);
        assertTrue(policy.tryRetireForbiddenGeneration(12));
        policy.onPlayerResponse(13);

        assertFalse(policy.tryRetireForbiddenGeneration(13));
        assertEquals(3, policy.getPlayerResponseCount());
    }

    @Test public void duplicateForbiddenErrorCannotRotateTwice() {
        LivePlayerResponseRetryPolicy policy = new LivePlayerResponseRetryPolicy(3);

        policy.onPlayerResponse(21);

        assertTrue(policy.tryRetireForbiddenGeneration(21));
        assertFalse(policy.tryRetireForbiddenGeneration(21));
    }

    @Test public void staleGenerationCannotRetireCurrentResponse() {
        LivePlayerResponseRetryPolicy policy = new LivePlayerResponseRetryPolicy(3);

        policy.onPlayerResponse(31);
        assertTrue(policy.tryRetireForbiddenGeneration(31));
        policy.onPlayerResponse(32);

        assertFalse(policy.tryRetireForbiddenGeneration(31));
        assertTrue(policy.tryRetireForbiddenGeneration(32));
    }

    @Test public void resetStartsANewLogicalVideoBudget() {
        LivePlayerResponseRetryPolicy policy = new LivePlayerResponseRetryPolicy(2);

        policy.onPlayerResponse(41);
        assertTrue(policy.tryRetireForbiddenGeneration(41));
        policy.onPlayerResponse(42);
        assertFalse(policy.tryRetireForbiddenGeneration(42));

        policy.reset();
        policy.onPlayerResponse(51);

        assertTrue(policy.tryRetireForbiddenGeneration(51));
        assertEquals(1, policy.getPlayerResponseCount());
    }
}
