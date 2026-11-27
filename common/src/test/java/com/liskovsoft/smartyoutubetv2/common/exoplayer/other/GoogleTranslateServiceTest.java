package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Validation of context translations, using real endpoint outputs (English source captions).
 * validate(...) returns null when the context result is accepted.
 */
public class GoogleTranslateServiceTest {
    private static String validate(String marked, String plain, String prevPlain, String nextPlain) {
        return GoogleTranslateService.validateContextResult(marked, plain, prevPlain, nextPlain);
    }

    // "and sat on the bank" after "we walked down to the river"
    @Test
    public void acceptsContextFixForAmbiguousWord_arabic() {
        assertNull(validate("وجلسنا على الضفة", "وجلس على البنك", "مشينا إلى النهر", "مشاهدة الضوء"));
    }

    @Test
    public void acceptsContextFixForAmbiguousWord_persian() {
        assertNull(validate("و در ساحل نشستیم", "و روی بانک نشست", "به سمت رودخانه رفتیم", "تماشای نور"));
    }

    @Test
    public void acceptsContextFixForImperative_german() {
        // "go ahead with the launch." after "and that we should just"
        assertNull(validate("mit dem Start fortfahren", "Fahren Sie mit dem Start fort.",
                "und das sollten wir einfach", "Rückblickend war das ein Fehler."));
    }

    // "actually decides whether to give you" followed by "a loan and why it matters":
    // the context result pulls "loan" (قرضًا) in from the next line.
    @Test
    public void rejectsWordPulledFromNextLine_arabic() {
        String reason = validate("في الواقع باتخاذ قرار بشأن منحك قرضًا", "يقرر في الواقع ما إذا كان سيعطيك",
                "نلقي نظرة على كيفية عمل البنك", "القرض ولماذا يهم");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("قرض"));
    }

    @Test
    public void rejectsWordPulledFromNextLine_german() {
        assertNotNull(validate("tatsächlich entscheidet, ob sie Ihnen einen Kredit gewährt",
                "entscheidet tatsächlich, ob ich es dir gebe",
                "Schauen Sie sich an, wie die Bank", "ein Darlehen und warum es wichtig ist"));
    }

    @Test
    public void rejectsLostMarker() {
        assertEquals("marker lost", validate(null, "das Licht beobachten", "und saß am Ufer", "bis es zu kalt wurde"));
        assertEquals("marker lost", validate("  ", "das Licht beobachten", null, null));
    }

    @Test
    public void rejectsMuchLongerResult() {
        assertEquals("too long", validate("one two three four five six seven eight nine ten eleven", "one two", null, null));
    }

    @Test
    public void acceptsContextWhenPlainFailed() {
        assertNull(validate("مشينا إلى النهر", null, null, "وجلس على البنك"));
    }
}
