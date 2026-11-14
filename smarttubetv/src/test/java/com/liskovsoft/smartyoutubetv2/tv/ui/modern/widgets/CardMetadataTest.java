package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CardMetadataTest {
    @Test
    public void authorViewsAge_splitsIntoTwoLines() {
        CardMetadata meta = CardMetadata.parse("BERRY • 80K views • 2 years ago");

        assertNull(meta.label);
        assertEquals("BERRY", meta.line1);
        assertEquals("80K views • 2 years ago", meta.line2);
    }

    @Test
    public void leadingLabel_isExtractedAsBadge() {
        CardMetadata meta = CardMetadata.parse("4K • Epico! • 76K views • 1 day ago");

        assertEquals("4K", meta.label);
        assertEquals("Epico!", meta.line1);
        assertEquals("76K views • 1 day ago", meta.line2);
    }

    @Test
    public void singlePart_isAuthorOnly() {
        CardMetadata meta = CardMetadata.parse("Tintoria Podcast");

        assertEquals("Tintoria Podcast", meta.line1);
        assertNull(meta.line2);
    }

    @Test
    public void viewsOnly_goesToSecondLine() {
        CardMetadata meta = CardMetadata.parse("1.4M views • 5 days ago");

        assertNull(meta.line1);
        assertEquals("1.4M views • 5 days ago", meta.line2);
    }

    @Test
    public void liveMark_isRemoved() {
        CardMetadata meta = CardMetadata.parse("Sky TG24 • 3K watching • LIVE");

        assertEquals("Sky TG24", meta.line1);
        assertEquals("3K watching", meta.line2);
    }

    @Test
    public void empty_returnsNoLines() {
        CardMetadata meta = CardMetadata.parse(null);

        assertNull(meta.line1);
        assertNull(meta.line2);
        assertNull(meta.label);
    }
}
