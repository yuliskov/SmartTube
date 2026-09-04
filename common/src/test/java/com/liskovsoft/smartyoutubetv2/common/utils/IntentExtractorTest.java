package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IntentExtractorTest {
    @Test
    public void controlPlaybackIntent_parsesAreaAndAction() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("smarttube://control/playback?action=play"));

        assertTrue(IntentExtractor.isControlCommand(intent));
        assertEquals("playback", IntentExtractor.extractControlArea(intent));
        assertEquals("play", IntentExtractor.extractControlAction(intent));
    }

    @Test
    public void controlQueueIntent_parsesVideoIdFromUrl() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "smarttube://control/queue?action=add&url=https%3A%2F%2Fyoutu.be%2FdQw4w9WgXcQ"));

        assertEquals("dQw4w9WgXcQ", IntentExtractor.extractControlVideoId(intent));
    }

    @Test
    public void controlPlaylistIntent_parsesPlaylistAndPosition() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "smarttube://control/playlist?action=play&playlistId=PL123&positionMs=12000"));

        assertEquals("PL123", IntentExtractor.extractControlPlaylistId(intent));
        assertEquals(12_000L, IntentExtractor.extractControlPositionMs(intent));
    }

    @Test
    public void nonSmartTubeIntent_isControlCommandReturnsFalse() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        assertFalse(IntentExtractor.isControlCommand(intent));
    }

    @Test
    public void controlIntent_emptyPath_extractControlAreaReturnsNull() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("smarttube://control"));

        assertTrue(IntentExtractor.isControlCommand(intent));
        assertNull(IntentExtractor.extractControlArea(intent));
    }

    @Test
    public void controlIntent_missingAction_extractControlActionReturnsNull() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("smarttube://control/playback"));

        assertNull(IntentExtractor.extractControlAction(intent));
    }

    @Test
    public void controlQueueIntent_bareVideoId_parsesCorrectly() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "smarttube://control/queue?action=add&videoId=dQw4w9WgXcQ"));

        assertEquals("dQw4w9WgXcQ", IntentExtractor.extractControlVideoId(intent));
    }

    @Test
    public void controlIntent_missingVideoId_extractControlVideoIdReturnsNull() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "smarttube://control/queue?action=add"));

        assertNull(IntentExtractor.extractControlVideoId(intent));
    }

    @Test
    public void controlIntent_missingPositionMs_returnsNegativeOne() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "smarttube://control/playback?action=seek"));

        assertEquals(-1L, IntentExtractor.extractControlPositionMs(intent));
    }
}
