package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Navigation icons of the modern interface (Material Symbols Rounded).<br/>
 * Selected sections use the filled variant.
 */
public final class ModernIcons {
    private static final Map<Integer, int[]> SECTION_ICONS = new HashMap<>();
    private static final Map<Integer, Integer> SETTINGS_ICONS = new HashMap<>();

    static {
        put(MediaGroup.TYPE_HOME, R.drawable.modern_ic_home, R.drawable.modern_ic_home_filled);
        put(MediaGroup.TYPE_SHORTS, R.drawable.modern_ic_smart_display, R.drawable.modern_ic_smart_display_filled);
        put(MediaGroup.TYPE_SUBSCRIPTIONS, R.drawable.modern_ic_subscriptions, R.drawable.modern_ic_subscriptions_filled);
        put(MediaGroup.TYPE_HISTORY, R.drawable.modern_ic_history, R.drawable.modern_ic_history_filled);
        put(MediaGroup.TYPE_USER_PLAYLISTS, R.drawable.modern_ic_video_library, R.drawable.modern_ic_video_library_filled);
        put(MediaGroup.TYPE_MY_VIDEOS, R.drawable.modern_ic_playlist_play, R.drawable.modern_ic_playlist_play_filled);
        put(MediaGroup.TYPE_CHANNEL_UPLOADS, R.drawable.modern_ic_account_circle, R.drawable.modern_ic_account_circle_filled);
        put(MediaGroup.TYPE_PLAYBACK_QUEUE, R.drawable.modern_ic_queue_music, R.drawable.modern_ic_queue_music_filled);
        put(MediaGroup.TYPE_NOTIFICATIONS, R.drawable.modern_ic_notifications, R.drawable.modern_ic_notifications_filled);
        put(MediaGroup.TYPE_MUSIC, R.drawable.modern_ic_music_note, R.drawable.modern_ic_music_note_filled);
        put(MediaGroup.TYPE_GAMING, R.drawable.modern_ic_sports_esports, R.drawable.modern_ic_sports_esports_filled);
        put(MediaGroup.TYPE_NEWS, R.drawable.modern_ic_newspaper, R.drawable.modern_ic_newspaper_filled);
        put(MediaGroup.TYPE_LIVE, R.drawable.modern_ic_sensors, R.drawable.modern_ic_sensors_filled);
        put(MediaGroup.TYPE_SPORTS, R.drawable.modern_ic_trophy, R.drawable.modern_ic_trophy_filled);
        put(MediaGroup.TYPE_MOVIES, R.drawable.modern_ic_movie, R.drawable.modern_ic_movie_filled);
        put(MediaGroup.TYPE_TRENDING, R.drawable.modern_ic_trending_up, R.drawable.modern_ic_trending_up_filled);
        put(MediaGroup.TYPE_KIDS_HOME, R.drawable.modern_ic_child_care, R.drawable.modern_ic_child_care_filled);
        put(MediaGroup.TYPE_BLOCKED_CHANNELS, R.drawable.modern_ic_block, R.drawable.modern_ic_block_filled);
        put(MediaGroup.TYPE_SETTINGS, R.drawable.modern_ic_settings, R.drawable.modern_ic_settings_filled);

        SETTINGS_ICONS.put(R.drawable.settings_account, R.drawable.modern_ic_account_circle);
        SETTINGS_ICONS.put(R.drawable.settings_cast, R.drawable.modern_ic_cast);
        SETTINGS_ICONS.put(R.drawable.settings_language, R.drawable.modern_ic_language);
        SETTINGS_ICONS.put(R.drawable.settings_app, R.drawable.modern_ic_apps);
        SETTINGS_ICONS.put(R.drawable.settings_main_ui, R.drawable.modern_ic_dashboard);
        SETTINGS_ICONS.put(R.drawable.settings_player, R.drawable.modern_ic_play_circle);
        SETTINGS_ICONS.put(R.drawable.settings_afr, R.drawable.modern_ic_60fps_select);
        SETTINGS_ICONS.put(R.drawable.settings_subtitles, R.drawable.modern_ic_closed_caption);
        SETTINGS_ICONS.put(R.drawable.settings_search, R.drawable.modern_ic_search);
        SETTINGS_ICONS.put(R.drawable.settings_block, R.drawable.modern_ic_block);
        SETTINGS_ICONS.put(R.drawable.settings_dearrow, R.drawable.modern_ic_swap_vert);
        SETTINGS_ICONS.put(R.drawable.settings_backup, R.drawable.modern_ic_settings_backup_restore);
        SETTINGS_ICONS.put(R.drawable.settings_about, R.drawable.modern_ic_info);
    }

    private ModernIcons() {
    }

    public static int forSection(int mediaGroupType, boolean selected) {
        int[] icons = SECTION_ICONS.get(mediaGroupType);

        if (icons == null) {
            return selected ? R.drawable.modern_ic_push_pin_filled : R.drawable.modern_ic_push_pin;
        }

        return selected ? icons[1] : icons[0];
    }

    /**
     * Modern counterpart of a settings category icon, 0 when there is none.
     */
    public static int forSettings(int legacyIcon) {
        Integer icon = SETTINGS_ICONS.get(legacyIcon);
        return icon != null ? icon : 0;
    }

    private static void put(int mediaGroupType, int outlined, int filled) {
        SECTION_ICONS.put(mediaGroupType, new int[] {outlined, filled});
    }
}
