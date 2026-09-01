package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackRequestHeadersTest {
    @Test
    public void selectedClientUserAgentTakesPrecedence() {
        MediaItemFormatInfo.ClientInfo clientInfo = clientInfo("VisionOS/1.0");

        assertEquals("VisionOS/1.0", PlaybackRequestHeaders.resolveUserAgent(clientInfo, "Default/1.0"));
    }

    @Test
    public void missingClientUserAgentUsesApplicationDefault() {
        assertEquals("Default/1.0", PlaybackRequestHeaders.resolveUserAgent(null, "Default/1.0"));
        assertEquals("Default/1.0", PlaybackRequestHeaders.resolveUserAgent(clientInfo(""), "Default/1.0"));
    }

    @Test
    public void appliesSelectedUserAgentAsExplicitCronetRequestProperty() {
        HttpDataSource.RequestProperties properties = new HttpDataSource.RequestProperties();

        PlaybackRequestHeaders.applyUserAgent(properties, "VisionOS/1.0");

        assertEquals("VisionOS/1.0", properties.getSnapshot().get("User-Agent"));
    }

    @Test
    public void newGenerationRebuildsFactoryEvenWhenUserAgentMatches() {
        assertTrue(PlaybackRequestHeaders.shouldRebuildFactory("UA", "UA", 10, 11));
        assertTrue(PlaybackRequestHeaders.shouldRebuildFactory("UA-1", "UA-2", 10, 10));
        assertFalse(PlaybackRequestHeaders.shouldRebuildFactory("UA", "UA", 10, 10));
    }

    private static MediaItemFormatInfo.ClientInfo clientInfo(String userAgent) {
        return new MediaItemFormatInfo.ClientInfo() {
            @Override
            public String getClientName() {
                return "TEST";
            }

            @Override
            public String getClientVersion() {
                return "1.0";
            }

            @Override
            public String getOsName() {
                return "TestOS";
            }

            @Override
            public String getOsVersion() {
                return "1";
            }

            @Override
            public String getUserAgent() {
                return userAgent;
            }
        };
    }
}
