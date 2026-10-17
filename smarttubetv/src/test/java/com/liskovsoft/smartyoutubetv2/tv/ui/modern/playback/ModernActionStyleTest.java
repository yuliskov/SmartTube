package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModernActionStyleTest {
    @Test
    public void groups_followReferenceLayout() {
        assertEquals(ModernActionStyle.GROUP_LEFT, ModernActionStyle.groupOf(R.id.action_channel, false));
        assertEquals(ModernActionStyle.GROUP_LEFT, ModernActionStyle.groupOf(R.id.action_info, false));
        assertEquals(ModernActionStyle.GROUP_SOCIAL, ModernActionStyle.groupOf(R.id.action_thumbs_up, false));
        assertEquals(ModernActionStyle.GROUP_SOCIAL, ModernActionStyle.groupOf(R.id.action_chat, false));
        assertEquals(ModernActionStyle.GROUP_OTHER, ModernActionStyle.groupOf(R.id.lb_control_high_quality, false));
        assertEquals(ModernActionStyle.GROUP_OTHER, ModernActionStyle.groupOf(R.id.action_video_speed, false));
        assertEquals(ModernActionStyle.GROUP_TRANSPORT, ModernActionStyle.groupOf(R.id.action_video_speed, true));
    }

    @Test
    public void shapes() {
        assertEquals(ModernActionStyle.SHAPE_AVATAR, ModernActionStyle.shapeOf(R.id.action_channel));
        // The video description is the "i" icon (final layout of the player)
        assertEquals(ModernActionStyle.SHAPE_ICON, ModernActionStyle.shapeOf(R.id.action_info));
        assertEquals(R.drawable.modern_ic_info, ModernActionStyle.iconOf(R.id.action_info, false));
        assertEquals(ModernActionStyle.SHAPE_ICON, ModernActionStyle.shapeOf(R.id.action_subscribe));
        assertEquals(ModernActionStyle.SHAPE_ICON, ModernActionStyle.shapeOf(R.id.action_thumbs_up));
    }

    @Test
    public void icons_dependOnState() {
        assertEquals(R.drawable.modern_ic_thumb_up, ModernActionStyle.iconOf(R.id.action_thumbs_up, false));
        assertEquals(R.drawable.modern_ic_thumb_up_filled, ModernActionStyle.iconOf(R.id.action_thumbs_up, true));
        assertEquals(R.drawable.modern_ic_settings, ModernActionStyle.iconOf(R.id.lb_control_high_quality, false));
        assertEquals(0, ModernActionStyle.iconOf(R.id.action_channel, false));
        assertEquals(R.drawable.modern_ic_notifications, ModernActionStyle.iconOf(R.id.action_subscribe, false));
        assertEquals(R.drawable.modern_ic_notifications_active_filled, ModernActionStyle.iconOf(R.id.action_subscribe, true));
    }

    @Test
    public void startGroup_avatarDescriptionSubscription() {
        int avatar = ModernActionStyle.startOrder(R.id.action_channel);
        int description = ModernActionStyle.startOrder(R.id.action_info);
        int subscription = ModernActionStyle.startOrder(R.id.action_subscribe);
        org.junit.Assert.assertTrue(avatar < description && description < subscription);
    }
}
