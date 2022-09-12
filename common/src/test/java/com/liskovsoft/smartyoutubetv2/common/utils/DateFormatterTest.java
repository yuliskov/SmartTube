package com.liskovsoft.smartyoutubetv2.common.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class DateFormatterTest {
    @Test
    public void testToUnixTimeMs() {
        long timeMs = DateFormatter.toUnixTimeMs("2022-09-11T23:39:38+00:00");
        assertTrue("time not null", timeMs > 0);
    }
}