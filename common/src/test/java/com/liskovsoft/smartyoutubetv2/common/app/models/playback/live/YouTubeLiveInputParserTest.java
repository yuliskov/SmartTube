package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YouTubeLiveInputParserTest {
    private final YouTubeLiveInputParser parser = new YouTubeLiveInputParser();

    @Test public void parsesVideoId() { assertVideo("dQw4w9WgXcQ"); }
    @Test public void parsesWatchUrlAndDropsTracking() { assertVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=x"); }
    @Test public void parsesLiveUrl() { assertVideo("https://youtube.com/live/dQw4w9WgXcQ?feature=share"); }
    @Test public void parsesShortUrl() { assertVideo("https://youtu.be/dQw4w9WgXcQ?t=12"); }
    @Test public void parsesChannelId() { assertChannel("UC1234567890123456789012"); }
    @Test public void parsesChannelUrl() { assertChannel("https://youtube.com/channel/UC1234567890123456789012"); }
    @Test public void parsesHandle() { assertChannel("@SmartTubeApp"); }
    @Test public void parsesHandleLiveUrl() { assertChannel("https://www.youtube.com/@SmartTubeApp/live?view=2"); }
    @Test public void parsesChannelLiveUrl() { assertChannel("https://youtube.com/channel/UC1234567890123456789012/live"); }
    @Test public void parsesLegacyCustomChannelUrl() { assertChannel("https://youtube.com/c/SmartTubeApp/live"); }
    @Test public void parsesLegacyUserChannelUrl() { assertChannel("https://youtube.com/user/SmartTubeApp"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedHost() { parser.parse("https://example.com/watch?v=dQw4w9WgXcQ"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRandomText() { parser.parse("this is not a channel"); }

    private void assertVideo(String input) {
        LiveInput parsed = parser.parse(input);
        assertEquals(LiveInput.Type.VIDEO, parsed.getType());
        assertEquals("dQw4w9WgXcQ", parsed.getValue());
    }

    private void assertChannel(String input) {
        LiveInput parsed = parser.parse(input);
        assertEquals(LiveInput.Type.CHANNEL, parsed.getType());
    }
}
