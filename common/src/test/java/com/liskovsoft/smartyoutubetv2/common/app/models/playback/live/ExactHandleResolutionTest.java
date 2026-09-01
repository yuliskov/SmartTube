package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExactHandleResolutionTest {
    private static final String CHANNEL = "UC1234567890123456789012";

    @Test public void resolvesExactHandleFromChannelMetadata() {
        assertEquals(CHANNEL, MediaServiceLiveChannelProvider.findExactChannelId(
                Collections.singletonList(group(item("Smart Tube", "@SmartTubeApp • 1M subscribers"))),
                "@SmartTubeApp"));
    }

    @Test public void rejectsUnrelatedFuzzyChannelResult() {
        assertNull(MediaServiceLiveChannelProvider.findExactChannelId(
                Collections.singletonList(group(item("SmartTube Fans", "@SmartTubeFans"))),
                "@SmartTubeApp"));
    }

    private static MediaGroup group(MediaItem item) {
        return (MediaGroup) Proxy.newProxyInstance(MediaGroup.class.getClassLoader(),
                new Class<?>[]{MediaGroup.class}, (proxy, method, args) -> {
                    if ("getMediaItems".equals(method.getName())) return Collections.singletonList(item);
                    return defaultValue(method.getReturnType());
                });
    }

    private static MediaItem item(String title, String secondTitle) {
        return (MediaItem) Proxy.newProxyInstance(MediaItem.class.getClassLoader(),
                new Class<?>[]{MediaItem.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getType": return MediaItem.TYPE_CHANNEL;
                        case "getChannelId": return CHANNEL;
                        case "getTitle": return title;
                        case "getSecondTitle": return secondTitle;
                        default: return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
