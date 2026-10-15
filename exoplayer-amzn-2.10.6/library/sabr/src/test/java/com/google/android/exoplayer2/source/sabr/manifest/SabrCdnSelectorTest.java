package com.google.android.exoplayer2.source.sabr.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class SabrCdnSelectorTest {
    private static final String PRIMARY_URL =
            "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                    + "mn=sn-primary%2Csn-secondary&sig=signed";
    private static final String SECONDARY_URL =
            "https://rr2---sn-secondary.googlevideo.com/videoplayback?"
                    + "mn=sn-primary%2Csn-secondary&sig=signed";

    @Before
    public void setUp() {
        SabrCdnSelector.clearFailedNetworks();
    }

    @Test
    public void startsWithOriginalUrlAndAdvancesOnce() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(PRIMARY_URL, selector.getCurrentUrl());
        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(SECONDARY_URL, selector.getCurrentUrl());
        assertFalse(selector.maybeAdvance(SECONDARY_URL));
    }

    @Test
    public void staleFailureRemainsRetryableAfterAnotherTrackAdvances() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(SECONDARY_URL, selector.getCurrentUrl());
    }

    @Test
    public void advancesThroughAllAdvertisedNetworksWithoutWrapping() {
        String primary = "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary%2Csn-tertiary&sig=signed";
        String secondary = "https://rr2---sn-secondary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary%2Csn-tertiary&sig=signed";
        String tertiary = "https://rr3---sn-tertiary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary%2Csn-tertiary&sig=signed";
        SabrCdnSelector selector = new SabrCdnSelector(primary);

        assertTrue(selector.maybeAdvance(primary));
        assertEquals(secondary, selector.getCurrentUrl());
        assertTrue(selector.maybeAdvance(secondary));
        assertEquals(tertiary, selector.getCurrentUrl());
        assertFalse(selector.maybeAdvance(tertiary));
        assertEquals(tertiary, selector.getCurrentUrl());
    }

    @Test
    public void unrelatedHostDoesNotChangeSelection() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertFalse(selector.maybeAdvance("https://redirect.googlevideo.com/videoplayback"));
        assertEquals(PRIMARY_URL, selector.getCurrentUrl());
    }

    @Test
    public void malformedCandidateIsIgnoredDuringFailureHandling() {
        String malformedUrl = "https://rr1---sn-primary.googlevideo.com/%";
        SabrCdnSelector selector = new SabrCdnSelector(malformedUrl);

        assertFalse(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(malformedUrl, selector.getCurrentUrl());
    }

    @Test
    public void urlWithoutMatchingNetworkHasNoFallback() {
        String url = "https://rr1---sn-other.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary";
        SabrCdnSelector selector = new SabrCdnSelector(url);

        assertFalse(selector.maybeAdvance(url));
        assertEquals(url, selector.getCurrentUrl());
    }

    @Test
    public void newSelectorSkipsRecentlyFailedNetwork() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));

        // e.g. the engine was restarted: a new manifest is built from the same player response
        SabrCdnSelector second = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(SECONDARY_URL, second.getCurrentUrl());
    }

    @Test
    public void lastNetworkIsKeptEvenIfAllFailed() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));
        assertFalse(first.maybeAdvance(SECONDARY_URL));

        SabrCdnSelector second = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(SECONDARY_URL, second.getCurrentUrl());
    }

    @Test
    public void failedNetworkDoesNotAffectUnrelatedUrls() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));

        String other = "https://rr3---sn-other.googlevideo.com/videoplayback?"
                + "mn=sn-other%2Csn-secondary&sig=signed";

        assertEquals(other, new SabrCdnSelector(other).getCurrentUrl());
    }
}
