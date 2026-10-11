package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BlockedChannelDataTest {
    private BlockedChannelData mBlockedChannelData;

    @Before
    public void setUp() throws Exception {
        resetSingleton(BlockedChannelData.class, "sInstance");
        resetSingleton(AppPrefs.class, "sInstance");
        mBlockedChannelData = BlockedChannelData.instance(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() throws Exception {
        Utils.sHandler.removeCallbacksAndMessages(null);
        resetSingleton(BlockedChannelData.class, "sInstance");
        resetSingleton(AppPrefs.class, "sInstance");
    }

    @Test
    public void sameNameChannelsRemainSeparateAndMatchById() {
        mBlockedChannelData.addChannel("UCaaa", "News");
        mBlockedChannelData.addChannel("UCbbb", "News");

        assertEquals(2, mBlockedChannelData.getChannelCount());
        assertTrue(mBlockedChannelData.containsChannel("UCaaa", "News"));
        assertTrue(mBlockedChannelData.containsChannel("UCbbb", "News"));
        assertFalse(mBlockedChannelData.containsChannel("UCccc", "News"));

        mBlockedChannelData.removeChannel("UCaaa", "News");

        assertEquals(1, mBlockedChannelData.getChannelCount());
        assertTrue(mBlockedChannelData.containsChannel("UCbbb", "News"));
    }

    @Test
    public void renamedChannelStillMatchesAndCanBeRemovedById() {
        mBlockedChannelData.addChannel("UCaaa", "News");

        assertTrue(mBlockedChannelData.containsChannel("UCaaa", "News Daily"));

        mBlockedChannelData.removeChannel("UCaaa", "News Daily");

        assertFalse(mBlockedChannelData.containsChannel("UCaaa", "News"));
        assertEquals(0, mBlockedChannelData.getChannelCount());
    }

    @Test
    public void missingIdsFallBackToCaseInsensitiveNameMatching() {
        mBlockedChannelData.addChannel(null, "Home Channel");

        assertTrue(mBlockedChannelData.containsChannel(null, "home channel"));
        assertTrue(mBlockedChannelData.containsChannel("UCaaa", "HOME CHANNEL"));

        mBlockedChannelData.removeChannel("UCaaa", "HOME CHANNEL");

        assertTrue(mBlockedChannelData.isEmpty());

        mBlockedChannelData.addChannel("UCbbb", "Other Channel");

        assertTrue(mBlockedChannelData.containsChannel(null, "other channel"));

        mBlockedChannelData.removeChannel(null, "OTHER CHANNEL");

        assertTrue(mBlockedChannelData.isEmpty());
    }

    private static void resetSingleton(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }
}
