package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModernIconsTest {
    @Test
    public void knownSections_mapToOutlinedOrFilled() {
        assertEquals(R.drawable.modern_ic_home, ModernIcons.forSection(MediaGroup.TYPE_HOME, false));
        assertEquals(R.drawable.modern_ic_home_filled, ModernIcons.forSection(MediaGroup.TYPE_HOME, true));
        assertEquals(R.drawable.modern_ic_subscriptions, ModernIcons.forSection(MediaGroup.TYPE_SUBSCRIPTIONS, false));
        assertEquals(R.drawable.modern_ic_history_filled, ModernIcons.forSection(MediaGroup.TYPE_HISTORY, true));
        assertEquals(R.drawable.modern_ic_settings, ModernIcons.forSection(MediaGroup.TYPE_SETTINGS, false));
    }

    @Test
    public void unknownSection_mapsToPin() {
        assertEquals(R.drawable.modern_ic_push_pin, ModernIcons.forSection(123456, false));
        assertEquals(R.drawable.modern_ic_push_pin_filled, ModernIcons.forSection(123456, true));
    }
}
