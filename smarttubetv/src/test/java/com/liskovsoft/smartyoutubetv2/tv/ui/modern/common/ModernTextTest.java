package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ModernTextTest {
    @Test
    public void allCaps_becomesSentenceCase() {
        assertEquals("Live", ModernText.sentenceCase("LIVE", Locale.ENGLISH));
        assertEquals("Spazio", ModernText.sentenceCase("SPAZIO", Locale.ITALIAN));
        assertEquals("Top 20 music", ModernText.sentenceCase("TOP 20 MUSIC", Locale.ENGLISH));
    }

    @Test
    public void mixedCase_isKept() {
        assertEquals("Watch later", ModernText.sentenceCase("Watch later", Locale.ENGLISH));
        assertEquals("iPhone", ModernText.sentenceCase("iPhone", Locale.ENGLISH));
        assertEquals("Home", ModernText.sentenceCase("Home", Locale.ENGLISH));
    }

    @Test
    public void shortOrEmpty_isKept() {
        assertEquals("A", ModernText.sentenceCase("A", Locale.ENGLISH));
        assertEquals("4K", ModernText.sentenceCase("4K", Locale.ENGLISH));
        assertEquals("", ModernText.sentenceCase("", Locale.ENGLISH));
        assertNull(ModernText.sentenceCase(null, Locale.ENGLISH));
    }
}
